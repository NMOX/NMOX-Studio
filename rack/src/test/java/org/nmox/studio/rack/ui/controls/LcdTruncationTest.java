package org.nmox.studio.rack.ui.controls;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.ToIntFunction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An LCD that has to cut its text keeps the head and says it cut
 * (v1.282.0, the Task Rack persona walk).
 *
 * <p>The single-line paint dropped characters off the FRONT, one at a
 * time, with no marker: VERITAS's "UNTRUSTED WORKSPACE — EXECUTION
 * REFUSED" reached the user as "ED WORKSPACE — EXECUTION REFUSED",
 * which loses the one word the message exists to say AND reads like
 * content rather than a cut. Observed live in the shipped 1.280.0.
 * The multi-line branch cut from the end, also silently, so the two
 * halves of one widget disagreed about which end matters.
 */
class LcdTruncationTest {

    /** A monospace stand-in: every character one pixel wide. */
    private static final ToIntFunction<String> ONE_PX = String::length;

    @Test
    @DisplayName("text that fits is untouched")
    void fittingTextIsVerbatim() {
        assertThat(LcdDisplay.fit("READY", 40, ONE_PX)).isEqualTo("READY");
        assertThat(LcdDisplay.fit("", 40, ONE_PX)).isEmpty();
        assertThat(LcdDisplay.fit(null, 40, ONE_PX))
                .as("a device that clears its LCD must not crash the paint")
                .isEmpty();
    }

    @Test
    @DisplayName("the head survives and the cut is marked")
    void keepsTheHeadAndSaysSo() {
        String refusal = "UNTRUSTED WORKSPACE — EXECUTION REFUSED";
        String shown = LcdDisplay.fit(refusal, 12, ONE_PX);

        assertThat(shown)
                .as("the first word is the one the message exists to say")
                .startsWith("UNTRUSTED")
                .endsWith("…");
        assertThat(shown.length()).isLessThanOrEqualTo(12);
        assertThat(shown)
                .as("dropping from the front turned this into 'ED WORKSPACE'")
                .isNotEqualTo(refusal.substring(refusal.length() - 12));
    }

    @Test
    @DisplayName("the cut never splits a surrogate pair")
    void cutIsCodePointSafe() {
        // An emoji is one glyph but two UTF-16 units. The first version
        // of this test used the uniform ONE_PX metric and the char-chop
        // mutant SURVIVED: when every unit costs the same, a lone
        // surrogate still costs 1, so the loop never STOPS on one — the
        // two chop styles converge. The mutant is only visible under a
        // metric where a dangling surrogate measures NARROWER than the
        // whole pair, which is exactly what a real font does (no glyph,
        // no width). The v1.149.0 lesson again: pick inputs where the
        // mutant's behavior DIVERGES, not inputs that merely contain
        // the hazard.
        java.util.function.ToIntFunction<String> fontLike = str -> {
            int w = 0;
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);
                if (Character.isHighSurrogate(c)
                        && i + 1 < str.length()
                        && Character.isLowSurrogate(str.charAt(i + 1))) {
                    w += 2;      // the emoji glyph
                    i++;
                } else if (Character.isHighSurrogate(c) || Character.isLowSurrogate(c)) {
                    w += 0;      // a lone surrogate renders nothing
                } else {
                    w += 1;
                }
            }
            return w;
        };

        String s = "AB\uD83D\uDE80";                     // "AB🚀", width 4
        String shown = LcdDisplay.fit(s, 3, fontLike);
        assertThat(shown)
                .as("a char-based chop stops on AB\uD83D… (the dangling"
                        + " surrogate is free, so it 'fits'); the cut must"
                        + " remove the whole rocket")
                .isEqualTo("AB…");
        for (int i = 0; i < shown.length(); i++) {
            char c = shown.charAt(i);
            boolean dangling = Character.isHighSurrogate(c)
                    && (i == shown.length() - 1
                        || !Character.isLowSurrogate(shown.charAt(i + 1)));
            assertThat(dangling)
                    .as("no dangling high surrogate in [%s]", shown)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("an impossibly narrow display shows the marker, not a stray letter")
    void degradesToTheMarker() {
        assertThat(LcdDisplay.fit("REFUSED", 0, ONE_PX))
                .as("one leftover letter reads as content; an ellipsis reads"
                        + " as 'there is more'")
                .isEqualTo("…");
    }

    @Test
    @DisplayName("both halves of the widget truncate through the one helper")
    void oneTruncationForBothModes() throws Exception {
        String src = Files.readString(Path.of("src", "main", "java", "org",
                "nmox", "studio", "rack", "ui", "controls", "LcdDisplay.java"),
                StandardCharsets.UTF_8);
        int paint = src.indexOf("protected void paintComponent(");
        assertThat(paint).isPositive();
        String body = src.substring(paint);

        assertThat(body.split("fit\\(", -1).length - 1)
                .as("single-line and multi-line must agree — they disagreed"
                        + " about which end to keep for the whole of v1.0")
                .isEqualTo(2);
        assertThat(body)
                .as("no hand-rolled trimming loop may come back alongside it")
                .doesNotContain("t.substring(1)");
    }
}
