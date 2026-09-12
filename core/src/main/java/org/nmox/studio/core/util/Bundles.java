package org.nmox.studio.core.util;

import java.util.MissingResourceException;
import java.util.ResourceBundle;
import org.openide.util.NbBundle;

/**
 * Looking up a key that is allowed to be missing.
 *
 * <p>The catalogue seams (v2.132.0–v2.134.0) all share one rule: a device
 * description, a piece name, a template name, a chain label or a learning
 * family carries a key when somebody translated it and is shown as written
 * when nobody did. Expressed with {@code NbBundle.getMessage} inside a
 * {@code catch (MissingResourceException)}, that rule is correct and
 * expensive in exactly the wrong place.
 *
 * <p><b>Measured, because the cost is not where it looks.</b> These keys
 * have no English base bundle ON PURPOSE — their English lives in the enum
 * or the catalogue, and a base-bundle copy would be the second home
 * v2.131.0 spent a release removing. So an ENGLISH reader misses EVERY
 * key, every time, and the miss path is the throw path: 300,000 lookups
 * cost 834 ms in English against 71 ms in German, twelve times slower on
 * the majority path, with the exceptions constructed inside Swing paint
 * loops that repaint on every scroll and hover.
 *
 * <p>{@link ResourceBundle#containsKey} answers the same question without
 * building a stack trace, so the fallback stops being the slow path. The
 * bundle itself is resolved per call on purpose: {@code ResourceBundle}
 * caches by locale, and reading it fresh is what lets the LIVE language
 * switch (v2.103.0) reach a catalogue that was rendered a moment ago.
 */
public final class Bundles {

    private Bundles() {
    }

    /**
     * The value of {@code key} in {@code owner}'s bundle, or {@code english}
     * when no translation was written.
     *
     * @param owner   a class in the package whose {@code Bundle.properties}
     *                family holds the key
     * @param key     the key a translator may or may not have written
     * @param english what to show when nobody did — never null in practice,
     *                because the English IS the record this falls back to
     */
    public static String optional(Class<?> owner, String key, String english) {
        ResourceBundle bundle;
        try {
            bundle = NbBundle.getBundle(owner);
        } catch (MissingResourceException noBundleAtAll) {
            // a package with no bundle family at all is a build problem, not
            // an untranslated key; the reader still gets a readable word
            return english;
        }
        return bundle.containsKey(key) ? bundle.getString(key) : english;
    }
}
