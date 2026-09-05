package org.nmox.studio.tools.npm;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The stop surfaces exist only if the run REGISTERS with them: a registry
 * with green tests and no call site is a payload without a gate (the
 * v1.321.0 law). Source-gated at the one spawn site.
 */
class StopRunWiringTest {

    @Test
    @DisplayName("Every IDE run joins LiveRuns and BuildExecutionSupport on spawn and leaves both on exit")
    void runsRegisterWithBothStopSurfaces() throws Exception {
        // comments do not count: a call site that "appears" in a // line is
        // the v1.290.0 guard-merely-appears trap (the first mutant survived it)
        String src = Files.readAllLines(Path.of("src/main/java/org/nmox/studio/tools/npm/WebProjectActionProvider.java"))
                .stream().filter(l -> !l.strip().startsWith("//")).collect(java.util.stream.Collectors.joining("\n"));
        assertThat(src).as("the toolbar ■ finds the run").contains("LiveRuns.add(new LiveRuns.Run(servingId, label, handle::kill))");
        assertThat(src).as("the exit handler withdraws it").contains("LiveRuns.remove(servingId);");
        assertThat(src).as("the platform's Run ▸ Stop Build/Run finds it").contains("BuildExecutionSupport.registerRunningItem(item)");
        assertThat(src).as("… and forgets it on exit").contains("BuildExecutionSupport.registerFinishedItem(item)");
    }

    @Test
    @DisplayName("The NPM Service lane (NPM Explorer double-click, Run Script, install) joins both stop surfaces too (v2.70.0)")
    void npmServiceRunsRegisterWithBothStopSurfaces() throws Exception {
        String src = Files.readAllLines(Path.of("src/main/java/org/nmox/studio/tools/npm/NpmService.java"))
                .stream().filter(l -> !l.strip().startsWith("//")).collect(java.util.stream.Collectors.joining("\n"));
        assertThat(src).as("the toolbar ■ finds the run").contains("LiveRuns.add(new LiveRuns.Run(runId, label, handle::kill))");
        assertThat(src).as("the exit handler withdraws it").contains("LiveRuns.remove(runId);");
        assertThat(src).as("the platform's Run ▸ Stop Build/Run finds it").contains("BuildExecutionSupport.registerRunningItem(item)");
        assertThat(src).as("… and forgets it on exit").contains("BuildExecutionSupport.registerFinishedItem(item)");
        assertThat(src).as("a printed local URL announces through the ▶'s own reader").contains("WebProjectActionProvider.servingUrlFor(line)");
        assertThat(src).as("… and the serving dies with the process").contains(".deregister(runId)");
    }

    @Test
    @DisplayName("The ■ sits on the Build toolbar beside ▶ and in the Run menu (generated layer)")
    void stopButtonIsRegistered() throws Exception {
        String layer = Files.readString(Path.of("target/classes/META-INF/generated-layer.xml"));
        assertThat(layer).contains("org-nmox-studio-tools-npm-StopRunAction.shadow");
        assertThat(layer).as("the toolbar shadow").containsPattern("(?s)<folder name=[\"]Build[\"]>.*StopRunAction[.]shadow");
        assertThat(Files.exists(Path.of("target/classes/org/nmox/studio/tools/npm/stop.png"))).as("the icon ships").isTrue();
    }
}
