package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.spi.LiveRuns;
import org.nmox.studio.rack.model.Rack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The ■ is total (v2.74.0): a rack device's run registers with LiveRuns
 * on exec under its title and leaves on exit, and a stop from outside
 * the faceplate (the ■, the Workbench row, ⌘I) is the USER's stop — the
 * device's verdict reads STOPPED, never OK/FAIL (the v2.69.15 law kept
 * across the new door).
 */
class DeviceRunsJoinTheStopTest {

    /** See {@link Sleeper#keepalive}. */
    private static final String KEEPALIVE = "keepalive";

    @TempDir
    Path projectDir;

    @AfterEach
    void drain() {
        LiveRuns.stopAll();
        try {
            // belt and braces: a shell that outlived the kill ends itself on
            // its next turn round the loop, so nothing holds the @TempDir
            // when JUnit deletes it
            Files.deleteIfExists(projectDir.resolve(KEEPALIVE));
        } catch (java.io.IOException ignored) {
            // the directory is on its way out anyway
        }
    }

    private static final class Sleeper extends CommandDevice {
        final CountDownLatch finished = new CountDownLatch(1);
        volatile int exitCode = Integer.MIN_VALUE;

        final Path started;
        /**
         * How this fixture stays alive without spawning anything. A
         * {@code sleep} here is a grandchild of the JVM, and where the
         * parent-PID chain is broken — Git Bash on Windows, ledger 38 — the
         * tree kill cannot see it: it holds the pump's pipe open (which is
         * why the exit half below is POSIX-only) AND it holds this
         * {@code @TempDir} as its working directory, which Windows then
         * refuses to delete, failing the method on JUnit's cleanup. A shell
         * looping on a builtin spawns nothing, so the kill always reaches
         * it; the teardown drops the file as belt and braces.
         */
        final Path keepalive;

        Sleeper(Path started, Path keepalive) {
            super("sleeper", "SLEEPER", "TEST DEVICE", new Color(20, 20, 20), 1);
            this.started = started;
            this.keepalive = keepalive;
        }

        @Override
        protected List<String> buildCommand() {
            // the marker proves a REAL process ran before the stop — the
            // pending handle answers "alive" before the spawn, so the first
            // cut of this test stopped a process that never existed (36 ms)
            // a process that TRAPS the stop and exits 0 — a dev server's shape:
            // a signal exit reads STOPPED by the v2.69.15 code rule alone, so
            // only THIS shape tells the flag apart (the first mutant survived
            // a plain sleep)
            return List.of("sh", "-c", "trap 'exit 0' TERM; touch '" + started + "'; "
                    + "while [ -e '" + keepalive + "' ]; do :; done");
        }

        /** The verdict the exit handler paints (EDT): "STOPPED  1.2s", "OK  …", "FAIL [n]  …". */
        String verdict() {
            return statusLcd.getText();
        }

        @Override
        protected void onFinished(int code) {
            exitCode = code;
            finished.countDown();
        }

        boolean runningNow() {
            return isProcessRunning();
        }
    }

    /** Every device exec registers now, so other rack tests' runs may be live too: pick OURS by label. */
    private static boolean isOurs(LiveRuns.Run r) {
        return r.id().startsWith("device:") && r.label().startsWith("SLEEPER — ");
    }

    private static boolean stopRequested(CommandDevice d) throws Exception {
        Field f = CommandDevice.class.getDeclaredField("stopRequested");
        f.setAccessible(true);
        return f.getBoolean(d);
    }

