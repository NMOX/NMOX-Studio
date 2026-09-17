package org.nmox.studio.ui.browser.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shaping core behind the Browser's complex scripts (v2.165.0, ledger 99).
 * A painted run arrives as one plain glyph per character in VISUAL order; the
 * fake font here maps glyph = code point + 10000 so every rule can be read off
 * the arrays directly.
 */
class ComplexScriptsTest {

    private static final int BLANK = 3;
    private static final IntUnaryOperator CHAR_FOR = g -> g >= 10000 ? g - 10000 : (g == 32 ? 32 : -1);

    private static int[] glyphs(String visual) {
        return visual.codePoints().map(cp -> cp == ' ' ? 32 : cp + 10000).toArray();
    }

    private static float[] advances(int n, float each) {
        float[] a = new float[n];
        java.util.Arrays.fill(a, each);
        return a;
    }

    @Test
    @DisplayName("an Arabic run reaches the shaper in logical order and keeps its right edge")
    void rightToLeftRunIsReversedShapedAndKeepsItsRightEdge() {
        // visual order of "بيت" is ت ي ب
        String visual = new StringBuilder("بيت").reverse().toString();
        int[] g = glyphs(visual);
        float[] a = advances(3, 10f);
        List<String> asked = new ArrayList<>();
        Function<String, ComplexScripts.Shaped> shaper = text -> {
            asked.add(text);
            return new ComplexScripts.Shaped(new int[]{7, 8}, new float[]{6f, 9f}); // a ligature: two glyphs
        };
        float slack = ComplexScripts.reshape(g, a, CHAR_FOR, shaper, BLANK);
        assertThat(asked).containsExactly("بيت");
        assertThat(g).containsExactly(BLANK, 7, 8);        // the blank sits where reading starts... on the left
        assertThat(a).containsExactly(0f, 6f, 9f);
        assertThat(slack).isEqualTo(15f);                  // 30 measured, 15 painted: moved right by all of it
    }

    @Test
    @DisplayName("a Devanagari run is not reversed, keeps its left edge and moves nothing")
    void leftToRightRunKeepsOrderAndLeftEdge() {
        int[] g = glyphs("हिन्दी");
        float[] a = advances(g.length, 10f);
        List<String> asked = new ArrayList<>();
        float slack = ComplexScripts.reshape(g, a, CHAR_FOR, text -> {
            asked.add(text);
            return new ComplexScripts.Shaped(new int[]{1, 2, 4}, new float[]{5f, 5f, 5f});
        }, BLANK);
        assertThat(asked).containsExactly("हिन्दी");
        assertThat(g).startsWith(1, 2, 4).endsWith(BLANK, BLANK, BLANK);
        assertThat(slack).isZero();
    }

    @Test
    @DisplayName("words are shaped one at a time and every Arabic word's difference adds up")
    void spacesSeparateWordsAndSlackSums() {
        // logical "با تا" -> visual: "ات اب"
        String visual = new StringBuilder("با تا").reverse().toString();
        int[] g = glyphs(visual);
        float[] a = advances(g.length, 10f);
        List<String> asked = new ArrayList<>();
        float slack = ComplexScripts.reshape(g, a, CHAR_FOR, text -> {
            asked.add(text);
            return new ComplexScripts.Shaped(new int[]{50, 51}, new float[]{8f, 8f});
        }, BLANK);
        assertThat(asked).containsExactly("تا", "با"); // visual left-to-right: the second word first
        assertThat(g[2]).isEqualTo(32);                  // the space is untouched
        assertThat(slack).isEqualTo(8f);                 // (20-16) twice
    }

    @Test
    @DisplayName("text outside the shaped scripts is never touched and never shaped")
    void latinAndHebrewAreLeftAlone() {
        int[] g = {65, 66, 32, 10000 + 0x05D0, 10000 + 0x05D1};
        int[] before = g.clone();
        float[] a = advances(g.length, 10f);
        float slack = ComplexScripts.reshape(g, a, CHAR_FOR, text -> {
            throw new AssertionError("nothing here needs shaping: " + text);
        }, BLANK);
        assertThat(g).containsExactly(before);
        assertThat(slack).isZero();
    }

