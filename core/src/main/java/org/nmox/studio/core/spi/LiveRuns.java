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
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private LiveRuns() {
    }

    public static void add(Run run) {
        synchronized (LIVE) {
            LIVE.put(run.id(), run);
        }
        notifyListeners();
    }

    public static void remove(String id) {
        boolean removed;
        synchronized (LIVE) {
            removed = LIVE.remove(id) != null;
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
