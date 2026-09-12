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
 * a test-scope bundle beside this class makes both MISS branches reachable
 * — a bundle that is absent entirely and a bundle that simply lacks the key
 * are different code, and until that file existed every case here took the
 * first one and a mutant lived in the second. The seams prove the same
 * contract against the SHIPPED bundles in real locales ({@code
 * CatalogTextTest}, {@code BlockTextTest}, {@code CatalogueSeamsTest},
 * {@code ChainTextTest}).
 */
class BundlesTest {

    private final Locale started = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(started);
    }

    @Test
    @DisplayName("a key the bundle resolves comes from the bundle")
    void presentKeyComesFromTheBundle() {
        assertThat(Bundles.optional(Bundles.class, "Bundles_presentKey", "ignored"))
                .isEqualTo("a value from the bundle");
    }

    @Test
    @DisplayName("a bundle WITHOUT the key falls back to the English it was given")
    void missingKeyFallsBackToEnglish() {
        // this package has a bundle (test resources) and that is the point:
        // the two miss branches are different code, and with no bundle at all
        // every case took the catch and left the ternary untested
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
        assertThat(Bundles.optional(Bundles.class, "NoSuchKey_Anywhere", null))
                .as("through the present-bundle path").isNull();
        assertThat(Bundles.optional(Object.class, "NoSuchKey_Anywhere", null))
                .as("through the absent-bundle path").isNull();
    }

}
