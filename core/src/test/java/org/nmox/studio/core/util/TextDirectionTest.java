package org.nmox.studio.core.util;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextDirectionTest {

    @AfterEach
    void clearForce() {
        System.clearProperty(TextDirection.FORCE);
    }

    @Test
    @DisplayName("the languages we ship run left to right; Arabic and Hebrew do not")
    void theLocaleDecides() {
        for (String ltr : new String[] {"en", "es", "fr", "de", "ru", "uk", "pl",
                "pt", "id", "tl", "vi", "zh", "hi"}) {
            assertThat(TextDirection.isRightToLeft(Locale.forLanguageTag(ltr)))
                    .as(ltr + " runs left to right").isFalse();
        }
        for (String rtl : new String[] {"ar", "he", "fa", "ur"}) {
            assertThat(TextDirection.isRightToLeft(Locale.forLanguageTag(rtl)))
                    .as(rtl + " runs right to left").isTrue();
        }
    }

    @Test
    @DisplayName("Hebrew keeps its modern code, so Bundle_he is the bundle it reads")
    void hebrewIsNotTheLegacyCode() {
        // Java mapped he -> iw for decades; since JDK 17 the modern code wins by
        // default and iw NORMALISES to he. The bundles are named for what
        // getLanguage() answers, so this is the assertion that keeps the file
        // name and the runtime in step.
        assertThat(Locale.forLanguageTag("he").getLanguage()).isEqualTo("he");
        assertThat(Locale.forLanguageTag("iw").getLanguage()).isEqualTo("he");
        assertThat(new Locale("iw").getLanguage()).isEqualTo("he");
    }

    @Test
    @DisplayName("the force property answers for a build that has no right-to-left language yet")
    void forcedDirectionWins() {
        System.setProperty(TextDirection.FORCE, "true");
        assertThat(TextDirection.isRightToLeft(Locale.ENGLISH)).isTrue();
        System.setProperty(TextDirection.FORCE, "false");
        assertThat(TextDirection.isRightToLeft(Locale.forLanguageTag("he"))).isFalse();
        System.setProperty(TextDirection.FORCE, "  ");
        assertThat(TextDirection.isRightToLeft(Locale.forLanguageTag("he")))
                .as("a blank force is not an answer; the locale decides").isTrue();
    }

    @Test
    @DisplayName("a null locale is not right to left")
    void nullIsLeftToRight() {
        assertThat(TextDirection.isRightToLeft(null)).isFalse();
    }
}
