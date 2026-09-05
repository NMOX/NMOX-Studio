package org.nmox.studio.tools.npm;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No process work on the EDT (the v1.57.0 class): the IDE's own ▶ forks on
 * a named lane after the trust gate, and the NPM lane's Repeat rerun posts
 * to its lane instead of forking on the platform's menu thread (v2.70.0).
 */
class SpawnThreadGateTest {

    private static String body(String file) throws Exception {
        return Files.readAllLines(Path.of(file)).stream()
                .filter(l -> !l.strip().startsWith("//"))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    @Test
    @DisplayName("the ▶: trust gate on the EDT, then the lane, then the spawn")
    void runButtonForksOffTheEdt() throws Exception {
        String src = body("src/main/java/org/nmox/studio/tools/npm/WebProjectActionProvider.java");
        int gate = src.indexOf("WorkspaceTrust.requestTrust(dir)");
        int post = src.indexOf("RUN_RP.post(() -> launch(");
        int spawn = src.indexOf("CommandExecutor.run(");
        assertThat(gate).isPositive();
        assertThat(post).as("the fork is posted to the IDE Run lane").isGreaterThan(gate);
        assertThat(spawn).as("the spawn lives in the posted body").isGreaterThan(post);
        assertThat(src).contains("new org.openide.util.RequestProcessor(\"IDE Run\"");
    }

    @Test
    @DisplayName("the NPM lane's Repeat rerun rides its lane, not the menu thread")
    void repeatRerunPostsToTheLane() throws Exception {
        String src = body("src/main/java/org/nmox/studio/tools/npm/NpmService.java");
        assertThat(src).contains("() -> RP.post(() -> runCommand(workingDir, again))");
    }
}
