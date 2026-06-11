package org.nmox.studio.rack.model;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RackTest {

    /** A minimal source device with one trigger out and one data out. */
    private static class SourceDevice extends RackDevice {

        SourceDevice() {
            super("test-source", "SOURCE", "TEST", Color.RED, 1);
            addOutPort("trig", "TRIG", SignalType.TRIGGER);
            addOutPort("data", "DATA", SignalType.DATA);
        }

        void fire() {
            emit("trig", Signal.trigger());
        }
    }

    /** A minimal sink device recording what it receives. */
    private static class SinkDevice extends RackDevice {

        final CountDownLatch received = new CountDownLatch(1);
        final AtomicReference<Signal> last = new AtomicReference<>();

        SinkDevice() {
            super("test-sink", "SINK", "TEST", Color.BLUE, 1);
            addInPort("trig", "TRIG", SignalType.TRIGGER);
            addInPort("data", "DATA", SignalType.DATA);
        }

        @Override
        public void receive(Port in, Signal signal) {
            last.set(signal);
            received.countDown();
        }
    }

    @Test
    @DisplayName("Should connect matching ports OUT to IN")
    void shouldConnectMatchingPorts() {
        Rack rack = new Rack();
        SourceDevice src = new SourceDevice();
        SinkDevice sink = new SinkDevice();
        rack.addDevice(src);
        rack.addDevice(sink);

        Cable cable = rack.connect(src.getPort("trig"), sink.getPort("trig"));

        assertThat(cable).isNotNull();
        assertThat(rack.getCables()).containsExactly(cable);
    }

    @Test
    @DisplayName("Should reject type-mismatched and same-direction connections")
    void shouldRejectInvalidConnections() {
        Rack rack = new Rack();
        SourceDevice src = new SourceDevice();
        SinkDevice sink = new SinkDevice();
        rack.addDevice(src);
        rack.addDevice(sink);

        // TRIGGER out into DATA in: type mismatch
        assertThat(rack.connect(src.getPort("trig"), sink.getPort("data"))).isNull();
        // OUT to OUT on different devices
        SourceDevice src2 = new SourceDevice();
        rack.addDevice(src2);
        assertThat(rack.connect(src.getPort("trig"), src2.getPort("trig"))).isNull();
        // duplicate cable
        assertThat(rack.connect(src.getPort("trig"), sink.getPort("trig"))).isNotNull();
        assertThat(rack.connect(src.getPort("trig"), sink.getPort("trig"))).isNull();
    }

    @Test
    @DisplayName("Should route signals from output to patched input")
    void shouldRouteSignals() throws Exception {
        Rack rack = new Rack();
        SourceDevice src = new SourceDevice();
        SinkDevice sink = new SinkDevice();
        rack.addDevice(src);
        rack.addDevice(sink);
        rack.connect(src.getPort("trig"), sink.getPort("trig"));

        src.fire();

        assertThat(sink.received.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(sink.last.get().type()).isEqualTo(SignalType.TRIGGER);
    }

    @Test
    @DisplayName("Should drop cables when a device is removed")
    void shouldDropCablesOnDeviceRemoval() {
        Rack rack = new Rack();
        SourceDevice src = new SourceDevice();
        SinkDevice sink = new SinkDevice();
        rack.addDevice(src);
        rack.addDevice(sink);
        rack.connect(src.getPort("trig"), sink.getPort("trig"));

        rack.removeDevice(src);

        assertThat(rack.getCables()).isEmpty();
        assertThat(rack.getDevices()).containsExactly(sink);
    }

    @Test
    @DisplayName("Should reorder devices with moveDevice")
    void shouldReorderDevices() {
        Rack rack = new Rack();
        SourceDevice a = new SourceDevice();
        SinkDevice b = new SinkDevice();
        SourceDevice c = new SourceDevice();
        rack.addDevice(a);
        rack.addDevice(b);
        rack.addDevice(c);

        rack.moveDevice(c, 0);

        assertThat(rack.getDevices()).containsExactly(c, a, b);
    }

    @Test
    @DisplayName("Should apply env overrides for device commands")
    void shouldTrackEnvOverrides() {
        Rack rack = new Rack();
        rack.putEnv("NODE_ENV", "production");
        rack.putEnv("CI", "true");
        rack.putEnv("CI", null); // unset

        assertThat(rack.getEnvOverrides())
                .containsEntry("NODE_ENV", "production")
                .doesNotContainKey("CI");
    }
}
