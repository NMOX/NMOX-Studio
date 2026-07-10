package org.nmox.studio.rack.model;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Ledger item 15: the interactive stop paths (Stop All, the project-switch
 * guard) must not run {@link RackDevice#panic()} on the EDT — a stubborn
 * process eats the full TERM grace before the KILL, ~2.5s of frozen paint
 * thread per device. {@link Rack#stopAllAsync} moves the kills to bounded
 * workers and reports completion on the EDT; these tests pin the EDT-return
 * time, the orphan guarantee after the pass, the double-fire guard, and
 * that the JVM shutdown reaper kept its synchronous panics (async
 * escalation threads never run at shutdown).
 */
class RackStopAsyncTest {

    private Rack rack;

    @AfterEach
    void tearDown() {
        if (rack != null) {
            rack.shutdown();
        }
    }

    /**
     * A device running a real process that ignores SIGTERM — the stubborn
     * dev server that makes panic() block through the whole grace period.
     */
    private static final class StubbornDevice extends RackDevice {

        final CountDownLatch pidSeen = new CountDownLatch(1);
        volatile long childPid = -1;

        StubbornDevice() {
            super("stubborn", "STUBBORN", "TEST DEVICE", new Color(0, 0, 0), 1);
        }

        void launch() {
            exec(List.of("sh", "-c", "trap '' TERM INT; echo PID $$; while :; do sleep 0.1; done"),
                    line -> {
                        if (line.startsWith("PID ")) {
                            try {
                                childPid = Long.parseLong(line.substring(4).trim());
                                pidSeen.countDown();
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    },
                    code -> { });
        }
    }

    @Test
    @DisplayName("Stop All returns the EDT in <200ms while a SIGTERM-proof process is being killed")
    void stopAllReturnsTheEdtWhileTheKillRunsAsync(@TempDir Path dir) throws Exception {
        assumeTrue(!System.getProperty("os.name", "").toLowerCase().contains("win"),
                "needs a POSIX sh with trap");
        rack = new Rack();
        rack.setProjectDir(dir.toFile());
        StubbornDevice stubborn = new StubbornDevice();
        rack.addDevice(stubborn);
        stubborn.launch();
        assertThat(stubborn.pidSeen.await(30, TimeUnit.SECONDS))
                .as("the stubborn process must come up and log its PID").isTrue();
        assertThat(ProcessHandle.of(stubborn.childPid)).isPresent();

        // fire the stop from the EDT, exactly like the toolbar button
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicLong edtBlockedNanos = new AtomicLong(-1);
        SwingUtilities.invokeAndWait(() -> {
            long t0 = System.nanoTime();
            rack.stopAllAsync(stopped::countDown);
            edtBlockedNanos.set(System.nanoTime() - t0);
        });

        // the regression this pins: the old synchronous path held the EDT
        // through killAndWait's 1.5s TERM grace (the process ignores TERM),
        // so a probe posted right behind the stop took >1.5s to run
        CountDownLatch probe = new CountDownLatch(1);
        SwingUtilities.invokeLater(probe::countDown);
        assertThat(probe.await(200, TimeUnit.MILLISECONDS))
                .as("the EDT must be free while the kill escalates in the background").isTrue();
        assertThat(TimeUnit.NANOSECONDS.toMillis(edtBlockedNanos.get()))
                .as("firing the stop itself must not block").isLessThan(200);

        // ... and the pass still finishes with the ORPHAN GUARANTEE intact
        assertThat(stopped.await(15, TimeUnit.SECONDS))
                .as("the async pass must complete").isTrue();
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline
                && ProcessHandle.of(stubborn.childPid).map(ProcessHandle::isAlive).orElse(false)) {
            Thread.sleep(50);
        }
        assertThat(ProcessHandle.of(stubborn.childPid).map(ProcessHandle::isAlive).orElse(false))
                .as("zero children after the async pass completes").isFalse();
    }

    /** A device whose panic blocks until the test releases it. */
    private static final class LatchedDevice extends RackDevice {

        final CountDownLatch panicStarted = new CountDownLatch(1);
        final CountDownLatch releasePanic = new CountDownLatch(1);
        final AtomicInteger panics = new AtomicInteger();

        LatchedDevice() {
            super("latched", "LATCHED", "TEST DEVICE", new Color(0, 0, 0), 1);
        }

        @Override
        public void panic() {
            panics.incrementAndGet();
            panicStarted.countDown();
            try {
                releasePanic.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    @DisplayName("A second Stop All while one is in flight is refused — no double-fire")
    void secondStopAllWhileInFlightIsRefused() throws Exception {
        rack = new Rack();
        LatchedDevice slow = new LatchedDevice();
        rack.addDevice(slow);

        CountDownLatch done = new CountDownLatch(1);
        assertThat(rack.stopAllAsync(done::countDown)).as("first pass starts").isTrue();
        assertThat(slow.panicStarted.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(rack.stopAllAsync(null))
                .as("a pass is in flight: the guard must refuse a second").isFalse();
        assertThat(slow.panics.get()).as("the device was panicked exactly once").isEqualTo(1);

        slow.releasePanic.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).as("first pass completes").isTrue();

        // guard resets before the callback runs, so a NEW pass may start
        assertThat(rack.stopAllAsync(null)).as("guard released after completion").isTrue();
    }

    @Test
    @DisplayName("An empty rack still runs the completion callback")
    void emptyRackStillCompletes() throws Exception {
        rack = new Rack();
        CountDownLatch done = new CountDownLatch(1);
        assertThat(rack.stopAllAsync(done::countDown)).isTrue();
        assertThat(done.await(5, TimeUnit.SECONDS))
                .as("the switch guard relies on the callback even with nothing to stop").isTrue();
    }

    @Test
    @DisplayName("The JVM shutdown reaper still panics synchronously — never through the async pool")
    void shutdownReaperStaysSynchronous() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/org/nmox/studio/rack/model/Rack.java"),
                StandardCharsets.UTF_8);
        int start = source.indexOf("addShutdownHook");
        assertThat(start).as("the reaper hook exists").isGreaterThan(0);
        int end = source.indexOf("nmox-rack-reaper", start);
        assertThat(end).as("the reaper thread name pins the block's extent").isGreaterThan(start);
        String hook = source.substring(start, end);
        // shutdown hooks are the last code that runs: a panic posted to a
        // worker pool there would simply never execute, orphaning children
        assertThat(hook).contains("d.panic()");
        assertThat(hook).doesNotContain("stopAllAsync");
        assertThat(hook).doesNotContain("stopAsync");
        assertThat(hook).doesNotContain("STOP_RP");
    }
}
