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
 * The rack's Stop All is the USER's stop (v2.75.0): it reached devices
 * through panic() — the internal cancel — so every device read OK/FAIL
 * after it while the faceplate STOP and the toolbar ■ read STOPPED. Now
 * it sets the verdict flag through markStoppedByUser() first; the bounded
 * panic — unchanged — keeps the orphan guarantee (routing the kill through
 * stopFromOutside would have left panic() nothing to wait on).
 */
class StopAllReadsStoppedTest {

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
            super("sleeper2", "SLEEPER2", "TEST DEVICE", new Color(20, 20, 20), 1);
            this.started = started;
            this.keepalive = keepalive;
        }

        @Override
        protected List<String> buildCommand() {
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
            finished.countDown();
        }
    }

    private static String pollVerdict(Sleeper d) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        String[] v = {""};
        while (System.currentTimeMillis() < deadline) {
            javax.swing.SwingUtilities.invokeAndWait(() -> v[0] = d.verdict());
            if (v[0].startsWith("STOPPED") || v[0].startsWith("OK") || v[0].startsWith("FAIL")) {
                return v[0];
            }
            Thread.sleep(25);
        }
        return v[0];
    }

    private static boolean stopRequested(CommandDevice d) throws Exception {
        Field f = CommandDevice.class.getDeclaredField("stopRequested");
        f.setAccessible(true);
        return f.getBoolean(d);
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
    @DisplayName("Stop All sets the user's flag before the panic, so the device reads STOPPED")
    void stopAllReadsStopped() throws Exception {
        assumeTrue(CommandDevice.toolOnPath("sh"), "POSIX shell required");
        Files.writeString(projectDir.resolve("package.json"), "{}");
        Files.writeString(projectDir.resolve(KEEPALIVE), "");
        Rack rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        Sleeper device = new Sleeper(projectDir.resolve("started"), projectDir.resolve(KEEPALIVE));
        try {
            rack.addDevice(device);
            device.primaryAction();
            long deadline = System.currentTimeMillis() + 10_000;
            while (!Files.exists(device.started) && System.currentTimeMillis() < deadline) {
                Thread.sleep(25);
            }
            assertThat(Files.exists(device.started)).as("a real process ran").isTrue();
            assertOneProcess(device.started);
            CountDownLatch all = new CountDownLatch(1);
            assertThat(rack.stopAllAsync(all::countDown)).as("a pass started").isTrue();
            assertThat(all.await(15, TimeUnit.SECONDS)).as("Stop All finished").isTrue();
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
            assertThat(device.finished.await(10, TimeUnit.SECONDS)).isTrue();
            // the flag is CLEARED after the verdict, so read the verdict itself
            // (painted on the EDT after onFinished): poll it
            assertThat(pollVerdict(device)).as("Stop All is the user's stop: the faceplate reads STOPPED").startsWith("STOPPED");
        } finally {
            rack.shutdown();
        }
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/rack/model/Rack.java"));
        // the reaper's loop panics too (earlier in the file): the panic that
        // must follow the mark is the one AFTER it, in Stop All's loop
        int mark = src.indexOf("d.markStoppedByUser();");
        assertThat(mark).as("Stop All marks the user's stop").isPositive();
        assertThat(src.indexOf("d.panic();", mark) - mark).as("… immediately before its unchanged bounded panic")
                .isPositive().isLessThan(80);
    }
}
