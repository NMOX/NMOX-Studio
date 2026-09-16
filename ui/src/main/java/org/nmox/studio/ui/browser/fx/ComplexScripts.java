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
 * ({@link #measuredWidth}), and each shaped run keeps the edge its script
 * reads from, so the few pixels the estimate misses fall where the run ends.
 * Pure: glyph lookup and shaping arrive as functions.
 */
public final class ComplexScripts {

    private ComplexScripts() {
    }

    /**
     * Shaped glyphs in visual order with their advances and their vertical
     * offsets from the baseline (screen direction: positive is down). Vowel
     * marks sit above or below their letters and Nastaliq steps each word down
     * its line; a Naskh letter's offset is zero.
     */
    public record Shaped(int[] glyphs, float[] advances, float[] rises) {
        /** A run whose every glyph sits on the baseline. */
        public Shaped(int[] glyphs, float[] advances) {
            this(glyphs, advances, new float[glyphs.length]);
        }
    }

    /** Arabic, Arabic Supplement and Arabic Extended-A: joined, right to left. */
    static boolean rightToLeft(int cp) {
        return (cp >= 0x0600 && cp <= 0x06FF) || (cp >= 0x0750 && cp <= 0x077F) || (cp >= 0x08A0 && cp <= 0x08FF);
    }

    /** Devanagari through Malayalam: reordered and conjoined, left to right. */
    static boolean leftToRight(int cp) {
        return cp >= 0x0900 && cp <= 0x0D7F;
    }

    /**
     * Every code point this core shapes: the letters and marks of those
     * scripts (v2.167.0). A digit or a punctuation mark never joins, and a
     * number inside a right-to-left line already arrives left to right, so
     * reversing it with the letters around it painted {@code ١٢٣} as
     * {@code ٣٢١}; they stay where WebKit put them, measured by the font.
     */
    public static boolean shapes(int cp) {
        return (rightToLeft(cp) || leftToRight(cp)) && letterOrMark(cp);
    }

    private static boolean letterOrMark(int cp) {
        int type = Character.getType(cp);
        return Character.isLetter(cp) || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK || type == Character.ENCLOSING_MARK;
    }

    /** The code point ranges a glyph lookup table needs, inclusive pairs. */
    public static final int[][] RANGES = {{0x0600, 0x06FF}, {0x0750, 0x077F}, {0x08A0, 0x08FF}, {0x0900, 0x0D7F}};

    /**
     * How far along from an Arabic letter's medial form to its final form its
     * measured width sits (v2.166.0). Measured, not chosen: over 34 words of
     * running Arabic at 26px the shaped total was 2376px and the medial forms
     * summed short. Every blend from 0.2 to 0.3 lands within a word-average of
     * 4.3px; they differ in which way they miss, and a word measured short
     * paints over its neighbour while one measured long leaves a sliver of
     * space. At 0.2, 19 words came out more than 3px short; at 0.25, 11.
     */
    static final double ARABIC_TOWARD_FINAL = 0.25;

    /**
     * The share of its plain width an Indic letter or spacing sign is measured
     * at (v2.166.0). Conjuncts, half forms and reordered signs make shaped
     * Devanagari far narrower than its characters: over 42 Hindi words the
     * shaped total was 1937px against 2690 for the non-mark characters, and 0.72
     * lands within a word-average of 6.5px. Raising it barely changes how many
     * words come out short (24 at 0.72 and at 0.75) while every word's error
     * grows, so the miss here is per word, not a bias to correct.
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
     * shows as a gap beside it.
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
     * @return how far the call must move right so its right-to-left runs keep their right edge
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
        return slack;
    }

    private static float segment(int[] glyphs, float[] advances, int start, int end,
            IntUnaryOperator charFor, Function<String, Shaped> shaper, int blank) {
        int n = end - start;
        if (n < 2) {
            return 0f; // one glyph has no neighbours to join or reorder with
        }
        boolean rtl = rightToLeft(charFor.applyAsInt(glyphs[start]));
        Shaped shaped = usable(shaper.apply(logical(glyphs, start, end, charFor, rtl)));
        if (shaped == null || shaped.glyphs().length > n || !onBaseline(shaped.rises())) {
            // more glyphs than WebKit gave slots, or glyphs off the line: an
            // in-place rewrite can hold neither, and plain paints better
            return 0f;
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
        // a run keeps the edge its script reads from: an Arabic run its right,
        // an Indic run its left, so what the width estimate missed falls where
        // the run ends (centring was tried first and pushed a Hindi heading past
        // its card's padding by half the shortfall)
        return rtl ? original - used : 0f;
    }

    /**
     * A painted run in visual order: a right-to-left script's characters
     * arrive reversed, a left-to-right one's do not.
     */
    private static String logical(int[] glyphs, int start, int end, IntUnaryOperator charFor, boolean rtl) {
        StringBuilder text = new StringBuilder(end - start);
        for (int k = 0; k < end - start; k++) {
            text.appendCodePoint(charFor.applyAsInt(glyphs[rtl ? end - 1 - k : start + k]));
        }
        return text.toString();
    }

    private static Shaped usable(Shaped shaped) {
        return shaped == null || shaped.glyphs().length == 0 || shaped.advances().length != shaped.glyphs().length
                || shaped.rises().length != shaped.glyphs().length ? null : shaped;
    }

    /**
     * A whole painted run laid out anew (v2.167.0): its glyphs, advances and
     * vertical offsets, as many as shaping needs, and how far the run moves
     * right. {@code changed} is false when nothing in it was shaped.
     */
    public record Laid(int[] glyphs, float[] advances, float[] rises, float slack, boolean changed) {
    }

    /**
     * Lays a painted run out for a glyph list built from positions rather than
     * advances, so a shaped segment may take more glyphs than it had characters
     * (a Nastaliq letter and its dots are separate glyphs) and its glyphs may
     * leave the baseline (vowel marks, Nastaliq's descending words). Segments
     * follow the same rules as {@link #reshape}; everything else is copied as
     * WebKit painted it.
     */
    public static Laid layout(int[] glyphs, float[] advances, IntUnaryOperator charFor,
            Function<String, Shaped> shaper) {
        if (glyphs == null || advances == null || glyphs.length != advances.length) {
            return null;
        }
        int[] outG = new int[glyphs.length];
        float[] outA = new float[glyphs.length];
        float[] outR = new float[glyphs.length];
        int size = 0;
        float slack = 0f;
        boolean changed = false;
        int i = 0;
        while (i < glyphs.length) {
            int start = i;
            if (!shapes(charFor.applyAsInt(glyphs[i]))) {
                i++;
            } else {
                while (i < glyphs.length && shapes(charFor.applyAsInt(glyphs[i]))) {
                    i++;
                }
            }
            Shaped shaped = null;
            boolean rtl = false;
            if (i - start >= 2) {
                rtl = rightToLeft(charFor.applyAsInt(glyphs[start]));
                shaped = usable(shaper.apply(logical(glyphs, start, i, charFor, rtl)));
            }
            int need = size + (shaped == null ? i - start : shaped.glyphs().length);
            if (need > outG.length) {
                int grown = Math.max(need, outG.length * 2);
                outG = java.util.Arrays.copyOf(outG, grown);
                outA = java.util.Arrays.copyOf(outA, grown);
                outR = java.util.Arrays.copyOf(outR, grown);
            }
            if (shaped == null) {
                System.arraycopy(glyphs, start, outG, size, i - start);
                System.arraycopy(advances, start, outA, size, i - start);
                size += i - start;
                continue;
            }
            float original = 0f;
            for (int k = start; k < i; k++) {
                original += advances[k];
            }
            float used = 0f;
            for (int k = 0; k < shaped.glyphs().length; k++) {
                outG[size] = shaped.glyphs()[k];
                outA[size] = shaped.advances()[k];
                outR[size] = shaped.rises()[k];
                used += shaped.advances()[k];
                size++;
            }
            slack += rtl ? original - used : 0f;
            changed = true;
        }
        return new Laid(java.util.Arrays.copyOf(outG, size), java.util.Arrays.copyOf(outA, size),
                java.util.Arrays.copyOf(outR, size), slack, changed);
    }

    /** Whether every offset is zero: the run paints along one line. */
    public static boolean onBaseline(float[] rises) {
        for (float r : rises) {
            if (r != 0f) {
                return false;
            }
        }
        return true;
    }

    /**
     * The x/y pairs a positioned glyph run is drawn from (v2.167.0): x the sum
     * of the advances before a glyph, y its offset, and one closing pair whose
     * x is the run's whole width.
     */
    public static float[] positions(float[] advances, float[] rises) {
        float[] pos = new float[2 * (advances.length + 1)];
        float x = 0f;
        for (int k = 0; k < advances.length; k++) {
            pos[2 * k] = x;
            pos[2 * k + 1] = rises[k];
            x += advances[k];
        }
        pos[2 * advances.length] = x;
        return pos;
    }

    /**
     * Which way a layout measures y, read from where it puts a kasra (a mark
     * below its letter in every Arabic font): +1 when the kasra's offset is
     * positive, so the layout already measures down as drawing does; -1 when
     * negative; 0 when the font placed it on the baseline and nothing is known.
     */
    public static float downwardFrom(float kasraOffset) {
        return Math.signum(kasraOffset);
    }
}
