package com.example.uptime;

import java.awt.Color;
import java.util.List;
import org.nmox.studio.core.spi.device.DeviceCategory;
import org.nmox.studio.core.spi.device.DeviceDescriptor;
import org.nmox.studio.core.spi.device.DeviceExtension;
import org.nmox.studio.core.spi.device.DeviceFace;
import org.nmox.studio.core.spi.device.DeviceLogic;
import org.nmox.studio.core.spi.device.DeviceServices;
import org.nmox.studio.core.spi.device.PortSpec;
import org.openide.util.lookup.ServiceProvider;

/**
 * A third-party rack device, exactly as docs/device-spi.md describes:
 * CHECK runs the host's uptime command, the load line lands on the LCD,
 * and OK fires onward so it chains like any built-in.
 */
@ServiceProvider(service = DeviceExtension.class)
public class UptimeDeviceExtension implements DeviceExtension {

    @Override
    public DeviceDescriptor descriptor() {
        return new DeviceDescriptor(
                "com.example.uptime", "UPTIME", "Host uptime & load on a button",
                new Color(110, 190, 160), DeviceCategory.OBSERVE,
                "CHECK runs uptime and shows the load line on the LCD.\n"
                        + "Patch OK onward to chain, or CHECK from a TEMPO tick for a clock.",
                1,
                List.of(new PortSpec("check", "CHECK", PortSpec.Direction.IN, PortSpec.Signal.TRIGGER),
                        new PortSpec("ok", "OK", PortSpec.Direction.OUT, PortSpec.Signal.TRIGGER)));
    }

    @Override
    public DeviceLogic build(DeviceFace face, DeviceServices services) {
        DeviceFace.LcdHandle screen = face.lcd("STATUS", 420, 1);
        DeviceFace.LedHandle eye = face.led("EYE", DeviceFace.LedTone.INFO);
        DeviceFace.ButtonHandle check = face.button("CHECK", DeviceFace.ButtonRole.QUERY);
        Runnable run = () -> {
            eye.setOn(true);
            services.exec(List.of("uptime"),
                    line -> screen.setText(line.strip()),
                    code -> {
                        eye.setOn(false);
                        services.emitTrigger("ok", code == 0);
                    });
        };
        check.onPress(run);
        return new DeviceLogic() {
            @Override
            public void onTrigger(String portId, boolean ok) {
                run.run();
            }
        };
    }
}
