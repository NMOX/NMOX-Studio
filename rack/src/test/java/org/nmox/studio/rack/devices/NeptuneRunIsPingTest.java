package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;
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
 * NEPTUNE's RUN jack is a ping, whatever button was pressed last, and the
 * CONNECTED gate is a ping's verdict only.
 *
 * <p>RUN used to fall to {@code primaryAction → buildCommand()}, which
 * switches on the last BUTTON pressed: a RUN cable pinged until someone
 * pressed MIGRATE on the faceplate, after which the same cable ran
 * migrations. And a migration's exit drove the CONNECTED gate, so a
 * failed migrate.sql read as "not connected" (the 2026-09-17 rack audit).
 */
class NeptuneRunIsPingTest {

    @TempDir
    Path dir;

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

    @Test
    @DisplayName("After MIGRATE was pressed, a RUN trigger still builds the ping command")
    void runIsAlwaysPing() throws Exception {
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile()); // no manifest: every launch refuses, nothing spawns
        try {
            DatabaseDevice neptune = new DatabaseDevice();
            rack.addDevice(neptune);
            neptune.receive(neptune.getPort("migrate"), Signal.trigger());
            assertThat(neptune.buildCommand()).as("the faceplate's last verb").contains("-f", "migrate.sql");

            neptune.receive(neptune.getPort("run"), Signal.trigger());
            assertThat(neptune.buildCommand())
                    .as("RUN by cable is a ping regardless of the last button")
                    .contains("-c", "SELECT 1;");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("CONNECTED answers a ping's exit; a migration's exit leaves the gate alone")
    void connectedGateIsAPingVerdict() throws Exception {
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile()); // no manifest: the verb is captured, nothing spawns
        try {
            DatabaseDevice neptune = new DatabaseDevice();
            GateProbe probe = new GateProbe();
            rack.addDevice(neptune);
            rack.addDevice(probe);
            rack.connect(neptune.getPort("connected"), probe.getPort("gate"));

            neptune.receive(neptune.getPort("migrate"), Signal.trigger());
            neptune.onFinished(1); // the migration failed: that says nothing about the connection
            rack.awaitRouterIdle();
            assertThat(probe.gates).as("a migration's exit never drives CONNECTED").isEmpty();

            neptune.receive(neptune.getPort("run"), Signal.trigger());
            neptune.onFinished(0);
            rack.awaitRouterIdle();
            assertThat(probe.gates).as("a ping's exit does").containsExactly(true);
        } finally {
            rack.shutdown();
        }
    }
}
