package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.spi.device.DeviceCategory;
import org.nmox.studio.core.spi.device.DeviceDescriptor;
import org.nmox.studio.core.spi.device.DeviceExtension;
import org.nmox.studio.core.spi.device.DeviceFace;
import org.nmox.studio.core.spi.device.DeviceLogic;
import org.nmox.studio.core.spi.device.DeviceServices;
import org.nmox.studio.core.spi.device.PortSpec;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The hosting contract: an extension's descriptor becomes a real device
 * with real jacks, its face persists like any faceplate, its signals
 * route, and the HOST — not the plugin — enforces the trust gate before
 * anything spawns.
 */
class ExtensionDeviceTest {

    private static final String USAGE =
            "A test device with enough usage text to satisfy the shelf law.\n"
                    + "Patch OK onward to chain, like every other device.";

    @AfterEach
    void restoreTrustGate() {
        ExtensionDevice.trustGate = f -> true;
    }

    private static DeviceDescriptor descriptor(int units, List<PortSpec> ports) {
        return new DeviceDescriptor("com.example.fix", "FIX", "test", new Color(1, 2, 3),
                DeviceCategory.OBSERVE, USAGE, units, ports);
    }

    private static DeviceExtension ext(DeviceDescriptor d,
            java.util.function.BiFunction<DeviceFace, DeviceServices, DeviceLogic> build) {
        return new DeviceExtension() {
            @Override
            public DeviceDescriptor descriptor() {
                return d;
            }

            @Override
            public DeviceLogic build(DeviceFace face, DeviceServices services) {
                return build.apply(face, services);
            }
        };
    }

    @Test
    @DisplayName("the descriptor becomes a mounted device: id, title, units, typed jacks")
    void descriptorBecomesDevice() {
        ExtensionDevice d = new ExtensionDevice(ext(descriptor(2, List.of(
                new PortSpec("run", "RUN", PortSpec.Direction.IN, PortSpec.Signal.TRIGGER),
                new PortSpec("out", "OUT", PortSpec.Direction.OUT, PortSpec.Signal.DATA))),
                (face, services) -> new DeviceLogic() {
                }));
        assertThat(d.getTypeId()).isEqualTo("com.example.fix");
        assertThat(d.getTitle()).isEqualTo("FIX");
        assertThat(d.getUnits()).isEqualTo(2);
        Port run = d.getPort("run");
        assertThat(run.getDirection()).isEqualTo(Port.Direction.IN);
        assertThat(run.getType()).isEqualTo(SignalType.TRIGGER);
        assertThat(d.getPort("out").getType()).isEqualTo(SignalType.DATA);
    }

    @Test
    @DisplayName("signals dispatch to the logic by kind; emits route out through cables")
    void signalsFlowBothWays() throws Exception {
        List<String> seen = new CopyOnWriteArrayList<>();
        DeviceServices[] svc = new DeviceServices[1];
        ExtensionDevice d = new ExtensionDevice(ext(descriptor(1, List.of(
                new PortSpec("run", "RUN", PortSpec.Direction.IN, PortSpec.Signal.TRIGGER),
                new PortSpec("in", "IN", PortSpec.Direction.IN, PortSpec.Signal.DATA),
                new PortSpec("enable", "ENABLE", PortSpec.Direction.IN, PortSpec.Signal.GATE),
                new PortSpec("ok", "OK", PortSpec.Direction.OUT, PortSpec.Signal.TRIGGER))),
                (face, services) -> {
                    svc[0] = services;
                    return new DeviceLogic() {
                        @Override
                        public void onTrigger(String portId, boolean ok) {
                            seen.add("trigger:" + portId + ":" + ok);
                        }

                        @Override
                        public void onData(String portId, String text) {
                            seen.add("data:" + portId + ":" + text);
                        }

                        @Override
                        public void onGate(String portId, boolean high) {
                            seen.add("gate:" + portId + ":" + high);
                        }
                    };
                }));
        d.receive(d.getPort("run"), Signal.trigger(false));
        d.receive(d.getPort("in"), Signal.data("hello"));
        d.receive(d.getPort("enable"), Signal.gate(true));
        assertThat(seen).containsExactly("trigger:run:false", "data:in:hello", "gate:enable:true");

        // emit routes out through a real cable to a probe device
        Rack rack = new Rack();
        rack.addDevice(d);
        List<String> probeSeen = new CopyOnWriteArrayList<>();
        RackDevice probe = new RackDevice("test.probe", "PROBE", "", Color.GRAY, 1) {
            final Port in = addInPort("in", "IN", SignalType.TRIGGER);

            @Override
            public void receive(Port in, Signal s) {
                probeSeen.add("ok:" + s.high());
            }
        };
        rack.addDevice(probe);
        rack.connect(d.getPort("ok"), probe.getPort("in"));
        svc[0].emitTrigger("ok", true);
        rack.awaitRouterIdle();
        assertThat(probeSeen).containsExactly("ok:true");

        // an emit on an undeclared or wrong-kind port is ignored, not a crash
        svc[0].emitData("nope", "x");
        svc[0].emitData("ok", "wrong kind");
        rack.awaitRouterIdle();
        assertThat(probeSeen).hasSize(1);
    }

