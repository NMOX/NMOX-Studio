package org.nmox.studio.ui.browser.devtools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Style write-back (v1.358.0): the DOM-tab edit lands in the source
 * stylesheet — replace in place, insert with the block's indent, and
 * refuse whenever writing would be a guess or would corrupt the file.
 */
class StyleWritebackTest {

    private static final String CSS = """
            /* header rules */
            .hero {
                color: red;
                font-size: 14px;
            }

            .hero .title, h1 {
                margin: 0;
            }
            """;

    @Test
    @DisplayName("an existing declaration is replaced in place")
    void replaceInPlace() {
        StyleWriteback.Result r = StyleWriteback.apply(CSS, ".hero", "color", "tomato");
        assertThat(r.ok()).isTrue();
        assertThat(r.css()).contains("color: tomato;");
        assertThat(r.css()).doesNotContain("color: red");
        assertThat(r.css()).contains("font-size: 14px;");
    }

    @Test
    @DisplayName("a missing declaration is inserted with the block's own indent")
    void insertWithIndent() {
        StyleWriteback.Result r = StyleWriteback.apply(CSS, ".hero", "background", "black");
        assertThat(r.ok()).isTrue();
        assertThat(r.css()).contains("\n    background: black;\n");
        // the existing declarations survive untouched
        assertThat(r.css()).contains("color: red;").contains("font-size: 14px;");
    }

    @Test
    @DisplayName("multi-selector rules match by normalized CSSOM selector text")
    void multiSelector() {
        StyleWriteback.Result r = StyleWriteback.apply(CSS, ".hero .title, h1", "margin", "4px");
        assertThat(r.ok()).isTrue();
        assertThat(r.css()).contains("margin: 4px;");
        assertThat(r.css()).doesNotContain("margin: 0;");
    }

    @Test
    @DisplayName("property match is whole-word: color must not hit background-color")
    void wholeWordProperty() {
        String css = ".a {\n    background-color: blue;\n}\n";
        StyleWriteback.Result r = StyleWriteback.apply(css, ".a", "color", "red");
        assertThat(r.ok()).isTrue();
        assertThat(r.css())
                .contains("background-color: blue;")
                .contains("color: red;");
    }

    @Test
    @DisplayName("a selector inside a comment is not a rule")
    void commentShadow() {
        String css = "/* .ghost { color: white; } */\n.ghost {\n    color: black;\n}\n";
        StyleWriteback.Result r = StyleWriteback.apply(css, ".ghost", "color", "gray");
        assertThat(r.ok()).isTrue();
        // the real rule changed; the commented copy is untouched
        assertThat(r.css()).contains("/* .ghost { color: white; } */");
        assertThat(r.css()).contains("color: gray;");
        assertThat(r.css()).doesNotContain("color: black");
    }

    @Test
    @DisplayName("rules inside @media are found (nesting handled by depth)")
    void insideMedia() {
        String css = "@media (max-width: 600px) {\n    .hero {\n        color: red;\n    }\n}\n";
        StyleWriteback.Result r = StyleWriteback.apply(css, ".hero", "color", "teal");
        assertThat(r.ok()).isTrue();
        assertThat(r.css()).contains("color: teal;");
    }

    @Test
    @DisplayName("a duplicated selector edits the LAST block — the one the cascade applies")
    void duplicateSelectorLastWins() {
        // editing the first block would land in dead CSS: the later rule
        // keeps overriding and the page never changes (v1.359.0 review)
        String css = ".a {\n    color: red;\n}\n\n.a {\n    color: blue;\n}\n";
        StyleWriteback.Result r = StyleWriteback.apply(css, ".a", "color", "green");
        assertThat(r.ok()).isTrue();
        assertThat(r.css())
                .as("the first (dead) rule is untouched")
                .contains("color: red;");
        assertThat(r.css())
                .as("the last (live) rule carries the edit")
                .contains("color: green;")
                .doesNotContain("color: blue");
    }

    @Test
    @DisplayName("refusals: missing selector, structural characters, blanks")
    void refusals() {
        assertThat(StyleWriteback.apply(CSS, ".absent", "color", "red").ok()).isFalse();
        assertThat(StyleWriteback.apply(CSS, ".absent", "color", "red").reason())
                .contains("not found");
        assertThat(StyleWriteback.apply(CSS, ".hero", "color", "red}").ok()).isFalse();
        assertThat(StyleWriteback.apply(CSS, ".hero", "col:or", "red").ok()).isFalse();
        assertThat(StyleWriteback.apply(CSS, ".hero", "color", " ").ok()).isFalse();
        assertThat(StyleWriteback.apply("", ".hero", "color", "red").ok()).isFalse();
    }
}
