package org.nmox.studio.core.util;

/**
 * Status-line text that can never render as markup (v2.86.0): the
 * platform's status line is a {@code JLabel} with no html-disable, and
 * Swing treats a label whose text BEGINS with {@code <html>} as markup.
 * A status text that starts with something the product did not write —
 * a package.json script name, a run label, an exception's words, a
 * CSS name — is therefore one hostile file away from the v1.208.0
 * fetch. Swing's check reads the first six characters only, so a
 * leading space defeats it invisibly; every status text whose head is
 * not our own literal rides {@link #text}, and PlainStatusGateTest
 * refuses the rest.
 */
public final class PlainStatus {

    private PlainStatus() {
    }

    /** The text, with a leading space added when its head would read as markup. */
    public static String text(String status) {
        if (status == null) {
            return "";
        }
        String s = status;
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        if (s.regionMatches(true, i, "<html", 0, 5)) {
            return " " + s.substring(i);
        }
        return s;
    }
}
