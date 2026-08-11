package org.nmox.studio.editor.emmet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v1.342.0 arc-review probes, kept as pinned law: compositions of
 * the v1.336–v1.341 features (CSS grammar, chains, lorem, climb-up)
 * that no single feature's build tests tried together. Each was run as
 * an open probe FIRST and pinned at its observed-and-judged-correct
 * value — the review found no divergence from the documented laws.
 */
class EmmetArcProbeTest {

    @Test
    @DisplayName("a climbed unit is a full citizen: *N repeats it at the landing level")
    void climbTimes() {
        assertThat(Emmet.expand("div>p^section*2", "  ").html()).isEqualTo(
                "<div>\n  <p></p>\n</div>\n<section></section>\n<section></section>");
    }

    @Test
    @DisplayName("climb inside a times'd group is relative to the group's own depth")
    void climbInsideGroup() {
        assertThat(Emmet.expand("(x>y^z)*2", "  ").html()).isEqualTo(
                "<x>\n  <y></y>\n</x>\n<z></z>\n"
                + "<x>\n  <y></y>\n</x>\n<z></z>");
    }

    @Test
    @DisplayName("lorem and climb-up compose")
    void loremThenClimb() {
        assertThat(Emmet.expand("p>lorem2^h1", "  ").html())
                .isEqualTo("<p>\n  Lorem ipsum.\n</p>\n<h1></h1>");
    }

    @Test
    @DisplayName("suffix extraction never swallows a real tag ahead of a climb")
    void extractionWithClimb() {
        assertThat(Emmet.abbreviationIn("<p>header>h1^main"))
                .isEqualTo("header>h1^main");
    }

    @Test
    @DisplayName("a mid-chain !important stays on its own part")
    void chainImportantMidChain() {
        assertThat(CssEmmet.expand("m0!+df").css())
                .isEqualTo("margin: 0 !important;\ndisplay: flex;");
    }

    @Test
    @DisplayName("a group-top climb refuses even under *N — the wall holds")
    void groupWallUnderTimes() {
        assertThat(Emmet.expand("(a{x$}^i)*2", "  ")).isNull();
    }
}
