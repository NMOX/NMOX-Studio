package org.nmox.studio.ui.browser.fx;

import java.util.function.Function;
import java.util.function.IntUnaryOperator;

/**
 * Shapes the complex scripts the Browser's WebKit paints unshaped (v2.165.0).
 *
 * <p>OpenJFX's WebKit port paints every run through its simple text path, and
 * the hook that path relies on to shape text ({@code Font::applyTransforms}) is
 * a no-op there. So Arabic arrives at the paint call as isolated letter forms
 * and Devanagari as unreordered characters with loose marks — measured on every
 * OpenJFX release from 17 to 26 (ledger 99). The painted glyph array is still
 * enough to recover the text: each glyph is the font's plain mapping of one
 * character, in visual order. This core finds the runs of those glyphs, asks a
 * real shaper for the joined forms, and writes them back in place.
 *
 * <p>WebKit measures the run itself, glyph by glyph, before painting it; since
 * v2.166.0 those widths are estimated close to the shaped forms
 * ({@link #measuredWidth}), and the shaped run is painted centred in the box
 * WebKit reserved, so what the estimate missed splits evenly on both sides.
 * Pure: glyph lookup and shaping arrive as functions.
 */
public final class ComplexScripts {

    private ComplexScripts() {
    }

    /** Shaped glyphs in visual order with their advances. */
    public record Shaped(int[] glyphs, float[] advances) {
    }

    /** Arabic, Arabic Supplement and Arabic Extended-A: joined, right to left. */
    static boolean rightToLeft(int cp) {
        return (cp >= 0x0600 && cp <= 0x06FF) || (cp >= 0x0750 && cp <= 0x077F) || (cp >= 0x08A0 && cp <= 0x08FF);
    }

    /** Devanagari through Malayalam: reordered and conjoined, left to right. */
    static boolean leftToRight(int cp) {
        return cp >= 0x0900 && cp <= 0x0D7F;
    }

    /** Every code point this core shapes. */
    public static boolean shapes(int cp) {
        return rightToLeft(cp) || leftToRight(cp);
    }

    /** The code point ranges a glyph lookup table needs, inclusive pairs. */
    public static final int[][] RANGES = {{0x0600, 0x06FF}, {0x0750, 0x077F}, {0x08A0, 0x08FF}, {0x0900, 0x0D7F}};

    /**
     * How far along from an Arabic letter's medial form to its final form its
     * measured width sits (v2.166.0). Measured, not chosen: over 34 words of
     * running Arabic at 26px the shaped total was 2376px, the medial forms sum to
     * 2291, and 0.2 of the way to the final forms brings the estimate within a
     * word-average of 4.3px — words end on their widest form, once each.
     */
    static final double ARABIC_TOWARD_FINAL = 0.2;

    /**
     * The share of its plain width an Indic letter or spacing sign is measured
     * at (v2.166.0). Conjuncts, half forms and reordered signs make shaped
     * Devanagari far narrower than its characters: over 42 Hindi words the
     * shaped total was 1937px against 2690 for the non-mark characters, and 0.72
     * lands within a word-average of 6.2px.
     */
    static final double INDIC_SHARE = 0.72;

