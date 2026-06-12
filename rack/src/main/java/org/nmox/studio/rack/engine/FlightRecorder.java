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

        void addOk(long ms) {
            lastMs = ms;
            count++;
            avgMs += (ms - avgMs) / count;
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
                add(new Event(now, device, Kind.LAUNCH, line.substring(2), -1));
            } else if (line.startsWith("[exit ")) {
                int code = parseExit(line);
                Long started = launchAt.remove(device);
                long ms = started == null ? -1 : now - started;
                if (code == 0) {
                    stats.computeIfAbsent(device, d -> new Stats()).addOk(ms);
                    add(new Event(now, device, Kind.EXIT_OK, "OK", ms));
                } else {
                    add(new Event(now, device, Kind.EXIT_FAIL, "exit " + code, ms));
                }
            } else if (err && !line.isBlank()) {
                int seen = errorsThisRun.merge(device, 1, Integer::sum);
                if (seen <= ERRORS_PER_RUN) {
                    add(new Event(now, device, Kind.ERROR, line, -1));
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
