package org.nmox.studio.rack.projectstudio;

import java.util.List;
import java.util.Locale;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A space introduces itself in the reader's language, and keeps its
 * English.
 *
 * <p>{@code blurb.de} and friends are SIBLING keys rather than a nested
 * object, so a drop-in author adds one language by adding one line and a
 * catalogue written before v2.133.0 parses unchanged. This pins the three
 * things that arrangement has to get right: the triple is assembled
 * whatever order the keys arrive in, an absent language falls back to
 * English field by field, and the catalogue's own {@code name}/{@code
 * blurb} never move — the picker's search still matches what a doc or a
 * URL calls this space.
 */
class LearningCatalogShownTest {

    private final Locale started = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(started);
    }

    /** One space, with whatever translation siblings the caller names. */
    private static LearningCatalog.Space space(String siblings) {
        String json = """
            { "spaces": [ {
              "slug": "zig",
              "name": "Zig",
              "category": "LANGUAGE",
              "family": "Systems",
              "blurb": "Manual memory without the footguns.",
              "driver": { "kind": "run", "command": ["zig", "run", "hello.zig"],
                          "prompt": "", "snippets": [] },
              "install": { "mac": "brew install zig" },
              "files": [ { "path": "hello.zig", "content": "// hi\\n" } ],
              "tutorial": "# Zig\\n"
              SIBLINGS
            } ] }
            """.replace("SIBLINGS", siblings.isEmpty() ? "" : "," + siblings);
        List<LearningCatalog.Space> spaces = LearningCatalog.parse(new JSONObject(json));
        assertThat(spaces).hasSize(1);
        return spaces.get(0);
    }

    @Test
    @DisplayName("a reader whose language the space speaks reads it")
    void translatedLanguageIsShown() {
        LearningCatalog.Space zig = space(
                "\"name.de\": \"Zig\", \"blurb.de\": \"Handbetriebener Speicher ohne Fußangeln.\"");
        Locale.setDefault(Locale.GERMAN);
        assertThat(zig.shown().blurb()).isEqualTo("Handbetriebener Speicher ohne Fußangeln.");
    }

    @Test
    @DisplayName("a language the space does not speak falls back to English")
    void untranslatedLanguageFallsBackToEnglish() {
        LearningCatalog.Space zig = space("\"blurb.de\": \"Handbetriebener Speicher.\"");
        Locale.setDefault(Locale.forLanguageTag("vi"));
        assertThat(zig.shown().blurb()).isEqualTo("Manual memory without the footguns.");
        assertThat(zig.shown().name()).isEqualTo("Zig");
    }

    @Test
    @DisplayName("a language translated in part keeps English for the rest")
    void partialTranslationKeepsEnglishFieldByField() {
        LearningCatalog.Space zig = space("\"blurb.fr\": \"Mémoire manuelle, sans les pièges.\"");
        Locale.setDefault(Locale.FRENCH);
        assertThat(zig.shown().blurb()).isEqualTo("Mémoire manuelle, sans les pièges.");
        // the name is a technology name and carries no key: it must survive
        assertThat(zig.shown().name()).isEqualTo("Zig");
        // the tutorial slot is open and empty for the built-ins (plan.md
        // names the size of that job); an empty slot reads as English
        assertThat(zig.shown().tutorial()).isEqualTo("# Zig\n");
    }

    @Test
    @DisplayName("the triple is assembled whatever order the keys arrive in")
    void keyOrderDoesNotDecideTheTriple() {
        String blurbFirst = "\"blurb.pl\": \"Pamięć ręczna.\", \"name.pl\": \"Zig!\"";
        String nameFirst = "\"name.pl\": \"Zig!\", \"blurb.pl\": \"Pamięć ręczna.\"";
        Locale.setDefault(Locale.forLanguageTag("pl"));
        for (String siblings : List.of(blurbFirst, nameFirst)) {
            LearningCatalog.Translated shown = space(siblings).shown();
            assertThat(shown.name()).as("order: %s", siblings).isEqualTo("Zig!");
            assertThat(shown.blurb()).as("order: %s", siblings).isEqualTo("Pamięć ręczna.");
        }
    }

    @Test
    @DisplayName("the English record never moves, whatever the reader speaks")
    void englishStaysTheRecord() {
        LearningCatalog.Space zig = space(
                "\"name.de\": \"Ziggy\", \"blurb.de\": \"Handbetriebener Speicher.\"");
        Locale.setDefault(Locale.GERMAN);
        assertThat(zig.name()).as("what a doc or URL calls this space").isEqualTo("Zig");
        assertThat(zig.blurb()).isEqualTo("Manual memory without the footguns.");
    }

    @Test
    @DisplayName("a catalogue written before translations existed parses unchanged")
    void aCatalogueWithNoSiblingsIsUntouched() {
        LearningCatalog.Space zig = space("");
        assertThat(zig.translations()).isEmpty();
        Locale.setDefault(Locale.forLanguageTag("hi"));
        assertThat(zig.shown().blurb()).isEqualTo("Manual memory without the footguns.");
    }
}
