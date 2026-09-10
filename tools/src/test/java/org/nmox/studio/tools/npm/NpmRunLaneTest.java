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

    /**
     * The file the long-lived script waits on. It is how the fixture stays
     * alive WITHOUT a child process: a {@code sleep} would be a grandchild
     * of the JVM, and where the parent-PID chain is broken — Git Bash on
     * Windows, ledger 38 — the tree kill cannot see it, so it outlives the
     * test holding this {@code @TempDir} as its working directory. Windows
     * refuses to remove a directory that is a live process's cwd, JUnit's
     * cleanup then fails the method with "Failed to close extension
     * context", and the whole CI cycle is spent on a flake. A shell looping
     * on a builtin test spawns nothing, so the kill always reaches it — and
     * if it ever did not, deleting this file ends the loop anyway.
     */
    private static final String KEEPALIVE = "keepalive";

    /** The unique argument that identifies this fixture's own shell. */
    private static final String MARKER = "my dev";

    @TempDir
    Path dir;

    /** The one long-lived run; awaited in {@link #stopEverything} before cleanup. */
    private CompletableFuture<String> longRun;

    @AfterEach
    void stopEverything() {
        LiveRuns.stopAll();
        try {
            // belt and braces: any shell that outlived the kill ends itself
            // on its next turn round the loop
            Files.deleteIfExists(dir.resolve(KEEPALIVE));
        } catch (java.io.IOException ignored) {
            // the dir is about to be deleted anyway; the await below is the real barrier
        }
        if (longRun != null) {
            // @TempDir cleanup runs immediately after this method returns, and
            // it must not race a dying child: on Windows a process keeps its
            // working directory locked until it is truly gone (the same reason
            // ProcessSupport.killTreeAndWait exists). This future completes
            // from CommandExecutor's pump AFTER process.waitFor() returns, so
            // waiting on it means the OS has already reaped the process.
            // A killed run completes exceptionally — the wait is the point,
            // not the value.
            try {
                longRun.get(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception expected) {
                // a killed run completes exceptionally; a timeout here would
                // mean a child we cannot reach, which is what the fixture's
                // shape exists to prevent
            } finally {
                longRun = null;
            }
        }
        for (ServingRegistry.Serving s : ServingRegistry.getDefault().snapshot()) {
            if (s.deviceId().startsWith("npm-run:")) {
                ServingRegistry.getDefault().deregister(s.deviceId());
            }
        }
    }

    /**
     * This fixture's own shell, found among the JVM's children by the unique
     * argument the test passes it.
     */
    private static java.util.Optional<ProcessHandle> ourShell() {
        return ProcessHandle.current().descendants()
                .filter(h -> h.info().arguments()
                        .map(args -> java.util.Arrays.asList(args).contains(MARKER))
                        .orElse(false))
                .findFirst();
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
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
        // The script must outlive the assertions below WITHOUT spawning
        // anything: it prints its address and then spins on a shell builtin
        // until the test drops the keepalive file. `sleep 30` here used to be
        // a grandchild of the JVM, and the tree kill is blind to those
        // wherever the parent-PID chain is broken (see KEEPALIVE).
        Files.writeString(dir.resolve(KEEPALIVE), "");
        Files.writeString(dir.resolve("run"),
                "echo \"  Local:   http://localhost:45671/\"\n"
                + "while [ -e " + KEEPALIVE + " ]; do :; done\n");
        // the script is named WITH A SPACE on purpose (v2.71.0): legal in package.json,
        // and the one shape a label-parsing marker could never find
        longRun = new NpmService().runCommand(dir.toFile(), "sh", "run", MARKER);

        assertThat(poll(() -> announced("http://localhost:45671/"), 5_000))
                .as("the printed address is a serving (⇄ chip, Live Servers)").isTrue();
        // The law this fixture has to keep, and the one the flake broke: the
        // run is ONE process. A child of the script is a grandchild of the
        // JVM; where the PID chain is broken (Git Bash, ledger 38) the tree
        // kill never reaches it, it holds the pipe open so the run's exit
        // never arrives, and it holds this @TempDir as its cwd so Windows
        // refuses to delete it and JUnit fails the method on cleanup.
        ourShell().ifPresentOrElse(
                shell -> {
                    try {
                        // 300ms is generous: a script's child is forked in the same
                        // breath as the echo the announce above already saw
                        assertThat(poll(() -> shell.descendants().findAny().isPresent(), 300))
                                .as("the run is ONE process — the script spawned a child the "
                                        + "tree kill cannot promise to reach (ledger 38)")
                                .isFalse();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                },
                () -> assertThat(windows())
                        .as("the fixture's own shell is among the JVM's children; only "
                                + "Windows hides a process's arguments from ProcessHandle")
                        .isTrue());
        assertThat(LiveRuns.live()).as("the toolbar ■ can see the run").anyMatch(r -> r.id().startsWith("npm-run:")
                && r.label().equals("sh run my dev — " + dir.toFile().getName()));

        assertThat(NpmService.runningScripts(dir.toFile())).as("the explorer's marker sees it, space and all").containsExactly("my dev");
        assertThat(NpmService.runningSince(dir.toFile(), "my dev"))
                .as("… and since when — a time in the reader's own language, never a word "
                        + "(v2.76.0; data since v2.100.0; localized since v2.104.0)")
                .containsPattern("\\d").doesNotContainPattern("[\\p{L}]{3,}");
        assertThat(NpmService.runningSince(dir.toFile(), "build")).as("a script that isn't running").isEmpty();
        assertThat(NpmService.stopScript(dir.toFile(), "build")).as("a script that isn't running").isFalse();
        assertThat(NpmService.stopScript(dir.toFile(), "my dev")).as("the row's own Stop").isTrue();
        // The exit half stays POSIX-only (ledger 38, v1.42.0): under Git Bash
        // the Windows PID chain breaks, and this lane has never been run there
        // to prove otherwise. What the windows lane gives up is only the three
        // assertions below — that a killed run completes exceptionally, that
        // the serving is withdrawn, and that LiveRuns empties; the announce,
        // the ■'s view, the explorer's marker and the row's own Stop above are
        // asserted on every platform. The abort is now SAFE: with no child to
        // outlive the kill, nothing holds this @TempDir when cleanup runs, and
        // stopEverything waits for the process to be reaped before it returns.
        org.junit.jupiter.api.Assumptions.assumeFalse(windows(),
                "tree-kill exit is POSIX-only (ledger 38)");
        Throwable exit = null;
        try {
            longRun.get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            exit = e.getCause();
        }
        assertThat(exit).as("a killed run completes exceptionally with its exit code").isNotNull();
        assertThat(poll(() -> !announced("http://localhost:45671/"), 5_000))
                .as("the serving died with the process").isTrue();
        assertThat(LiveRuns.live()).as("the exit withdrew the run").isEmpty();
        // the property stopEverything leans on before @TempDir cleanup: the
        // future completes from the pump's process.waitFor(), so by the time
        // it hands back a value the OS has reaped the process and released
        // the working directory. (On POSIX a cwd never blocked a delete, so
        // dropping the await in stopEverything cannot fail a test here — this
        // is where the property itself is pinned instead.)
        assertThat(ourShell()).as("a completed run has no process left").isEmpty();
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
    @DisplayName("a run while the project's setup install is live is refused out loud: no spawn, the wall returned (v2.72.0)")
    void runWhileInstallingIsRefused() throws Exception {
        Files.writeString(dir.resolve("run"), "echo \"  Local:   http://localhost:45673/\"\n");
        LiveRuns.add(new LiveRuns.Run("project-setup:" + dir.toFile().getAbsolutePath() + "#1", "npm install — x", () -> { }));
        String out = new NpmService().runCommand(dir.toFile(), "sh", "run", "dev").get(5, TimeUnit.SECONDS);
        assertThat(out).contains("still installing").contains("■");
        assertThat(LiveRuns.live()).extracting(LiveRuns.Run::id).as("nothing spawned").allMatch(id -> id.startsWith("project-setup:"));
        assertThat(announced("http://localhost:45673/")).isFalse();
    }

    @Test
    @DisplayName("declared dependencies with no node_modules: the run is refused out loud and points at Install; the install verb itself passes (v2.73.0)")
    void uninstalledDependenciesRefuseTheRun() throws Exception {
        Files.writeString(dir.resolve("package.json"), "{\"name\":\"w\",\"dependencies\":{\"express\":\"^5\"}}");
        Files.writeString(dir.resolve("run"), "echo \"  Local:   http://localhost:45674/\"\n");
        Files.writeString(dir.resolve("install"), "echo installing\n");
        String out = new NpmService().runCommand(dir.toFile(), "sh", "run", "dev").get(5, TimeUnit.SECONDS);
        assertThat(out).contains("aren't installed").contains("Install");
        assertThat(announced("http://localhost:45674/")).as("nothing ran").isFalse();
        assertThat(new NpmService().runCommand(dir.toFile(), "sh", "install").get(10, TimeUnit.SECONDS))
                .as("the install itself is the way through the wall").contains("installing");
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
