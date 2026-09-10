package org.nmox.studio.core.util;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A number a person reads and a number a machine reads (v2.105.0). */
class NumbersTest {

    @Test
    @DisplayName("the display form follows the reader: a German reads a decimal comma")
    void displayFollowsTheReader() {
        Locale was = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            assertThat(Numbers.display(17.85, 1)).isEqualTo("17.9");
            Locale.setDefault(Locale.GERMANY);
            assertThat(Numbers.display(17.85, 1)).as("German writes 17,9").isEqualTo("17,9");
            Locale.setDefault(Locale.forLanguageTag("pl"));
            assertThat(Numbers.display(1234.5, 2)).contains(",");
        } finally {
            Locale.setDefault(was);
        }
    }

    @Test
    @DisplayName("the stable form does not move for anyone — the half that had no name")
    void stableIsTheSameEverywhere() {
        Locale was = Locale.getDefault();
        try {
            for (String tag : new String[] {"en-US", "de-DE", "pl", "fr", "ru", "hi", "vi"}) {
                Locale.setDefault(Locale.forLanguageTag(tag));
                assertThat(Numbers.stable(17.85, 1)).as("%s must not move a record", tag)
                        .isEqualTo("17.9");
                assertThat(Numbers.stable(1500L)).as("%s, and no grouping separator", tag)
                        .isEqualTo("1500");
            }
        } finally {
            Locale.setDefault(was);
        }
    }

    @Test
    @DisplayName("a stable number keeps Western digits where the locale numbers differently")
    void stableResistsLocaleDigits() {
        Locale was = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("hi-IN-u-nu-deva"));
            assertThat(Numbers.stable(2.5, 1)).containsPattern("^[0-9]+\\.[0-9]+$").isEqualTo("2.5");
            assertThat(Numbers.stable(42L)).isEqualTo("42");
        } finally {
            Locale.setDefault(was);
        }
    }

    @Test
    @DisplayName("a negative decimal count is treated as none rather than throwing mid-paint")
    void negativeDecimalsAreSurvivable() {
        assertThat(Numbers.stable(2.6, -3)).isEqualTo("3");
        assertThat(Numbers.display(2.6, -3)).isNotBlank();
    }
}
