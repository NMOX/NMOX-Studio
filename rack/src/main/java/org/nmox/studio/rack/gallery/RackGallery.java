package org.nmox.studio.rack.gallery;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.nmox.studio.core.process.ToolLocator;
import org.nmox.studio.core.search.SearchTerms;
import org.nmox.studio.rack.devices.ProjectInspector;
import org.nmox.studio.rack.model.RackCard;
import org.nmox.studio.rack.projectstudio.RackPresets;
import org.nmox.studio.rack.projectstudio.StarterRacks;
import org.nmox.studio.rack.projectstudio.UserPresets;
import org.openide.util.NbBundle;

/**
 * The one catalog of racks: every rack the product can hand a user, as one
 * kind of thing. Four sources, one {@link Entry} shape —
 * <ul>
 * <li>{@link Source#COMMUNITY}: the racks that ship in this module's
 * {@code racks/} resources, each through {@link RackJudge};</li>
 * <li>{@link Source#PRESET}: one per {@link RackPresets} value;</li>
 * <li>{@link Source#STARTER}: the wirings a project kind is born with
 * ({@link StarterRacks#all}) — never one that is a preset re-used;</li>
 * <li>{@link Source#YOURS}: the user's drop-ins in {@code ~/.nmox/presets.d}.</li>
 * </ul>
 *
 * <p><b>Call off the EDT.</b> Listing reads the drop-in directory, the
 * classpath and the aimed project's manifests, and the first listing builds
 * the presets' throwaway racks; {@link #missingTools} walks the PATH. Nothing
 * here touches Swing, runs a process or writes a file — a user's corrupt
 * drop-in is skipped with its reason logged, not moved aside (listing a shelf
 * must not rename files on it; {@code RackIO.readDocument} does, which is why
 * it is not used here).
 */
@NbBundle.Messages({
    "RackGallery_starterPolyglotName=Run · Debug · Test",
    "RackGallery_starterPolyglotDescription=Run, debug and test any toolchain on a save loop: REFLEX re-runs VERITAS, with IGNITION and INSPECTOR beside it on one MONITOR.",
    "RackGallery_starterNodeName=Node Package",
    "RackGallery_starterNodeDescription=CRATE installs, NPM-9000 runs the script you dial from package.json, and REFLEX re-runs the tests on every save.",
    "RackGallery_starterViteName=Vite Site",
    "RackGallery_starterViteDescription=SURGE serves into SCOPE, MAESTRO fires install → build → test, and REFLEX re-tests on every save.",
    "RackGallery_starterAngularName=Angular App",
    "RackGallery_starterAngularDescription=HALO serves and opens SCOPE when ready, CRATE installs, and REFLEX re-runs VERITAS on every save.",
    "RackGallery_starterExpressName=Node HTTP Service",
    "RackGallery_starterExpressDescription=SURGE runs the service and, the moment it is READY, PING calls its health route; every lane lands on MONITOR.",
    "RackGallery_starterStaticName=Static Site",
    "RackGallery_starterStaticDescription=SURGE serves the folder and SCOPE opens it when ready — nothing to build.",
    "RackGallery_starterBeamName=BEAM Project",
    "RackGallery_starterBeamDescription=CRATE fetches dependencies, then VERITAS runs the suite; REFLEX re-tests on every save and IGNITION runs the app.",
    "RackGallery_starterFoundryName=Foundry Contracts",
    "RackGallery_starterFoundryDescription=ANVIL is the local chain, and REFLEX runs forge test through VERITAS on every save."
})
public final class RackGallery {

    /** Where an entry comes from. Declared in the order the sources were born; {@link #rank} is the shelf order. */
    public enum Source {
        PRESET, STARTER, COMMUNITY, YOURS;

        /** Shelf order: what the product vouches for and wrote down first, the user's own files last. */
        int rank() {
            return switch (this) {
                case COMMUNITY -> 0;
                case PRESET -> 1;
                case STARTER -> 2;
                case YOURS -> 3;
            };
        }
    }

