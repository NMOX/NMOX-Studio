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
        // normalize CRLF → LF: the Windows CI runner may check out .java
        // with \r\n, which breaks the "\n    }" method-body delimiters
        return Files.readString(Path.of(rel), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    @Test
    @DisplayName("Run/Build/Test/Clean requests trust BEFORE it spawns the project's command")
    void actionProviderGatesBeforeSpawn() throws Exception {
        String src = read("src/main/java/org/nmox/studio/tools/npm/WebProjectActionProvider.java");
        int m = src.indexOf("public void invokeAction(");
        assertThat(m).as("invokeAction exists").isPositive();
        String body = src.substring(m, src.indexOf("\n    }", m));
        int gate = body.indexOf("WorkspaceTrust.requestTrust(dir)");
        // v2.70.0: the spawn moved to launch(), posted from invokeAction
        // AFTER the gate (SpawnThreadGateTest owns the thread half)
        int post = body.indexOf("RUN_RP.post(() -> launch(");
        assertThat(gate).as("the trust gate is present").isGreaterThan(0);
        assertThat(post).as("the launch is posted from invokeAction").isGreaterThan(0);
        assertThat(gate).as("trust is checked BEFORE the launch is posted").isLessThan(post);
        int l = src.indexOf("private void launch(");
        String launch = src.substring(l, src.indexOf("\n    }\n", l));
        assertThat(launch.indexOf("CommandExecutor.run(")).as("the spawn lives in launch()").isPositive();
        assertThat(launch).as("launch() never re-asks — the gate is invokeAction's").doesNotContain("requestTrust(");
    }

    @Test
    @DisplayName("NpmService.runCommand requests trust BEFORE it spawns the script")
    void npmServiceGatesBeforeSpawn() throws Exception {
        String src = read("src/main/java/org/nmox/studio/tools/npm/NpmService.java");
        // package-private since v2.70.0: the lane test spawns through it for real
        int m = src.indexOf("    CompletableFuture<String> runCommand(File workingDir, String... command) {");
        assertThat(m).as("runCommand exists").isPositive();
        String body = src.substring(m, src.indexOf("\n    }\n", m));
        // v1.114.0: the spawn is CommandExecutor.run (streams via its own
        // pump threads — no RP pin, no drain-before-waitFor), not pb.start()
        int gate = body.indexOf("WorkspaceTrust.requestTrust(workingDir)");
        int spawn = body.indexOf("CommandExecutor.run(");
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