    @Test
    @DisplayName("a shaper that needs more glyphs than WebKit gave, or none, leaves the run as it was")
    void unusableShapesLeaveTheRun() {
        int[] g = glyphs("تب");
        int[] before = g.clone();
        float[] a = advances(2, 10f);
        ComplexScripts.reshape(g, a, CHAR_FOR, t -> new ComplexScripts.Shaped(new int[]{1, 2, 3}, new float[]{1, 1, 1}), BLANK);
        assertThat(g).containsExactly(before);
        ComplexScripts.reshape(g, a, CHAR_FOR, t -> null, BLANK);
        assertThat(g).containsExactly(before);
        assertThat(a).containsExactly(10f, 10f);
    }

    @Test
    @DisplayName("a lone letter has nothing to join with and is not sent to the shaper")
    void singleGlyphIsNotShaped() {
        int[] g = glyphs("ب");
        float slack = ComplexScripts.reshape(g, advances(1, 10f), CHAR_FOR, t -> {
            throw new AssertionError("one letter needs no shaping");
        }, BLANK);
        assertThat(slack).isZero();
    }

    @Test
    @DisplayName("WebKit measures a mark at zero, an Arabic letter between its medial and final forms, an Indic letter at its share")
    void measuredWidths() {
        java.util.function.IntToDoubleFunction medial = cp -> 10d;
        java.util.function.IntToDoubleFunction fin = cp -> 20d;
        java.util.function.IntToDoubleFunction plain = cp -> 50d;
        assertThat(ComplexScripts.measuredWidth(0x0628, medial, fin, plain)).isEqualTo(12.5d); // beh: 10 + 0.25 * 10
        assertThat(ComplexScripts.measuredWidth(0x064E, medial, fin, plain)).isZero();        // fatha, a mark
        assertThat(ComplexScripts.measuredWidth(0x094D, medial, fin, plain)).isZero();        // virama, a mark
        assertThat(ComplexScripts.measuredWidth(0x0915, medial, fin, plain)).isEqualTo(42d); // ka: 0.84 * 50
        assertThat(ComplexScripts.measuredWidth('A', medial, fin, plain)).isNaN();           // the font's own
        assertThat(ComplexScripts.measuredWidth(0x05D0, medial, fin, plain)).isNaN();        // Hebrew is not shaped
        assertThat(ComplexScripts.measuredWidth(0x0628, cp -> Double.NaN, fin, plain)).isNaN();
        assertThat(ComplexScripts.measuredWidth(0x0915, medial, fin, cp -> Double.NaN)).isNaN();
        assertThat(ComplexScripts.measuredWidth(0x0628, medial, fin, plain, true)).isEqualTo(15d); // Nastaliq: 10 + 0.5 * 10
        assertThat(ComplexScripts.measuredWidth(0x0915, medial, fin, plain, true)).isEqualTo(42d); // Indic ignores it
    }

    @Test
    @DisplayName("mismatched arrays are refused before anything is read")
    void mismatchedArraysAreRefused() {
        assertThat(ComplexScripts.reshape(new int[2], new float[3], CHAR_FOR, t -> null, BLANK)).isZero();
        assertThat(ComplexScripts.reshape(null, null, CHAR_FOR, t -> null, BLANK)).isZero();
    }

    @Test
    @DisplayName("digits and punctuation stay where WebKit put them; only the letters around them are reversed and shaped")
    void numbersAndPunctuationAreNotReversed() {
        // logical "ص ١٢٣، بت": WebKit paints the number left to right inside the right-to-left line,
        // so the visual array here is: letters "بت" reversed, the comma, then the digits in reading order
        String visual = "تب" + "،" + "١٢٣";
        int[] g = glyphs(visual);
        int[] digitsBefore = java.util.Arrays.copyOfRange(g, 2, 6);
        float[] a = advances(g.length, 10f);
        List<String> asked = new ArrayList<>();
        ComplexScripts.reshape(g, a, CHAR_FOR, text -> {
            asked.add(text);
            return new ComplexScripts.Shaped(new int[]{70, 71}, new float[]{9f, 9f});
        }, BLANK);
        assertThat(asked).containsExactly("بت");
        assertThat(java.util.Arrays.copyOfRange(g, 2, 6)).containsExactly(digitsBefore);
        assertThat(java.util.Arrays.copyOfRange(a, 2, 6)).containsExactly(10f, 10f, 10f, 10f);
    }

