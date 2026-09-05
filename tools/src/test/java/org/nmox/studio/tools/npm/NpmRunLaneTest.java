package org.nmox.studio.tools.npm;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.spi.LiveRuns;
import org.nmox.studio.rack.service.ServingRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The NPM Service lane, spawned for real (v2.70.0): a `<pm> run <script>`
 * that prints a local address announces a serving and is stoppable from the
 * toolbar ■ through {@link LiveRuns}; its exit withdraws both. An install
 * that prints the same address announces nothing. The "package manager" is
 * {@code sh} running a script literally named {@code run}/{@code install}
 * in the working directory — the verb rule reads argv[1], and sh exists on
 * every CI lane.
 */
class NpmRunLaneTest {

    @TempDir
    Path dir;

    @AfterEach
    void stopEverything() {
        LiveRuns.stopAll();
        for (ServingRegistry.Serving s : ServingRegistry.getDefault().snapshot()) {
            if (s.deviceId().startsWith("npm-run:")) {
                ServingRegistry.getDefault().deregister(s.deviceId());
            }
        }
    }

    private static boolean poll(java.util.function.BooleanSupplier ok, long millis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            if (ok.getAsBoolean()) {
                return true;
            }
            Thread.sleep(50);
        }
        return ok.getAsBoolean();
    }

    private static boolean announced(String url) {
        return ServingRegistry.getDefault().snapshot().stream().anyMatch(s -> s.url().equals(url));
    }

    @Test
    @DisplayName("run <script> announces its printed server, joins LiveRuns, and the ■ stops it; the exit withdraws both")
    void runAnnouncesAndStops() throws Exception {
        Files.writeString(dir.resolve("run"), "echo \"  Local:   http://localhost:45671/\"\nsleep 30\n");
        // the script is named WITH A SPACE on purpose (v2.71.0): legal in package.json,
        // and the one shape a label-parsing marker could never find
        CompletableFuture<String> done = new NpmService().runCommand(dir.toFile(), "sh", "run", "my dev");

        assertThat(poll(() -> announced("http://localhost:45671/"), 5_000))
                .as("the printed address is a serving (⇄ chip, Live Servers)").isTrue();
        assertThat(LiveRuns.live()).as("the toolbar ■ can see the run").anyMatch(r -> r.id().startsWith("npm-run:")
                && r.label().equals("sh run my dev — " + dir.toFile().getName()));

        assertThat(NpmService.runningScripts(dir.toFile())).as("the explorer's marker sees it, space and all").containsExactly("my dev");
        assertThat(NpmService.stopScript(dir.toFile(), "build")).as("a script that isn't running").isFalse();
        assertThat(NpmService.stopScript(dir.toFile(), "my dev")).as("the row's own Stop").isTrue();
        // The exit half is POSIX-only (ledger 38, v1.42.0): under Git Bash the
        // Windows PID chain breaks, the `sleep` grandchild outlives the tree
        // kill and holds the pipe open, so the run's exit arrives only when
        // sleep ends — the windows lane timed out here on the batch's first
        // gate. The announce and stop halves above run everywhere.
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"),
                "tree-kill exit is POSIX-only (ledger 38)");
        Throwable exit = null;
        try {
            done.get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            exit = e.getCause();
        }
        assertThat(exit).as("a killed run completes exceptionally with its exit code").isNotNull();
        assertThat(poll(() -> !announced("http://localhost:45671/"), 5_000))
                .as("the serving died with the process").isTrue();
        assertThat(LiveRuns.live()).as("the exit withdrew the run").isEmpty();
    }

    @Test
    @DisplayName("a launch that fails before it starts (tool not on PATH) leaves NO phantom in the ■ (v2.71.0 review find)")
    void failedLaunchLeavesNoPhantom() throws Exception {
        CompletableFuture<String> done = new NpmService().runCommand(dir.toFile(), "no-such-package-manager-xyz", "run", "dev");
        Throwable exit = null;
        try {
            done.get(10, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            exit = e.getCause();
        }
        assertThat(exit).as("the launch failed").isNotNull();
        assertThat(LiveRuns.live()).as("the ■ has nothing to stop — the exit came before the add").isEmpty();
    }

    @Test
    @DisplayName("install prints a URL: no serving — lifecycle output never announces")
    void installNeverAnnounces() throws Exception {
        Files.writeString(dir.resolve("install"), "echo \"postinstall: see http://localhost:45672/\"\n");
        String out = new NpmService().runCommand(dir.toFile(), "sh", "install").get(10, TimeUnit.SECONDS);
        assertThat(out).contains("http://localhost:45672/");
        assertThat(announced("http://localhost:45672/")).as("an install's URL is noise").isFalse();
        assertThat(poll(() -> LiveRuns.live().isEmpty(), 5_000)).as("a finished install leaves LiveRuns").isTrue();
    }
}
