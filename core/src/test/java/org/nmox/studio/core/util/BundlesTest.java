package org.nmox.studio.core.util;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A key that is allowed to be missing is looked up, not thrown at.
 *
 * <p>The behaviour here is the same behaviour the catch idiom had — that
 * was never the defect. The defect was WHERE the cost fell: these keys have
 * no English base bundle by design, so an English reader misses every one,
 * and the miss path built a stack trace inside a Swing paint loop. The
 * structural half lives in {@code OptionalKeyLookupGateTest}; this half
 * pins the three FALLBACK answers, which is all core can honestly reach —
 * core ships no bundle of its own, so the HIT path is proven where the
 * keys actually live ({@code CatalogTextTest}, {@code BlockTextTest},
 * {@code CatalogueSeamsTest}, {@code ChainTextTest}), each reading the
 * shipped bundles in a real locale.
 */
class BundlesTest {

    private final Locale started = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(started);
    }

    @Test
    @DisplayName("a key nobody translated comes back as the English it was given")
    void missingKeyFallsBackToEnglish() {
        assertThat(Bundles.optional(Bundles.class, "NoSuchKey_Anywhere", "Toggle class"))
                .isEqualTo("Toggle class");
    }

    @Test
    @DisplayName("a package with no bundle at all is a build problem, not a blank label")
    void missingBundleFallsBackToEnglish() {
        // Object's package ships no NMOX bundle family
        assertThat(Bundles.optional(Object.class, "AnyKey", "Element")).isEqualTo("Element");
    }

    @Test
    @DisplayName("a null English fallback comes back as null, for callers that mean \"unknown\"")
    void nullFallbackSurvives() {
        // LocaleRefresher uses this: a window it cannot name is left alone,
        // never blanked, so null has to travel
        assertThat(Bundles.optional(Bundles.class, "NoSuchKey_Anywhere", null)).isNull();
    }

}
