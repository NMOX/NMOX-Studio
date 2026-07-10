package org.nmox.studio.rack.service;

import java.awt.Color;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The project-switch guard: work in flight is named and stopped with
 * consent, never silently killed by the patch swap - and a declined
 * switch leaves everything exactly as it was.
 *
 * <p>Since ledger item 15 the post-consent stops run on background
 * workers (panic() can block ~2.5s per stubborn device on what is an EDT
 * path) and the swap itself completes in a callback, so consenting tests
 * await the switch instead of asserting synchronously.
 */
class ProjectSwitchGuardTest {

    /** Polls until the rack aims at {@code dir} — the async swap landing. */
    private static void awaitAim(Rack rack, File dir) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline && !rack.getProjectDir().equals(dir)) {
            Thread.sleep(25);
        }
        assertThat(rack.getProjectDir()).isEqualTo(dir);
    }

    /** A device that reports live until told to panic. */
    private static final class FakeLive extends RackDevice {

        volatile boolean live;
        volatile boolean panicked;

        FakeLive() {
            super("fake-live", "FAKE", "TEST DEVICE", new Color(0, 0, 0), 1);
        }

        @Override
        public boolean isLive() {
            return live;
        }

        @Override
        public void panic() {
            panicked = true;
            live = false;
        }
    }

    @Test
    @DisplayName("Switching away from live work asks once, then stops it cleanly")
    void switchStopsLiveWorkWithConsent(@TempDir Path a, @TempDir Path b) throws Exception {
        RackService service = new RackService();
        Rack rack = service.getRack();
        AtomicInteger asked = new AtomicInteger();
        service.switchConfirmer = message -> {
            asked.incrementAndGet();
            assertThat(message).contains("FAKE");
            return true;
        };

        service.openProject(a.toFile());
        FakeLive device = new FakeLive();
        rack.addDevice(device);
        device.live = true;

        service.openProject(b.toFile());

        assertThat(asked.get()).isEqualTo(1);
        awaitAim(rack, b.toFile());
        assertThat(device.panicked).as("live device stopped before the swap").isTrue();
        rack.shutdown();
    }

    @Test
    @DisplayName("The switch waits for the stops: the aim moves only after every live device is dead")
    void switchWaitsForTheAsyncStops(@TempDir Path a, @TempDir Path b) throws Exception {
        RackService service = new RackService();
        Rack rack = service.getRack();
        service.switchConfirmer = message -> true;
        service.openProject(a.toFile());

        // a device whose stop takes real time — the stubborn dev server
        CountDownLatch stopStarted = new CountDownLatch(1);
        CountDownLatch releaseStop = new CountDownLatch(1);
        final class SlowStop extends RackDevice {
            volatile boolean live = true;
            SlowStop() {
                super("slow-stop", "SLOW", "TEST DEVICE", new Color(0, 0, 0), 1);
            }
            @Override public boolean isLive() {
                return live;
            }
            @Override public void panic() {
                stopStarted.countDown();
                try {
                    releaseStop.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                live = false;
            }
        }
        SlowStop device = new SlowStop();
        rack.addDevice(device);

        service.openProject(b.toFile()); // returns immediately; the stop is in flight

        assertThat(stopStarted.await(5, TimeUnit.SECONDS)).isTrue();
        // the kill is still running: the swap must not have happened —
        // a patch mounting over a dying dev server is the race this pins
        Thread.sleep(150); // generous window for a gun-jumping implementation
        assertThat(rack.getProjectDir())
                .as("the aim must hold until the stop completes").isEqualTo(a.toFile());

        releaseStop.countDown();
        awaitAim(rack, b.toFile());
        assertThat(device.live).as("the swap only ran after the device died").isFalse();
        rack.shutdown();
    }

    @Test
    @DisplayName("Declining the switch keeps the aim and the running work")
    void decliningKeepsEverything(@TempDir Path a, @TempDir Path b) {
        RackService service = new RackService();
        Rack rack = service.getRack();
        service.switchConfirmer = message -> false;

        service.openProject(a.toFile());
        FakeLive device = new FakeLive();
        rack.addDevice(device);
        device.live = true;

        service.openProject(b.toFile());

        assertThat(device.panicked).isFalse();
        assertThat(rack.getProjectDir()).isEqualTo(a.toFile());
        rack.shutdown();
    }

    @Test
    @DisplayName("Re-aiming the same project never prompts")
    void sameDirNeverPrompts(@TempDir Path a) {
        RackService service = new RackService();
        Rack rack = service.getRack();
        AtomicInteger asked = new AtomicInteger();
        service.switchConfirmer = message -> {
            asked.incrementAndGet();
            return true;
        };

        service.openProject(a.toFile());
        FakeLive device = new FakeLive();
        rack.addDevice(device);
        device.live = true;

        service.openProject(a.toFile());

        assertThat(asked.get()).isZero();
        assertThat(device.panicked).isFalse();
        rack.shutdown();
    }
}
