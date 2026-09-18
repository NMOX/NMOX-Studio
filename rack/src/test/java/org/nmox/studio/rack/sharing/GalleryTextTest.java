package org.nmox.studio.rack.sharing;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.gallery.RackGallery;
import org.nmox.studio.rack.model.RackCard;

import static org.assertj.core.api.Assertions.assertThat;

/** What the gallery tells a reader about one rack, without a window. */
class GalleryTextTest {

    private Locale before;

    @BeforeEach
    void english() {
        before = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @AfterEach
    void restore() {
        Locale.setDefault(before);
    }

    private static RackGallery.Entry entry(RackCard card, RackGallery.Source source, boolean fits,
            List<String> devices, List<String> wiring) {
        return new RackGallery.Entry("test:x", source, card, devices, wiring.size(), wiring, fits, null, "",
                JSONObject::new);
    }

    @Test
    @DisplayName("a row says the name, where the rack comes from, and that it fits the aimed project when it does")
    void rows() {
        RackCard card = new RackCard("Rust save loop", "", "", List.of("RUST"), List.of("cargo"));
        assertThat(GalleryText.row(entry(card, RackGallery.Source.COMMUNITY, false, List.of(), List.of())))
                .contains("Rust save loop").contains("community").doesNotContain("fits");
        assertThat(GalleryText.row(entry(card, RackGallery.Source.YOURS, true, List.of(), List.of())))
                .contains("yours").contains("fits this project");
        assertThat(GalleryText.row(entry(RackCard.EMPTY, RackGallery.Source.PRESET, false, List.of(), List.of())))
                .as("a rack with no name still has a row").contains("(unnamed rack)");
    }

    @Test
    @DisplayName("the page says what the rack is, what it needs, what it holds and how it is wired — and the tool verdict only once the lookup has answered")
    void detailPage() {
        RackCard card = new RackCard("Rust save loop", "Tests on every save.", "Ada",
                List.of("RUST"), List.of("cargo", "rustfmt"));
        RackGallery.Entry e = entry(card, RackGallery.Source.COMMUNITY, true,
                List.of("REFLEX", "VERITAS"), List.of("REFLEX changed > VERITAS run"));

        String pending = GalleryText.detail(e, null);
        assertThat(pending).contains("Rust save loop").contains("Tests on every save.").contains("Shared by Ada")
                .contains("Made for: RUST").contains("Needs on the PATH: cargo, rustfmt")
                .contains("Devices (2), 1 cables:").contains("  REFLEX").contains("Wiring:")
                .contains("  REFLEX changed > VERITAS run");
        assertThat(pending).as("no verdict before the lookup answers — never a guess")
                .doesNotContain("Not found").doesNotContain("Everything it needs");

        assertThat(GalleryText.detail(e, List.of())).contains("Everything it needs is on this machine.");
        assertThat(GalleryText.detail(e, List.of("rustfmt"))).contains("Not found on this machine: rustfmt")
                .doesNotContain("Everything it needs");
    }

    @Test
    @DisplayName("a rack that needs nothing and wires nothing says so plainly, with no empty headings")
    void sparseRack() {
        String page = GalleryText.detail(entry(new RackCard("Bare", "", "", null, null),
                RackGallery.Source.STARTER, false, List.of("MONITOR"), List.of()), List.of());
        assertThat(page).contains("No cables").doesNotContain("Needs on the PATH").doesNotContain("Made for")
                .doesNotContain("Shared by").doesNotContain("Everything it needs");
    }

    @Test
    @DisplayName("docs/rack-files.md shows the rust-save-loop rack byte for byte as it ships — a worked example is a fixture, not prose")
    void documentedExampleIsTheShippedFile() throws Exception {
        String doc = Files.readString(Path.of("..", "docs", "rack-files.md"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        int open = doc.indexOf("```json\n");
        assertThat(open).as("the doc carries a json example").isPositive();
        int close = doc.indexOf("\n```", open);
        String shown = doc.substring(open + "```json\n".length(), close).strip();
        String shipped = Files.readString(Path.of("src", "main", "resources", "org", "nmox", "studio", "rack",
                "gallery", "racks", "rust-save-loop.nmoxrack.json"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertThat(shown).isEqualTo(shipped);
        // and the prose beside it names the devices the file actually mounts
        RackCard card = RackCard.of(new JSONObject(shipped), "");
        assertThat(doc).contains(card.name().toLowerCase(Locale.ROOT).replace(' ', '-'));
    }

    @Test
    @DisplayName("the README's wiring sketch is the generated one: every line of its block is a Rust save loop line in docs/racks.md")
    void readmeSketchIsGenerated() throws Exception {
        String readme = Files.readString(Path.of("..", "README.md"), StandardCharsets.UTF_8).replace("\r\n", "\n");
        int open = readme.indexOf("```\nREFLEX CHANGED");
        assertThat(open).as("the README shows the Rust save loop's wiring").isPositive();
        String block = readme.substring(open + 4, readme.indexOf("\n```", open + 4));
        String racks = Files.readString(Path.of("..", "docs", "racks.md"), StandardCharsets.UTF_8).replace("\r\n", "\n");
        int section = racks.indexOf("### Rust save loop");
        assertThat(section).isPositive();
        String rust = racks.substring(section, racks.indexOf("\n### ", section + 4));
        for (String line : block.split("\n")) {
            assertThat(rust).as("README line is generated wiring: " + line).contains("`" + line + "`");
        }
    }
}