    @Test
    @DisplayName("Arabic-Indic, Persian and Devanagari digits and the danda keep the font's own width")
    void digitsAndPunctuationAreMeasuredByTheFont() {
        java.util.function.IntToDoubleFunction any = cp -> 10d;
        for (int cp : new int[]{0x0661, 0x06F4, 0x0967, 0x0964, 0x060C, 0x066B}) {
            assertThat(ComplexScripts.measuredWidth(cp, any, any, any)).as("U+%04X", cp).isNaN();
        }
        assertThat(ComplexScripts.shapes(0x06CC)).isTrue();  // Farsi yeh
        assertThat(ComplexScripts.shapes(0x06D2)).isTrue();  // Urdu yeh barree
        assertThat(ComplexScripts.shapes(0x0640)).isTrue();  // tatweel joins
        assertThat(ComplexScripts.shapes(0x093E)).isTrue();  // a spacing vowel sign
    }

    @Test
    @DisplayName("the in-place rewrite leaves alone a run whose shaped glyphs leave the baseline or outnumber its slots")
    void inPlaceRewriteRefusesWhatItCannotHold() {
        int[] g = glyphs("\u064Eب"); // visual: fatha, beh
        int[] before = g.clone();
        float[] a = advances(2, 10f);
        ComplexScripts.reshape(g, a, CHAR_FOR, t -> new ComplexScripts.Shaped(
                new int[]{70, 71}, new float[]{9f, 0f}, new float[]{0f, -6f}), BLANK);
        assertThat(g).containsExactly(before);
        assertThat(a).containsExactly(10f, 10f);
    }

    @Test
    @DisplayName("a laid-out run takes as many glyphs as shaping needs, with their offsets, and copies the rest as painted")
    void layoutTakesAnyGlyphCountAndOffsets() {
        // visual: "A", then Urdu "ٹا" reversed, then "B": the letters shape into three glyphs, one lifted
        int[] g = glyphs("A" + "اٹ" + "B");
        float[] a = {5f, 10f, 10f, 6f};
        ComplexScripts.Laid laid = ComplexScripts.layout(g, a, CHAR_FOR, text -> {
            assertThat(text).isEqualTo("ٹا");
            return new ComplexScripts.Shaped(new int[]{80, 81, 82}, new float[]{4f, 4f, 4f}, new float[]{0f, -9f, 3f});
        }, 32);
        assertThat(laid.changed()).isTrue();
        assertThat(laid.glyphs()).containsExactly(g[0], 80, 81, 82, g[3]);
        // no spaces to absorb the 8px the word came out narrow: the run keeps its right edge
        assertThat(laid.origin()).isEqualTo(8f);
        assertThat(laid.xs()).containsExactly(0f, 5f, 9f, 13f, 17f);
        assertThat(laid.rises()).containsExactly(0f, 0f, -9f, 3f, 0f);
        assertThat(laid.width()).isEqualTo(23f);
        assertThat(g).containsExactly(glyphs("A" + "اٹ" + "B")); // WebKit's own arrays are untouched

        ComplexScripts.Laid refused = ComplexScripts.layout(g, a, CHAR_FOR, text -> null, 32);
        assertThat(refused.changed()).isFalse();
        assertThat(refused.glyphs()).containsExactly(g);
        assertThat(refused.xs()).containsExactly(0f, 5f, 15f, 25f);
        assertThat(ComplexScripts.layout(new int[1], new float[2], CHAR_FOR, text -> null, 32)).isNull();
    }

