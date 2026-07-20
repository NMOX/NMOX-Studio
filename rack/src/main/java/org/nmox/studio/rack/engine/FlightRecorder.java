package org.nmox.studio.rack.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The flight recorder: a session-long memory of what every device did
 * and when. It folds raw bus traffic into events - launches, exits with
 * durations, error lines - so "it worked twenty minutes ago, what
 * happened since?" has an answer you can scroll instead of a feeling.
 *
 * It also keeps per-device duration statistics, which is how BLACKBOX
 * notices the slow creep: the build that quietly went from 1.2s to
 * 3.4s while you weren't measuring.
 */
public final class FlightRecorder implements RackBus.Listener {

    public enum Kind {
        LAUNCH, EXIT_OK, EXIT_FAIL, ERROR
    }

    /** One timeline entry. durationMs is -1 except on exits. */
    public record Event(long at, String device, Kind kind, String text, long durationMs) {
    }

    /** Rolling duration stats for one device's successful runs. */
    public static final class Stats {

        private long count;
        private double avgMs;
        private long lastMs = -1;
        private long lastOkAt = -1;

        void addOk(long ms) {
            lastMs = ms;
            count++;
            avgMs += (ms - avgMs) / count;
        }

        void stampOk(long at) {
            lastOkAt = at;
        }

        /** When this device last went green; -1 if never this tape. */
        public long lastOkAt() {
            return lastOkAt;
        }

        public long count() {
            return count;
        }

        public long averageMs() {
            return Math.round(avgMs);
        }

        public long lastMs() {
            return lastMs;
        }

        /** True when the latest run took notably longer than usual. */
        public boolean creeping() {
            return count >= 3 && lastMs > avgMs * 1.8 && lastMs - avgMs > 500;
        }
    }

    private static final FlightRecorder INSTANCE = new FlightRecorder();
    private static final int CAPACITY = 2_000;
    /** Error lines kept per run, so a 10k-line stack trace stays a sample. */
    private static final int ERRORS_PER_RUN = 5;

    private final Deque<Event> events = new ArrayDeque<>();
    private final Map<String, Long> launchAt = new HashMap<>();
    private final Map<String, Integer> errorsThisRun = new HashMap<>();
    private final Map<String, Stats> stats = new HashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final java.util.function.LongSupplier clock;

    private FlightRecorder() {
        this(System::currentTimeMillis);
        RackBus.subscribe(this);
    }

    /** Test constructor: injectable clock, no bus subscription. */
    FlightRecorder(java.util.function.LongSupplier clock) {
        this.clock = clock;
    }

    public static FlightRecorder getDefault() {
        return INSTANCE;
    }

    @Override
    public void line(String device, String line, boolean err) {
        long now = clock.getAsLong();
        synchronized (this) {
            if (line.startsWith("$ ")) {
                launchAt.put(device, now);
                errorsThisRun.put(device, 0);
                record(new Event(now, device, Kind.LAUNCH, line.substring(2), -1));
            } else if (line.startsWith("[exit ")) {
                int code = parseExit(line);
                Long started = launchAt.remove(device);
                long ms = started == null ? -1 : now - started;
                if (code == 0) {
                    Stats st = stats.computeIfAbsent(device, d -> new Stats());
                    st.addOk(ms);
                    st.stampOk(now);
                    record(new Event(now, device, Kind.EXIT_OK, "OK", ms));
                } else {
                    record(new Event(now, device, Kind.EXIT_FAIL, "exit " + code, ms));
                }
            } else if (err && !line.isBlank()) {
                int seen = errorsThisRun.merge(device, 1, Integer::sum);
                if (seen <= ERRORS_PER_RUN) {
                    record(new Event(now, device, Kind.ERROR, line, -1));
                }
            }
        }
        for (Runnable l : listeners) {
            try {
                l.run();
            } catch (RuntimeException ignored) {
            }
        }
    }

