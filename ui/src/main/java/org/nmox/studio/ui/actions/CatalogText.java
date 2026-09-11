package org.nmox.studio.ui.actions;

import java.util.Locale;
import java.util.MissingResourceException;
import org.nmox.studio.rack.projectstudio.LearningCatalog;
import org.openide.util.NbBundle;

/**
 * The learning catalogue's grouping words, in the reader's language.
 *
 * <p>A space's blurb travels with the space, because a drop-in author writes
 * their own and the catalogue file is where it belongs. Its CATEGORY and
 * FAMILY do not: they are a shared vocabulary the picker groups by, so they
 * live in a bundle beside the picker that renders them — the same placement
 * law as {@code DeviceText} (v2.132.0).
 *
 * <p>A family is only sometimes prose. "Start Here", "Systems" and "Data
 * stores" are words; "Python", "Go", "Solidity" and "BEAM" are names, and a
 * name must survive translation intact. So only the prose families carry a
 * key, a missing key returns the family unchanged, and that single rule also
 * gives a drop-in author's own family the right behaviour for free.
 */
final class CatalogText {

    private CatalogText() {
    }

    /** "Languages" / "Sprachen" / "Программирование" — the picker's grouping word. */
    static String category(LearningCatalog.Category category) {
        return lookup("LearnCategory_" + category.name(), category.label);
    }

    /** A family as the picker shows it: translated when it is prose, as written when it is a name. */
    static String family(String family) {
        if (family == null || family.isBlank()) {
            return "";
        }
        return lookup("LearnFamily_" + key(family), family);
    }

    /** A properties-safe key for a family that may hold spaces, slashes and dots. */
    static String key(String family) {
        return family.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
    }

    private static String lookup(String key, String fallback) {
        try {
            return NbBundle.getMessage(CatalogText.class, key);
        } catch (MissingResourceException untranslated) {
            return fallback;
        }
    }
}
