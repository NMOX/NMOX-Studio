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
    @DisplayName("update-gauntlet.sh keeps the laws its rehearsals paid for (v2.69.3): no --refresh on the fresh userdir, TERM after the tracking wait, the last=\"true\" poll, the installed-version proof")
    void gauntletKeepsItsLaws() throws Exception {
        String s = Files.readString(SCRIPTS.resolve("update-gauntlet.sh"));
        assertThat(s.lines().filter(l -> l.contains("--update-all")).filter(l -> !l.stripLeading().startsWith("#")))
                .as("the headless --update-all CLI loops either way; --refresh multiplied it to 386 iterations — the fresh userdir fetches its own catalog")
                .isNotEmpty().allSatisfy(l -> assertThat(l).doesNotContain("--refresh"));
        assertThat(s).as("the update JVM never exits on its own — it is TERMed after update_tracking moves").contains("kill -TERM \"$UPD\"");
        assertThat(s).as("update_tracking keeps a history: only the last=\"true\" entry says what is installed").contains("last=\"true\"");
        assertThat(s).as("the version proven is the one that installed, read from the cluster after the update").contains("-> installed $LATEST");
        // v2.113.0: the tracking line used to NARRATE ("1 expected; more =
        // the updater looped") and its own numbers contradicted the PASS
        // beside it — a history file never holds one entry, and the loop is
        // documented behaviour six lines up. It checks something now: the
        // entry the platform actually reads must agree with the jars.
        assertThat(s)
                .as("last=\"true\" is what the platform reads; it must agree with the installed jars, "
                        + "and a harness line that only narrates cannot fail when they disagree")
                .contains("GAUNTLET-FAIL: last=");
        assertThat(s.lines().filter(l -> l.contains("1 expected")))
                .as("the retired label: a history file leaves TWO entries for a clean single update, "
                        + "so calling one expected made every healthy run read as suspicious")
                .isEmpty();
    }

    @Test
    @DisplayName("CI runs the gauntlet after every Release under xvfb (v2.69.5) — the script, not a copy of it")
    void ciRunsTheGauntlet() throws Exception {
        String wf = Files.readString(SCRIPTS.resolve("../.github/workflows/update-gauntlet.yml"));
        assertThat(wf).as("triggered by the Release workflow's completion, when the assets and catalog are live")
                .contains("workflow_run").contains("workflows: [\"Release\"]");
        assertThat(wf).as("the What's New hook needs a display: xvfb-run in front of the ONE script")
                .contains("xvfb-run -a scripts/update-gauntlet.sh");
    }

    @Test
    @DisplayName("update-gauntlet.sh refuses to run without a from-tag")
    void gauntletDemandsItsTag() throws Exception {
        assertThat(run("bash", SCRIPTS.resolve("update-gauntlet.sh").toString()))
                .as("no from-tag → non-zero, never a silent run").isNotZero();
    }

    @Test
    @DisplayName("post-ship.sh detaches the worktree that still holds the merged branch before deleting it (v2.71.0)")
    void postShipDetachesTheHoldingWorktree() throws Exception {
        String src = java.nio.file.Files.readString(SCRIPTS.resolve("post-ship.sh"));
        int holder = src.indexOf("git worktree list --porcelain");
        int detach = src.indexOf("checkout -q --detach");
        int delete = src.indexOf("git branch -D \"$BRANCH\"");
        assertThat(holder).as("the holder is found from the worktree list").isPositive();
        assertThat(detach).as("… detached").isGreaterThan(holder);
        assertThat(delete).as("… before the branch is deleted").isGreaterThan(detach);
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
