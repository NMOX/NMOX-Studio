package org.nmox.studio.tools.npm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.spi.LiveRuns;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A script on the NPM lane that the USER stopped (3.6.0 review): it is
 * neither a failure (143 from its TERM) nor a success (0 from a script
 * that catches TERM and leaves cleanly), so a VS Code task chain — or a
 * launch configuration's {@code preLaunchTask} — that waits for it hears
 * STOPPED and starts nothing after it. Spawned for real, as in {@link
 * NpmTapTest}: the "package manager" is {@code sh} running a file.
 */
@DisabledOnOs(OS.WINDOWS)
class NpmStopTest {

    @TempDir
    Path dir;

    @AfterEach
    void stopEverything() {
        LiveRuns.stopAll();
        LiveRuns.clearForTest();
    }

    private Throwable stoppedRun(String script) throws Exception {
        Files.writeString(dir.resolve("run"), script);
        CompletableFuture<String> run = new NpmService().runCommand(dir.toFile(), "sh", "run", "watch");
        long deadline = System.currentTimeMillis() + 10_000;
        String id = null;
        while (id == null && System.currentTimeMillis() < deadline) {
            id = LiveRuns.live().stream().map(LiveRuns.Run::id).filter(i -> i.startsWith("npm-run:"))
                    .findFirst().orElse(null);
            Thread.sleep(20);
        }
        assertThat(id).as("the run is live").isNotNull();
        Thread.sleep(300); // the script has reached its wait
        assertThat(LiveRuns.stop(id)).isNotNull();
        return run.handle((output, failed) -> failed).get(30, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("a stopped script that exits 143 reads STOPPED, not a failure")
    void stoppedIsNotFailed() throws Exception {
        Throwable failed = stoppedRun("sleep 30\n");
        assertThat(failed).isInstanceOf(NpmService.StoppedByUser.class);
        assertThat(NpmLaneRun.exitOf(false, failed)).isEqualTo(NpmLaneRun.STOPPED);
    }

    @Test
    @DisplayName("a stopped script that catches TERM and exits 0 reads STOPPED, not done")
    void stoppedIsNotDone() throws Exception {
        Throwable failed = stoppedRun("trap 'exit 0' TERM\nsleep 30 &\nwait\nexit 0\n");
        assertThat(failed).as("a clean exit after the user's stop is still the user's stop")
                .isInstanceOf(NpmService.StoppedByUser.class);
        assertThat(NpmLaneRun.exitOf(false, failed)).isEqualTo(NpmLaneRun.STOPPED);
    }

    @Test
    @DisplayName("STOPPED is its own answer: not zero, not a code, not NOT_RUN; and a wrapped stop is still a stop")
    void theAnswer() {
        assertThat(NpmLaneRun.STOPPED).isNotZero().isNotEqualTo(NpmLaneRun.NOT_RUN).isNegative();
        assertThat(NpmLaneRun.exitOf(false, new java.util.concurrent.CompletionException(
                new NpmService.StoppedByUser(0)))).isEqualTo(NpmLaneRun.STOPPED);
    }
}
