package org.nmox.studio.rack.ui;

import org.nmox.studio.core.util.Bundles;
import org.nmox.studio.rack.devices.DeviceCatalog;

/**
 * A device's shelf description in the reader's language.
 *
 * <p>The model keeps English and the consumer renders it — the v2.101.0
 * rule, spelled here by WHERE this class lives. {@link DeviceType} is the
 * record: its {@code description()} is the English that GENERATES
 * {@code docs/devices.md} (see DeviceDocsTest) and it must not move. The
 * shelf is a picker a person reads to choose a device, so it reads this.
 *
 * <p>English lives in exactly one place — the enum — so there is no base
 * bundle here and no parity to drift: a missing key falls back to the
 * record. The faceplate is untouched and stays English by decision (the
 * hardware panel, ledger 85); a shelf card is a catalogue entry, not a
 * silkscreen.
 *
 * <p>Tool names inside a description are never translated. "vite/webpack/
 * rollup & co" names programs; a reader who types {@code vite} must find
 * the device that runs it, in every language.
 */
public final class DeviceText {

    private DeviceText() {
    }

    /** The whole description — "Noun — what it does" — translated, or as authored. */
    public static String description(DeviceCatalog.Entry entry) {
        return Bundles.optional(DeviceText.class, "DeviceDesc_" + entry.id(),
                entry.description());
    }

    /**
     * The half after the em dash: what the shelf card paints under the
     * device's name, in the space of one small line.
     */
    public static String gloss(DeviceCatalog.Entry entry) {
        String desc = description(entry);
        int dash = desc.indexOf('—');
        return dash > 0 ? desc.substring(dash + 1).trim() : desc;
    }
}
