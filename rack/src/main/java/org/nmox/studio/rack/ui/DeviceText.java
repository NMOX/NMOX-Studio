package org.nmox.studio.rack.ui;

import java.util.List;
import org.nmox.studio.core.util.Bundles;
import org.nmox.studio.rack.devices.DeviceCatalog;
import org.nmox.studio.rack.devices.DeviceType;

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

    private static final String DESC = "DeviceDesc_";
    private static final String CAT = "DeviceCat_";

    /**
     * The key family this seam owns.
     *
     * <p>Its English lives in {@code DeviceType}, so these keys have no
     * base bundle BY DESIGN and a parity scan would otherwise call all 708
     * of them extra. {@code LocaleBundleParityTest} reads this declaration
     * instead of keeping its own copy of the fact — the second home
     * v2.131.0 spent a release removing. It cannot drift from the lookup
     * below, because there is one literal and both use it.
     */
    static final List<String> KEY_PREFIXES = List.of(DESC, CAT);

    private DeviceText() {
    }

    /** The whole description — "Noun — what it does" — translated, or as authored. */
    public static String description(DeviceCatalog.Entry entry) {
        return Bundles.optional(DeviceText.class, DESC + entry.id(),
                entry.description());
    }

    /**
     * A shelf SECTION heading — "Run &amp; Automate", "Build &amp; Verify" — in
     * the reader's language.
     *
     * <p>These seven lived as string literals on a nested enum and read
     * English in every translated build until the Hindi walk of v2.147.0
     * photographed them. The prose ledger could not see them: it keyed its
     * census by FILE, {@code DeviceType.java} was classified for the 53
     * device literals it holds, and a second catalogue in the same file
     * inherited a verdict that was never about it. A population unit
     * coarser than the thing it classifies hides members.
     *
     * <p>A heading is not faceplate vocabulary. The silkscreen stays
     * English by decision; the words above a group of cards are the
     * picker telling a reader what the group is for.
     */
    public static String heading(DeviceType.PaletteCategory category) {
        return Bundles.optional(DeviceText.class, CAT + category.name(),
                category.label);
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
