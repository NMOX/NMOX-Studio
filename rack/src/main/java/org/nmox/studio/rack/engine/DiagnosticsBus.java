package org.nmox.studio.rack.engine;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Carries structured problems from rack tools (eslint, tsc) to anyone
 * who can show them - the editor module subscribes and turns them
 * into in-editor squiggles. Each tool replaces its own previous batch
 * per run, so stale problems vanish when a clean run completes.
 */
public final class DiagnosticsBus {

    /** One problem at a location. */
    public record Problem(File file, int line, String message, boolean error) {
    }

    public interface Listener {
        /** A tool published a fresh batch (possibly empty = all clear). */
        void published(String tool, List<Problem> problems);
    }

    private static final Map<String, List<Problem>> BY_TOOL = new ConcurrentHashMap<>();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private DiagnosticsBus() {
    }

    public static void publish(String tool, List<Problem> problems) {
        BY_TOOL.put(tool, List.copyOf(problems));
        for (Listener l : LISTENERS) {
            l.published(tool, problems);
        }
    }

    public static void addListener(Listener l) {
        LISTENERS.add(l);
        // late subscribers get the current state
        for (Map.Entry<String, List<Problem>> e : BY_TOOL.entrySet()) {
            l.published(e.getKey(), e.getValue());
        }
    }

    /** All problems for a file across tools (for the squiggle layer). */
    public static List<Problem> problemsFor(File file) {
        List<Problem> result = new ArrayList<>();
        for (List<Problem> batch : BY_TOOL.values()) {
            for (Problem p : batch) {
                if (p.file().equals(file)) {
                    result.add(p);
                }
            }
        }
        return result;
    }
}
