package org.nmox.studio.editor.diagnostics;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.engine.DiagnosticsBus.Problem;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pure half of the Action Items bridge: diagnostic → task-row mapping
 * and the replace-per-run semantics the bus promises (a fresh run REPLACES
 * that tool's previous batch, so stale rows must vanish — including on an
 * all-clear run — without ever erasing another tool's findings).
 */
class RackFindingsTest {

    private static final File A = new File("/tmp/proj/a.js");
    private static final File B = new File("/tmp/proj/b.js");

    // ---- mapping ---------------------------------------------------------

    @Test
    @DisplayName("errors ride the platform's error group, warnings its warning group")
    void shouldMapSeverityToStandardTaskGroups() {
        assertThat(RackFindings.group(true)).isEqualTo("nb-tasklist-error");
        assertThat(RackFindings.group(false)).isEqualTo("nb-tasklist-warning");
    }

    @Test
    @DisplayName("task text carries the tool name, matching the squiggle hover prefix")
    void shouldPrefixTextWithToolName() {
        assertThat(RackFindings.text("eslint", "Unexpected console statement"))
                .isEqualTo("[eslint] Unexpected console statement");
    }

    @Test
    @DisplayName("lines are 1-based; a parser's 0 or negative clamps to 1")
    void shouldClampLineToOne() {
        assertThat(RackFindings.line(0)).isEqualTo(1);
        assertThat(RackFindings.line(-3)).isEqualTo(1);
        assertThat(RackFindings.line(42)).isEqualTo(42);
    }

    @Test
    @DisplayName("a published problem becomes a complete finding on its file")
    void shouldMapProblemToFinding() {
        RackFindings findings = new RackFindings();

        Map<File, List<RackFindings.Finding>> delta = findings.publish("eslint",
                List.of(new Problem(A, 7, "no-unused-vars", false)));

        assertThat(delta).containsOnlyKeys(A);
        assertThat(delta.get(A)).containsExactly(new RackFindings.Finding(
                "nb-tasklist-warning", "[eslint] no-unused-vars", 7));
    }

    // ---- replace / clear-on-rerun ----------------------------------------

    @Test
    @DisplayName("a re-run replaces the tool's batch: files it no longer names come back EMPTY")
    void shouldClearFilesDroppedByRerun() {
        RackFindings findings = new RackFindings();
        findings.publish("eslint", List.of(
                new Problem(A, 1, "one", true),
                new Problem(B, 2, "two", true)));

        // the fix landed in b.js; the fresh run only names a.js
        Map<File, List<RackFindings.Finding>> delta = findings.publish("eslint",
                List.of(new Problem(A, 1, "one", true)));

        assertThat(delta).containsOnlyKeys(A, B);
        assertThat(delta.get(B)).as("b.js was in the OLD batch — its rows must clear").isEmpty();
        assertThat(delta.get(A)).hasSize(1);
    }

    @Test
    @DisplayName("an all-clear run (empty batch) clears every file the tool had touched")
    void shouldClearEverythingOnAllClearRun() {
        RackFindings findings = new RackFindings();
        findings.publish("tsc", List.of(
                new Problem(A, 1, "TS2304", true),
                new Problem(B, 9, "TS2551", true)));

        Map<File, List<RackFindings.Finding>> delta = findings.publish("tsc", List.of());

        assertThat(delta).containsOnlyKeys(A, B);
        assertThat(delta.values()).allSatisfy(rows -> assertThat(rows).isEmpty());
    }

    @Test
    @DisplayName("one tool going clean never erases another tool's findings on the same file")
    void shouldPreserveCrossToolUnionWhenOneToolClears() {
        RackFindings findings = new RackFindings();
        findings.publish("eslint", List.of(new Problem(A, 3, "semi", false)));
        findings.publish("tsc", List.of(new Problem(A, 5, "TS2304", true)));

        Map<File, List<RackFindings.Finding>> delta = findings.publish("eslint", List.of());

        assertThat(delta.get(A))
                .as("eslint cleared, tsc's finding stands")
                .containsExactly(new RackFindings.Finding(
                        "nb-tasklist-error", "[tsc] TS2304", 5));
    }

    @Test
    @DisplayName("snapshot returns the cross-tool union per file — what scope activation replays")
    void shouldSnapshotUnionAcrossTools() {
        RackFindings findings = new RackFindings();
        findings.publish("eslint", List.of(new Problem(A, 3, "semi", false)));
        findings.publish("tsc", List.of(
                new Problem(A, 5, "TS2304", true),
                new Problem(B, 1, "TS1005", true)));

        Map<File, List<RackFindings.Finding>> all = findings.snapshot();

        assertThat(all).containsOnlyKeys(A, B);
        assertThat(all.get(A)).extracting(RackFindings.Finding::text)
                .containsExactly("[eslint] semi", "[tsc] TS2304");
        assertThat(all.get(B)).hasSize(1);
    }
}
