package org.nmox.studio.ui.actions;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The ■ finds a running ng generate (v2.71.0) — registered on spawn, withdrawn on exit. */
class NgSchematicStopWiringTest {

    @Test
    @DisplayName("ng generate joins LiveRuns on spawn and leaves on exit")
    void generateJoinsLiveRuns() throws Exception {
        String src = Files.readAllLines(Path.of("src/main/java/org/nmox/studio/ui/actions/NgSchematicAction.java"))
                .stream().filter(l -> !l.strip().startsWith("//")).collect(java.util.stream.Collectors.joining("\n"));
        assertThat(src).contains("LiveRuns.add(new LiveRuns.Run(runId, runLabel, handle::kill))");
        assertThat(src).contains("LiveRuns.remove(runId);");
        assertThat(src.indexOf("LiveRuns.remove(runId);")).as("the withdrawal sits in the exit handler")
                .isGreaterThan(src.indexOf("CommandExecutor.Handle handle = CommandExecutor.run(runLabel,"));
    }
}
