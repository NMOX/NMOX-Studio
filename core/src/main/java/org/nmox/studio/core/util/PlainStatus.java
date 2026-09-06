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
 * refuses the rest. The rule itself lives in {@link PlainText}, which
 * the tooltip sites share.
 */
public final class PlainStatus {

    private PlainStatus() {
    }

    /** The text, with a leading space added when its head would read as markup. */
    public static String text(String status) {
        return status == null ? "" : PlainText.plain(status);
    }
}
