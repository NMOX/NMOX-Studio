package org.nmox.studio.rack.devices;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.nmox.studio.core.spi.device.DeviceDescriptor;
import org.nmox.studio.core.spi.device.DeviceExtension;
import org.nmox.studio.core.spi.device.DeviceFace;
import org.nmox.studio.core.spi.device.DeviceLogic;
import org.nmox.studio.core.spi.device.DeviceServices;
import org.nmox.studio.core.spi.device.PortSpec;

/**
 * A {@link DeviceFile} wearing the Device SPI — the adapter that makes a
 * user's JSON indistinguishable from a Java plugin once it reaches the
 * shelf.
 *
 * <p>Deliberately thin. Everything that could be a law lives elsewhere:
 * the file was judged by {@link DeviceFile#read} before this exists, the
 * descriptor is judged again by {@link DeviceCatalog#validate} beside
 * every built-in, and the spawn is gated by the host's workspace-trust
 * check inside {@code ExtensionDevice}. What is left here is wiring.
 */
final class JsonDeviceExtension implements DeviceExtension {

    private final DeviceFile file;

    JsonDeviceExtension(DeviceFile file) {
        this.file = file;
    }

    /** The parsed file behind this device, for tests and diagnostics. */
    DeviceFile file() {
        return file;
    }

    @Override
    public DeviceDescriptor descriptor() {
        return new DeviceDescriptor(file.id(), file.title(), file.tagline(), file.accent(),
                file.category(), file.usage(), file.units(), file.ports());
    }

    @Override
    public DeviceLogic build(DeviceFace face, DeviceServices services) {
        DeviceFace.LcdHandle screen = face.lcd(file.lcdLabel(), file.lcdWidth(), 1);
        DeviceFace.LedHandle running = face.led("RUN", DeviceFace.LedTone.BUSY);
        DeviceFace.LedHandle failed = face.led("FAIL", DeviceFace.LedTone.FAIL);

        Map<String, DeviceFace.KnobHandle> knobs = new HashMap<>();
        for (DeviceFile.Knob k : file.knobs()) {
            knobs.put(k.key(), face.knob(k.key(), k.label(),
                    k.options().toArray(new String[0]), 0));
        }

        // Output rides the first declared OUT DATA port, so `MONITOR` and
        // friends can read the run without the author wiring anything up.
        String dataOut = file.ports().stream()
                .filter(p -> p.direction() == PortSpec.Direction.OUT
                        && p.signal() == PortSpec.Signal.DATA)
                .map(PortSpec::id)
                .findFirst().orElse(null);

        Map<String, Runnable> byTrigger = new HashMap<>();
        for (DeviceFile.Button b : file.buttons()) {
            DeviceFace.ButtonHandle handle = face.button(b.label(), b.role());
            Runnable press = press(b, services, screen, running, failed, knobs, dataOut);
            handle.onPress(press);
            if (b.trigger() != null) {
                byTrigger.put(b.trigger(), press);
            }
        }

        return new DeviceLogic() {
            @Override
            public void onTrigger(String portId, boolean ok) {
                Runnable press = byTrigger.get(portId);
                if (press != null) {
                    press.run();
                }
            }
        };
    }

    private Runnable press(DeviceFile.Button b, DeviceServices services,
            DeviceFace.LcdHandle screen, DeviceFace.LedHandle running,
            DeviceFace.LedHandle failed, Map<String, DeviceFace.KnobHandle> knobs,
            String dataOut) {
        if (b.role() == DeviceFace.ButtonRole.STOP) {
            return () -> {
                services.stop();
                running.setOn(false);
                screen.setText("STOPPED");
            };
        }
        return () -> {
            List<String> argv = DeviceFile.substitute(b.command(),
                    key -> {
                        DeviceFace.KnobHandle k = knobs.get(key);
                        return k == null ? null : k.selected();
                    });
            // Say what is about to run before running it: the command a
            // button fires is never a secret from the person pressing it.
            screen.setText(String.join(" ", argv));
            failed.setOn(false);
            running.setOn(true);
            List<String> tail = new ArrayList<>(1);
            services.exec(argv,
                    line -> {
                        String text = line.strip();
                        if (!text.isEmpty()) {
                            tail.clear();
                            tail.add(text);
                            screen.setText(text);
                        }
                        if (dataOut != null) {
                            services.emitData(dataOut, line);
                        }
                    },
                    code -> {
                        running.setOn(false);
                        failed.setOn(code != 0);
                        if (tail.isEmpty()) {
                            screen.setText(code == 0 ? "OK" : "EXIT " + code);
                        }
                        if (b.emit() != null) {
                            services.emitTrigger(b.emit(), code == 0);
                        }
                    });
        };
    }
}
