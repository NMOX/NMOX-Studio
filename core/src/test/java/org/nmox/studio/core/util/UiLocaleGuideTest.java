package org.nmox.studio.core.util;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The manual follows the language (v2.104.0).
 *
 * <p>A reader who set the IDE to their own language and then clicked "User
 * Guide" landed on the English manual — the wall the whole localization arc
 * exists to remove, one click past where the translation stopped.
 */
class UiLocaleGuideTest {

    @Test
    @DisplayName("a shipped language gets its own guide")
    void shippedLanguagesGetTheirOwnGuide() {
        assertThat(UiLocale.guideDoc(Locale.forLanguageTag("uk"))).isEqualTo("docs/user-guide.uk.md");
        assertThat(UiLocale.guideDoc(Locale.forLanguageTag("hi"))).isEqualTo("docs/user-guide.hi.md");
        assertThat(UiLocale.guideDoc(Locale.GERMANY)).isEqualTo("docs/user-guide.de.md");
    }

    @Test
    @DisplayName("a country variant of a shipped language gets that language's guide")
    void countryVariantsFallToTheirLanguage() {
        // pt and zh ship without country codes on purpose (v2.99.0), so
        // Portugal and Singapore must reach the guide their bundles reach
        assertThat(UiLocale.guideDoc(Locale.forLanguageTag("pt-PT"))).isEqualTo("docs/user-guide.pt.md");
        assertThat(UiLocale.guideDoc(Locale.forLanguageTag("zh-SG"))).isEqualTo("docs/user-guide.zh.md");
    }

    @Test
    @DisplayName("English and any language we do not ship get the English guide, never a dead link")
    void unshippedLanguagesFallBackToEnglish() {
        assertThat(UiLocale.guideDoc(Locale.US)).isEqualTo("docs/user-guide.md");
        assertThat(UiLocale.guideDoc(Locale.JAPAN)).isEqualTo("docs/user-guide.md");
        assertThat(UiLocale.guideDoc(null)).isEqualTo("docs/user-guide.md");
    }

    @Test
    @DisplayName("the no-argument form follows a live switch rather than a startup snapshot")
    void followsTheLiveLanguage() {
        Locale was = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("vi"));
            assertThat(UiLocale.guideDoc()).isEqualTo("docs/user-guide.vi.md");
            Locale.setDefault(Locale.forLanguageTag("pl"));
            assertThat(UiLocale.guideDoc()).as("a cached path would still say vi")
                    .isEqualTo("docs/user-guide.pl.md");
        } finally {
            Locale.setDefault(was);
        }
    }
}
