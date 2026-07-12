package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nmox.studio.core.spi.device.DeviceDescriptor;
import org.nmox.studio.core.spi.device.DeviceExtension;
import org.nmox.studio.core.spi.device.PortSpec;
import org.nmox.studio.rack.model.RackDevice;
import org.openide.util.Lookup;

/**
 * The one registry every device lookup routes through — the palette,
 * patch persistence, Quick Search, the how-to card, and CI export all
 * ask this class, never the enum directly. The built-in fleet comes
 * from {@link DeviceType}; installed {@link DeviceExtension} providers
 * merge in behind the same interface, validated first — a law-breaking
 * descriptor is skipped with a logged note naming the offender, never
 * allowed to break the shelf (the learn-catalog.d idiom).
 */
public final class DeviceCatalog {

    private static final Logger LOG = Logger.getLogger(DeviceCatalog.class.getName());

    /** Everything a consumer may know about a rackable device kind. */
    public interface Entry {

        String id();

        String title();

        String description();

        Color accent();

        DeviceType.PaletteCategory category();

        /** The How-to-use card: what it does plus a patch recipe. */
        String usage();

        /** True when the device's primary action exports as a CI step. */
        boolean ciStep();

        /** True for the built-in fleet; false for installed extensions. */
        boolean builtIn();

        RackDevice create();
    }

    private DeviceCatalog() {
    }

    /** Every registered device kind: built-ins in shelf order, then extensions. */
    public static List<Entry> all() {
        List<Entry> entries = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (DeviceType t : DeviceType.values()) {
            entries.add(new EnumEntry(t));
            ids.add(t.getId());
        }
        for (DeviceExtension ext : Lookup.getDefault().lookupAll(DeviceExtension.class)) {
            DeviceDescriptor d;
            try {
                d = ext.descriptor();
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "device extension " + ext.getClass().getName()
                        + " skipped: descriptor() failed", ex);
                continue;
            }
            String problem = validate(d, ids);
            if (problem != null) {
                LOG.log(Level.WARNING, "device extension {0} skipped: {1}",
                        new Object[]{ext.getClass().getName(), problem});
                continue;
            }
            ids.add(d.id());
            entries.add(new ExtensionEntry(ext, d));
        }
        return entries;
    }

    /** Resolves a patch-file type id; empty for a kind not installed here. */
    public static Optional<Entry> byId(String id) {
        for (Entry e : all()) {
            if (e.id().equals(id)) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    /**
     * The laws a descriptor must obey before its device reaches the
     * shelf — the same ones DeviceContractTest pins on every built-in.
     * Returns the problem, or null when the descriptor is sound.
     */
    static String validate(DeviceDescriptor d, Set<String> takenIds) {
        if (d == null) {
            return "descriptor() returned null";
        }
        if (d.id() == null || d.id().isBlank()) {
            return "blank id";
        }
        if (!d.id().contains(".")) {
            return "id \"" + d.id() + "\" must be reverse-DNS namespaced "
                    + "(contain a dot) — un-dotted ids belong to the built-in fleet";
        }
        if (takenIds.contains(d.id())) {
            return "id \"" + d.id() + "\" is already registered";
        }
        if (d.title() == null || d.title().isBlank()) {
            return "blank title";
        }
        if (d.category() == null) {
            return "null category";
        }
        if (d.usage() == null || d.usage().isBlank()
                || !d.usage().contains("\n") || d.usage().length() <= 60) {
            return "usage must be at least two lines and more than 60 characters "
                    + "(what it does, then a patch recipe — the shelf law)";
        }
        Set<String> portKeys = new HashSet<>();
        Set<String> inIds = new HashSet<>();
        for (PortSpec p : d.ports()) {
            if (p == null || p.id() == null || p.id().isBlank()
                    || p.label() == null || p.label().isBlank()
                    || p.direction() == null || p.signal() == null) {
                return "every port needs a non-blank id and label, a direction, and a signal";
            }
            if (!portKeys.add(p.direction() + ":" + p.id())) {
                return "duplicate port id \"" + p.id() + "\"";
            }
            if (p.direction() == PortSpec.Direction.IN) {
                inIds.add(p.id());
            }
            if (p.direction() == PortSpec.Direction.OUT && p.signal() == PortSpec.Signal.GATE
                    && !List.of("RUNNING", "SERVING", "ENABLE").contains(p.label())) {
                return "GATE out \"" + p.id() + "\" must be labeled RUNNING, SERVING, "
                        + "or ENABLE (gate outputs speak one vocabulary)";
            }
        }
        if ((inIds.contains("serve") || inIds.contains("start")) && !inIds.contains("stop")) {
            return "a device you can start by cable needs a \"stop\" IN "
                    + "(the transport law)";
        }
        return null;
    }

    private record EnumEntry(DeviceType type) implements Entry {

        @Override
        public String id() {
            return type.getId();
        }

        @Override
        public String title() {
            return type.getTitle();
        }

        @Override
        public String description() {
            return type.getDescription();
        }

        @Override
        public Color accent() {
            return type.getAccent();
        }

        @Override
        public DeviceType.PaletteCategory category() {
            return type.getPaletteCategory();
        }

        @Override
        public String usage() {
            return type.getUsage();
        }

        @Override
        public boolean ciStep() {
            return type.isCiStep();
        }

        @Override
        public boolean builtIn() {
            return true;
        }

        @Override
        public RackDevice create() {
            return type.create();
        }

        /** Parameterized tests display entries by id. */
        @Override
        public String toString() {
            return type.getId();
        }
    }

    private record ExtensionEntry(DeviceExtension extension, DeviceDescriptor d)
            implements Entry {

        @Override
        public String id() {
            return d.id();
        }

        @Override
        public String title() {
            return d.title();
        }

        @Override
        public String description() {
            return d.tagline() == null ? "" : d.tagline();
        }

        @Override
        public Color accent() {
            return d.accent() == null ? new Color(120, 150, 170) : d.accent();
        }

        @Override
        public DeviceType.PaletteCategory category() {
            // the SPI's categories are the shelf's categories by name
            return DeviceType.PaletteCategory.valueOf(d.category().name());
        }

        @Override
        public String usage() {
            return d.usage();
        }

        /** Extensions never export as CI steps (deliberate v1 scope). */
        @Override
        public boolean ciStep() {
            return false;
        }

        @Override
        public boolean builtIn() {
            return false;
        }

        @Override
        public RackDevice create() {
            return new ExtensionDevice(extension);
        }

        @Override
        public String toString() {
            return d.id();
        }
    }
}
