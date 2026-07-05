package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.service.ServingRegistry;
import org.nmox.studio.rack.service.ServingRegistry.Serving;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The device removal/undo lifecycle: a deleted device leaves no ghost
 * serving behind, a signal that arrives after removal launches nothing,
 * and undo of a remove — which re-attaches the SAME instance — brings
 * the device fully back to life. The disposed flag must be a state, not
 * a death sentence.
 */
class DeviceDisposeLifecycleTest {

    @TempDir
    Path projectDir;

    private Rack aimedRack() throws IOException {
        Files.writeString(projectDir.resolve("package.json"), "{\"name\":\"demo\"}");
        Rack rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        return rack;
    }

    /** This project's servings only — the registry is a JVM singleton. */
    private List<Serving> mine() {
        File dir = projectDir.toFile();
        return ServingRegistry.getDefault().snapshot().stream()
                .filter(s -> s.projectDir().equals(dir)).toList();
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    /** Drain BOTH async paths before asserting — the rack-test law. */
    private static void settle(Rack rack) throws Exception {
        rack.awaitRouterIdle();
        flushEdt();
    }

    private static final class EchoDevice extends CommandDevice {

        final CountDownLatch finished = new CountDownLatch(1);
        volatile int exitCode = Integer.MIN_VALUE;

        EchoDevice() {
            super("test-echo", "ECHO", "TEST", Color.GRAY, 2);
        }

        @Override
        protected List<String> buildCommand() {
            return List.of("echo", "hello");
        }

        @Override
        protected void onFinished(int code) {
            exitCode = code;
            finished.countDown();
        }
    }

    @Test
    @DisplayName("Removing a device mid-serve deregisters its live serving — no ghost URL")
    void disposeDeregistersLiveServing() throws Exception {
        Rack rack = aimedRack();
        try {
            NextDevice next = new NextDevice();
            rack.addDevice(next);
            next.onLine("  ▲ Next.js  - Local: http://localhost:3000");
            assertThat(mine()).as("serving registered on the URL announce")
                    .extracting(Serving::url).containsExactly("http://localhost:3000");

            // remove WITHOUT the exit pump ever firing onFinished — the
            // kernel-unkillable edge where dispose is the only cleanup path
            rack.removeDevice(next);
            settle(rack);
            ServingRegistry.getDefault().awaitIdle();

            assertThat(next.isDisposed()).isTrue();
            assertThat(mine()).as("dispose withdrew the serving").isEmpty();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("A signal delivered after removal launches nothing")
    void signalAfterRemovalLaunchesNothing() throws Exception {
        Rack rack = aimedRack();
        EchoDevice device = new EchoDevice();
        try {
            rack.addDevice(device);
            rack.removeDevice(device);
            settle(rack);
            assertThat(device.isDisposed()).isTrue();

            // exactly what the router does for a signal queued before the
            // removal: deliver into the (now removed) captured device
            device.receive(device.getPort("run"), Signal.trigger());
            settle(rack);

            assertThat(device.finished.await(500, TimeUnit.MILLISECONDS))
                    .as("no process may start inside a deleted device").isFalse();
            assertThat(device.isLive()).isFalse();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("Undo of a remove re-attaches the same instance and it works again")
    void undoOfRemoveRevivesTheDevice() throws Exception {
        Rack rack = aimedRack();
        EchoDevice device = new EchoDevice();
        try {
            rack.addDevice(device);
            rack.enableUndoCapture(); // interactive edits from here on
            rack.removeDevice(device);
            settle(rack);
            assertThat(device.isDisposed()).isTrue();
            assertThat(rack.getDevices()).doesNotContain(device);

            rack.undo();
            settle(rack);

            assertThat(rack.getDevices()).as("undo re-mounts the SAME instance")
                    .contains(device);
            assertThat(device.isDisposed())
                    .as("re-attach clears the disposed flag — a permanent flag "
                            + "would leave undo-restored devices dead").isFalse();

            // and it is functionally alive: receive triggers a real run
            device.receive(device.getPort("run"), Signal.trigger());
            assertThat(device.finished.await(10, TimeUnit.SECONDS))
                    .as("the revived device runs its command").isTrue();
            assertThat(device.exitCode).isZero();
        } finally {
            rack.shutdown();
        }
    }
}
