package org.nmox.studio.core.spi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The IDE's own running commands, so a Stop can find them: Run/Build/
 * Test/Clean from the toolbar and the Run menu (v2.69.10), NPM Explorer's
 * and Run Script's spawns, and the editor's Focused Test / Tests-window
 * runs (v2.70.0 — the registry moved here from the tools module so the
 * editor lane could join; a pure registry has no module to belong to).
 * David's walk of 2.69.8: the ▶ started a server and nothing on screen
 * stopped it — the only stop was the Cancel inside the status-bar
 * progress popup, which nobody finds. Add on spawn, remove on exit,
 * {@link #stopAll()} kills every live one through its killer; listeners
 * follow the count (any thread — the toolbar action marshals to the EDT
 * itself).
 *
 * <p><b>A stopped run stays until it has exited (3.4).</b> Stop used to
 * remove the row before the kill ran, so the ■ and the Workbench said
 * "stopped" for up to the kill's three-second grace while a root that
 * traps SIGTERM was still alive — and forever for a child that escaped.
 * Now a stop marks the run {@link #isStopping stopping}, runs its killer
 * once, and the row leaves when the run's own exit handler calls
 * {@link #remove} — the contract every registering site already keeps.
 */
public final class LiveRuns {

    /**
     * One running command: its id, the label the user saw, and how to kill
     * it. The label reaches platform-owned Swing text (the status line is a
     * JLabel, Run ▸ Stop Build/Run is a JMenuItem — both decompiled) and
     * Swing renders a string that BEGINS with {@code <html>} as markup, an
     * {@code <img src>} fetching at paint time (the v1.208.0 class). Every
     * caller's label starts with fixed text today; the record keeps the
     * shape true by construction rather than by convention.
     */
    public record Run(String id, String label, Runnable killer) {
        public Run {
            label = plainLeading(label);
        }
    }

    /** A label that can never be taken for markup: a leading {@code <html} is set off by a space. */
    static String plainLeading(String label) {
        if (label != null && label.regionMatches(true, 0, "<html", 0, 5)) {
            return " " + label;
        }
        return label;
    }

    private static final Map<String, Run> LIVE = new LinkedHashMap<>();

    /**
     * Runs the USER stopped (v2.73.0 review): a deliberate Stop — the ■, a
     * RUNNING row, ⌘I, the Tests window — ends the process with the same
     * exit code a crash would, and the exit handlers that report failure
     * (the wizard's "install didn't finish" dialog, "Focused test FAILED
     * [143]") could not tell the two apart: the v2.69.15 law (STOP reads
     * STOPPED) one registry over. Marked at the stop, consumed by the exit
     * handler's {@link #wasStoppedByUser}; bounded like the tombstones.
     */
    private static final java.util.LinkedHashSet<String> STOPPED_BY_USER = new java.util.LinkedHashSet<>();

    /** Live runs a stop has been asked of; they leave {@link #LIVE} at their exit (3.4). */
    private static final java.util.Set<String> STOPPING = new java.util.HashSet<>();

    /** When each live run was registered (v2.73.0) — the Workbench row says "since 10:41". */
    private static final Map<String, Long> STARTED = new java.util.HashMap<>();

    /**
     * Ids withdrawn BEFORE they were added (v2.71.0 review find): when a
     * launch fails — the tool not on PATH, the beginner's commonest wall —
     * CommandExecutor.run fires the exit callback synchronously, before it
     * returns, so every "register after the spawn" site removed a run that
     * was not there yet and then added a phantom: the ■ lit for a command
     * that never started and "stopped" a no-op handle. A withdrawal of an
     * unknown id leaves a tombstone; the late add sees it and is dropped.
     * Ids are unique per spawn, so a tombstone can never block a real run;
     * the set is bounded (the oldest tombstone is forgotten past 256).
     */
    private static final java.util.LinkedHashSet<String> WITHDRAWN = new java.util.LinkedHashSet<>();
    private static final int TOMBSTONES = 256;
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private LiveRuns() {
    }

    /** Registers a run; returns false when its exit already came through (a launch failure). */
    public static boolean add(Run run) {
        synchronized (LIVE) {
            if (WITHDRAWN.remove(run.id())) {
                return false; // withdrawn before it was added: never live
            }
            LIVE.put(run.id(), run);
            STARTED.put(run.id(), clock.getAsLong());
        }
        notifyListeners();
        java.util.function.Consumer<String> watcher = ADDS_WATCHED.get();
        if (watcher != null) {
            watcher.accept(run.id());
        }
        return true;
    }

    /** Who hears the runs added on this thread, inside {@link #watchingAdds}; null outside one. */
    private static final ThreadLocal<java.util.function.Consumer<String>> ADDS_WATCHED = new ThreadLocal<>();

    /**
     * Runs {@code body} and tells {@code sink} the id of every run {@link
     * #add added} on THIS thread while it runs, once that run is live —
     * so that a caller that starts work through another lane (a VS Code
     * task chain starting a task, which the npm lane may spawn) can stop
     * exactly what it started without that lane handing its ids back.
     * A run withdrawn before it was added was never live and is not told.
     * Nests: the inner sink hears what the inner body adds.
     */
    public static <T> T watchingAdds(java.util.function.Consumer<String> sink,
            java.util.function.Supplier<T> body) {
        java.util.function.Consumer<String> outer = ADDS_WATCHED.get();
        ADDS_WATCHED.set(sink);
        try {
            return body.get();
        } finally {
            if (outer == null) {
                ADDS_WATCHED.remove();
            } else {
                ADDS_WATCHED.set(outer);
            }
        }
    }

    private static void markStopped(String id) {
        STOPPED_BY_USER.add(id);
        if (STOPPED_BY_USER.size() > TOMBSTONES) {
            STOPPED_BY_USER.remove(STOPPED_BY_USER.iterator().next());
        }
    }

    /**
     * Whether the user stopped this run (through any Stop surface) — for
     * the exit handler that would otherwise report a failure. Consumed:
     * true once, so a later run under a reused id starts clean.
     */
    public static boolean wasStoppedByUser(String id) {
        synchronized (LIVE) {
            return STOPPED_BY_USER.remove(id);
        }
    }

    /** The clock behind {@link #startedAt}; tests pin it. */
    private static java.util.function.LongSupplier clock = System::currentTimeMillis;

    static void clockForTest(java.util.function.LongSupplier c) {
        clock = c == null ? System::currentTimeMillis : c;
    }

    /** Epoch millis the run was registered, or -1 when it is not live. */
    public static long startedAt(String id) {
        synchronized (LIVE) {
            Long t = STARTED.get(id);
            return t == null || !LIVE.containsKey(id) ? -1L : t;
        }
    }

    /**
     * "HH:mm" for a live run, in the local zone; empty when not live.
     *
     * <p>The bare time is DATA, and that is the whole point (ledger 88,
     * closed v2.100.0). This used to return the phrase {@code "since HH:mm"},
     * which every caller then handed to a bundle as {@code {0}} — so an
     * English word rode into all twelve translated builds underneath every
     * l10n gate, because the bundles themselves were correct. The word that
     * introduces the time belongs to each language's own key, where a
     * translator can choose it; Hindi had already written its postposition
     * that way and was reading it twice. A string handed to a bundle as an
     * argument is a name, a path, or a number — never prose.
     */
    public static String sinceTime(String id) {
        return sinceTime(startedAt(id), java.time.ZoneId.systemDefault());
    }

    static String sinceTime(long startedAt, java.time.ZoneId zone) {
        if (startedAt < 0) {
            return "";
        }
        return org.nmox.studio.core.util.Clocks.display(startedAt, zone);
    }

    public static void remove(String id) {
        boolean removed;
        synchronized (LIVE) {
            removed = LIVE.remove(id) != null;
            STARTED.remove(id);
            STOPPING.remove(id);
            if (!removed) {
                WITHDRAWN.add(id);
                if (WITHDRAWN.size() > TOMBSTONES) {
                    WITHDRAWN.remove(WITHDRAWN.iterator().next());
                }
            }
        }
        if (removed) {
            notifyListeners();
        }
    }

    /** Live runs in spawn order. */
    public static List<Run> live() {
        synchronized (LIVE) {
            return new ArrayList<>(LIVE.values());
        }
    }

    /**
     * Stops ONE live run (the row's own Stop, v2.70.0): runs its killer
     * and marks it stopping; the run stays live until its exit handler
     * removes it (3.4). Null when there is no such run or a stop is
     * already under way — a second press kills nothing twice.
     */
    public static Run stop(String id) {
        Run r;
        synchronized (LIVE) {
            r = LIVE.get(id);
            if (r == null || !STOPPING.add(id)) {
                return null;
            }
            markStopped(id);
        }
        kill(r);
        notifyListeners();
        return r;
    }

    /**
     * Runs a stop's killer. One that throws leaves the run as it was — not
     * "stopping…" forever with a second press answering "Already stopping"
     * about a stop that never happened (the 3.4 review) — and says so.
     */
    private static void kill(Run r) {
        try {
            r.killer().run();
        } catch (RuntimeException failed) {
            synchronized (LIVE) {
                STOPPING.remove(r.id());
            }
            java.util.logging.Logger.getLogger(LiveRuns.class.getName()).log(java.util.logging.Level.WARNING,
                    // a log token, not prose: core.spi carries no sentence (SpiHoldsNoProseTest)
                    "stop-failed:" + r.id() + " [" + r.label() + "]", failed);
        }
    }

    /**
     * Stops every live run not already stopping; returns what this press
     * stopped, in spawn order. The runs stay live, marked stopping, until
     * each one's exit removes it (3.4).
     */
    public static List<Run> stopAll() {
        List<Run> stopped = new ArrayList<>();
        synchronized (LIVE) {
            for (Run r : LIVE.values()) {
                if (STOPPING.add(r.id())) {
                    markStopped(r.id());
                    stopped.add(r);
                }
            }
        }
        for (Run r : stopped) {
            kill(r);
        }
        if (!stopped.isEmpty()) {
            notifyListeners();
        }
        return stopped;
    }

    /** Whether a stop has been asked of this live run and it has not exited yet (3.4). */
    public static boolean isStopping(String id) {
        synchronized (LIVE) {
            return STOPPING.contains(id) && LIVE.containsKey(id);
        }
    }

    /**
     * Forgets every run without killing anything. Tests only: a test's
     * fixture killers are lambdas with no process behind them, so no exit
     * handler will ever remove their runs.
     */
    public static void clearForTest() {
        synchronized (LIVE) {
            LIVE.clear();
            STARTED.clear();
            STOPPING.clear();
            WITHDRAWN.clear();
            STOPPED_BY_USER.clear();
        }
    }

    // The ■'s tooltip and its status line USED to be assembled here, in
    // English, and handed to a Swing sink — so they were English in every
    // translated build while every bundle in the product was complete
    // (ledger 89, closed v2.101.0). They live in the consumer now, with a
    // bundle behind them: the core returns the data, the consumer renders it.


    public static void addListener(Runnable l) {
        LISTENERS.add(l);
    }

    public static void removeListener(Runnable l) {
        LISTENERS.remove(l);
    }

    private static void notifyListeners() {
        for (Runnable l : LISTENERS) {
            l.run();
        }
    }
}