    @Test
    @DisplayName("the spaces absorb what the words miss, up to half each, and only the rest moves the run's edge (a Sindhi line lost its spaces)")
    void spacesAbsorbTheWordsMiss() {
        // visual: " " + "دت" + " " + "بت": two words each painted 4 wider than measured, two 6px spaces
        int[] g = glyphs(" " + "دت" + " " + "بت");
        float[] a = {6f, 10f, 10f, 6f, 10f, 10f};
        ComplexScripts.Laid laid = ComplexScripts.layout(g, a, CHAR_FOR,
                text -> new ComplexScripts.Shaped(new int[]{90, 91}, new float[]{12f, 12f}), 32);
        // 8px too wide; the spaces give up half their 12px (3 each) and only 2 spill past the left edge
        assertThat(laid.origin()).isEqualTo(-2f); // the run starts 2 left of where WebKit put it
        assertThat(laid.xs()).containsExactly(0f, 3f, 15f, 27f, 30f, 42f);
        assertThat(laid.width()).isEqualTo(54f);

        float[] tight = {6f, 5f, 5f, 6f, 5f, 5f};
        ComplexScripts.Laid over = ComplexScripts.layout(g, tight, CHAR_FOR,
                text -> new ComplexScripts.Shaped(new int[]{90, 91}, new float[]{12f, 12f}), 32);
        // 28px too wide: the spaces give up half their 12 (6), the remaining 22 spill past the left edge
        assertThat(over.origin()).isEqualTo(-22f);
        assertThat(over.xs()).containsExactly(0f, 3f, 15f, 27f, 30f, 42f);
        assertThat(over.origin() + over.xs()[over.xs().length - 1] + 12f).isEqualTo(32f); // the right edge is WebKit's
    }

    @Test
    @DisplayName("a space at the edge the run keeps stays as measured, and an Indic run's spaces give up less than they take")
    void keptEdgeSpacesStayMeasured() {
        // visual: "بت" + " " — the trailing space sits at a right-to-left run's kept right edge
        int[] g = glyphs("بت" + " ");
        float[] a = {10f, 10f, 6f};
        ComplexScripts.Laid laid = ComplexScripts.layout(g, a, CHAR_FOR,
                text -> new ComplexScripts.Shaped(new int[]{90, 91}, new float[]{12f, 12f}), 32);
        assertThat(laid.origin()).isEqualTo(-4f); // the space keeps 20..26 of WebKit's box; the 4 spill left
        assertThat(laid.xs()).containsExactly(0f, 12f, 24f);
        assertThat(laid.width()).isEqualTo(30f);
        // an Indic run keeps its left edge, so a leading space is the one that stays
        int[] h = glyphs(" " + "कम");
        ComplexScripts.Laid indic = ComplexScripts.layout(h, new float[]{6f, 10f, 10f}, CHAR_FOR,
                text -> new ComplexScripts.Shaped(new int[]{90, 91}, new float[]{12f, 12f}), 32);
        assertThat(indic.origin()).isZero();
        assertThat(indic.xs()).containsExactly(0f, 6f, 18f);
        // an Indic run's space gives up at most a quarter of itself when the words come out wide...
        int[] words = glyphs("कम" + " " + "कम");
        float[] measured = {10f, 10f, 6f, 10f, 10f};
        ComplexScripts.Laid wide = ComplexScripts.layout(words, measured, CHAR_FOR,
                text -> new ComplexScripts.Shaped(new int[]{90, 91}, new float[]{12f, 12f}), 32);
        assertThat(wide.xs()).containsExactly(0f, 12f, 24f, 28.5f, 40.5f); // 8 too wide: the space gives 1.5
        // ...and takes on up to half when they come out narrow
        ComplexScripts.Laid narrow = ComplexScripts.layout(words, measured, CHAR_FOR,
                text -> new ComplexScripts.Shaped(new int[]{90, 91}, new float[]{8f, 8f}), 32);
        assertThat(narrow.xs()).containsExactly(0f, 8f, 16f, 25f, 33f); // 8 too narrow: the space takes 3
    }

