package org.nmox.studio.tools.npm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v1.103.0: the IDE-native paths that RUN PROJECT CODE must gate on
 * Workspace Trust before spawning — the same act the debug actions and
 * rack devices gate. Run/Build/Test/Clean (WebProjectActionProvider)
 * executes package.json scripts / make / cargo(build.rs) / npx-resolved
 * node_modules binaries; the NPM Explorer double-click runs
 * `npm run &lt;script&gt;`. Both were spawning a cloned repo's code with no
 * gate (RCE). {@code CommandExecutor.run}/{@code ProcessSupport.builder}
 * are deliberately un-gated primitives, so the gate must sit at the call
 * site.
 */
class SpawnTrustGateTest {

    private static String read(String rel) throws Exception {
        return Files.readString(Path.of(rel), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Run/Build/Test/Clean requests trust BEFORE it spawns the project's command")
    void actionProviderGatesBeforeSpawn() throws Exception {
        String src = read("src/main/java/org/nmox/studio/tools/npm/WebProjectActionProvider.java");
        int m = src.indexOf("public void invokeAction(");
        assertThat(m).as("invokeAction exists").isPositive();
        String body = src.substring(m, src.indexOf("\n    }", m));
        int gate = body.indexOf("WorkspaceTrust.requestTrust(dir)");
        int spawn = body.indexOf("CommandExecutor.run(");
        assertThat(gate).as("the trust gate is present").isGreaterThan(0);
        assertThat(spawn).as("the spawn is present").isGreaterThan(0);
        assertThat(gate).as("trust is checked BEFORE the spawn").isLessThan(spawn);
    }

    @Test
    @DisplayName("NpmService.runCommand requests trust BEFORE it spawns the script")
    void npmServiceGatesBeforeSpawn() throws Exception {
        String src = read("src/main/java/org/nmox/studio/tools/npm/NpmService.java");
        int m = src.indexOf("private CompletableFuture<String> runCommand(");
        assertThat(m).as("runCommand exists").isPositive();
        String body = src.substring(m, src.indexOf("\n    }\n", m));
        int gate = body.indexOf("WorkspaceTrust.requestTrust(workingDir)");
        int spawn = body.indexOf("pb.start()");
        assertThat(gate).as("the trust gate is present").isGreaterThan(0);
        assertThat(spawn).as("the spawn is present").isGreaterThan(0);
        assertThat(gate).as("trust is checked BEFORE the spawn").isLessThan(spawn);
        // and the fixed-tool paths must NOT route through the gated method
        assertThat(src)
                .as("listGlobalPackages runs a fixed tool without the script gate")
                .contains("runBounded");
    }

    @Test
    @DisplayName("The subprocess output accumulator is bounded (no OOM on a runaway build)")
    void npmOutputAccumulatorBounded() throws Exception {
        String src = read("src/main/java/org/nmox/studio/tools/npm/NpmService.java");
        assertThat(src)
                .contains("MAX_OUTPUT_CHARS")
                .contains("output.length() < MAX_OUTPUT_CHARS");
    }
}
