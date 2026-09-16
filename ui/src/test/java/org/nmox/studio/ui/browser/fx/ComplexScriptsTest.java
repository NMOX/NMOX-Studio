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
        assertThat(ComplexScripts.measuredWidth(0x0915, medial, fin, plain)).isEqualTo(36d); // ka: 0.72 * 50
        assertThat(ComplexScripts.measuredWidth('A', medial, fin, plain)).isNaN();           // the font's own
        assertThat(ComplexScripts.measuredWidth(0x05D0, medial, fin, plain)).isNaN();        // Hebrew is not shaped
        assertThat(ComplexScripts.measuredWidth(0x0628, cp -> Double.NaN, fin, plain)).isNaN();
        assertThat(ComplexScripts.measuredWidth(0x0915, medial, fin, cp -> Double.NaN)).isNaN();
        assertThat(ComplexScripts.measuredWidth(0x0628, medial, fin, plain, true)).isEqualTo(15d); // Nastaliq: 10 + 0.5 * 10
        assertThat(ComplexScripts.measuredWidth(0x0915, medial, fin, plain, true)).isEqualTo(36d); // Indic ignores it
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
        assertThat(laid.xs()).containsExactly(8f, 13f, 17f, 21f, 25f);
        assertThat(laid.rises()).containsExactly(0f, 0f, -9f, 3f, 0f);
        assertThat(laid.width()).isEqualTo(31f);
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
        assertThat(laid.xs()).containsExactly(-2f, 1f, 13f, 25f, 28f, 40f);
        assertThat(laid.width()).isEqualTo(52f);

        float[] tight = {6f, 5f, 5f, 6f, 5f, 5f};
        ComplexScripts.Laid over = ComplexScripts.layout(g, tight, CHAR_FOR,
                text -> new ComplexScripts.Shaped(new int[]{90, 91}, new float[]{12f, 12f}), 32);
        // 28px too wide: the spaces give up half their 12 (6), the remaining 22 spill past the left edge
        assertThat(over.xs()).containsExactly(-22f, -19f, -7f, 5f, 8f, 20f);
        assertThat(over.xs()[over.xs().length - 1] + 12f).isEqualTo(32f); // the right edge is WebKit's
    }

    @Test
    @DisplayName("a space at the edge the run keeps stays as measured; the neighbour beside it is another run's")
    void keptEdgeSpacesStayMeasured() {
        // visual: "بت" + " " — the trailing space sits at a right-to-left run's kept right edge
        int[] g = glyphs("بت" + " ");
        float[] a = {10f, 10f, 6f};
        ComplexScripts.Laid laid = ComplexScripts.layout(g, a, CHAR_FOR,
                text -> new ComplexScripts.Shaped(new int[]{90, 91}, new float[]{12f, 12f}), 32);
        assertThat(laid.xs()).containsExactly(-4f, 8f, 20f); // the space keeps 20..26; the 4 spill left
        // an Indic run keeps its left edge, so a leading space is the one that stays
        int[] h = glyphs(" " + "कम");
        ComplexScripts.Laid indic = ComplexScripts.layout(h, new float[]{6f, 10f, 10f}, CHAR_FOR,
                text -> new ComplexScripts.Shaped(new int[]{90, 91}, new float[]{12f, 12f}), 32);
        assertThat(indic.xs()).containsExactly(0f, 6f, 18f);
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
        assertThat(laid.xs()).containsExactly(-1f, 11f, 14f); // 4 too wide: the space gives 3 of its 6, 1 spills
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
}