    private static boolean poll(java.util.function.BooleanSupplier ok, long millis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            if (ok.getAsBoolean()) {
                return true;
            }
            Thread.sleep(25);
        }
        return ok.getAsBoolean();
    }

    /**
     * The fixture's own shell, found among the JVM's children by the unique
     * marker path it was given. The marker, not the keepalive: the identity
     * has to survive any change to how the script waits, or a fixture that
     * went back to spawning would simply become unfindable instead of
     * failing. Windows exposes no per-argument info, so there the shell
     * cannot be identified — the one platform this law cannot be read on,
     * and the one it exists for.
     */
    private static java.util.Optional<ProcessHandle> ourShell(Path marker) {
        return ProcessHandle.current().descendants()
                .filter(h -> h.info().arguments()
                        .map(args -> java.util.Arrays.stream(args)
                                .anyMatch(a -> a.contains(marker.toString())))
                        .orElse(false))
                .findFirst();
    }

    /**
     * The law the fixture has to keep: the run is ONE process. A child of the
     * script is a grandchild of the JVM, and where the PID chain is broken
     * (Git Bash, ledger 38) the tree kill never reaches it — it holds the
     * pump's pipe open AND holds the @TempDir as its cwd, which Windows then
     * refuses to delete, failing the method on JUnit's cleanup.
     */
    private static void assertOneProcess(Path marker) throws Exception {
        java.util.Optional<ProcessHandle> shell = ourShell(marker);
        if (shell.isEmpty()) {
            assertThat(System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT))
                    .as("the fixture's own shell is among the JVM's children; only "
                            + "Windows hides a process's arguments from ProcessHandle")
                    .contains("win");
            return;
        }
        // 300ms is generous: a script's child is forked in the same breath as
        // the `touch` the marker above already saw
        long deadline = System.currentTimeMillis() + 300;
        while (System.currentTimeMillis() < deadline) {
            assertThat(shell.get().descendants().findAny())
                    .as("the run is ONE process — the script spawned a child the tree "
                            + "kill cannot promise to reach (ledger 38)")
                    .isEmpty();
            Thread.sleep(25);
        }
    }

    @Test
    @DisplayName("a device exec joins LiveRuns under its title; the outside stop reads STOPPED and withdraws the run")
    void execJoinsAndOutsideStopReadsStopped() throws Exception {
        assumeTrue(CommandDevice.toolOnPath("sh"), "POSIX shell required");
        Files.writeString(projectDir.resolve("package.json"), "{}");
        Files.writeString(projectDir.resolve(KEEPALIVE), "");
        Rack rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        Sleeper device = new Sleeper(projectDir.resolve("started"), projectDir.resolve(KEEPALIVE));
        try {
            rack.addDevice(device);
            device.primaryAction();
            assertThat(poll(() -> LiveRuns.live().stream().anyMatch(r -> isOurs(r)), 5_000))
                    .as("the run is in the ■'s registry").isTrue();
            LiveRuns.Run run = LiveRuns.live().stream().filter(r -> isOurs(r)).findFirst().orElseThrow();
            assertThat(run.label()).isEqualTo("SLEEPER — sh");
            assertThat(poll(() -> Files.exists(device.started), 10_000)).as("a REAL process ran (the marker)").isTrue();
            assertThat(device.runningNow()).as("… and is still up").isTrue();
            assertOneProcess(device.started);
            assertThat(stopRequested(device)).as("nothing stopped yet").isFalse();
            assertThat(LiveRuns.stop(run.id())).isNotNull();
            // The exit half stays POSIX-only (ledger 38, the v2.70.0
            // NpmRunLaneTest law): under Git Bash the Windows PID chain
            // breaks, and this lane has never been run there to prove
            // otherwise — the windows lane failed here twice on one sha
            // (v2.76.0's gate). What that platform gives up is the exit
            // assertions below; the join, the marker and the stop above are
            // asserted everywhere. The abort itself is now SAFE: the fixture
            // spawns nothing that could outlive the kill and hold the
            // @TempDir open (see Sleeper#keepalive), and the bounded
            // killAndWait inside rack.shutdown() reaps the shell before
            // JUnit's cleanup runs.
            org.junit.jupiter.api.Assumptions.assumeFalse(
                    System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"),
                    "tree-kill exit is POSIX-only (ledger 38)");
            // the flag is cleared by the exit handler, which can run before this
            // line: the VERDICT below is the durable proof (the flag read raced)
            assertThat(device.finished.await(10, TimeUnit.SECONDS)).as("the process died and the exit handler ran").isTrue();
            assertThat(CommandDevice.stoppedByUserOrSignal(true, device.exitCode)).isTrue();
            long deadline = System.currentTimeMillis() + 5_000;
            String[] v = {""};
            while (!v[0].startsWith("STOPPED") && System.currentTimeMillis() < deadline) {
                javax.swing.SwingUtilities.invokeAndWait(() -> v[0] = device.verdict());
                Thread.sleep(25);
            }
            assertThat(v[0]).as("the faceplate itself reads STOPPED").startsWith("STOPPED");
            assertThat(poll(() -> LiveRuns.live().stream().noneMatch(r -> isOurs(r)), 5_000))
                    .as("the exit withdrew the run").isTrue();
        } finally {
            rack.shutdown();
        }
    }
}
