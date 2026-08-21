package org.nmox.studio.editor.design;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The class-selector core (v2.27.0). Each boundary case names the
 * mutant it kills: dropping the string/url blanking, the digit
 * lookbehind, or the attribute-name check must fail these by name.
 */
class CssClassesTest {

    // ---- selector scan ----------------------------------------------------

    @Test
    @DisplayName("class selectors register: bare, compound, nested")
    void selectorsRegister() {
        Map<String, CssClasses.Selector> s = CssClasses.selectors(
                ".card { color: red }\n"
                + "li.item:hover { }\n"
                + ".wrap { .inner { } }\n");
        assertThat(s).containsKeys("card", "item", "inner");
        assertThat(s.get("card").offset()).isEqualTo(0);
    }

    @Test
    @DisplayName("decimals are numbers, not classes (digit lookbehind)")
    void decimalsAreNotClasses() {
        assertThat(CssClasses.selectors(
                ".real { margin: .5em; width: 0.25rem }"))
                .containsOnlyKeys("real");
    }

    @Test
    @DisplayName("comments, strings, and url() bodies never declare a class")
    void blankedRegionsDoNotDeclare() {
        assertThat(CssClasses.selectors(
                "/* .ghost1 */\n"
                + "// .ghost2\n"
                + ".real { content: \".ghost3\"; background: url(img.ghost4) }"))
                .containsOnlyKeys("real");
    }

    @Test
    @DisplayName("first declaration of a name wins")
    void firstWins() {
        Map<String, CssClasses.Selector> s = CssClasses.selectors(
                ".a { }\n.a { }\n");
        assertThat(s.get("a").offset()).isEqualTo(0);
    }

    // ---- the class attribute context --------------------------------------

    @Test
    @DisplayName("attrPrefix fires inside class=\"...\" — empty, partial, after a sibling")
    void attrPrefixFires() {
        assertThat(CssClasses.attrPrefix("<div class=\"")).isEmpty();
        assertThat(CssClasses.attrPrefix("<div class=\"bt")).isEqualTo("bt");
        assertThat(CssClasses.attrPrefix("<div class=\"btn ")).isEmpty();
        assertThat(CssClasses.attrPrefix("<div class=\"btn se")).isEqualTo("se");
        assertThat(CssClasses.attrPrefix("<div class='single-quoted ne"))
                .isEqualTo("ne");
        assertThat(CssClasses.attrPrefix("<div class = \"spaced")).isEqualTo("spaced");
    }

    @Test
    @DisplayName("attrPrefix refuses everywhere else (attribute-name check)")
    void attrPrefixRefuses() {
        assertThat(CssClasses.attrPrefix("<a href=\"x")).isNull();
        assertThat(CssClasses.attrPrefix("<div data-class=\"x")).isNull();
        assertThat(CssClasses.attrPrefix("<div :class=\"x")).isNull();
        assertThat(CssClasses.attrPrefix("plain prose cl")).isNull();
        assertThat(CssClasses.attrPrefix("<div id=\"x")).isNull();
        assertThat(CssClasses.attrPrefix("<div class=\"done\" title=\"x")).isNull();
    }

    @Test
    @DisplayName("attrNameSpanAt spans a class token in the attribute, refuses prose")
    void spanAt() {
        String html = "<p class=\"hero big\">hero prose</p>";
        int inAttr = html.indexOf("hero") + 2;
        assertThat(CssClasses.attrNameSpanAt(html, inAttr))
                .containsExactly(html.indexOf("hero"), html.indexOf("hero") + 4);
        int inProse = html.indexOf("hero prose") + 2;
        assertThat(CssClasses.attrNameSpanAt(html, inProse)).isNull();
    }

    // ---- project scan -----------------------------------------------------

    @Test
    @DisplayName("the project scan reads stylesheets AND the family's style blocks, skips heavy dirs, refreshes on change")
    void projectScan(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("styles.css"), ".from-css { }");
        Path vue = dir.resolve("App.vue");
        Files.writeString(vue,
                "<template><p class=\"used-not-declared\"/></template>\n"
                + "<style>.from-vue { }</style>\n");
        Path heavy = dir.resolve("node_modules");
        Files.createDirectory(heavy);
        Files.writeString(heavy.resolve("lib.css"), ".from-heavy { }");

        File root = dir.toFile();
        List<CssClasses.ProjectSelector> found = CssClasses.scanProject(root);
        assertThat(found).extracting(CssClasses.ProjectSelector::name)
                .contains("from-css", "from-vue")
                .as("a class ATTRIBUTE is a usage, not a declaration")
                .doesNotContain("used-not-declared")
                .as("node_modules is never scanned")
                .doesNotContain("from-heavy");

        // freshness: rewriting the file replaces its entry
        Thread.sleep(1100); // mtime granularity
        Files.writeString(vue, "<style>.renamed { }</style>\n");
        assertThat(CssClasses.scanProject(root))
                .extracting(CssClasses.ProjectSelector::name)
                .contains("renamed").doesNotContain("from-vue");
    }
}
