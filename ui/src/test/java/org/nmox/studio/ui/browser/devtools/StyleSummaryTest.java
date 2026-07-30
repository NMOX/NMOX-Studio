package org.nmox.studio.ui.browser.devtools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The curated computed-style vocabulary: exactly the documented 15. */
class StyleSummaryTest {

    @Test
    @DisplayName("the curated list is exactly the documented 15 properties")
    void exactlyFifteen() {
        assertThat(StyleSummary.KEYS).containsExactly(
                "display", "position", "width", "height", "margin", "padding",
                "border", "box-sizing", "font-family", "font-size", "font-weight",
                "line-height", "color", "background-color", "z-index");
        assertThat(StyleSummary.KEYS).hasSize(15);
    }

    @Test
    @DisplayName("parse keeps only curated keys, in curated order")
    void parseFiltersAndOrders() {
        var m = StyleSummary.parse(
                "{\"z-index\":\"auto\",\"--custom\":\"x\",\"display\":\"flex\",\"color\":\"rgb(0, 0, 0)\"}");
        assertThat(m.keySet()).containsExactly("display", "color", "z-index");
        assertThat(m).containsEntry("display", "flex");
    }

    @Test
    @DisplayName("malformed input is an empty map, never a throw")
    void malformedIsEmpty() {
        assertThat(StyleSummary.parse(null)).isEmpty();
        assertThat(StyleSummary.parse("junk")).isEmpty();
        assertThat(StyleSummary.parse("[]")).isEmpty();
    }

    @Test
    @DisplayName("hostile oversized values are capped")
    void hostileValuesCapped() {
        var m = StyleSummary.parse("{\"display\":\"" + "d".repeat(1000) + "\"}");
        assertThat(m.get("display")).hasSize(200);
    }
}
