package org.nmox.studio.editor.probe;

import java.awt.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The probe's verdict logic, pinned without booting the IDE. Both halves
 * of the v1.10.1 bug must read as FAIL; the shipped dark scheme as PASS.
 * The boot-level red/green proof lives in scripts/rendering-probe.sh; this
 * guards the thresholds and the expected Phosphor color against a
 * fat-finger.
 */
class RenderingProbeTest {

    private static final Color PHOSPHOR_KEYWORD = new Color(0xff6ac1);
    private static final Color FLATLAF_DARK_BG = new Color(0x2b, 0x2b, 0x2b);
    private static final Color WHITE = Color.WHITE;

    @Test
    @DisplayName("The shipped dark scheme passes")
    void darkSchemePasses() {
        assertThat(RenderingProbe.classify(FLATLAF_DARK_BG, PHOSPHOR_KEYWORD))
                .startsWith("PASS");
    }

    @Test
    @DisplayName("A light background fails — bug half 1, the profile fell back to NetBeans")
    void lightBackgroundFails() {
        String verdict = RenderingProbe.classify(WHITE, PHOSPHOR_KEYWORD);
        assertThat(verdict).startsWith("FAIL").contains("light");
    }

    @Test
    @DisplayName("A non-Phosphor keyword fails — bug half 2, palette not under active profile")
    void wrongKeywordFails() {
        // dark background (profile is right) but the platform-default
        // keyword color instead of Phosphor's
        String verdict = RenderingProbe.classify(FLATLAF_DARK_BG, new Color(0x808080));
        assertThat(verdict).startsWith("FAIL").contains("keyword");
    }

    @Test
    @DisplayName("Missing colors fail rather than passing on nulls")
    void missingColorsFail() {
        assertThat(RenderingProbe.classify(null, PHOSPHOR_KEYWORD))
                .startsWith("FAIL").contains("background");
        assertThat(RenderingProbe.classify(FLATLAF_DARK_BG, null))
                .startsWith("FAIL").contains("keyword");
    }

    @Test
    @DisplayName("The luminance threshold sits clear of both real values")
    void thresholdHasMargin() {
        // FlatLaf Dark background must classify dark, white must classify
        // light, with the threshold comfortably between the two
        assertThat(RenderingProbe.DARK_MAX_LUMINANCE)
                .isGreaterThan(luminance(FLATLAF_DARK_BG))
                .isLessThan(luminance(WHITE));
    }

    private static int luminance(Color c) {
        return (c.getRed() * 299 + c.getGreen() * 587 + c.getBlue() * 114) / 1000;
    }
}