    @Test
    @DisplayName("a lone letter is shaped too, and its real width joins the sum")
    void loneLetterIsShaped() {
        int[] g = glyphs("ڭ" + " " + "A");
        float[] a = {8f, 6f, 5f};
        java.util.List<String> asked = new java.util.ArrayList<>();
        ComplexScripts.Laid laid = ComplexScripts.layout(g, a, CHAR_FOR, text -> {
            asked.add(text);
            return new ComplexScripts.Shaped(new int[]{95}, new float[]{12f}); // its isolated form is wider
        }, 32);
        assertThat(asked).containsExactly("ڭ");
        assertThat(laid.origin()).isEqualTo(-1f); // 4 too wide: the space gives 3 of its 6, 1 spills
        assertThat(laid.xs()).containsExactly(0f, 12f, 15f);
    }

    @Test
    @DisplayName("positions are each glyph's x with its offset, closed by the run's width")
    void positionsFromPlacesAndOffsets() {
        assertThat(ComplexScripts.positions(new float[]{-2f, 4f}, new float[]{0f, -2f}, 9f))
                .containsExactly(-2f, 0f, 4f, -2f, 9f, 0f);
        assertThat(ComplexScripts.positions(new float[0], new float[0], 0f)).containsExactly(0f, 0f);
    }

    @Test
    @DisplayName("a kasra below its letter says which way the layout measures y")
    void kasraGivesTheLayoutsDirection() {
        assertThat(ComplexScripts.downwardFrom(6.2f)).isEqualTo(1f);
        assertThat(ComplexScripts.downwardFrom(-7.7f)).isEqualTo(-1f); // JavaFX 26 on macOS
        assertThat(ComplexScripts.downwardFrom(0f)).isZero();
    }

    @Test
    @DisplayName("each Indic script is measured at its own share, from the first to the last code point of its block")
    void indicSharesPerScript() {
        java.util.function.IntToDoubleFunction none = cp -> Double.NaN;
        java.util.function.IntToDoubleFunction plain = cp -> 100d;
        int[][] firstAndLast = {{0x0915, 0x097F}, {0x0995, 0x09FF}, {0x0A15, 0x0A7F}, {0x0A95, 0x0AFF},
            {0x0B15, 0x0B7F}, {0x0B95, 0x0BFF}, {0x0C15, 0x0C7F}, {0x0C95, 0x0CFF}, {0x0D15, 0x0D7F}};
        double[] share = {84, 84, 90, 82, 76, 78, 98, 82, 78};
        for (int k = 0; k < firstAndLast.length; k++) {
            int letter = firstAndLast[k][0];
            assertThat(ComplexScripts.measuredWidth(letter, none, none, plain)).as("U+%04X", letter)
                    .isCloseTo(share[k], org.assertj.core.data.Offset.offset(1e-9));
            assertThat(ComplexScripts.indicShare(firstAndLast[k][1])).as("end of block U+%04X", firstAndLast[k][1])
                    .isCloseTo(share[k] / 100d, org.assertj.core.data.Offset.offset(1e-9));
        }
    }

    @Test
    @DisplayName("a letter and its combining marks shape as one cluster, in reading order; the rest of the run is untouched (Vietnamese written decomposed)")
    void combiningMarksShapeWithTheirLetter() {
        // "Tiê̂́" style: T, i, e + U+0302 + U+0301, n — the marks ride on e
        int[] g = glyphs("Tie\u0302\u0301n");
        float[] a = {10f, 5f, 11f, 0f, 0f, 12f};
        java.util.List<String> asked = new java.util.ArrayList<>();
        ComplexScripts.Laid laid = ComplexScripts.layout(g, a, CHAR_FOR, text -> {
            asked.add(text);
            return new ComplexScripts.Shaped(new int[]{77}, new float[]{11f}); // the precomposed glyph
        }, 32);
        assertThat(asked).containsExactly("e\u0302\u0301");
        assertThat(laid.changed()).isTrue();
        assertThat(laid.glyphs()).containsExactly(g[0], g[1], 77, g[5]);
        assertThat(laid.xs()).containsExactly(0f, 10f, 15f, 26f);
        // no marks: nothing to shape, nothing changes
        int[] plain = glyphs("Tien");
        assertThat(ComplexScripts.layout(plain, new float[]{10f, 5f, 11f, 12f}, CHAR_FOR, text -> {
            throw new AssertionError("plain Latin is never shaped");
        }, 32).changed()).isFalse();
        // a mark with no letter before it is left as painted
        int[] stray = glyphs(" \u0301");
        assertThat(ComplexScripts.layout(stray, new float[]{6f, 0f}, CHAR_FOR, text -> {
            throw new AssertionError("a mark after a space has no letter to join");
        }, 32).changed()).isFalse();
    }

