package org.nmox.studio.core.util;

/**
 * "1 card", "2 cards" (v2.85.0): the first-show sweep read "1 pieces"
 * on a fresh Block Studio canvas, and the census that followed found
 * the same shape in six more user-visible counts. One helper, so the
 * next count reads right without anyone remembering the rule.
 */
public final class Plural {

    private Plural() {
    }

    /** {@code n} with the noun's regular plural: "1 card", "0 cards", "2 cards". */
    public static String of(long n, String singular) {
        return of(n, singular, singular + "s");
    }

    /** {@code n} with an irregular plural: "1 match", "2 matches". */
    public static String of(long n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }
}
