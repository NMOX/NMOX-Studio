package org.nmox.studio.editor.diagnostics;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.nmox.studio.rack.engine.DiagnosticsBus;

/**
 * The pure half of the Action Items bridge (tech-debt #32): turns
 * DiagnosticsBus batches into per-file Task List payloads, carrying the
 * bus's replace-per-run semantics through — when a tool publishes a fresh
 * batch, every file its PREVIOUS batch touched is part of the answer, with
 * an empty list if the new run cleared it, so stale rows vanish exactly
 * when stale squiggles do.
 *
 * <p>Kept free of platform types (the tasklist SPI's {@code Callback} is
 * final with a package-private constructor, so the replace/clear logic
 * must live where a plain unit test can reach it). The scanner maps
 * {@link Finding}s to real {@code Task}s at push time.
 */
final class RackFindings {

    /** The Task List's own severity axis (tasklist-ui layer groups). */
    static final String GROUP_ERROR = "nb-tasklist-error";
    static final String GROUP_WARNING = "nb-tasklist-warning";

    /** One Action Items row: group id, display text, 1-based line. */
    record Finding(String group, String text, int line) {
    }

    /** Per-tool current batches, in first-publish order (stable rows). */
    private final Map<String, List<DiagnosticsBus.Problem>> byTool = new LinkedHashMap<>();

    /** error → the platform's error group, warning → its warning group. */
    static String group(boolean error) {
        return error ? GROUP_ERROR : GROUP_WARNING;
    }

    /** Same "[tool] " prefix the squiggle hover text carries. */
    static String text(String tool, String message) {
        return "[" + tool + "] " + message;
    }

    /** Task lines are 1-based; parsers occasionally emit 0. */
    static int line(int line) {
        return Math.max(1, line);
    }

    /**
     * A tool published a fresh batch. Returns every file whose rows
     * changed — the union of the tool's OLD batch files (they may need
     * clearing) and its new ones — mapped to the file's full recomputed
     * findings across ALL tools (empty list = clear this file's rows).
     * Cross-tool union matters: eslint going clean on a file must not
     * erase tsc's findings there.
     */
    synchronized Map<File, List<Finding>> publish(String tool,
            List<DiagnosticsBus.Problem> problems) {
        Set<File> affected = new LinkedHashSet<>();
        List<DiagnosticsBus.Problem> old = byTool.put(tool, List.copyOf(problems));
        if (old != null) {
            for (DiagnosticsBus.Problem p : old) {
                affected.add(p.file());
            }
        }
        for (DiagnosticsBus.Problem p : problems) {
            affected.add(p.file());
        }
        Map<File, List<Finding>> result = new LinkedHashMap<>();
        for (File f : affected) {
            result.put(f, findingsFor(f));
        }
        return result;
    }

    /** Everything currently known, per file — for scope (re)activation. */
    synchronized Map<File, List<Finding>> snapshot() {
        Set<File> files = new LinkedHashSet<>();
        for (List<DiagnosticsBus.Problem> batch : byTool.values()) {
            for (DiagnosticsBus.Problem p : batch) {
                files.add(p.file());
            }
        }
        Map<File, List<Finding>> result = new LinkedHashMap<>();
        for (File f : files) {
            result.put(f, findingsFor(f));
        }
        return result;
    }

    private List<Finding> findingsFor(File file) {
        List<Finding> findings = new ArrayList<>();
        for (Map.Entry<String, List<DiagnosticsBus.Problem>> e : byTool.entrySet()) {
            for (DiagnosticsBus.Problem p : e.getValue()) {
                if (p.file().equals(file)) {
                    findings.add(new Finding(group(p.error()),
                            text(e.getKey(), p.message()), line(p.line())));
                }
            }
        }
        return findings;
    }
}