    /**
     * One rack on the shelf.
     *
     * @param id stable across runs and locales: {@code preset:WEB_PIPELINE},
     *        {@code starter:polyglot}, {@code community:<file stem>},
     *        {@code yours:<file name>}
     * @param source where it comes from
     * @param card what it says about itself, in the reader's language; a user's
     *        file with no card is named after its file
     * @param deviceTitles catalog titles in rack order; a type this install
     *        lacks reads as its type id and {@code " ?"}
     * @param cables how many cables the rack carries
     * @param wiring the readable sketch, see {@link RackWiring#sketch}
     * @param fitsProject true when the rack suits the project the listing was
     *        made for
     * @param file the file behind a {@link Source#YOURS} entry, else null
     * @param searchText the English name and description when {@code card}
     *        was translated, else empty — a translation adds a way in and
     *        never removes one (the v2.133.0 law), so {@link #filter} reads both
     * @param patchSource builds {@link #patch}
     */
    public record Entry(String id, Source source, RackCard card, List<String> deviceTitles, int cables,
            List<String> wiring, boolean fitsProject, File file, String searchText,
            Supplier<JSONObject> patchSource) {

        public Entry {
            deviceTitles = List.copyOf(deviceTitles);
            wiring = List.copyOf(wiring);
            searchText = searchText == null ? "" : searchText;
        }

        /**
         * The rack as a patch document, loadable through {@code RackIO.fromJson}
         * (pass a community or user file through {@code RackShare.imported}
         * first, as Import does). A fresh copy every call: the caller may
         * change it. A {@link Source#YOURS} entry reads its file again here, so
         * call off the EDT.
         *
         * @throws java.io.UncheckedIOException when a user's file can no longer be read
         * @throws org.json.JSONException when a user's file stopped being JSON since the listing
         */
        public JSONObject patch() {
            return patchSource.get();
        }
    }

    /**
     * The most a drop-in may be before it is left off the shelf unread — the
     * cap {@code RackIO} puts on any patch file.
     */
    static final long MAX_YOURS_BYTES = 8L * 1024 * 1024;

    /** The most drop-ins listed; a presets.d holding more is a directory of something else. */
    static final int MAX_YOURS = 200;

    private static final Logger LOG = Logger.getLogger(RackGallery.class.getName());

    /** A preset or starter, built once: code cannot change while the module is loaded. */
    private record Built(String id, Source source, RackPresets preset, String starterId, String json,
            List<String> titles, int cables, List<String> wiring) {
    }

    private static volatile List<Built> codeBuilt;

    private RackGallery() {
    }

    /**
     * Every rack on the shelf, for the project at {@code projectDirOrNull}
     * (null: nothing is aimed, and nothing "fits"). Entries that fit the
     * project come first; then community, presets, starters and the user's
     * own — each group in its own stable order.
     */
    public static List<Entry> entries(File projectDirOrNull) {
        return entries(projectDirOrNull, UserPresets.dropInDir());
    }

    /** {@link #entries(File)} over a given drop-in directory — the seam that keeps tests out of {@code ~/.nmox}. */
    static List<Entry> entries(File projectDir, File dropInDir) {
        Fit fit = Fit.of(projectDir);
        List<Entry> out = new ArrayList<>();
        String language = java.util.Locale.getDefault().getLanguage();
        for (CommunityRacks.Loaded rack : CommunityRacks.all()) {
            String json = rack.json();
            out.add(fromDocument("community:" + rack.stem(), Source.COMMUNITY, new JSONObject(json), rack.titles(),
                    rack.cables(), rack.wiring(), null, null, language, fit, () -> new JSONObject(json)));
        }
        for (Built built : codeBuilt()) {
            boolean fits = built.preset() != null ? built.preset() == fit.preset()
                    : built.starterId().equals(fit.starterId());
            out.add(new Entry(built.id(), built.source(), cardOf(built), built.titles(), built.cables(),
                    built.wiring(), fits, null, "", () -> new JSONObject(built.json())));
        }
        out.addAll(yours(dropInDir, language, fit));
        // a sort over two small integers, never over a name (the shelf order is
        // the product's, not the alphabet's); stable, so within one group the
        // order above is the order shown
        out.sort(Comparator.comparingInt(e -> (e.fitsProject() ? 0 : Source.values().length) + e.source().rank()));
        return List.copyOf(out);
    }

    /**
     * The tools in {@code card.requires()} that are not on this machine's
     * search path ({@link ToolLocator}: the PATH plus the usual toolchain
     * homes). Looks, never runs. Off the EDT — it stats directories.
     */
    public static List<String> missingTools(RackCard card) {
        return missingTools(card, ToolLocator::resolve);
    }

