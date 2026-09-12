package org.nmox.studio.rack.projectstudio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two catalogues this module owns, read in the reader's language.
 *
 * <p>{@link TemplateText} is the New Project list; {@link Checkpoints} is
 * Check My Work. Both keep their English as the record and resolve the
 * reader's language per call, because the language switch is live
 * (v2.103.0) and a catalogue is built once.
 */
class CatalogueSeamsTest {

    private final Locale started = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(started);
    }

    @Test
    @DisplayName("a template's description is read in the reader's language")
    void everyTemplateDescriptionIsTranslated() {
        Locale.setDefault(Locale.GERMAN);
        List<String> english = new ArrayList<>();
        for (ProjectTemplates t : ProjectTemplates.values()) {
            if (TemplateText.description(t).equals(t.getDescription())) {
                english.add(t.name());
            }
        }
        assertThat(english).as("descriptions a German reader would still meet in English")
                .isEmpty();
    }

    @Test
    @DisplayName("a template name that is a technology name survives translation")
    void aTechnologyNameIsNotTranslated() {
        Locale.setDefault(Locale.GERMAN);
        assertThat(TemplateText.name(ProjectTemplates.VITE_REACT))
                .as("a name a person searches for must not move")
                .isEqualTo(ProjectTemplates.VITE_REACT.getDisplayName());
        assertThat(TemplateText.name(ProjectTemplates.TS_LIBRARY))
                .as("\"Library\" is a word, and a word gets translated")
                .isNotEqualTo(ProjectTemplates.TS_LIBRARY.getDisplayName());
    }

    @Test
    @DisplayName("the English record never moves, whatever the reader speaks")
    void englishStaysTheRecord() {
        Locale.setDefault(Locale.GERMAN);
        assertThat(ProjectTemplates.TS_LIBRARY.getDisplayName()).isEqualTo("TypeScript Library");
        assertThat(ProjectTemplates.VANILLA.getDescription())
                .isEqualTo("HTML/CSS/JS, no build step, static dev server");
    }

    /** One checkpoint with whatever translation siblings the caller names. */
    private static Checkpoints.Checkpoint checkpoint(String siblings) {
        String json = """
            [ { "label": "You changed the heading",
                "hint": "edit the text between <h1> and </h1>",
                "file": { "path": "index.html", "absent": "Hello" }
                SIBLINGS } ]
            """.replace("SIBLINGS", siblings.isEmpty() ? "" : "," + siblings);
        List<String> notes = new ArrayList<>();
        List<Checkpoints.Checkpoint> parsed = Checkpoints.parse(new JSONArray(json), notes);
        assertThat(notes).as("the fixture should parse cleanly").isEmpty();
        assertThat(parsed).hasSize(1);
        return parsed.get(0);
    }

    @Test
    @DisplayName("a checkpoint speaks the reader's language, and falls back field by field")
    void checkpointShown() {
        Checkpoints.Checkpoint c = checkpoint(
                "\"label.de\": \"Sie haben die Überschrift geändert\"");
        Locale.setDefault(Locale.GERMAN);
        assertThat(c.shown().label()).isEqualTo("Sie haben die Überschrift geändert");
        assertThat(c.shown().hint())
                .as("an untranslated hint stays English rather than vanishing")
                .isEqualTo("edit the text between <h1> and </h1>");

        Locale.setDefault(Locale.forLanguageTag("vi"));
        assertThat(c.shown().label()).isEqualTo("You changed the heading");
    }

    @Test
    @DisplayName("a checkpoint's triple does not depend on key order")
    void checkpointKeyOrder() {
        Locale.setDefault(Locale.forLanguageTag("pl"));
        String labelFirst = "\"label.pl\": \"Zmieniłeś nagłówek\", \"hint.pl\": \"zmień tekst\"";
        String hintFirst = "\"hint.pl\": \"zmień tekst\", \"label.pl\": \"Zmieniłeś nagłówek\"";
        for (String siblings : List.of(labelFirst, hintFirst)) {
            Checkpoints.Shown shown = checkpoint(siblings).shown();
            assertThat(shown.label()).as("order: %s", siblings).isEqualTo("Zmieniłeś nagłówek");
            assertThat(shown.hint()).as("order: %s", siblings).isEqualTo("zmień tekst");
        }
    }

    @Test
    @DisplayName("a checkpoint file written before translations existed parses unchanged")
    void checkpointWithNoSiblings() {
        Checkpoints.Checkpoint c = checkpoint("");
        assertThat(c.translations()).isEmpty();
        Locale.setDefault(Locale.GERMAN);
        assertThat(c.shown().label()).isEqualTo("You changed the heading");
    }

    @Test
    @DisplayName("a failing checkpoint reports in the reader's language")
    void theResultSpeaksToo() throws Exception {
        java.io.File dir = java.nio.file.Files.createTempDirectory("nmox-cp").toFile();
        java.nio.file.Files.writeString(new java.io.File(dir, "index.html").toPath(),
                "<h1>Hello</h1>", java.nio.charset.StandardCharsets.UTF_8);
        Checkpoints.Checkpoint c = checkpoint(
                "\"label.de\": \"Sie haben die Überschrift geändert\", "
                + "\"hint.de\": \"ändern Sie den Text\"");
        Locale.setDefault(Locale.GERMAN);
        Checkpoints.Result r = Checkpoints.run(dir, c, null);
        assertThat(r.passed()).as("the sample heading is still there").isFalse();
        assertThat(r.label()).isEqualTo("Sie haben die Überschrift geändert");
        assertThat(r.detail()).contains("ändern Sie den Text");
    }
}
