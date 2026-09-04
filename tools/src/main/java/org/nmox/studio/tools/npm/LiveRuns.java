package org.nmox.studio.tools.npm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The IDE's own running commands (Run/Build/Test/Clean from the toolbar and
 * the Run menu), so a Stop can find them. David's walk of 2.69.8: the ▶
 * started a server and nothing on screen stopped it — the only stop was the
 * Cancel inside the status-bar progress popup, which nobody finds. Pure
 * registry: add on spawn, remove on exit, {@link #stopAll()} kills every
 * live one through its killer; listeners follow the count (any thread —
 * the toolbar action marshals to the EDT itself).
 */
public final class LiveRuns {

    /** One running command: its id, the label the user saw, and how to kill it. */
    public record Run(String id, String label, Runnable killer) {
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
