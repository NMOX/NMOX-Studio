package org.nmox.studio.application;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The in-repo pipeline scripts parse, and the ship half refuses to run
 * without its four arguments — the scratchpad era's silent failures
 * (a masked rebase exit, a title spliced into a script, no PR and no
 * error) are exactly what a script with usage checks refuses to repeat.
 */
@DisabledOnOs(OS.WINDOWS)
class ShipScriptsGateTest {

    private static final Path SCRIPTS = Path.of("..", "scripts");

    @Test
    @DisplayName("every shell script in scripts/ parses (bash -n)")
    void everyScriptParses() throws Exception {
        try (Stream<Path> walk = Files.list(SCRIPTS)) {
            List<Path> scripts = walk.filter(p -> p.toString().endsWith(".sh")).sorted().toList();
            assertThat(scripts).isNotEmpty();
            for (Path s : scripts) {
                assertThat(run("bash", "-n", s.toString())).as("%s must parse", s.getFileName()).isZero();
            }
        }
    }

    @Test
    @DisplayName("Both pipeline scripts set pipefail — a `cmd | tail` must fail when cmd fails (v2.69.1 review)")
    void pipelineScriptsFailOnPipes() throws Exception {
        for (String name : List.of("ship-branch.sh", "post-ship.sh", "update-gauntlet.sh")) {
            assertThat(Files.readString(SCRIPTS.resolve(name)))
                    .as("%s must set pipefail: its pushes and reinstalls run through `| tail`", name)
                    .contains("set -o pipefail");
        }
    }

    @Test
    @DisplayName("update-gauntlet.sh refuses to run without a from-tag")
    void gauntletDemandsItsTag() throws Exception {
        assertThat(run("bash", SCRIPTS.resolve("update-gauntlet.sh").toString()))
                .as("no from-tag → non-zero, never a silent run").isNotZero();
    }

    @Test
    @DisplayName("post-ship.sh refuses to run without a tag")
    void postShipDemandsItsTag() throws Exception {
        assertThat(run("bash", SCRIPTS.resolve("post-ship.sh").toString()))
                .as("no tag → non-zero, never a silent run").isNotZero();
    }

    @Test
    @DisplayName("ship-branch.sh refuses to run without its four arguments and says the usage")
    void shipBranchDemandsItsArguments() throws Exception {
        assertThat(run("bash", SCRIPTS.resolve("ship-branch.sh").toString()))
                .as("no arguments → a non-zero exit, never a silent run").isNotZero();
        assertThat(run("bash", SCRIPTS.resolve("ship-branch.sh").toString(), "b", "sha", "title", "/nonexistent/body.md"))
                .as("a missing body file is refused before any git command").isEqualTo(2);
    }

    private static int run(String... argv) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(argv).redirectErrorStream(true).redirectOutput(new File(System.getProperty("java.io.tmpdir"), "ship-scripts-gate.out")).start();
        assertThat(p.waitFor(30, TimeUnit.SECONDS)).as("script exits promptly").isTrue();
        return p.exitValue();
    }
}
