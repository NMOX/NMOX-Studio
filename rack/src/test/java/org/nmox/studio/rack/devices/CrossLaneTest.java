package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
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

/**
 * The cross-lane coordination primitives: QUORUM (the AND/ANY barrier),
 * the readiness ENABLE gate on long-runners, and REFLEX's per-lane glob
 * route. These are what make parallel polyglot lanes converge.
 */
class CrossLaneTest {

    // ---- QUORUM: the barrier ----

    @Test
    @DisplayName("ALL mode fires OK only once every wired lane has arrived, AND of their results")
    void allBarrierWaitsForEveryLane() throws InterruptedException {
        Rack rack = new Rack();
        Emitter web = new Emitter();
        Emitter api = new Emitter();
        JoinDevice quorum = new JoinDevice();
        Sink okSink = new Sink();
        rack.addDevice(web);
        rack.addDevice(api);
        rack.addDevice(quorum);
        rack.addDevice(okSink);
        rack.connect(web.getPort("out"), quorum.getPort("in1"));
        rack.connect(api.getPort("out"), quorum.getPort("in2"));
        rack.connect(quorum.getPort("ok"), okSink.getPort("in"));

        web.fire(true);
        // one lane in: the barrier must NOT have fired yet
        Thread.sleep(200);
        assertThat(okSink.hit.getCount()).as("still waiting for the second lane").isEqualTo(1);

        api.fire(true);
        assertThat(okSink.hit.await(3, TimeUnit.SECONDS)).as("both lanes green → OK").isTrue();
        assertThat(okSink.got).containsExactly(true);
        rack.shutdown();
    }

    @Test
    @DisplayName("ALL mode fires FAIL (not OK) when any lane fails")
    void allBarrierFailsIfAnyLaneFails() throws InterruptedException {
        Rack rack = new Rack();
        Emitter web = new Emitter();
        Emitter api = new Emitter();
        JoinDevice quorum = new JoinDevice();
        Sink okSink = new Sink();
        Sink failSink = new Sink();
        rack.addDevice(web);
        rack.addDevice(api);
        rack.addDevice(quorum);
        rack.addDevice(okSink);
        rack.addDevice(failSink);
        rack.connect(web.getPort("out"), quorum.getPort("in1"));
        rack.connect(api.getPort("out"), quorum.getPort("in2"));
        rack.connect(quorum.getPort("ok"), okSink.getPort("in"));
        rack.connect(quorum.getPort("fail"), failSink.getPort("in"));

        web.fire(true);
        api.fire(false);
        assertThat(failSink.hit.await(3, TimeUnit.SECONDS)).as("one red lane → FAIL").isTrue();
        assertThat(okSink.hit.getCount()).as("OK must not fire").isEqualTo(1);
        rack.shutdown();
    }

    @Test
    @DisplayName("ANY mode relays the first arrival without waiting")
    void anyModeRacesTheLanes() throws InterruptedException {
        Rack rack = new Rack();
        Emitter web = new Emitter();
        JoinDevice quorum = new JoinDevice();
        quorum.applyState(Map.of("mode", "1")); // ANY
        Sink okSink = new Sink();
        rack.addDevice(web);
        rack.addDevice(quorum);
        rack.addDevice(okSink);
        rack.connect(web.getPort("out"), quorum.getPort("in1"));
        rack.connect(quorum.getPort("ok"), okSink.getPort("in"));

        web.fire(true);
        assertThat(okSink.hit.await(3, TimeUnit.SECONDS)).as("first arrival relayed").isTrue();
        rack.shutdown();
    }

    // ---- ENABLE gate: readiness-driven start/stop ----

    @Test
    @DisplayName("ENABLE gate starts a stopped long-runner on a high edge, stops it on low")
    void enableGateStartsAndStops() {
        GateProbe p = new GateProbe();

        p.alive = false;
        p.callEnable(true); // high while stopped → start
        assertThat(p.starts).isEqualTo(1);
        assertThat(p.stops).isZero();

        p.alive = true;
        p.callEnable(true); // high while already running → ignored, no double-launch
        assertThat(p.starts).isEqualTo(1);

        p.callEnable(false); // low → stop
        assertThat(p.stops).isEqualTo(1);
    }

    // ---- REFLEX glob route ----

    @Test
    @DisplayName("REFLEX glob parses bare, dotted, and glob extensions into one lane")
    void globRoutesToOneLane() {
        assertThat(ReflexDevice.parseGlob("rs")).containsExactly("rs");
        assertThat(ReflexDevice.parseGlob("ts,tsx")).containsExactlyInAnyOrder("ts", "tsx");
        assertThat(ReflexDevice.parseGlob("*.rs")).containsExactly("rs");
        assertThat(ReflexDevice.parseGlob("*.{ts,tsx}")).containsExactlyInAnyOrder("ts", "tsx");
        assertThat(ReflexDevice.parseGlob(".go")).containsExactly("go");
        assertThat(ReflexDevice.parseGlob("   ")).isNull();
    }

    // ---- probes ----

    /** Fires a trigger down a cable on command. */
    private static final class Emitter extends RackDevice {
        Emitter() {
            super("emit", "EMIT", "", new Color(0, 0, 0), 1);
            addOutPort("out", "OUT", SignalType.TRIGGER);
        }

        void fire(boolean ok) {
            emit("out", Signal.trigger(ok));
        }

        @Override
        public void receive(Port in, Signal signal) {
        }
    }

    /** Latches and records the first trigger it receives. */
    private static final class Sink extends RackDevice {
        final ConcurrentLinkedQueue<Boolean> got = new ConcurrentLinkedQueue<>();
        final CountDownLatch hit = new CountDownLatch(1);

        Sink() {
            super("sink", "SINK", "", new Color(0, 0, 0), 1);
            addInPort("in", "IN", SignalType.TRIGGER);
        }

        @Override
        public void receive(Port in, Signal signal) {
            got.add(signal.high());
            hit.countDown();
        }
    }

    /** Exercises enableGate with a controllable running state. */
    private static final class GateProbe extends RackDevice {
        boolean alive;
        int starts;
        int stops;

        GateProbe() {
            super("gate", "GATE", "", new Color(0, 0, 0), 1);
        }

        @Override
        protected boolean isProcessRunning() {
            return alive;
        }

        void callEnable(boolean high) {
            enableGate(high, () -> starts++, () -> stops++);
        }

        @Override
        public void receive(Port in, Signal signal) {
        }
    }
}
