package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The serving gate tells the truth through the trust gate (v1.93.0).
 * Serve verbs used to emit {@code serving=true} BEFORE {@code launch()},
 * whose trust gate returns early on a Keep Safe answer — no process, no
 * onFinished, and the gate stayed high forever with nothing serving:
 * SPECTER's ENABLE would run a suite against nothing, TEMPO/QUORUM were
 * lied to. Every gate emitter now fires only when launch() reports the
 * command was really handed to the executor.
 */
class ServingTruthTest {

    @TempDir
    Path dir;

    private final Predicate<java.io.File> originalTrust = CommandDevice.trustCheck;

    @AfterEach
    void restoreTrust() {
        CommandDevice.trustCheck = originalTrust;
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
    @DisplayName("Keep Safe leaves the SERVING gate silent — no phantom high")
    void trustRefusalEmitsNoGate() throws Exception {
        Rack rack = rackWithNodeProject();
        try {
            ViteDevice vite = new ViteDevice();
            GateProbe probe = new GateProbe();
            rack.addDevice(vite);
            rack.addDevice(probe);
            rack.connect(vite.getPort("serving"), probe.getPort("gate"));

            CommandDevice.trustCheck = f -> false; // the Keep Safe answer
            vite.receive(vite.getPort("serve"), Signal.trigger());
            settle(rack);

            assertThat(probe.gates)
                    .as("a refused launch must not raise the serving gate")
                    .isEmpty();
            assertThat(vite.isLive()).isFalse();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("A granted launch raises the gate exactly as before")
    void trustGrantRaisesGate() throws Exception {
        Rack rack = rackWithNodeProject();
        try {
            ViteDevice vite = new ViteDevice();
            GateProbe probe = new GateProbe();
            rack.addDevice(vite);
            rack.addDevice(probe);
            rack.connect(vite.getPort("serving"), probe.getPort("gate"));

            CommandDevice.trustCheck = f -> true;
            vite.receive(vite.getPort("serve"), Signal.trigger());
            settle(rack);

            assertThat(probe.gates)
                    .as("granted trust: the serving gate goes high")
                    .contains(true);
            vite.receive(vite.getPort("stop"), Signal.trigger()); // the v1.90.0 jack
            settle(rack);
        } finally {
            rack.shutdown();
        }
    }
}