    /**
     * The width WebKit should MEASURE a glyph at (v2.166.0), or NaN to keep
     * the font's own. WebKit lays text out from one width per glyph with no
     * context, and for these scripts the font's width is the wrong one: an
     * Arabic letter's plain glyph is its isolated form, the widest it takes; a
     * combining mark advances although shaping sets it on its letter; Devanagari
     * shrinks into conjuncts. Measuring them close to their shaped size keeps the
     * box WebKit reserves close to the painted word, so the difference no longer
     * shows as a gap beside it (a shaped run is centred in its box, splitting
     * what remains).
     *
     * @param medial   a letter's advance shaped between two joining neighbours, or NaN
     * @param finalForm its advance shaped after a joining neighbour, or NaN
     * @param plain    the font's own advance for the plain glyph
     */
    public static double measuredWidth(int cp, java.util.function.IntToDoubleFunction medial,
            java.util.function.IntToDoubleFunction finalForm, java.util.function.IntToDoubleFunction plain) {
        if (!shapes(cp)) {
            return Double.NaN;
        }
        int type = Character.getType(cp);
        if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK) {
            return 0d;
        }
        if (rightToLeft(cp)) {
            double m = medial.applyAsDouble(cp);
            double f = finalForm.applyAsDouble(cp);
            return Double.isNaN(m) || Double.isNaN(f) ? Double.NaN : m + ARABIC_TOWARD_FINAL * (f - m);
        }
        double p = plain.applyAsDouble(cp);
        return Double.isNaN(p) ? Double.NaN : INDIC_SHARE * p;
    }

    /** The joining character a letter is shaped between to find its in-word form. */
    public static final char TATWEEL = '\u0640';

    /**
     * Rewrites every shapeable run in {@code glyphs}/{@code advances} in place.
     *
     * @param charFor the character a painted glyph stands for, or -1
     * @param shaper  shapes one logical string; null or an empty result leaves the run alone
     * @param blank   a glyph that draws nothing, used where shaping produced fewer glyphs
     * @return how far the call must move right to centre its shaped runs in the width WebKit measured
     */
    public static float reshape(int[] glyphs, float[] advances, IntUnaryOperator charFor,
            Function<String, Shaped> shaper, int blank) {
        if (glyphs == null || advances == null || glyphs.length != advances.length) {
            return 0f;
        }
        float slack = 0f;
        int i = 0;
        while (i < glyphs.length) {
            if (!shapes(charFor.applyAsInt(glyphs[i]))) {
                i++;
                continue;
            }
            int start = i;
            while (i < glyphs.length && shapes(charFor.applyAsInt(glyphs[i]))) {
                i++;
            }
            slack += segment(glyphs, advances, start, i, charFor, shaper, blank);
        }
        // centred in the box WebKit measured: what the estimate missed splits evenly
        return slack / 2f;
    }

    private static float segment(int[] glyphs, float[] advances, int start, int end,
            IntUnaryOperator charFor, Function<String, Shaped> shaper, int blank) {
        int n = end - start;
        if (n < 2) {
            return 0f; // one glyph has no neighbours to join or reorder with
        }
        boolean rtl = rightToLeft(charFor.applyAsInt(glyphs[start]));
        // a painted run is in visual order: a right-to-left script's characters
        // arrive reversed, a left-to-right one's do not
        StringBuilder logical = new StringBuilder(n);
        for (int k = 0; k < n; k++) {
            logical.appendCodePoint(charFor.applyAsInt(glyphs[rtl ? end - 1 - k : start + k]));
        }
        Shaped shaped = shaper.apply(logical.toString());
        if (shaped == null || shaped.glyphs().length == 0 || shaped.glyphs().length > n
                || shaped.advances().length != shaped.glyphs().length) {
            return 0f; // a shaper that needs more slots than WebKit gave is left unshaped
        }
        float original = 0f;
        for (int k = start; k < end; k++) {
            original += advances[k];
        }
        float used = 0f;
        for (float a : shaped.advances()) {
            used += a;
        }
        int count = shaped.glyphs().length;
        int pad = n - count;
        // unused slots draw nothing; they sit where reading starts, so the
        // shaped glyphs stay together at the run's reading edge
        int first = rtl ? start + pad : start;
        for (int k = 0; k < count; k++) {
            glyphs[first + k] = shaped.glyphs()[k];
            advances[first + k] = shaped.advances()[k];
        }
        for (int k = 0; k < pad; k++) {
            int at = rtl ? start + k : start + count + k;
            glyphs[at] = blank;
            advances[at] = 0f;
        }
        return original - used;
    }
}
