package org.nmox.studio.rack.gallery;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nmox.studio.rack.devices.DeviceCatalog;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.RackDevice;

/**
 * The names {@link RackWiring} shows, read from the device catalog: a type's
 * faceplate title, and the label on each of its jacks.
 *
 * <p>A jack's label lives on the device, not in the catalog entry, so the
 * first question about a type builds one throwaway device, reads its ports
 * and disposes it (what {@code DeviceDocsTest} does for the reference). The
 * answer is kept per type: a device class's jacks cannot change while the
 * module is loaded. A type whose device cannot be built (a plugin whose
 * {@code build()} throws) answers no labels, with the reason logged — the
 * sketch then shows the port ids, which is still true.
 */
final class CatalogNames implements RackWiring.Names {

    static final CatalogNames INSTANCE = new CatalogNames();

    private static final Logger LOG = Logger.getLogger(CatalogNames.class.getName());

    private final Map<String, Map<String, String>> labels = new ConcurrentHashMap<>();

    private CatalogNames() {
    }

    @Override
    public String title(String typeId) {
        return DeviceCatalog.byId(typeId).map(DeviceCatalog.Entry::title).orElse(null);
    }

    @Override
    public String label(String typeId, String portId) {
        Map<String, String> known = labels.get(typeId);
        if (known == null) {
            // only a type the catalog HAS is remembered: the ids in a stranger's
            // file are unbounded, the catalog is not
            if (DeviceCatalog.byId(typeId).isEmpty()) {
                return null;
            }
            known = labels.computeIfAbsent(typeId, CatalogNames::read);
        }
        return known.get(portId);
    }

    private static Map<String, String> read(String typeId) {
        Map<String, String> out = new LinkedHashMap<>();
        DeviceCatalog.Entry entry = DeviceCatalog.byId(typeId).orElse(null);
        if (entry == null) {
            return out;
        }
        RackDevice device = null;
        try {
            device = entry.create();
            for (Port p : device.getPorts()) {
                out.put(p.getId(), p.getLabel());
            }
        } catch (RuntimeException ex) {
            LOG.log(Level.INFO, "rack gallery: no jack labels for {0}: {1}", new Object[]{typeId, ex.toString()});
        } finally {
            if (device != null) {
                device.dispose();
            }
        }
        return out;
    }
}