    /** {@code resolve} answers an absolute path for a tool it found and the bare name back for one it did not. */
    static List<String> missingTools(RackCard card, UnaryOperator<String> resolve) {
        List<String> missing = new ArrayList<>();
        for (String tool : card.requires()) {
            String resolved = resolve.apply(tool);
            if (resolved == null || resolved.equals(tool)) {
                missing.add(tool);
            }
        }
        return List.copyOf(missing);
    }

    /**
     * The entries matching {@code query} through the product's one term
     * matcher ({@link SearchTerms}: phrases, plurals, accent folding) over
     * name, description, device titles, kinds and required tools. A blank
     * query is every entry; otherwise whole-word hits list before looser
     * ones, the incoming order kept within each.
     */
    public static List<Entry> filter(List<Entry> entries, String query) {
        if (query == null || query.isBlank()) {
            return List.copyOf(entries);
        }
        List<Entry> exact = new ArrayList<>();
        List<Entry> loose = new ArrayList<>();
        for (Entry e : entries) {
            int score = SearchTerms.score(query, e.card().name(), e.card().description(),
                    String.join(" ", e.deviceTitles()), String.join(" ", e.card().kinds()),
                    String.join(" ", e.card().requires()), e.searchText());
            if (score == SearchTerms.EXACT) {
                exact.add(e);
            } else if (score == SearchTerms.LOOSE) {
                loose.add(e);
            }
        }
        exact.addAll(loose);
        return List.copyOf(exact);
    }

    // ---- the project the listing is for ----

    /** What the aimed project is, read once per listing: its detected kinds and the starter it would be born with. */
    private record Fit(Set<String> kinds, String starterId, RackPresets preset) {

        static Fit of(File projectDir) {
            if (projectDir == null || !projectDir.isDirectory()) {
                return new Fit(Set.of(), "", null);
            }
            // the same detection the lanes and the starters use — every toolchain
            // present, so a Rust rack fits the monorepo that also has a package.json
            Set<String> kinds = new java.util.LinkedHashSet<>();
            for (ProjectInspector.ProjectKind kind : ProjectInspector.detectKinds(projectDir).keySet()) {
                kinds.add(kind.name());
            }
            Optional<StarterRacks.Starter> starter = StarterRacks.forProject(projectDir);
            String starterId = starter.map(StarterRacks.Starter::id).orElse("");
            return new Fit(Set.copyOf(kinds), starterId, StarterRacks.presetBehind(starterId).orElse(null));
        }

        boolean fits(RackCard card) {
            for (String kind : kinds) {
                if (card.fits(kind)) {
                    return true;
                }
            }
            return false;
        }
    }

    // ---- presets and starters ----

    private static List<Built> codeBuilt() {
        List<Built> result = codeBuilt;
        if (result == null) {
            List<Built> built = new ArrayList<>();
            for (RackPresets preset : RackPresets.values()) {
                build("preset:" + preset.name(), Source.PRESET, preset, "", preset::buildPatch).ifPresent(built::add);
            }
            for (StarterRacks.Starter starter : StarterRacks.all()) {
                build("starter:" + starter.id(), Source.STARTER, null, starter.id(), starter::buildPatch)
                        .ifPresent(built::add);
            }
            result = List.copyOf(built);
            codeBuilt = result;
        }
        return result;
    }

    private static Optional<Built> build(String id, Source source, RackPresets preset, String starterId,
            Supplier<JSONObject> patch) {
        try {
            JSONObject doc = patch.get();
            return Optional.of(new Built(id, source, preset, starterId, doc.toString(),
                    RackWiring.titles(doc, CatalogNames.INSTANCE), RackWiring.cableCount(doc),
                    RackWiring.sketch(doc, CatalogNames.INSTANCE)));
        } catch (RuntimeException ex) {
            // a wiring that cannot build is a defect its own test names; the shelf goes on without it
            LOG.log(Level.WARNING, "rack gallery: {0} skipped: {1}", new Object[]{id, ex.toString()});
            return Optional.empty();
        }
    }

    private static RackCard cardOf(Built built) {
        if (built.preset() != null) {
            return new RackCard(built.preset().getDisplayName(), built.preset().getDescription(), "",
                    List.of(), List.of());
        }
        String id = built.starterId();
        return new RackCard(starterName(id), starterDescription(id), "", List.of(), List.of());
    }

