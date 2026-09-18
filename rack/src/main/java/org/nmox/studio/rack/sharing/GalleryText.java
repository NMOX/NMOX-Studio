package org.nmox.studio.rack.sharing;

import java.util.List;
import org.nmox.studio.rack.gallery.RackGallery;
import org.nmox.studio.rack.model.RackCard;
import org.openide.util.NbBundle.Messages;

/**
 * The words the Rack Gallery shows for one rack: the row in the list and the
 * page beside it. Pure, so what a reader is told is tested without a window.
 * A rack's card is its author's text — plain strings here, painted by
 * components that do not render markup (the v2.86.0 class).
 */
@Messages({
    "GalleryText_sourceCommunity=community",
    "GalleryText_sourcePreset=preset",
    "GalleryText_sourceStarter=starter",
    "GalleryText_sourceYours=yours",
    "# {0} - rack name, {1} - where it comes from (community, preset, starter, yours)",
    "GalleryText_row={0}  · {1}",
    "# {0} - rack name, {1} - where it comes from",
    "GalleryText_rowFits={0}  · {1} · fits this project",
    "# {0} - author",
    "GalleryText_sharedBy=Shared by {0}",
    "# {0} - project kinds, comma separated",
    "GalleryText_fits=Made for: {0}",
    "# {0} - tool names, comma separated",
    "GalleryText_needs=Needs on the PATH: {0}",
    "# {0} - tool names, comma separated",
    "GalleryText_missing=Not found on this machine: {0} — Tools ▸ Environment Doctor… says how to install them.",
    "GalleryText_allFound=Everything it needs is on this machine.",
    "# {0} - number of devices, {1} - number of cables",
    "GalleryText_devices=Devices ({0}), {1} cables:",
    "GalleryText_wiring=Wiring:",
    "GalleryText_noWiring=No cables: the devices stand side by side.",
    "GalleryText_unnamed=(unnamed rack)"
})
public final class GalleryText {

    private GalleryText() {
    }

    /** One list row: the name, where the rack comes from, and whether it fits the aimed project. */
    public static String row(RackGallery.Entry entry) {
        String name = name(entry);
        String source = source(entry.source());
        return entry.fitsProject() ? Bundle.GalleryText_rowFits(name, source) : Bundle.GalleryText_row(name, source);
    }

    /**
     * The page beside the list.
     *
     * @param missingTools the rack's {@code requires} not found on the PATH; null while that lookup is still running
     */
    public static String detail(RackGallery.Entry entry, List<String> missingTools) {
        RackCard card = entry.card();
        StringBuilder sb = new StringBuilder();
        sb.append(name(entry)).append('\n');
        if (!card.description().isEmpty()) {
            sb.append(card.description()).append('\n');
        }
        if (!card.author().isEmpty()) {
            sb.append(Bundle.GalleryText_sharedBy(card.author())).append('\n');
        }
        sb.append('\n');
        if (!card.kinds().isEmpty()) {
            sb.append(Bundle.GalleryText_fits(String.join(", ", card.kinds()))).append('\n');
        }
        if (!card.requires().isEmpty()) {
            sb.append(Bundle.GalleryText_needs(String.join(", ", card.requires()))).append('\n');
            if (missingTools != null) {
                sb.append(missingTools.isEmpty() ? Bundle.GalleryText_allFound()
                        : Bundle.GalleryText_missing(String.join(", ", missingTools))).append('\n');
            }
        }
        if (!card.kinds().isEmpty() || !card.requires().isEmpty()) {
            sb.append('\n');
        }
        sb.append(Bundle.GalleryText_devices(entry.deviceTitles().size(), entry.cables())).append('\n');
        for (String title : entry.deviceTitles()) {
            sb.append("  ").append(title).append('\n');
        }
        sb.append('\n');
        if (entry.wiring().isEmpty()) {
            sb.append(Bundle.GalleryText_noWiring()).append('\n');
        } else {
            sb.append(Bundle.GalleryText_wiring()).append('\n');
            for (String line : entry.wiring()) {
                sb.append("  ").append(line).append('\n');
            }
        }
        return sb.toString();
    }

    static String name(RackGallery.Entry entry) {
        return entry.card().name().isEmpty() ? Bundle.GalleryText_unnamed() : entry.card().name();
    }

    static String source(RackGallery.Source source) {
        return switch (source) {
            case COMMUNITY -> Bundle.GalleryText_sourceCommunity();
            case PRESET -> Bundle.GalleryText_sourcePreset();
            case STARTER -> Bundle.GalleryText_sourceStarter();
            case YOURS -> Bundle.GalleryText_sourceYours();
        };
    }
}
