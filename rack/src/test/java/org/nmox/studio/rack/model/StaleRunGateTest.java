package org.nmox.studio.rack.model;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.devices.ViteDevice;
import org.nmox.studio.rack.service.WorkspaceTrust;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 18, second half: a stale run's exit must not drop the gate of
 * the run that replaced it.
 */
class StaleRunGateTest {

    @TempDir
    Path dir;

    private final List<Runnable> lane = new CopyOnWriteArrayList<>();

    @BeforeEach
    void trustScratch() {
        WorkspaceTrust.clearForTest();
    }

    @AfterEach
    void restore() {
        RackDevice.resetExecLane();
        WorkspaceTrust.clearForTest();
    }

    private static final class GateProbe extends RackDevice {
        final ConcurrentLinkedQueue<Boolean> gates = new ConcurrentLinkedQueue<>();

        GateProbe() {
            super("probe", "PROBE", "PROBE", new Color(0, 0, 0), 1);
            addInPort("gate", "GATE", SignalType.GATE);
        }

        @Override
        public void receive(Port in, Signal signal) {
            gates.add(signal.high());
        }
    }

    private Rack rackWithNodeProject() throws IOException {
        Files.writeString(dir.resolve("package.json"), "{}");
        WorkspaceTrust.trust(dir.toFile());
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        return rack;
    }

    private static void settle(Rack rack) {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) {
            // not relevant to the assertion
        }
        rack.awaitRouterIdle();
    }

    @Test
    @DisplayName("DEV pressed twice: the first run's exit must not drop the second run's SERVING gate")
    void staleExitKeepsItsHandsOffTheLiveRun() throws Exception {
        RackDevice.execLane = lane::add;
        Rack rack = rackWithNodeProject();
        try {
            ViteDevice vite = new ViteDevice();
            GateProbe probe = new GateProbe();
            rack.addDevice(vite);
            rack.addDevice(probe);
            rack.connect(vite.getPort("serving"), probe.getPort("gate"));

            vite.receive(vite.getPort("serve"), Signal.trigger());   // run A
            vite.receive(vite.getPort("serve"), Signal.trigger());   // run B replaces it
            settle(rack);
            assertThat(lane).as("both launches queued their spawn on the lane").hasSize(2);
            assertThat(probe.gates).as("each launch raised the gate").containsExactly(true, true);

            // Run A's lane task now runs: it finds itself cancelled by B,
            // spawns nothing, and reports its exit — the STALE run's exit,
            // arriving while B is the live run.
            lane.get(0).run();
            settle(rack);

            assertThat(vite.isLive())
                    .as("B is still the live run — nothing stopped it")
                    .isTrue();
            assertThat(probe.gates)
                    .as("a stale run's exit must not drop the live run's SERVING gate")
                    .containsExactly(true, true);
        } finally {
            rack.shutdown();
        }
    }
}