    @Test
    @DisplayName("combining marks from the general blocks measure zero; the letters under them keep the font's width")
    void combiningMarksMeasureZero() {
        java.util.function.IntToDoubleFunction any = cp -> 10d;
        for (int cp : new int[]{0x0300, 0x0301, 0x0302, 0x0303, 0x0309, 0x031B, 0x0323, 0x0342, 0x1DC4, 0x20D7, 0xFE20}) {
            assertThat(ComplexScripts.combining(cp)).as("U+%04X", cp).isTrue();
            assertThat(ComplexScripts.measuredWidth(cp, any, any, any)).as("U+%04X", cp).isZero();
        }
        for (int cp : new int[]{'e', 0x00E9, 0x1EBF, 0x0391, 0x0439, 0x0374, 0x0591}) {
            assertThat(ComplexScripts.combining(cp)).as("U+%04X", cp).isFalse();
            assertThat(ComplexScripts.measuredWidth(cp, any, any, any)).as("U+%04X", cp).isNaN();
        }
    }

    @Test
    @DisplayName("Sinhala, Thai, Tibetan, Myanmar and Khmer shape left to right and Syriac, Thaana and N'Ko right to left, each at its measured share; Lao is left as painted")
    void theV2171ScriptsShapeAtTheirShares() {
        java.util.function.IntToDoubleFunction none = cp -> Double.NaN;
        java.util.function.IntToDoubleFunction plain = cp -> 100d;
        int[] ltr = {0x0D9A, 0x0E01, 0x0F40, 0x1000, 0x1780};
        double[] ltrShare = {86, 100, 100, 96, 80};
        for (int k = 0; k < ltr.length; k++) {
            assertThat(ComplexScripts.leftToRight(ltr[k])).as("U+%04X", ltr[k]).isTrue();
            assertThat(ComplexScripts.rightToLeft(ltr[k])).as("U+%04X", ltr[k]).isFalse();
            assertThat(ComplexScripts.measuredWidth(ltr[k], none, none, plain)).as("U+%04X", ltr[k])
                    .isCloseTo(ltrShare[k], org.assertj.core.data.Offset.offset(1e-9));
        }
        int[] rtl = {0x0712, 0x0784, 0x07D3};
        double[] rtlShare = {90, 108, 118};
        for (int k = 0; k < rtl.length; k++) {
            assertThat(ComplexScripts.rightToLeft(rtl[k])).as("U+%04X", rtl[k]).isTrue();
            assertThat(ComplexScripts.arabic(rtl[k])).as("U+%04X", rtl[k]).isFalse();
            // measured from the plain width alone: these fonts have no medial and final pair to blend
            assertThat(ComplexScripts.measuredWidth(rtl[k], none, none, plain)).as("U+%04X", rtl[k])
                    .isCloseTo(rtlShare[k], org.assertj.core.data.Offset.offset(1e-9));
        }
        for (int cp = 0x0E80; cp <= 0x0EFF; cp++) {
            assertThat(ComplexScripts.shapes(cp)).as("Lao U+%04X", cp).isFalse();
            assertThat(ComplexScripts.measuredWidth(cp, plain, plain, plain)).as("Lao U+%04X", cp).isNaN();
        }
    }

    @Test
    @DisplayName("every code point the core shapes lies inside the ranges the glyph table is built from")
    void everyShapedCodePointIsInTheTable() {
        for (int cp = 0; cp <= 0xFFFF; cp++) {
            if (!ComplexScripts.shapes(cp) && !ComplexScripts.combining(cp)) {
                continue;
            }
            boolean inside = false;
            for (int[] range : ComplexScripts.RANGES) {
                inside |= cp >= range[0] && cp <= range[1];
            }
            assertThat(inside).as("U+%04X is shaped but no glyph table covers it", cp).isTrue();
        }
    }
}
