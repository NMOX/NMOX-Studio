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
        return true;
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

    /** "since HH:mm" for a live run, in the local zone; empty when not live. */
    public static String since(String id) {
        return since(startedAt(id), java.time.ZoneId.systemDefault());
    }

    static String since(long startedAt, java.time.ZoneId zone) {
        if (startedAt < 0) {
            return "";
        }
        return "since " + java.time.Instant.ofEpochMilli(startedAt).atZone(zone)
                .toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }

    public static void remove(String id) {
        boolean removed;
        synchronized (LIVE) {
            removed = LIVE.remove(id) != null;
            STARTED.remove(id);
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

    /** Kills ONE live run and forgets it (the row's own Stop, v2.70.0); null when no such run. */
    public static Run stop(String id) {
        Run r;
        synchronized (LIVE) {
            r = LIVE.remove(id);
            STARTED.remove(id);
        }
        if (r != null) {
            r.killer().run();
            notifyListeners();
        }
        return r;
    }

    /** Kills every live run and forgets it; returns what was stopped, in spawn order. */
    public static List<Run> stopAll() {
        List<Run> stopped;
        synchronized (LIVE) {
            stopped = new ArrayList<>(LIVE.values());
            LIVE.clear();
            STARTED.clear();
        }
        for (Run r : stopped) {
            r.killer().run();
        }
        if (!stopped.isEmpty()) {
            notifyListeners();
        }
        return stopped;
    }

    /**
     * The ■'s tooltip (and accessible description) BEFORE a press: what it
     * would stop, by label, with a count — a disabled button that only says
     * "Stop Running Command" leaves the user guessing which command
     * (v2.71.0). Pure; the toolbar action re-reads it on every change.
     */
    public static String tooltip(List<Run> live) {
        if (live.isEmpty()) {
            return "Stop Running Command — nothing is running";
        }
        StringBuilder sb = new StringBuilder(live.size() == 1
                ? "Stop the running command: " : "Stop " + live.size() + " running commands: ");
        for (int i = 0; i < live.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(live.get(i).label());
        }
        return sb.toString();
    }

    /** The status line after a ■ press: what was stopped, or that nothing was running. */
    public static String stoppedMessage(List<Run> stopped) {
        if (stopped.isEmpty()) {
            return "Nothing is running";
        }
        StringBuilder sb = new StringBuilder("Stopped: ");
        for (int i = 0; i < stopped.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(stopped.get(i).label());
        }
        return sb.toString();
    }

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
