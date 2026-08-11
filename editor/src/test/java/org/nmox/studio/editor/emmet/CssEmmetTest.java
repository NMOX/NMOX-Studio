package org.nmox.studio.editor.emmet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CSS abbreviation grammar (v1.336.0), pinned the {@link EmmetTest}
 * way: what expands is exact, what refuses leaves the text untouched.
 */
class CssEmmetTest {

    private static String css(String abbrev) {
        CssEmmet.Expansion e = CssEmmet.expand(abbrev);
        return e == null ? null : e.css();
    }

    @Test
    @DisplayName("keyword abbreviations expand from the exact-match table")
    void keywords() {
        assertThat(css("df")).isEqualTo("display: flex;");
        assertThat(css("aic")).isEqualTo("align-items: center;");
        assertThat(css("jcsb")).isEqualTo("justify-content: space-between;");
        assertThat(css("posa")).isEqualTo("position: absolute;");
        assertThat(css("ttu")).isEqualTo("text-transform: uppercase;");
        assertThat(css("m0a")).isEqualTo("margin: 0 auto;");
        assertThat(css("bxbb")).isEqualTo("box-sizing: border-box;");
    }

    @Test
    @DisplayName("numeric properties: px default, p/e/r unit suffixes, bare 0")
    void numericUnits() {
        assertThat(css("m10")).isEqualTo("margin: 10px;");
        assertThat(css("w100p")).isEqualTo("width: 100%;");
        assertThat(css("fz1.2r")).isEqualTo("font-size: 1.2rem;");
        assertThat(css("pt2e")).isEqualTo("padding-top: 2em;");
        assertThat(css("m0")).isEqualTo("margin: 0;");
        assertThat(css("bdrs4")).isEqualTo("border-radius: 4px;");
    }

    @Test
    @DisplayName("longest prefix wins: miw is min-width, never margin")
    void longestPrefix() {
        assertThat(css("miw320")).isEqualTo("min-width: 320px;");
        assertThat(css("mah100p")).isEqualTo("max-height: 100%;");
        assertThat(css("mt8")).isEqualTo("margin-top: 8px;");
    }

    @Test
    @DisplayName("margin and padding take up to four dash-separated values; others refuse multi")
    void multiValues() {
        assertThat(css("p10-20")).isEqualTo("padding: 10px 20px;");
        assertThat(css("m0-auto")).isNull(); // auto is not a numeric value
        assertThat(css("m1-2-3-4")).isEqualTo("margin: 1px 2px 3px 4px;");
        assertThat(css("m1-2-3-4-5")).isNull(); // five values is not a box
        assertThat(css("w10-20")).isNull();     // width is single-value
    }

    @Test
    @DisplayName("z-index and opacity are unitless; a unit suffix on them refuses")
    void unitless() {
        assertThat(css("z10")).isEqualTo("z-index: 10;");
        assertThat(css("op0.5")).isEqualTo("opacity: 0.5;");
        assertThat(css("z10e")).isNull();
    }

    @Test
    @DisplayName("colors: c# and bgc# with validated 3/4/6/8-digit hex")
    void colors() {
        assertThat(css("c#f00")).isEqualTo("color: #f00;");
        assertThat(css("bgc#1a2b3c")).isEqualTo("background-color: #1a2b3c;");
        assertThat(css("c#f0")).isNull();     // 2 digits is not a color
        assertThat(css("c#gggggg")).isNull(); // not hex
    }

    @Test
    @DisplayName("a trailing bang appends !important")
    void important() {
        assertThat(css("dn!")).isEqualTo("display: none !important;");
        assertThat(css("m10!")).isEqualTo("margin: 10px !important;");
        assertThat(css("!")).isNull();
    }

    @Test
    @DisplayName("refusals: unknown soup, fuzzy near-misses, empty")
    void refusals() {
        assertThat(css("qqq")).isNull();
        assertThat(css("margin")).isNull();  // full property names are typing, not abbreviations
        assertThat(css("ov-h")).isNull();    // real Emmet fuzz; our subset says no
        assertThat(css("m")).isNull();       // prefix with no value
        assertThat(css("m-10")).isNull();    // leading dash: recorded out
        assertThat(css("")).isNull();
        assertThat(CssEmmet.expand(null)).isNull();
    }

    @Test
    @DisplayName("abbreviationIn takes the trailing token, refuses in value position")
    void abbreviationIn() {
        assertThat(CssEmmet.abbreviationIn("  m10")).isEqualTo("m10");
        assertThat(CssEmmet.abbreviationIn("  df")).isEqualTo("df");
        // a token that does not parse is no abbreviation
        assertThat(CssEmmet.abbreviationIn("  border-tac")).isNull();
        // VALUE position: someone typing after a colon is writing CSS,
        // not an abbreviation — color: tdn must never mutate
        assertThat(CssEmmet.abbreviationIn("  color: tdn")).isNull();
        assertThat(CssEmmet.abbreviationIn("  display:df")).isNull();
        // nothing before the caret
        assertThat(CssEmmet.abbreviationIn("")).isNull();
        assertThat(CssEmmet.abbreviationIn("  color: ")).isNull();
    }

    @Test
    @DisplayName("the caret lands after the semicolon")
    void caret() {
        CssEmmet.Expansion e = CssEmmet.expand("df");
        assertThat(e.caretOffset()).isEqualTo(e.css().length());
    }
}
