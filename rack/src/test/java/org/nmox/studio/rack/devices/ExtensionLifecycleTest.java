package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.spi.device.DeviceCategory;
import org.nmox.studio.core.spi.device.DeviceDescriptor;
import org.nmox.studio.core.spi.device.DeviceExtension;
import org.nmox.studio.core.spi.device.DeviceFace;
import org.nmox.studio.core.spi.device.DeviceLogic;
import org.nmox.studio.core.spi.device.DeviceServices;
import org.nmox.studio.core.spi.device.PortSpec;
import org.nmox.studio.rack.model.Rack;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The freeze-critical lifecycles the v1.56 review found unpinned: undo
 * re-attach must revive a plugin (the v1.50 TAIL/TEMPO bug class), the
 * process must be stopped before onDispose() runs (the javadoc's
 * promise), and a plugin's throwing callbacks must never break the
 * rack's own teardown/re-attach.
 */
class ExtensionLifecycleTest {

    private static final String USAGE =
            "A lifecycle fixture with enough usage text to pass the shelf law.\n"
                    + "Patch OK onward like any device.";

    private static DeviceExtension ext(DeviceLogic logic) {
        DeviceDescriptor d = new DeviceDescriptor("com.example.life", "LIFE", "t",
                new Color(1, 2, 3), DeviceCategory.OBSERVE, USAGE, 1, List.of(
                        new PortSpec("ok", "OK", PortSpec.Direction.OUT, PortSpec.Signal.TRIGGER)));
        return new DeviceExtension() {
            @Override
            public DeviceDescriptor descriptor() {
                return d;
            }

            @Override
            public DeviceLogic build(DeviceFace face, DeviceServices services) {
                face.button("GO", DeviceFace.ButtonRole.GO);
                return logic;
            }
        };
    }

    @Test
    @DisplayName("undo re-attach calls onAttached so a plugin can re-arm — the v1.50 bug class, closed for the SPI")
    void reAttachRevivesThePlugin() {
        List<String> events = new CopyOnWriteArrayList<>();
        ExtensionDevice d = new ExtensionDevice(ext(new DeviceLogic() {
            @Override
            public void onAttached(DeviceServices services) {
                events.add("attached");
            }

            @Override
            public void onDispose() {
                events.add("disposed");
            }
        }));

        Rack rack = new Rack();
        rack.addDevice(d);                 // first mount → attached
        rack.removeDevice(d);              // → disposed
        rack.addDevice(d);                 // undo re-attach of the SAME instance → attached again

        assertThat(events).containsExactly("attached", "disposed", "attached");
    }

    @Test
    @DisplayName("onAttached hands the plugin its services, so it can re-announce serving on revival")
    void reAttachProvidesServices() {
        DeviceServices[] seen = new DeviceServices[1];
        ExtensionDevice d = new ExtensionDevice(ext(new DeviceLogic() {
            @Override
            public void onAttached(DeviceServices services) {
                seen[0] = services;
            }
        }));
        new Rack().addDevice(d);
        assertThat(seen[0]).as("the plugin gets a live services handle to re-arm with").isNotNull();
    }

    @Test
    @org.junit.jupiter.api.condition.DisabledOnOs(
            value = org.junit.jupiter.api.condition.OS.WINDOWS,
            disabledReason = "starts a real POSIX `sleep` to hold a live process across dispose")
    @DisplayName("with a LIVE process, onDispose observes it already stopped — the javadoc's promise")
    void processStoppedBeforeOnDispose() throws Exception {
        boolean[] runningAtDispose = new boolean[]{true};
        DeviceServices[] svc = new DeviceServices[1];
        ExtensionDevice d = new ExtensionDevice(new DeviceExtension() {
            @Override
            public DeviceDescriptor descriptor() {
                return new DeviceDescriptor("com.example.life2", "LIFE2", "t", new Color(1, 2, 3),
                        DeviceCategory.OBSERVE, USAGE, 1, List.of());
            }

            @Override
            public DeviceLogic build(DeviceFace face, DeviceServices services) {
                svc[0] = services;
                return new DeviceLogic() {
                    @Override
                    public void onDispose() {
                        // the ordering contract: by the time onDispose runs,
                        // stopProcess has already killed the live process
                        runningAtDispose[0] = services.isRunning();
                    }
                };
            }
        });
        ExtensionDevice.trustGate = f -> true;   // never let the trust gate block the spawn
        new Rack().addDevice(d);

        // hold a real process open, then confirm it's actually running. Process
        // fork can lag under a saturated CI runner, so wait generously; if the
        // OS never gives us a live process the ordering can't be observed, so
        // SKIP rather than false-fail (the mutation proof holds on any runner
        // that does spawn — which is every healthy one, and every dev machine).
        svc[0].exec(List.of("sleep", "30"), line -> { }, code -> { });
        for (int i = 0; i < 500 && !svc[0].isRunning(); i++) {
            Thread.sleep(20);
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(svc[0].isRunning(),
                "process fork did not become live on this runner; skipping the ordering check");

        d.dispose();
        assertThat(runningAtDispose[0])
                .as("onDispose must observe a stopped process — stopProcess runs first").isFalse();
    }

    @Test
    @DisplayName("a plugin that throws in onAttached/onDispose cannot break the rack")
    void throwingCallbacksDoNotBreakTheRack() {
        ExtensionDevice d = new ExtensionDevice(ext(new DeviceLogic() {
            @Override
            public void onAttached(DeviceServices services) {
                throw new IllegalStateException("plugin re-arm bug");
            }

            @Override
            public void onDispose() {
                throw new IllegalStateException("plugin cleanup bug");
            }
        }));
        Rack rack = new Rack();
        List<Runnable> ops = new ArrayList<>();
        ops.add(() -> rack.addDevice(d));
        ops.add(() -> rack.removeDevice(d));
        ops.add(() -> rack.addDevice(d));
        // none of these may propagate the plugin's exception
        for (Runnable op : ops) {
            op.run();
        }
        assertThat(rack.getDevices()).contains(d);
    }
}