    @Test
    @DisplayName("face state persists: knob/toggle/field round-trip through the patch format")
    void faceStatePersists() {
        DeviceExtension e = ext(descriptor(2, List.of()), (face, services) -> {
            var mode = face.knob("mode", "MODE", new String[]{"a", "b", "c"}, 0);
            face.toggle("armed", "ARM", false);
            face.lcdField("target", "TARGET", 160);
            mode.select("c");
            return new DeviceLogic() {
            };
        });
        ExtensionDevice first = new ExtensionDevice(e);
        flushEdt();
        Map<String, String> state = first.getState();
        assertThat(state).containsEntry("mode", "c").containsKey("armed").containsKey("target");

        ExtensionDevice clone = new ExtensionDevice(e);
        flushEdt();
        clone.applyState(state);
        assertThat(clone.getState()).isEqualTo(state);
    }

    @Test
    @DisplayName("every face control carries an accessible name; blank labels are refused")
    void faceEnforcesTheNameLaw() {
        ExtensionDevice d = new ExtensionDevice(ext(descriptor(2, List.of()),
                (face, services) -> {
                    face.knob("k", "MODE", new String[]{"a"}, 0);
                    face.button("GO", DeviceFace.ButtonRole.GO);
                    face.led("EYE", DeviceFace.LedTone.OK);
                    face.lcd("STATUS", 120, 1);
                    face.vu("FEED");
                    return new DeviceLogic() {
                    };
                }));
        for (java.awt.Component c : d.getComponents()) {
            assertThat(c.getAccessibleContext().getAccessibleName())
                    .as(c.getClass().getSimpleName()).isNotBlank();
        }
        assertThatThrownBy(() -> new ExtensionDevice(ext(descriptor(2, List.of()),
                (face, services) -> {
                    face.led("  ", DeviceFace.LedTone.OK);
                    return new DeviceLogic() {
                    };
                }))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a face that overflows its declared units fails at mount, naming the control")
    void overflowingFaceFailsLoudly() {
        // a knob is 78px tall; 1U is 66px — this must refuse to mount
        assertThatThrownBy(() -> new ExtensionDevice(ext(descriptor(1, List.of()),
                (face, services) -> {
                    face.knob("k", "MODE", new String[]{"a"}, 0);
                    return new DeviceLogic() {
                    };
                })))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MODE")
                .hasMessageContaining("units");
    }

    @Test
    @DisplayName("the HOST gates exec on workspace trust: no trust, no spawn, exit -1")
    void execGatesOnTrust() throws Exception {
        DeviceServices[] svc = new DeviceServices[1];
        new ExtensionDevice(ext(descriptor(1, List.of()), (face, services) -> {
            svc[0] = services;
            return new DeviceLogic() {
            };
        }));

        ExtensionDevice.trustGate = f -> false;
        List<String> lines = new CopyOnWriteArrayList<>();
        CountDownLatch refused = new CountDownLatch(1);
        int[] code = new int[]{Integer.MIN_VALUE};
        svc[0].exec(List.of("echo", "must-not-run"), lines::add, c -> {
            code[0] = c;
            refused.countDown();
        });
        assertThat(refused.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(code[0]).isEqualTo(-1);
        assertThat(lines).as("nothing may spawn without trust").isEmpty();
        assertThat(svc[0].isRunning()).isFalse();

        // trusted: the same call really runs and streams
        ExtensionDevice.trustGate = f -> true;
        CountDownLatch done = new CountDownLatch(1);
        svc[0].exec(List.of("echo", "hello-spi"), lines::add, c -> done.countDown());
        assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();
        assertThat(lines).anyMatch(l -> l.contains("hello-spi"));
    }

    @Test
    @DisplayName("dispose tells the logic, and a throwing plugin cannot block the rack's teardown")
    void disposeReachesLogic() {
        boolean[] disposed = new boolean[1];
        ExtensionDevice d = new ExtensionDevice(ext(descriptor(1, List.of()),
                (face, services) -> new DeviceLogic() {
                    @Override
                    public void onDispose() {
                        disposed[0] = true;
                        throw new IllegalStateException("plugin cleanup bug");
                    }
                }));
        d.dispose();
        assertThat(disposed[0]).isTrue();
        assertThat(d.isDisposed()).isTrue();
    }

    private static void flushEdt() {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> {
            });
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
