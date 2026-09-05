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
        assertThat(src.indexOf("BuildExecutionSupport.registerRunningItem(item)"))
                .as("registered BEFORE the spawn — a synchronous launch failure finishes a registered item (v2.71.0)")
                .isLessThan(src.indexOf("CommandExecutor.Handle handle = CommandExecutor.run("));
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
        int spawn = src.indexOf("CommandExecutor.Handle handle = CommandExecutor.run(label");
        assertThat(src.indexOf("BuildExecutionSupport.registerRunningItem(item)"))
                .as("registered BEFORE the spawn (v2.71.0)").isLessThan(spawn);
        assertThat(src.indexOf("SCRIPT_BY_RUN.put(runId, script)"))
                .as("the run→script entry precedes the spawn too (v2.72.0 review: a synchronous failure removed it before the put)")
                .isLessThan(spawn);
        assertThat(src.indexOf("OpenOnServe.getDefault().arm(workingDir)"))
                .as("run/start arms the Browser auto-open before the spawn, the ▶'s gesture (v2.72.0)")
                .isPositive().isLessThan(spawn);
        assertThat(src).as("a printed local URL announces through the ▶'s own reader").contains("WebProjectActionProvider.servingUrlFor(line)");
        assertThat(src).as("… and the serving dies with the process").contains(".deregister(runId)");
        assertThat(src).as("the output tab carries the run's label, the ▶'s convention (v2.71.0)")
                .contains("CommandExecutor.showOutput(label);").contains("CommandExecutor.run(label, workingDir");
        assertThat(src).as("no shared NPM Output tab").doesNotContain("\"NPM Output\"");
    }

    @Test
    @DisplayName("NPM Explorer follows LiveRuns symmetrically (open adds, close removes) and offers Stop Script on the row (v2.70.0)")
    void explorerFollowsLiveRuns() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/tools/npm/NpmExplorerTopComponent.java"));
        int opened = src.indexOf("public void componentOpened()");
        int closed = src.indexOf("public void componentClosed()");
        assertThat(src.indexOf("LiveRuns.addListener(liveRunsListener)")).isGreaterThan(opened).isLessThan(closed);
        assertThat(src.indexOf("LiveRuns.removeListener(liveRunsListener)")).isGreaterThan(closed);
        assertThat(src).contains("new JMenuItem(\"Stop Script\")").contains("NpmService.stopScript(currentProjectDir, s.name)");
        assertThat(src).as("a second copy is refused out loud").contains("is already running");
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
