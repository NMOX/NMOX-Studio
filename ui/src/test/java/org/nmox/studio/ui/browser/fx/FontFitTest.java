package org.nmox.studio.ui.browser.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToDoubleFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Width parameters fitted to the font the Browser draws with (v2.173.0), where
 * WebKit's own text path cannot be switched on: fitted from a word corpus, used
 * only when clearly better than the constants measured on macOS.
 */
class FontFitTest {

    /** Words whose laid width is {@code share} of their plain sum, give or take a pixel. */
    private static List<FontFit.Row> shareRows(double share, int n) {
        List<FontFit.Row> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double plain = 40 + 7 * i;
            rows.add(new FontFit.Row(plain, 0, share * plain + (i % 2 == 0 ? 0.5 : -0.5)));
        }
        return rows;
    }

    @Test
    @DisplayName("a share far from the constant is fitted to the font; one close to it keeps the constant")
    void shareFitsOnlyWhenClearlyBetter() {
        assertThat(FontFit.share(shareRows(1.01, 20), 0.78).factor()).isCloseTo(1.01, within(0.01)); // Linux Tamil
        assertThat(FontFit.share(shareRows(0.785, 20), 0.78).factor()).isEqualTo(0.78);               // under a pixel a word: kept
    }

    @Test
    @DisplayName("too few words, or a share no font has, keep the constant")
    void degenerateSharesKeepTheConstant() {
        assertThat(FontFit.share(shareRows(1.01, FontFit.MIN_WORDS - 1), 0.78).factor()).isEqualTo(0.78);
        assertThat(FontFit.share(shareRows(2.5, 20), 0.78).factor()).isEqualTo(0.78);
        assertThat(FontFit.share(shareRows(0.1, 20), 0.78).factor()).isEqualTo(0.78);
        assertThat(FontFit.share(List.of(), 0.78).factor()).isEqualTo(0.78);
    }

    @Test
    @DisplayName("Arabic's blend toward the final form is fitted within [0, 1] and only when clearly better")
    void arabicBlend() {
        List<FontFit.Row> rows = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            double medial = 30 + 5 * i;
            double fin = medial + 20 + i;
            rows.add(new FontFit.Row(medial, fin, medial + 0.6 * (fin - medial)));
        }
        assertThat(FontFit.arabic(rows, 0.25).factor()).isCloseTo(0.6, within(0.001));
        List<FontFit.Row> beyond = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            beyond.add(new FontFit.Row(30 + i, 50 + i, 30 + i + 1.5 * 20));
        }
        assertThat(FontFit.arabic(beyond, 0.25).factor()).isEqualTo(0.25); // past the final form: not a blend
        assertThat(FontFit.arabic(rows.subList(0, 3), 0.25).factor()).isEqualTo(0.25);
    }

    @Test
    @DisplayName("clearly better means a quarter less error and at least a pixel a word")
    void clearlyBetter() {
        assertThat(FontFit.clearlyBetter(7.3, 20.2)).isTrue();
        assertThat(FontFit.clearlyBetter(3.6, 4.5)).isFalse();  // 20% less: not enough
        assertThat(FontFit.clearlyBetter(1.5, 2.2)).isFalse();  // under a pixel
        assertThat(FontFit.clearlyBetter(4.0, 4.0)).isFalse();
    }

    @Test
    @DisplayName("the corpus carries enough words for every block the width hook measures")
    void corpusCoversEveryBlock() {
        List<Integer> blocks = new ArrayList<>(List.of(0x0600));
        for (double[] row : ComplexScripts.INDIC_SHARES) {
            blocks.add((int) row[0]);
        }
        for (double[] row : ComplexScripts.RTL_SHARES) {
            blocks.add((int) row[0]);
        }
        for (int block : blocks) {
            List<String> words = FontFit.corpus().get(block);
            assertThat(words).as("corpus words for U+%04X", block).isNotNull().hasSizeGreaterThanOrEqualTo(FontFit.MIN_WORDS);
            for (String word : words) {
                word.codePoints().forEach(cp -> assertThat(ComplexScripts.blockOf(cp))
                        .as("%s is written in U+%04X", word, block).isEqualTo(block));
            }
        }
    }

    @Test
    @DisplayName("a letter's block names its parameters, and a fit replaces the constant for it")
    void blocksAndFittedWidths() {
        assertThat(ComplexScripts.blockOf(0x0628)).isEqualTo(0x0600);
        assertThat(ComplexScripts.blockOf(0x0915)).isEqualTo(0x0900);
        assertThat(ComplexScripts.blockOf(0x0B95)).isEqualTo(0x0B80);
        assertThat(ComplexScripts.blockOf(0x07D3)).isEqualTo(0x07C0);
        assertThat(ComplexScripts.constantFor(0x0B80, false)).isEqualTo(0.78);
        assertThat(ComplexScripts.constantFor(0x0600, true)).isEqualTo(0.5);
        IntToDoubleFunction plain = cp -> 50d;
        IntToDoubleFunction medial = cp -> 10d;
        IntToDoubleFunction fin = cp -> 20d;
        assertThat(ComplexScripts.measuredWidth(0x0B95, medial, fin, plain, false, null)).isCloseTo(39d, within(1e-9));
        assertThat(ComplexScripts.measuredWidth(0x0B95, medial, fin, plain, false, new FontFit.Fit(1.01))).isCloseTo(50.5, within(1e-9));
        assertThat(ComplexScripts.measuredWidth(0x0628, medial, fin, plain, false, new FontFit.Fit(0.6))).isCloseTo(16d, within(1e-9));
        assertThat(ComplexScripts.measuredWidth(0x0BCD, medial, fin, plain, false, new FontFit.Fit(1.01))).isZero(); // a mark still measures zero
    }
}
