package org.nmox.studio.rack.service;

import java.awt.Color;
import java.nio.file.Path;
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
 */
class ProjectSwitchGuardTest {

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
    void switchStopsLiveWorkWithConsent(@TempDir Path a, @TempDir Path b) {
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
        assertThat(device.panicked).as("live device stopped before the swap").isTrue();
        assertThat(rack.getProjectDir()).isEqualTo(b.toFile());
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
