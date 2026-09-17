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

    /**
     * Right to left: Arabic, Arabic Supplement and Arabic Extended-A (joined),
     * and since v2.171.0 Syriac and N'Ko (joined) and Thaana (vowel marks).
     */
    static boolean rightToLeft(int cp) {
        return arabic(cp) || (cp >= 0x0700 && cp <= 0x074F) || (cp >= 0x0780 && cp <= 0x07FF);
    }

    /** The Arabic blocks, measured between their medial and final forms. */
    static boolean arabic(int cp) {
        return (cp >= 0x0600 && cp <= 0x06FF) || (cp >= 0x0750 && cp <= 0x077F) || (cp >= 0x08A0 && cp <= 0x08FF);
    }

    /**
     * Left to right, reordered, conjoined or stacked: Devanagari through
     * Malayalam, and since v2.171.0 Sinhala, Thai, Lao, Tibetan, Myanmar and
     * Khmer, which WebKit painted with broken conjuncts, stacks and vowels on
     * dotted circles while JavaFX's own text renders them.
     */
    static boolean leftToRight(int cp) {
        return (cp >= 0x0900 && cp <= 0x0FFF) || (cp >= 0x1000 && cp <= 0x109F) || (cp >= 0x1780 && cp <= 0x17FF);
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
    public static final int[][] RANGES = {{0x0600, 0x06FF}, {0x0750, 0x077F}, {0x08A0, 0x08FF}, {0x0900, 0x0D7F},
        // since v2.171.0: Syriac, Thaana and N'Ko; Sinhala through Tibetan; Myanmar; Khmer
        {0x0700, 0x074F}, {0x0780, 0x07FF}, {0x0D80, 0x0FFF}, {0x1000, 0x109F}, {0x1780, 0x17FF},
        // since v2.170.0 also the letters a combining mark sits on, and the marks themselves
        {0x0041, 0x024F}, {0x0300, 0x036F}, {0x0370, 0x03FF}, {0x0400, 0x052F}, {0x1AB0, 0x1AFF},
        {0x1DC0, 0x1DFF}, {0x1E00, 0x1FFF}, {0x20D0, 0x20FF}, {0xFE20, 0xFE2F}};

    /**
     * A combining mark from the general combining blocks (v2.170.0): the
     * accents Vietnamese, and any language written in decomposed form, puts
     * after its letter ({@code e} + U+0302 + U+0301 for {@code ế}). WebKit's simple
     * path paints such a mark as a character of its own, beside the letter and
     * taking space; shaped with its letter it becomes the letter's precomposed
     * glyph. The marks of the scripts above belong to their own blocks.
     */
    public static boolean combining(int cp) {
        boolean block = (cp >= 0x0300 && cp <= 0x036F) || (cp >= 0x1AB0 && cp <= 0x1AFF)
                || (cp >= 0x1DC0 && cp <= 0x1DFF) || (cp >= 0x20D0 && cp <= 0x20FF) || (cp >= 0xFE20 && cp <= 0xFE2F);
        int type = block ? Character.getType(cp) : -1;
        return type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK;
    }

    /** A letter outside the shaped scripts that combining marks can follow. */
    static boolean clusterBase(int cp) {
        return cp >= 0 && !shapes(cp) && Character.isLetter(cp);
    }

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
     * at, per script (v2.169.0): {first code point of the block, share}. The
     * plain width is the advance of the first glyph JavaFX gives the lone
     * character, the measure the bridge takes. v2.166.0 chose one share, 0.72,
     * for every script against a different measure (a lone character's laid-out
     * width, which for a vowel sign counts the dotted circle drawn in front of
     * it); measured the way the product measures, it left most words short in
     * every script but Oriya, and Tamil, Telugu, Kannada and Malayalam phrases
     * painted over the English word after them.
     *
     * <p>Measured in the Browser itself, from WebKit's own fonts: each word's
     * measured and shaped widths logged while running prose and short
     * interface phrases rendered, 25-35 words a script at 26px. Chosen as for
     * Arabic: among the shares within 1px of the best word-average, the one that
     * leaves the fewest words short, since a short word paints over its
     * neighbour and a long one leaves a gap after it. Word-average misses:
     * Devanagari 5.0px, Bengali 7.5, Gurmukhi 6.7, Gujarati 6.4, Oriya 8.1, Tamil
     * 7.2, Telugu 10.4, Kannada 7.3, Malayalam 11.0. Words full of conjuncts (a
     * Hindi {@code प्रोजेक्ट}) come out narrower than prose, so a phrase of them
     * can sit a little apart from the text after it.
     */
    static final double[][] INDIC_SHARES = {
        {0x0900, 0.84}, // Devanagari
        {0x0980, 0.84}, // Bengali
        {0x0A00, 0.90}, // Gurmukhi
        {0x0A80, 0.82}, // Gujarati
        {0x0B00, 0.76}, // Oriya
        {0x0B80, 0.78}, // Tamil
        {0x0C00, 0.98}, // Telugu
        {0x0C80, 0.82}, // Kannada
        {0x0D00, 0.78}, // Malayalam
        {0x0D80, 1.00}, // Sinhala
        {0x0E00, 1.00}, // Thai
        {0x0E80, 1.00}, // Lao
        {0x0F00, 1.00}, // Tibetan
        {0x1000, 1.00}, // Myanmar
        {0x1780, 1.00}, // Khmer
    };

    /** Syriac, Thaana and N'Ko's share of their plain width (v2.171.0); their fonts carry no Arabic tatweel. */
    static final double[][] RTL_SHARES = {
        {0x0700, 1.00}, // Syriac
        {0x0780, 1.00}, // Thaana
        {0x07C0, 1.00}, // N'Ko
    };

    /** The measured share for the Indic block {@code cp} falls in. */
    static double indicShare(int cp) {
        return shareIn(INDIC_SHARES, cp);
    }

    /** The measured share for the Syriac, Thaana or N'Ko block {@code cp} falls in. */
    static double rtlShare(int cp) {
        return shareIn(RTL_SHARES, cp);
    }

    private static double shareIn(double[][] table, int cp) {
        double share = table[0][1];
        for (double[] block : table) {
            if (cp >= block[0]) {
                share = block[1];
            }
        }
        return share;
    }

    /**
     * {@link #ARABIC_TOWARD_FINAL} for a Nastaliq font (v2.167.0), whose words
     * climb and descend instead of running along a line. Over 42 Urdu words in
     * Noto Nastaliq Urdu at 26px the shaped total was 1654px, the medial forms
     * 1113 and the final 2329. Blends from 0.25 to 0.5 all land within a
     * word-average of 9-10px, the per-letter estimate's ceiling for a script
     * this contextual; at 0.25, 27 words came out more than 3px short, at 0.5,
     * 17. Short paints over the neighbour, so 0.5.
     */
    static final double NASTALIQ_TOWARD_FINAL = 0.5;

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
        return measuredWidth(cp, medial, finalForm, plain, false);
    }

    /**
     * As {@link #measuredWidth(int, java.util.function.IntToDoubleFunction,
     * java.util.function.IntToDoubleFunction, java.util.function.IntToDoubleFunction)},
     * for a font whose Arabic letters leave the baseline when joined
     * ({@code stacking}, a Nastaliq face) or keep to it (a Naskh face).
     */
    public static double measuredWidth(int cp, java.util.function.IntToDoubleFunction medial,
            java.util.function.IntToDoubleFunction finalForm, java.util.function.IntToDoubleFunction plain,
            boolean stacking) {
        if (combining(cp)) {
            return 0d; // shaped into its letter's glyph, it takes no space of its own
        }
        if (!shapes(cp)) {
            return Double.NaN;
        }
        int type = Character.getType(cp);
        if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK) {
            return 0d;
        }
        if (arabic(cp)) {
            double m = medial.applyAsDouble(cp);
            double f = finalForm.applyAsDouble(cp);
            double k = stacking ? NASTALIQ_TOWARD_FINAL : ARABIC_TOWARD_FINAL;
            return Double.isNaN(m) || Double.isNaN(f) ? Double.NaN : m + k * (f - m);
        }
        double p = plain.applyAsDouble(cp);
        return Double.isNaN(p) ? Double.NaN : (rightToLeft(cp) ? rtlShare(cp) : indicShare(cp)) * p;
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
     * A whole painted run laid out anew: its glyphs, where each is drawn along
     * the run and how far off the baseline, and the run's width, all measured
     * from its first glyph; {@code origin} is how far that first glyph sits from
     * where WebKit put the run. {@code changed} is false when nothing in it was
     * shaped. JavaFX draws a positioned run from its first position, so a run
     * whose first glyph started left of zero was painted shifted right by that
     * much (v2.168.0): the paint call moves instead.
     */
    public record Laid(int[] glyphs, float[] xs, float[] rises, float width, float origin, boolean changed) {
    }

    /**
     * How much of its own width a space may give up or take on (v2.168.0) so
     * a paint call's words keep its measured width: half.
     */
    static final float SPACE_GIVES = 0.5f;

    /** How much of its width a space in an Indic run may give up (v2.169.0); it may still take on half. */
    static final float INDIC_SPACE_GIVES = 0.25f;

    /**
     * Lays a painted run out for a glyph list built from positions rather than
     * advances (v2.167.0), so a shaped segment may take more glyphs than it had
     * characters (a Nastaliq letter and its dots are separate glyphs) and its
     * glyphs may leave the baseline (vowel marks, Nastaliq's descending words).
     *
     * <p>The words are laid end to end, and the difference between their shaped
     * widths and the widths WebKit measured is absorbed by the spaces in the run
     * (v2.168.0): each space gives up, or takes on, up to half its width in
     * proportion, and only what the spaces cannot absorb moves the run's far
     * edge (a right-to-left run keeps its right edge, an Indic run its left).
     * Spaces at the kept edge are left as measured: a Pashto line painted its
     * {@code npm} against the word beside it when that space gave up half.
     * In an Indic run a space gives up at most a quarter of itself (v2.169.0)
     * while it may still take on half: an Indic word's estimate misses by more
     * (5-11px a word at 26px against Arabic script's 3.5-4.3), and half a space
     * each squeezed a Hindi heading and a Kannada phrase together in the
     * pictures, while spreading a phrase that came out narrow across its spaces
     * closed the gap it had left after its last word. v2.168.0 kept Indic runs
     * out of this entirely and let every miss fall past the run's end.
     * Until then all of it moved the far edge, so over a long right-to-left
     * stretch the words' few pixels each added up to a whole space and a Sindhi
     * line painted {@code ۽} against the {@code npm} beside it. Anchoring each
     * word at its own edge was tried first: every word measured short then ate
     * the space beside it. A lone letter is shaped too, so it takes its real
     * isolated width into the sum.
     *
     * @param space the glyph a space paints as
     */
    public static Laid layout(int[] glyphs, float[] advances, IntUnaryOperator charFor,
            Function<String, Shaped> shaper, int space) {
        if (glyphs == null || advances == null || glyphs.length != advances.length) {
            return null;
        }
        int[] outG = new int[glyphs.length];
        float[] outW = new float[glyphs.length]; // each output glyph's advance, spaces as measured
        float[] outR = new float[glyphs.length];
        boolean[] isSpace = new boolean[glyphs.length];
        int size = 0;
        float measured = 0f;
        float painted = 0f;
        float spaces = 0f;
        boolean rtl = false;
        boolean changed = false;
        int i = 0;
        while (i < glyphs.length) {
            int start = i;
            int first = charFor.applyAsInt(glyphs[i]);
            boolean shapeable = shapes(first);
            boolean cluster = !shapeable && clusterBase(first) && i + 1 < glyphs.length
                    && combining(charFor.applyAsInt(glyphs[i + 1]));
            if (cluster) {
                i++;
                while (i < glyphs.length && combining(charFor.applyAsInt(glyphs[i]))) {
                    i++;
                }
            } else if (!shapeable) {
                i++;
            } else {
                while (i < glyphs.length && shapes(charFor.applyAsInt(glyphs[i]))) {
                    i++;
                }
            }
            Shaped shaped = null;
            if (cluster) {
                // a letter and its marks, in reading order: the shaper composes them
                shaped = usable(shaper.apply(logical(glyphs, start, i, charFor, false)));
            } else if (shapeable) {
                boolean segmentRtl = rightToLeft(charFor.applyAsInt(glyphs[start]));
                shaped = usable(shaper.apply(logical(glyphs, start, i, charFor, segmentRtl)));
                if (shaped != null && !changed) {
                    rtl = segmentRtl; // WebKit paints one direction per call
                }
            }
            int need = size + (shaped == null ? i - start : shaped.glyphs().length);
            if (need > outG.length) {
                int grown = Math.max(need, outG.length * 2);
                outG = java.util.Arrays.copyOf(outG, grown);
                outW = java.util.Arrays.copyOf(outW, grown);
                outR = java.util.Arrays.copyOf(outR, grown);
                isSpace = java.util.Arrays.copyOf(isSpace, grown);
            }
            for (int k = start; k < i; k++) {
                measured += advances[k];
            }
            if (shaped == null) {
                for (int k = start; k < i; k++) {
                    outG[size] = glyphs[k];
                    outW[size] = advances[k];
                    isSpace[size] = glyphs[k] == space && !shapeable;
                    if (isSpace[size]) {
                        spaces += advances[k];
                    }
                    painted += advances[k];
                    size++;
                }
            } else {
                { float o = 0f, u = 0f; for (int k = start; k < i; k++) o += advances[k]; for (float q : shaped.advances()) u += q;
                  System.err.printf("CAL %04X %.3f %.3f%n", charFor.applyAsInt(glyphs[start]), o, u); }
                for (int k = 0; k < shaped.glyphs().length; k++) {
                    outG[size] = shaped.glyphs()[k];
                    outW[size] = shaped.advances()[k];
                    outR[size] = shaped.rises()[k];
                    painted += shaped.advances()[k];
                    size++;
                }
                changed = true;
            }
        }
        // spaces at the edge the run keeps are its neighbour's too (the space
        // between a right-to-left word and the npm beside it): they stay as measured
        if (rtl) {
            for (int k = size - 1; k >= 0 && isSpace[k]; k--) {
                isSpace[k] = false;
                spaces -= outW[k];
            }
        } else {
            for (int k = 0; k < size && isSpace[k]; k++) {
                isSpace[k] = false;
                spaces -= outW[k];
            }
        }
        float excess = painted - measured; // positive: the words came out wider than WebKit's box
        float gives = rtl ? SPACE_GIVES : INDIC_SPACE_GIVES;
        float absorbed = spaces == 0f ? 0f
                : Math.max(-SPACE_GIVES * spaces, Math.min(gives * spaces, excess));
        float[] outX = new float[size];
        float x = rtl ? -(excess - absorbed) : 0f;
        for (int k = 0; k < size; k++) {
            outX[k] = x;
            float w = outW[k];
            if (isSpace[k] && spaces != 0f) {
                w -= absorbed * (outW[k] / spaces);
            }
            x += w;
        }
        float origin = size == 0 ? 0f : outX[0];
        for (int k = 0; k < size; k++) {
            outX[k] -= origin;
        }
        return new Laid(java.util.Arrays.copyOf(outG, size), outX, java.util.Arrays.copyOf(outR, size),
                measured - origin, origin, changed);
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
     * The x/y pairs a positioned glyph run is drawn from (v2.167.0): each
     * glyph's x and offset, and one closing pair whose x is the run's width.
     */
    public static float[] positions(float[] xs, float[] rises, float width) {
        float[] pos = new float[2 * (xs.length + 1)];
        for (int k = 0; k < xs.length; k++) {
            pos[2 * k] = xs[k];
            pos[2 * k + 1] = rises[k];
        }
        pos[2 * xs.length] = width;
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
