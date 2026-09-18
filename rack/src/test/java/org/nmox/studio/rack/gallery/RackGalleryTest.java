package org.nmox.studio.rack.gallery;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.gallery.RackGallery.Entry;
import org.nmox.studio.rack.gallery.RackGallery.Source;
import org.nmox.studio.rack.model.MissingDevice;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackCard;
import org.nmox.studio.rack.model.RackIO;
import org.nmox.studio.rack.model.RackShare;
import org.nmox.studio.rack.projectstudio.RackPresets;
import org.nmox.studio.rack.projectstudio.StarterRacks;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one shelf: four sources, one entry shape, what fits the aimed project
 * first. Every listing here goes through the package-private overload with a
 * temp drop-in directory, so no test reads or writes the real
 * {@code ~/.nmox/presets.d}.
 */
class RackGalleryTest {

    private static List<String> ids(List<Entry> entries) {
        List<String> out = new ArrayList<>();
        for (Entry e : entries) {
            out.add(e.id());
        }
        return out;
    }

    private static Entry byId(List<Entry> entries, String id) {
        return entries.stream().filter(e -> e.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError(id + " is not on the shelf: " + ids(entries)));
    }

    @Test
    @DisplayName("nothing aimed: every preset, every starter that is not a preset re-used, every community rack — community, presets, starters, in that order, none 'fitting'")
    void everySourceIsOnTheShelf(@TempDir Path empty) {
        List<Entry> entries = RackGallery.entries(null, empty.toFile());
        List<String> ids = ids(entries);

        for (RackPresets preset : RackPresets.values()) {
            assertThat(ids).contains("preset:" + preset.name());
        }
        for (StarterRacks.Starter starter : StarterRacks.all()) {
            assertThat(ids).contains("starter:" + starter.id());
        }
        assertThat(ids).as("a starter that IS a preset is listed once, as the preset")
                .doesNotContain("starter:lamp", "starter:classic").doesNotHaveDuplicates();
        assertThat(ids.stream().filter(id -> id.startsWith("community:"))).hasSizeGreaterThanOrEqualTo(8);

        assertThat(entries).noneMatch(Entry::fitsProject);
        List<Source> order = new ArrayList<>();
        for (Entry e : entries) {
            if (order.isEmpty() || order.get(order.size() - 1) != e.source()) {
                order.add(e.source());
            }
        }
        assertThat(order).containsExactly(Source.COMMUNITY, Source.PRESET, Source.STARTER);

        for (Entry e : entries) {
            assertThat(e.card().name()).as(e.id() + " has a name").isNotBlank();
            assertThat(e.card().description()).as(e.id() + " has a description").isNotBlank();
            assertThat(e.deviceTitles()).as(e.id() + " devices").isNotEmpty().noneMatch(t -> t.endsWith("?"));
            assertThat(e.wiring()).as(e.id() + " wiring").isNotEmpty();
            assertThat(e.cables()).as(e.id() + " cables").isEqualTo(e.patch().getJSONArray("cables").length());
            assertThat(e.file()).isNull();
        }
    }

    @Test
    @DisplayName("every entry's patch really mounts — a community rack through RackShare.imported, as Import does — and patch() hands out a fresh copy")
    void everyPatchMounts(@TempDir Path tmp) {
        for (Entry e : RackGallery.entries(null, tmp.toFile())) {
            JSONObject patch = e.patch();
            Rack rack = new Rack();
            rack.setProjectDir(tmp.toFile());
            try {
                RackIO.fromJson(rack, RackShare.imported(patch, null));
                assertThat(rack.getDevices()).as(e.id() + " devices").hasSize(e.deviceTitles().size())
                        .noneMatch(d -> d instanceof MissingDevice);
                assertThat(rack.getCables()).as(e.id() + " cables").hasSize(e.cables());
            } finally {
                rack.shutdown();
            }
            patch.remove("devices");
            assertThat(e.patch().has("devices")).as(e.id() + ": the caller's edit never reaches the shelf").isTrue();
        }
    }

    @Test
    @DisplayName("a Rust crate: the community Rust rack and the polyglot starter fit, and what fits comes first — community before starter")
    void whatFitsComesFirst(@TempDir Path project, @TempDir Path empty) throws IOException {
        Files.writeString(project.resolve("Cargo.toml"), "[package]\nname = \"demo\"\n");
        List<Entry> entries = RackGallery.entries(project.toFile(), empty.toFile());
        List<String> fitting = ids(entries.stream().filter(Entry::fitsProject).toList());
        assertThat(fitting).containsExactly("community:rust-save-loop", "starter:polyglot");
        assertThat(ids(entries).subList(0, 2)).isEqualTo(fitting);
        // and the rest keep the shelf order
        assertThat(entries.get(2).source()).isEqualTo(Source.COMMUNITY);
    }

