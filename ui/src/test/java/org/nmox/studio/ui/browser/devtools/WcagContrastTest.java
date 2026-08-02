package org.nmox.studio.ui.browser.devtools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The DevTools contrast verdict (v1.227.0): WCAG 2.x reference ratios,
 * the computed-style-only parse, and the honest transparent-background
 * refusal.
 */
class WcagContrastTest {

    @Test
    @DisplayName("black on white is 21:1 and passes everything")
    void blackOnWhite() {
        WcagContrast.Verdict v = WcagContrast.of("rgb(0, 0, 0)", "rgb(255, 255, 255)");
        assertThat(v).isNotNull();
        assertThat(v.ratio()).isEqualTo(21.0);
        assertThat(v.aaNormal()).isTrue();
        assertThat(v.aaaNormal()).isTrue();
    }

    @Test
    @DisplayName("the classic borderline: #767676 on white is ~4.54:1 — AA pass, AAA fail")
    void borderlineGray() {
        WcagContrast.Verdict v = WcagContrast.of("rgb(118, 118, 118)", "rgb(255, 255, 255)");
        assertThat(v.ratio()).isBetween(4.5, 4.6);
        assertThat(v.aaNormal()).isTrue();
        assertThat(v.aaaNormal()).isFalse();
        assertThat(v.aaLarge()).isTrue();
        assertThat(v.summary()).contains("AA pass").contains("AAA FAIL");
    }

    @Test
    @DisplayName("low contrast fails AA for normal text")
    void lowContrast() {
        WcagContrast.Verdict v = WcagContrast.of("rgb(200, 200, 200)", "rgb(255, 255, 255)");
        assertThat(v.aaNormal()).isFalse();
    }

    @Test
    @DisplayName("order does not matter: ratio is symmetric")
    void symmetric() {
        assertThat(WcagContrast.of("rgb(0,0,0)", "rgb(255,255,255)").ratio())
                .isEqualTo(WcagContrast.of("rgb(255,255,255)", "rgb(0,0,0)").ratio());
    }

    @Test
    @DisplayName("a fully transparent background yields no verdict — the backdrop is an ancestor's")
    void transparentBackgroundRefused() {
        assertThat(WcagContrast.of("rgb(0,0,0)", "rgba(0, 0, 0, 0)")).isNull();
        // but a merely translucent one still computes against its own value
        assertThat(WcagContrast.of("rgb(0,0,0)", "rgba(255, 255, 255, 0.9)")).isNotNull();
    }

    @Test
    @DisplayName("only the computed-style rgb()/rgba() shape parses; anything else is null")
    void onlyComputedShapes() {
        assertThat(WcagContrast.of("#336699", "rgb(255,255,255)")).isNull();
        assertThat(WcagContrast.of(null, "rgb(255,255,255)")).isNull();
        assertThat(WcagContrast.of("rgb(1,2)", "rgb(255,255,255)")).isNull();
        assertThat(WcagContrast.of("hotpink", "rgb(255,255,255)")).isNull();
    }
}
