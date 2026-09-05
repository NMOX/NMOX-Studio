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

    @TempDir
    Path projectDir;

    @AfterEach
    void drain() {
        LiveRuns.stopAll();
    }

    private static final class Sleeper extends CommandDevice {
        final CountDownLatch finished = new CountDownLatch(1);
        final Path started;

        Sleeper(Path started) {
            super("sleeper2", "SLEEPER2", "TEST DEVICE", new Color(20, 20, 20), 1);
            this.started = started;
        }

        @Override
        protected List<String> buildCommand() {
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

    @Test
    @DisplayName("Stop All sets the user's flag before the panic, so the device reads STOPPED")
    void stopAllReadsStopped() throws Exception {
        assumeTrue(CommandDevice.toolOnPath("sh"), "POSIX shell required");
        Files.writeString(projectDir.resolve("package.json"), "{}");
        Rack rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        Sleeper device = new Sleeper(projectDir.resolve("started"));
        try {
            rack.addDevice(device);
            device.primaryAction();
            long deadline = System.currentTimeMillis() + 10_000;
            while (!Files.exists(device.started) && System.currentTimeMillis() < deadline) {
                Thread.sleep(25);
            }
            assertThat(Files.exists(device.started)).as("a real process ran").isTrue();
            CountDownLatch all = new CountDownLatch(1);
            assertThat(rack.stopAllAsync(all::countDown)).as("a pass started").isTrue();
            assertThat(all.await(15, TimeUnit.SECONDS)).as("Stop All finished").isTrue();
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
