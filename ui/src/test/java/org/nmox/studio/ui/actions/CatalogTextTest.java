package org.nmox.studio.ui.actions;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.projectstudio.LearningCatalog;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The picker's grouping words, and the one rule that decides which of them
 * are prose.
 *
 * <p>A family is only sometimes a word. "Start Here" and "Data stores" are
 * prose a reader should meet in their own language; "Python", "BEAM" and
 * "Web3" are names that must survive translation intact. Rather than keep
 * a list of which is which, a family carries a key when somebody wrote one
 * and is returned unchanged when nobody did — so a drop-in author's own
 * family gets the right behaviour with no code at all.
 */
class CatalogTextTest {

    private final Locale started = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(started);
    }

    @Test
    @DisplayName("a family key is properties-safe whatever the family holds")
    void keyIsPropertiesSafe() {
        assertThat(CatalogText.key("Start Here")).isEqualTo("start_here");
        assertThat(CatalogText.key("Data stores")).isEqualTo("data_stores");
        assertThat(CatalogText.key(".NET web")).isEqualTo("_net_web");
        assertThat(CatalogText.key("Lisp/JVM")).isEqualTo("lisp_jvm");
        // Turkish would fold a dotted I to a dotless one and lose the key
        // (the v2.37.5 sweep's class)
        Locale.setDefault(Locale.forLanguageTag("tr"));
        assertThat(CatalogText.key("Interactive")).isEqualTo("interactive");
    }

    @Test
    @DisplayName("a family nobody translated is shown as written")
    void anUntranslatedFamilyIsANameAndSurvives() {
        Locale.setDefault(Locale.GERMAN);
        assertThat(CatalogText.family("Python")).isEqualTo("Python");
        assertThat(CatalogText.family("BEAM")).isEqualTo("BEAM");
        // a drop-in author's own family, which no bundle of ours can know
        assertThat(CatalogText.family("Klingon")).isEqualTo("Klingon");
    }

    @Test
    @DisplayName("a family that is prose is read in the reader's language")
    void aProseFamilyIsTranslated() {
        Locale.setDefault(Locale.GERMAN);
        assertThat(CatalogText.family("Start Here")).isNotEqualTo("Start Here");
    }

    @Test
    @DisplayName("every category the picker can group under is prose")
    void everyCategoryIsTranslated() {
        Locale.setDefault(Locale.GERMAN);
        for (LearningCatalog.Category category : LearningCatalog.Category.values()) {
            assertThat(CatalogText.category(category))
                    .as("heading for %s", category)
                    .isNotBlank();
        }
        assertThat(CatalogText.category(LearningCatalog.Category.LANGUAGE))
                .isNotEqualTo(LearningCatalog.Category.LANGUAGE.label);
    }

    @Test
    @DisplayName("an empty family is an empty heading, not a lookup")
    void aBlankFamilyIsBlank() {
        assertThat(CatalogText.family(null)).isEmpty();
        assertThat(CatalogText.family("   ")).isEmpty();
    }
}
