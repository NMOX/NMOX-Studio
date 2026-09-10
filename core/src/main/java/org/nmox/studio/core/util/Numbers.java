package org.nmox.studio.core.util;

import java.util.Locale;

/**
 * Two ways to write a number, and the difference is who is reading
 * (v2.105.0) — the third member of the family {@link Clocks} and
 * {@link Collate} opened.
 *
 * <p>A number a PERSON reads is written the way their language writes
 * numbers: {@code 17.8 MB} for an American, {@code 17,8 MB} for a German.
 * Java already does this — a bare {@code String.format("%.1f", x)} follows
 * {@code Locale.Category.FORMAT} — so the product's ten display sites were
 * measured correct before this class existed, and this half exists to give
 * them a name rather than to change them.
 *
 * <p>The half that had no name is the other one. A number written into a
 * project file, a command argument, a JSON body or a field an agent parses
 * must not move with the reader, and the default is exactly wrong there: a
 * German locale writes {@code 1,5}, which the next reader parses as
 * something else or not at all. Nothing in the product does that today —
 * that is a measurement, and {@code NumberSiteLedgerTest} is what keeps it
 * true.
 *
 * <p>The pattern is deliberate and now threefold: for a time, an order and
 * a number, the seam names the reader and a ledger makes every site say
 * which reader it serves.
 */
public final class Numbers {

    private Numbers() {
    }

    /**
     * The number as this user's language writes it — for anything painted
     * on screen. Reads the locale at call time, so it follows a live
     * language switch (v2.103.0).
     *
     * @param value the number
     * @param decimals digits after the decimal separator, 0 or more
     */
    public static String display(double value, int decimals) {
        return String.format(Locale.getDefault(Locale.Category.FORMAT),
                "%." + Math.max(0, decimals) + "f", value);
    }

    /**
     * The number as every machine reads it: a dot for the decimal
     * separator, no grouping, for every reader. For a file, a command
     * argument, a wire field — anywhere a second reader has to parse back
     * exactly what a first reader wrote.
     */
    public static String stable(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + Math.max(0, decimals) + "f", value);
    }

    /**
     * A whole number for a record: Western digits, no grouping separator,
     * whatever the reader's language would otherwise do to both.
     */
    public static String stable(long value) {
        return String.format(Locale.ROOT, "%d", value);
    }
}
