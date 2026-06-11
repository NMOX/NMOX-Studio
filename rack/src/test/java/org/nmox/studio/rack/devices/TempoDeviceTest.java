package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;

import static org.assertj.core.api.Assertions.assertThat;

class TempoDeviceTest {

    private static class TickSink extends RackDevice {

        final CountDownLatch ticks = new CountDownLatch(1);

        TickSink() {
            super("test-sink", "SINK", "TEST", Color.BLUE, 1);
            addInPort("in", "IN", SignalType.TRIGGER);
        }

        @Override
        public void receive(Port in, Signal signal) {
            ticks.countDown();
        }
    }

    @Test
    @DisplayName("Should fire TICK while running and stop when halted")
    void shouldTickWhileRunning() throws Exception {
        Rack rack = new Rack();
        TempoDevice tempo = new TempoDevice();
        TickSink sink = new TickSink();
        rack.addDevice(tempo);
        rack.addDevice(sink);
        rack.connect(tempo.getPort("tick"), sink.getPort("in"));

        // run at the fastest rate (5s); tick should land within ~7s
        tempo.applyState(java.util.Map.of("rate", "0", "running", "true"));

        try {
            assertThat(sink.ticks.await(8, TimeUnit.SECONDS))
                    .as("TICK fired while clock runs").isTrue();
        } finally {
            tempo.applyState(java.util.Map.of("running", "false"));
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("Should expose rate and running state for patch persistence")
    void shouldPersistState() {
        TempoDevice tempo = new TempoDevice();
        tempo.applyState(java.util.Map.of("rate", "3", "running", "false"));

        assertThat(tempo.getState())
                .containsEntry("rate", "3")
                .containsEntry("running", "false");
        tempo.dispose();
    }
}