    static String starterName(String id) {
        return switch (id) {
            case "polyglot" -> Bundle.RackGallery_starterPolyglotName();
            case "node" -> Bundle.RackGallery_starterNodeName();
            case "vite" -> Bundle.RackGallery_starterViteName();
            case "angular" -> Bundle.RackGallery_starterAngularName();
            case "express" -> Bundle.RackGallery_starterExpressName();
            case "static" -> Bundle.RackGallery_starterStaticName();
            case "beam" -> Bundle.RackGallery_starterBeamName();
            case "foundry" -> Bundle.RackGallery_starterFoundryName();
            default -> id;
        };
    }

    static String starterDescription(String id) {
        return switch (id) {
            case "polyglot" -> Bundle.RackGallery_starterPolyglotDescription();
            case "node" -> Bundle.RackGallery_starterNodeDescription();
            case "vite" -> Bundle.RackGallery_starterViteDescription();
            case "angular" -> Bundle.RackGallery_starterAngularDescription();
            case "express" -> Bundle.RackGallery_starterExpressDescription();
            case "static" -> Bundle.RackGallery_starterStaticDescription();
            case "beam" -> Bundle.RackGallery_starterBeamDescription();
            case "foundry" -> Bundle.RackGallery_starterFoundryDescription();
            default -> "";
        };
    }

    // ---- files: the community's and the user's ----

    private static Entry fromDocument(String id, Source source, JSONObject doc, List<String> titles, int cables,
            List<String> wiring, File file, String fallbackName, String language, Fit fit,
            Supplier<JSONObject> patch) {
        RackCard card = RackCard.of(doc, language);
        RackCard english = RackCard.of(doc, "");
        String searchText = card.equals(english) ? "" : english.name() + " " + english.description();
        if (card.name().isBlank() && fallbackName != null) {
            // a plain Save Patch file says nothing about itself: its file name is its name
            card = new RackCard(fallbackName, card.description(), card.author(), card.kinds(), card.requires());
        }
        return new Entry(id, source, card, titles, cables, wiring, fit.fits(card), file, searchText, patch);
    }

    /**
     * A user's file is read again when its patch is asked for, not held: two
     * hundred drop-ins at the cap would otherwise sit in memory for a listing.
     */
    private static JSONObject rereadPatch(File file) {
        try {
            return new JSONObject(readCapped(file));
        } catch (IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }

    private static List<Entry> yours(File dropInDir, String language, Fit fit) {
        List<Entry> out = new ArrayList<>();
        if (dropInDir == null) {
            return out;
        }
        for (UserPresets.Custom custom : UserPresets.listFrom(dropInDir)) {
            if (out.size() >= MAX_YOURS) {
                LOG.log(Level.INFO, "rack gallery: more than {0} files in {1}, the rest not listed",
                        new Object[]{MAX_YOURS, dropInDir});
                break;
            }
            File file = custom.file();
            try {
                JSONObject doc = new JSONObject(readCapped(file));
                out.add(fromDocument("yours:" + file.getName(), Source.YOURS, doc,
                        RackWiring.titles(doc, CatalogNames.INSTANCE), RackWiring.cableCount(doc),
                        RackWiring.sketch(doc, CatalogNames.INSTANCE), file, custom.name(), language, fit,
                        () -> rereadPatch(file)));
            } catch (IOException | JSONException ex) {
                // refusals speak: the file stays where it is, off the shelf, with the reason on record
                LOG.log(Level.WARNING, "rack gallery: {0} not listed: {1}", new Object[]{file.getName(), ex.getMessage()});
            }
        }
        return out;
    }

    /** Read-only and capped: one byte past the cap is enough to refuse, and the file is never touched. */
    private static String readCapped(File file) throws IOException {
        if (!file.isFile()) {
            throw new IOException("not a file");
        }
        try (InputStream in = Files.newInputStream(file.toPath())) {
            byte[] bytes = in.readNBytes((int) MAX_YOURS_BYTES + 1);
            if (bytes.length > MAX_YOURS_BYTES) {
                throw new IOException("over the " + (MAX_YOURS_BYTES / 1024 / 1024) + " MiB cap — not read");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
