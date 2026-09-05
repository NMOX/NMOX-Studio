package org.nmox.studio.editor.testing;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The toolbar ■ exists for a run only if the run REGISTERS (the v1.321.0
 * law: a registry with green tests and no call site is a payload without a
 * gate). The Focused Test lane — the editor's own and the Tests window's
 * Run, both through runDiscovered — joins LiveRuns on spawn and leaves on
 * exit (v2.70.0).
 */
class FocusedTestStopWiringTest {

    @Test
    @DisplayName("Focused Test runs join LiveRuns on spawn and leave it on exit")
    void focusedTestJoinsLiveRuns() throws Exception {
        String src = Files.readAllLines(Path.of("src/main/java/org/nmox/studio/editor/testing/RunFocusedTestAction.java"))
                .stream().filter(l -> !l.strip().startsWith("//")).collect(java.util.stream.Collectors.joining("\n"));
        assertThat(src).as("the toolbar ■ finds the run").contains("LiveRuns.add(new LiveRuns.Run(runId, runLabel, handle::kill))");
        assertThat(src).as("the exit handler withdraws it").contains("LiveRuns.remove(runId);");
        assertThat(src).as("a stopped test reads stopped, never FAILED [143] (v2.73.0)")
                .contains("LiveRuns.wasStoppedByUser(runId)").contains("\"Focused test stopped\"");
        assertThat(src).as("the Tests window's Run rides the same registered path")
                .contains("public static boolean runDiscovered(");
    }
}
