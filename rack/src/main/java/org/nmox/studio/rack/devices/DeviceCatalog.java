package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.nmox.studio.rack.model.RackDevice;

/**
 * The one registry every device lookup routes through — the palette,
 * patch persistence, Quick Search, the how-to card, and CI export all
 * ask this class, never the enum directly. Today it is backed solely by
 * the {@link DeviceType} catalog; when the device SPI ships, extension
 * devices merge in here and every consumer learns about them for free.
 */
public final class DeviceCatalog {

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

        RackDevice create();
    }

    private DeviceCatalog() {
    }

    /** Every registered device kind, in shelf order. */
    public static List<Entry> all() {
        List<Entry> entries = new ArrayList<>();
        for (DeviceType t : DeviceType.values()) {
            entries.add(new EnumEntry(t));
        }
        return entries;
    }

    /** Resolves a patch-file type id; empty for a kind not installed here. */
    public static Optional<Entry> byId(String id) {
        DeviceType t = DeviceType.byId(id);
        return t == null ? Optional.empty() : Optional.of(new EnumEntry(t));
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
        public RackDevice create() {
            return type.create();
        }

        /** Parameterized tests display entries by id. */
        @Override
        public String toString() {
            return type.getId();
        }
    }
}
