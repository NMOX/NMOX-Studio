package org.nmox.studio.ui.actions;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.projectstudio.LearningCatalog;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The picker renders and searches what the reader can read.
 *
 * <p>The catalogue speaking twelve languages is only half the release: a
 * translated pitch nothing reads is prose written for no one. These are the
 * CALL SITES — the row the picker paints and the filter the search box runs
 * — pinned against a real shipped space, because a seam that diverges with
 * no consumer is a payload without a gate (the v1.321.0 law).
 *
 * <p>The search law is the one worth stating twice: a translation ADDS a way
 * in and never removes one. A reader typing their own language finds the
 * space, and so does a reader who arrived from an English doc, a tutorial or
 * a URL — otherwise this release would have re-created the v1.215.0
 * findability defect in twelve languages at once.
 */
class PickerSpeaksTest {

    private final Locale started = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(started);
    }

    /** The catalogue's front door: the one space whose NAME is prose. */
    private static LearningCatalog.Space firstWebPage() {
        return LearningCatalog.all().stream()
                .filter(s -> s.slug().equals("first-web-page"))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("a row is painted in the reader's language")
    void theRowSpeaksTheReadersLanguage() {
        LearningCatalog.Space s = firstWebPage();
        Locale.setDefault(Locale.GERMAN);
        String html = NewLearningSpaceAction.cellHtml(s, 480);
        assertThat(html).as("the name a German reader sees")
                .contains(NewLearningSpaceAction.escape(s.shown().name()))
                .doesNotContain(s.name());
        assertThat(html).as("the pitch a German reader sees")
                .contains(NewLearningSpaceAction.escape(s.shown().blurb()));
    }

    @Test
    @DisplayName("the grouping words on a row are read in the reader's language too")
    void theRowGroupsInTheReadersLanguage() {
        LearningCatalog.Space s = firstWebPage();
        Locale.setDefault(Locale.GERMAN);
        String html = NewLearningSpaceAction.cellHtml(s, 480);
        assertThat(html).contains(CatalogText.family(s.family()));
        assertThat(html).as("the category is a heading word, not an enum name")
                .doesNotContain(s.category().name());
    }

    @Test
    @DisplayName("a reader finds the space by typing their own language")
    void searchFindsTheTranslatedWords() {
        LearningCatalog.Space s = firstWebPage();
        Locale.setDefault(Locale.GERMAN);
        String word = s.shown().name().split(" ")[1].toLowerCase(Locale.ROOT);
        assertThat(NewLearningSpaceAction.matches(s, word))
                .as("a German reader typing \"%s\"", word).isTrue();
    }

    @Test
    @DisplayName("a reader who arrived from an English doc still finds it")
    void searchStillFindsTheEnglishRecord() {
        LearningCatalog.Space s = firstWebPage();
        Locale.setDefault(Locale.GERMAN);
        assertThat(NewLearningSpaceAction.matches(s, "first web page"))
                .as("the name the docs, the tutorials and the URL use").isTrue();
        assertThat(NewLearningSpaceAction.matches(s, "first-web-page"))
                .as("the slug, which a person really does paste").isTrue();
    }

    @Test
    @DisplayName("a query that matches nothing still matches nothing")
    void searchDoesNotMatchEverything() {
        Locale.setDefault(Locale.GERMAN);
        assertThat(NewLearningSpaceAction.matches(firstWebPage(), "cobol"))
                .as("searching both languages must not widen into matching all of them")
                .isFalse();
    }
}
