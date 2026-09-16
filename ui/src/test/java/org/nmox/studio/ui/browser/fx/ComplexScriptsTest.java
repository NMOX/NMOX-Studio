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
    @DisplayName("an Arabic run reaches the shaper in logical order, and the call is centred in the width WebKit measured")
    void rightToLeftRunIsReversedShapedAndRightAligned() {
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
        assertThat(slack).isEqualTo(7.5f);                 // 30 measured, 15 painted: half on each side
    }

    @Test
    @DisplayName("a Devanagari run is not reversed, and is centred the same way")
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
        assertThat(slack).isEqualTo(22.5f);                // 60 measured, 15 painted
    }

    @Test
    @DisplayName("words are shaped one at a time and the whole call centres on their summed difference")
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
        assertThat(slack).isEqualTo(4f);                 // (20-16) twice, halved
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
        assertThat(ComplexScripts.measuredWidth(0x0628, medial, fin, plain)).isEqualTo(12d); // beh: 10 + 0.2 * 10
        assertThat(ComplexScripts.measuredWidth(0x064E, medial, fin, plain)).isZero();        // fatha, a mark
        assertThat(ComplexScripts.measuredWidth(0x094D, medial, fin, plain)).isZero();        // virama, a mark
        assertThat(ComplexScripts.measuredWidth(0x0915, medial, fin, plain)).isEqualTo(36d); // ka: 0.72 * 50
        assertThat(ComplexScripts.measuredWidth('A', medial, fin, plain)).isNaN();           // the font's own
        assertThat(ComplexScripts.measuredWidth(0x05D0, medial, fin, plain)).isNaN();        // Hebrew is not shaped
        assertThat(ComplexScripts.measuredWidth(0x0628, cp -> Double.NaN, fin, plain)).isNaN();
        assertThat(ComplexScripts.measuredWidth(0x0915, medial, fin, cp -> Double.NaN)).isNaN();
    }

    @Test
    @DisplayName("mismatched arrays are refused before anything is read")
    void mismatchedArraysAreRefused() {
        assertThat(ComplexScripts.reshape(new int[2], new float[3], CHAR_FOR, t -> null, BLANK)).isZero();
        assertThat(ComplexScripts.reshape(null, null, CHAR_FOR, t -> null, BLANK)).isZero();
    }
}