    static int parseExit(String line) {
        try {
            return Integer.parseInt(line.replaceAll("[^0-9-]", ""));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private void add(Event e) {
        events.addLast(e);
        while (events.size() > CAPACITY) {
            events.removeFirst();
        }
    }

    /**
     * add() + journal append - the path live events take. The in-memory add
     * stays under the caller's monitor (readers see a consistent tape); the
     * disk append is handed to JOURNAL_RP so no pump thread ever blocks on I/O
     * while holding the monitor. Posts happen in event order from the
     * synchronized line(), and JOURNAL_RP is single-threaded, so the journal
     * on disk stays in the same order as the tape in memory.
     */
    private void record(Event e) {
        add(e);
        JOURNAL_RP.post(() -> appendToJournal(e));
    }

    /**
     * Blocks until every append posted so far has hit the disk. The FIFO
     * barrier idiom (cf. Rack.awaitRouterIdle / RackDevice.awaitDeviceBgIdle):
     * for tests that read the journal file right after recording, and for a
     * clean-shutdown flush. Never call from the EDT in production.
     */
    static void awaitJournalIdle() {
        try {
            JOURNAL_RP.post(() -> { }).waitFinished(10_000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- reading the record ----

    public synchronized List<Event> timeline() {
        return new ArrayList<>(events);
    }

    public synchronized List<Event> errorsSince(long sinceMs) {
        List<Event> result = new ArrayList<>();
        for (Event e : events) {
            if (e.at() >= sinceMs && (e.kind() == Kind.ERROR || e.kind() == Kind.EXIT_FAIL)) {
                result.add(e);
            }
        }
        return result;
    }

    public synchronized Event last() {
        for (var it = events.descendingIterator(); it.hasNext();) {
            Event e = it.next();
            if (e.kind() == Kind.EXIT_OK || e.kind() == Kind.EXIT_FAIL) {
                return e;
            }
        }
        return null;
    }

    public synchronized Map<String, Stats> statistics() {
        return new HashMap<>(stats);
    }

    /** Any device whose latest run crept well past its average. */
    public synchronized String slowCreep() {
        for (Map.Entry<String, Stats> e : stats.entrySet()) {
            if (e.getValue().creeping()) {
                return e.getKey();
            }
        }
        return null;
    }

    // ---- the journal: the tape survives the JVM ----

    // volatile: written under the FlightRecorder monitor in attachJournal,
    // read on the JOURNAL_RP thread in appendToJournal
    private volatile java.io.File journal;
    private static final long JOURNAL_MAX_BYTES = 1_500_000;

    /**
     * The journal's disk I/O rides its own single-thread FIFO lane, OFF the
     * FlightRecorder monitor. That monitor gates every bus publish (so every
     * device's output-pump thread funnels through it) and every EDT reader
     * (timeline/export/statistics); doing the per-event {@code Files.writeString}
     * append and the readAllLines+write rotate under it meant one slow or full
     * disk could serialize and stall every pump at once, and block a UI read
     * behind a pump's write. Single-threaded so journal order stays event order.
     */
    private static final org.openide.util.RequestProcessor JOURNAL_RP =
            new org.openide.util.RequestProcessor("nmox-flightrec-journal", 1, true);

    /**
     * Attaches a JSONL journal: existing events load onto the tape (so
     * BLACKBOX remembers past sessions), and every new event appends.
     * Mosh for the flight record - the session outlives the process.
     */
    public synchronized void attachJournal(java.io.File file) {
        this.journal = file;
        try {
            if (file.isFile()) {
                for (String line : java.nio.file.Files.readAllLines(file.toPath())) {
                    Event e = eventFromJson(line);
                    if (e != null) {
                        add(e);
                    }
                }
            } else {
                java.nio.file.Files.createDirectories(file.getParentFile().toPath());
            }
        } catch (Exception ignored) {
            // an unreadable journal must never break recording
        }
    }

    private void appendToJournal(Event e) {
        if (journal == null) {
            return;
        }
        try {
            if (journal.length() > JOURNAL_MAX_BYTES) {
                rotateJournal();
            }
            java.nio.file.Files.writeString(journal.toPath(), eventToJson(e) + "\n",
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // disk trouble must never break recording
        }
    }

    /** Keeps the newer half when the journal outgrows its cap. */
    private void rotateJournal() throws java.io.IOException {
        java.util.List<String> lines = java.nio.file.Files.readAllLines(journal.toPath());
        java.util.List<String> keep = lines.subList(lines.size() / 2, lines.size());
        java.nio.file.Files.write(journal.toPath(), keep);
    }

    static String eventToJson(Event e) {
        return new org.json.JSONObject()
                .put("at", e.at()).put("device", e.device())
                .put("kind", e.kind().name()).put("text", e.text())
                .put("ms", e.durationMs()).toString();
    }

    static Event eventFromJson(String line) {
        try {
            org.json.JSONObject o = new org.json.JSONObject(line);
            return new Event(o.getLong("at"), o.getString("device"),
                    Kind.valueOf(o.getString("kind")), o.getString("text"),
                    o.optLong("ms", -1));
        } catch (RuntimeException ex) {
            return null; // a corrupt line is not a corrupt tape
        }
    }

    public void addChangeListener(Runnable r) {
        listeners.add(r);
    }

    public void removeChangeListener(Runnable r) {
        listeners.remove(r);
    }

    /** The whole record as text, for the EXPORT button and bug reports. */
    public synchronized String export() {
        StringBuilder sb = new StringBuilder("NMOX flight log\n");
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("HH:mm:ss");
        for (Event e : events) {
            sb.append(fmt.format(new java.util.Date(e.at()))).append("  ")
                    .append(String.format("%-10s", e.device())).append(" ")
                    .append(String.format("%-9s", e.kind())).append(" ")
                    .append(e.text());
            if (e.durationMs() >= 0) {
                sb.append("  (").append(e.durationMs() / 1000.0).append("s)");
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