    @Test
    @DisplayName("a PHP project fits the PRESET its starter re-uses (LAMP Bench) — the delegation StarterRacks states, not a second list here")
    void aStarterThatIsAPresetFitsAsThePreset(@TempDir Path project, @TempDir Path empty) throws IOException {
        Files.writeString(project.resolve("composer.json"), "{}");
        List<Entry> entries = RackGallery.entries(project.toFile(), empty.toFile());
        assertThat(ids(entries.stream().filter(Entry::fitsProject).toList())).containsExactly("preset:LAMP_BENCH");
        assertThat(entries.get(0).id()).isEqualTo("preset:LAMP_BENCH");
    }

    @Test
    @DisplayName("a Node project that declares express fits the express starter, not the plain node one — the starter forProject answers")
    void theStarterIsTheOneForProjectAnswers(@TempDir Path project, @TempDir Path empty) throws IOException {
        Files.writeString(project.resolve("package.json"), "{\"dependencies\":{\"express\":\"^5.0.0\"}}");
        List<Entry> entries = RackGallery.entries(project.toFile(), empty.toFile());
        List<String> fittingStarters = ids(entries.stream()
                .filter(e -> e.fitsProject() && e.source() == Source.STARTER).toList());
        assertThat(fittingStarters).containsExactly("starter:express");
        assertThat(byId(entries, "community:workspace-package-gate").fitsProject()).isTrue();
        assertThat(byId(entries, "community:rust-save-loop").fitsProject()).isFalse();
    }

    @Test
    @DisplayName("a mixed repository fits every toolchain it holds: the Rust rack fits a repo that also has a package.json")
    void aMixedRepoFitsEveryToolchainItHolds(@TempDir Path project, @TempDir Path empty) throws IOException {
        Files.writeString(project.resolve("package.json"), "{}");
        Files.writeString(project.resolve("Cargo.toml"), "[package]\nname = \"demo\"\n");
        List<Entry> entries = RackGallery.entries(project.toFile(), empty.toFile());
        assertThat(byId(entries, "community:rust-save-loop").fitsProject()).isTrue();
        assertThat(byId(entries, "community:parallel-release-gate").fitsProject()).isTrue();
    }

    @Test
    @DisplayName("YOURS: a carded file, a plain patch named by its file, a plugin device marked '?' — and a corrupt or oversize file is skipped where it lies, never renamed")
    void yoursComeFromTheDropInDir(@TempDir Path project, @TempDir Path dropIns) throws IOException {
        Files.writeString(project.resolve("Cargo.toml"), "[package]\nname = \"demo\"\n");
        JSONObject plain = RackPresets.TDD_LOOP.buildPatch();
        Files.writeString(dropIns.resolve("Log Watch.nmoxrack.json"), plain.toString());
        JSONObject carded = new JSONObject(plain.toString());
        carded.put("shared", new JSONObject().put("name", "My crate loop").put("description", "Mine.")
                .put("kinds", List.of("RUST")).put("requires", List.of("cargo")));
        carded.getJSONArray("devices").put(new JSONObject().put("type", "com.example.uptime"));
        Files.writeString(dropIns.resolve("crate.json"), carded.toString());
        Files.writeString(dropIns.resolve("broken.json"), "{ not json");
        Files.write(dropIns.resolve("huge.json"), new byte[(int) RackGallery.MAX_YOURS_BYTES + 1]);
        Files.writeString(dropIns.resolve("notes.txt"), "not a rack");

        List<Entry> entries = RackGallery.entries(project.toFile(), dropIns.toFile());
        List<Entry> yours = entries.stream().filter(e -> e.source() == Source.YOURS).toList();
        assertThat(ids(yours)).containsExactly("yours:crate.json", "yours:Log Watch.nmoxrack.json");

        Entry crate = byId(entries, "yours:crate.json");
        assertThat(crate.card().name()).isEqualTo("My crate loop");
        assertThat(crate.fitsProject()).isTrue();
        assertThat(crate.file()).isEqualTo(dropIns.resolve("crate.json").toFile());
        assertThat(crate.deviceTitles()).last().isEqualTo("com.example.uptime ?");
        assertThat(crate.patch().getJSONArray("devices").length()).isEqualTo(crate.deviceTitles().size());
        // it fits, so it is up with what fits — but after the product's own racks that fit
        assertThat(ids(entries.stream().filter(Entry::fitsProject).toList()))
                .containsExactly("community:rust-save-loop", "starter:polyglot", "yours:crate.json");

        Entry logWatch = byId(entries, "yours:Log Watch.nmoxrack.json");
        assertThat(logWatch.card().name()).as("a plain patch is named by its file").isEqualTo("Log Watch");
        assertThat(logWatch.fitsProject()).isFalse();
        assertThat(entries.get(entries.size() - 1)).as("the user's own files close the shelf").isSameAs(logWatch);

        assertThat(dropIns.resolve("broken.json")).as("listing a shelf never renames a file on it").exists();
        assertThat(dropIns.resolve("broken.json.bak")).doesNotExist();
        assertThat(Files.readString(dropIns.resolve("broken.json"))).isEqualTo("{ not json");
    }

