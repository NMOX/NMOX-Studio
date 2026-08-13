package org.nmox.studio.rack.devices;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nmox.studio.core.spi.device.DeviceExtension;

/**
 * The example gallery, installed and active: the six JSON devices in
 * {@code examples/devices.d/} ship INSIDE this module (the rack pom
 * copies them into {@code bundled/} at build time) and merge into the
 * shelf on every install — no copy step, no drop-in dir, nothing for
 * the user to do. They are ordinary JSON devices in every other way:
 * the same {@link DeviceFile} judge, the same load-time fit law, the
 * same trust gate on every spawn.
 *
 * <p>The classpath cannot be listed portably, so {@code bundled/index}
 * names the files — and {@code ExampleDevicesGateTest} pins the index
 * to the directory's real contents, so a new example that forgets the
 * index fails the build instead of silently not shipping.
 */
final class BundledDevices {

    private static final Logger LOG = Logger.getLogger(BundledDevices.class.getName());

    /** Loaded once; module resources cannot change at runtime. */
    private static volatile List<DeviceExtension> cached;

    private BundledDevices() {
    }

    static List<DeviceExtension> all() {
        List<DeviceExtension> result = cached;
        if (result == null) {
            result = load();
            cached = result;
        }
        return result;
    }

    private static List<DeviceExtension> load() {
        List<DeviceExtension> found = new ArrayList<>();
        String index = resource("bundled/index");
        if (index == null) {
            LOG.warning("bundled device index missing — gallery devices not shipped");
            return List.of();
        }
        for (String name : index.split("\n")) {
            name = name.strip();
            if (name.isEmpty()) {
                continue;
            }
            String json = resource("bundled/" + name);
            if (json == null) {
                LOG.log(Level.WARNING, "bundled device {0} skipped: resource missing", name);
                continue;
            }
            DeviceFile.Result r = DeviceFile.read(json);
            if (!r.ok()) {
                LOG.log(Level.WARNING, "bundled device {0} skipped: {1}",
                        new Object[]{name, r.problem()});
                continue;
            }
            DeviceFile fitted = UserDevices.fit(r.device());
            if (fitted == null) {
                continue; // the fit path logged the reason
            }
            found.add(new JsonDeviceExtension(fitted));
        }
        return List.copyOf(found);
    }

    private static String resource(String path) {
        try (InputStream in = BundledDevices.class.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return null;
        }
    }
}
