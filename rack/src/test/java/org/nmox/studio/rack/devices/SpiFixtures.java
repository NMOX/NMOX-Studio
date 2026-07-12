package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.List;
import org.nmox.studio.core.spi.device.DeviceCategory;
import org.nmox.studio.core.spi.device.DeviceDescriptor;
import org.nmox.studio.core.spi.device.DeviceExtension;
import org.nmox.studio.core.spi.device.DeviceFace;
import org.nmox.studio.core.spi.device.DeviceLogic;
import org.nmox.studio.core.spi.device.DeviceServices;
import org.nmox.studio.core.spi.device.PortSpec;

/**
 * Extensions registered on the TEST classpath via META-INF/services —
 * the same discovery path a real third-party NBM uses. GOOD is a
 * law-abiding citizen (so DeviceContractTest's catalog parameterization
 * exercises the whole hosting pipeline for free); the others each break
 * one validation law and must be skipped-with-note, never listed.
 */
public final class SpiFixtures {

    private SpiFixtures() {
    }

    /** Law-abiding: appears in the catalog next to the built-ins. */
    public static final class Good implements DeviceExtension {

        @Override
        public DeviceDescriptor descriptor() {
            return new DeviceDescriptor(
                    "org.nmox.fixture.echo", "FIXTURE", "SPI test citizen — echoes its inputs",
                    new Color(90, 160, 200), DeviceCategory.OBSERVE,
                    "A test-classpath device proving extensions obey the contract laws.\n"
                            + "Patch any DATA out into IN and press PING to fire OK onward.",
                    2,
                    List.of(new PortSpec("run", "RUN", PortSpec.Direction.IN, PortSpec.Signal.TRIGGER),
                            new PortSpec("in", "IN", PortSpec.Direction.IN, PortSpec.Signal.DATA),
                            new PortSpec("enable", "ENABLE", PortSpec.Direction.IN, PortSpec.Signal.GATE),
                            new PortSpec("ok", "OK", PortSpec.Direction.OUT, PortSpec.Signal.TRIGGER),
                            new PortSpec("out", "OUT", PortSpec.Direction.OUT, PortSpec.Signal.DATA),
                            new PortSpec("running", "RUNNING", PortSpec.Direction.OUT, PortSpec.Signal.GATE)));
        }

        @Override
        public DeviceLogic build(DeviceFace face, DeviceServices services) {
            var mode = face.knob("mode", "MODE", new String[]{"alpha", "beta", "gamma"}, 0);
            var armed = face.toggle("armed", "ARM", false);
            var ping = face.button("PING", DeviceFace.ButtonRole.QUERY);
            var eye = face.led("EYE", DeviceFace.LedTone.OK);
            var screen = face.lcd("STATUS", 220, 1);
            face.vu("FEED");
            ping.onPress(() -> {
                eye.setOn(true);
                screen.setText(mode.selected() + (armed.isOn() ? " armed" : ""));
                services.emitTrigger("ok", true);
            });
            return new DeviceLogic() {
                @Override
                public void onData(String portId, String text) {
                    services.emitData("out", text);
                }
            };
        }
    }

    /** Un-dotted id: must be refused (the namespace law). */
    public static final class UndottedId implements DeviceExtension {

        @Override
        public DeviceDescriptor descriptor() {
            return new DeviceDescriptor("nodots", "BAD", "no namespace",
                    null, DeviceCategory.UTILITY,
                    "This device breaks the namespace law and must never reach the shelf.\nSecond line.",
                    1, List.of());
        }

        @Override
        public DeviceLogic build(DeviceFace face, DeviceServices services) {
            return new DeviceLogic() {
            };
        }
    }

    /** Collides with the built-in SOLDER id: must be refused. */
    public static final class CollidingId implements DeviceExtension {

        @Override
        public DeviceDescriptor descriptor() {
            return new DeviceDescriptor("cmd", "IMPOSTOR", "claims a built-in id",
                    null, DeviceCategory.UTILITY,
                    "This device claims the built-in SOLDER's id and must never reach the shelf.\nSecond line.",
                    1, List.of());
        }

        @Override
        public DeviceLogic build(DeviceFace face, DeviceServices services) {
            return new DeviceLogic() {
            };
        }
    }

    /** One-line usage: breaks the shelf law, must be refused. */
    public static final class ThinUsage implements DeviceExtension {

        @Override
        public DeviceDescriptor descriptor() {
            return new DeviceDescriptor("org.nmox.fixture.thin", "THIN", "no recipe",
                    null, DeviceCategory.UTILITY, "too thin", 1, List.of());
        }

        @Override
        public DeviceLogic build(DeviceFace face, DeviceServices services) {
            return new DeviceLogic() {
            };
        }
    }
}