    @Test
    @DisplayName("a drop-in directory that does not exist is an empty shelf, not an error")
    void noDropInDirIsNoProblem(@TempDir Path tmp) {
        File missing = tmp.resolve("nope").toFile();
        assertThat(RackGallery.entries(null, missing)).noneMatch(e -> e.source() == Source.YOURS).isNotEmpty();
        assertThat(RackGallery.entries(null, null)).isNotEmpty();
    }

    @Test
    @DisplayName("missingTools: what the locator cannot find, in the card's order — it looks and never runs")
    void missingToolsAreTheOnesTheLocatorCannotFind() {
        RackCard card = new RackCard("n", "d", "", List.of(), List.of("cargo", "nmox-no-such-tool", "git"));
        Map<String, String> found = Map.of("cargo", "/opt/bin/cargo", "git", "/usr/bin/git");
        assertThat(RackGallery.missingTools(card, name -> found.getOrDefault(name, name)))
                .containsExactly("nmox-no-such-tool");
        assertThat(RackGallery.missingTools(card, name -> name)).containsExactly("cargo", "nmox-no-such-tool", "git");
        assertThat(RackGallery.missingTools(RackCard.EMPTY)).isEmpty();
        // the real locator: a name nothing on any machine answers to
        assertThat(RackGallery.missingTools(new RackCard("n", "d", "", List.of(), List.of("nmox-no-such-tool-7f3a"))))
                .containsExactly("nmox-no-such-tool-7f3a");
    }

    @Test
    @DisplayName("filter: the product's one matcher — a plural, a two-word phrase, a device title, a kind, a tool; blank is everything")
    void filterSpeaksTheOneMatcher(@TempDir Path empty) {
        List<Entry> entries = RackGallery.entries(null, empty.toFile());
        assertThat(RackGallery.filter(entries, "  ")).containsExactlyElementsOf(entries);
        assertThat(RackGallery.filter(entries, null)).containsExactlyElementsOf(entries);
        assertThat(RackGallery.filter(entries, "zzzqqq")).isEmpty();

        assertThat(ids(RackGallery.filter(entries, "gates"))).as("a plural finds the singular")
                .contains("community:parallel-release-gate", "community:workspace-package-gate");
        assertThat(ids(RackGallery.filter(entries, "gas regression"))).as("two words, both must match")
                .containsExactly("community:contract-gas-loop");
        assertThat(ids(RackGallery.filter(entries, "beacon"))).as("a device title")
                .contains("community:static-site-quality");
        assertThat(ids(RackGallery.filter(entries, "foundry"))).as("a kind").contains("community:contract-gas-loop");
        assertThat(ids(RackGallery.filter(entries, "ruff"))).as("a required tool")
                .containsExactly("community:python-quality-loop");
    }

    @Test
    @DisplayName("filter lists a whole-word hit before a looser one: 'go' is the Go rack first, though the Rust rack ('goes') stands before it on the shelf")
    void wholeWordHitsListFirst(@TempDir Path empty) {
        List<Entry> entries = RackGallery.entries(null, empty.toFile());
        List<String> hits = ids(RackGallery.filter(entries, "go"));
        assertThat(hits).contains("community:rust-save-loop");
        assertThat(hits.get(0)).isEqualTo("community:go-green-restart");
        assertThat(ids(entries).indexOf("community:rust-save-loop"))
                .isLessThan(ids(entries).indexOf("community:go-green-restart"));
    }

    @Test
    @DisplayName("a translated card is still found by its English words: a translation adds a way in and never removes one")
    void aTranslationNeverRemovesAWayIn(@TempDir Path dropIns) throws IOException {
        JSONObject doc = RackPresets.TDD_LOOP.buildPatch();
        doc.put("shared", new JSONObject().put("name", "Crate loop").put("name.de", "Kistenschleife")
                .put("description", "Tests on every save."));
        Files.writeString(dropIns.resolve("crate.json"), doc.toString(), StandardCharsets.UTF_8);
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMAN);
            List<Entry> entries = RackGallery.entries(null, dropIns.toFile());
            Entry crate = byId(entries, "yours:crate.json");
            assertThat(crate.card().name()).isEqualTo("Kistenschleife");
            assertThat(crate.card().description()).as("field by field").isEqualTo("Tests on every save.");
            assertThat(ids(RackGallery.filter(entries, "Kistenschleife"))).contains("yours:crate.json");
            assertThat(ids(RackGallery.filter(entries, "crate loop"))).contains("yours:crate.json");
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    @DisplayName("ids are stable and say their source")
    void idsSayTheirSource(@TempDir Path empty) {
        for (Entry e : RackGallery.entries(null, empty.toFile())) {
            String prefix = e.source().name().toLowerCase(Locale.ROOT) + ":";
            assertThat(e.id()).startsWith(prefix);
        }
        assertThat(ids(RackGallery.entries(null, empty.toFile())))
                .isEqualTo(ids(RackGallery.entries(null, empty.toFile())));
    }
}
