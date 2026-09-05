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

    @TempDir
    Path projectDir;

    @AfterEach
    void drain() {
        LiveRuns.stopAll();
    }

    private static final class Sleeper extends CommandDevice {
        final CountDownLatch finished = new CountDownLatch(1);
        volatile int exitCode = Integer.MIN_VALUE;

        final Path started;

        Sleeper(Path started) {
            super("sleeper", "SLEEPER", "TEST DEVICE", new Color(20, 20, 20), 1);
            this.started = started;
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
            return List.of("sh", "-c", "trap 'exit 0' TERM; touch '" + started + "'; sleep 30 & wait");
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

    @Test
    @DisplayName("a device exec joins LiveRuns under its title; the outside stop reads STOPPED and withdraws the run")
    void execJoinsAndOutsideStopReadsStopped() throws Exception {
        assumeTrue(CommandDevice.toolOnPath("sh"), "POSIX shell required");
        Files.writeString(projectDir.resolve("package.json"), "{}");
        Rack rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        Sleeper device = new Sleeper(projectDir.resolve("started"));
        try {
            rack.addDevice(device);
            device.primaryAction();
            assertThat(poll(() -> LiveRuns.live().stream().anyMatch(r -> isOurs(r)), 5_000))
                    .as("the run is in the ■'s registry").isTrue();
            LiveRuns.Run run = LiveRuns.live().stream().filter(r -> isOurs(r)).findFirst().orElseThrow();
            assertThat(run.label()).isEqualTo("SLEEPER — sh");
            assertThat(poll(() -> Files.exists(device.started), 10_000)).as("a REAL process ran (the marker)").isTrue();
            assertThat(device.runningNow()).as("… and is still up").isTrue();
            assertThat(stopRequested(device)).as("nothing stopped yet").isFalse();
            assertThat(LiveRuns.stop(run.id())).isNotNull();
            // The exit half is POSIX-only (ledger 38, the v2.70.0 NpmRunLaneTest
            // law): under Git Bash the Windows PID chain breaks, the `sleep 30`
            // grandchild outlives the tree kill and holds the stdout pipe, and
            // the exit handler rides the pump's EOF — so onFinished lands when
            // sleep ends (~30 s), past this wait. The windows lane failed here
            // twice on one sha (v2.76.0's gate). The join and stop halves
            // above run everywhere.
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
