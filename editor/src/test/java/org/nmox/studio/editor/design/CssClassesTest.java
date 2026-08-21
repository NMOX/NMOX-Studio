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

    // ---- the reverse direction (v2.28.0) ----------------------------------

    @Test
    @DisplayName("selectorSpanAt spans a selector name, refuses comments and values")
    void selectorSpanAtBoundaries() {
        String css = "/* .ghost */ .card { margin: .5em }";
        int onCard = css.indexOf("card") + 1;
        assertThat(CssClasses.selectorSpanAt(css, onCard))
                .containsExactly(css.indexOf("card"), css.indexOf("card") + 4);
        int onGhost = css.indexOf("ghost") + 1;
        assertThat(CssClasses.selectorSpanAt(css, onGhost)).isNull();
        int onDecimal = css.indexOf("5em");
        assertThat(CssClasses.selectorSpanAt(css, onDecimal)).isNull();
    }

    @Test
    @DisplayName("usagesIn finds whole tokens inside class attributes only")
    void usagesInBoundaries() {
        String markup = "<p class=\"card big\">card prose</p>\n"
                + "<div class='card'></div>\n"
                + "<span class=\"cardigan\"></span>\n"
                + "<a title=\"card\"></a>\n";
        assertThat(CssClasses.usagesIn(markup, "card"))
                .as("two attr usages; prose, super-string, and other attrs out")
                .hasSize(2)
                .containsExactly(markup.indexOf("card big"),
                        markup.indexOf("'card'") + 1);
    }

    @Test
    @DisplayName("findUsages sweeps the project's markup, capped")
    void findUsagesSweeps(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.html"), "<p class=\"hero\"></p>");
        Files.writeString(dir.resolve("b.vue"),
                "<template><i class=\"hero small\"/></template>");
        Files.writeString(dir.resolve("styles.css"), ".hero { }");
        List<CssClasses.Usage> u = CssClasses.findUsages(dir.toFile(), "hero", 50);
        assertThat(u).hasSize(2);
        assertThat(u).extracting(x -> x.file().getName())
                .containsExactlyInAnyOrder("a.html", "b.vue");
        assertThat(CssClasses.findUsages(dir.toFile(), "hero", 1)).hasSize(1);
    }

    // ---- rename (v2.29.0) --------------------------------------------------

    @Test
    @DisplayName("renameInText: every selector, whole names only")
    void renameStylesheet() {
        assertThat(CssClasses.renameInText(
                ".card { } .cardigan { } li.card:hover { } /* .card */",
                false, "card", "tile"))
                .isEqualTo(".tile { } .cardigan { } li.tile:hover { } /* .card */");
    }

    @Test
    @DisplayName("renameInText on markup: attr tokens AND style-region selectors, prose untouched")
    void renameMarkup() {
        String vue = "<template><p class=\"card big\">card prose</p></template>\n"
                + "<style>.card { } .cardigan { }</style>\n";
        assertThat(CssClasses.renameInText(vue, true, "card", "tile"))
                .isEqualTo("<template><p class=\"tile big\">card prose</p></template>\n"
                        + "<style>.tile { } .cardigan { }</style>\n");
    }

    @Test
    @DisplayName("surveyRename counts spans, flags collisions, reports the census honestly")
    void surveyRename(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("styles.css"), ".hero { } .hero { } .taken { }");
        Files.writeString(dir.resolve("a.html"), "<p class=\"hero\"></p>");
        CssClasses.RenameSurvey ok =
                CssClasses.surveyRename(dir.toFile(), "hero", "fresh");
        assertThat(ok.spanCount()).isEqualTo(3);
        assertThat(ok.files()).hasSize(2);
        assertThat(ok.collision()).isFalse();
        assertThat(ok.censusComplete()).isTrue();

        assertThat(CssClasses.surveyRename(dir.toFile(), "hero", "taken")
                .collision())
                .as("renaming onto an existing class is a collision")
                .isTrue();
    }

    @Test
    @DisplayName("a census at the walk cap reports incomplete — the rename must refuse")
    void censusAtCapIsIncomplete(@TempDir Path dir) throws Exception {
        for (int i = 0; i < 61; i++) {
            Files.writeString(dir.resolve("s" + i + ".css"), ".x" + i + " { }");
        }
        assertThat(CssClasses.surveyRename(dir.toFile(), "x1", "y1")
                .censusComplete()).isFalse();
    }

    @Test
    @DisplayName("valid class names: ident family only")
    void validNames() {
        assertThat(CssClasses.validClassName("btn-primary")).isTrue();
        assertThat(CssClasses.validClassName("_private")).isTrue();
        assertThat(CssClasses.validClassName("2col")).isFalse();
        assertThat(CssClasses.validClassName("has space")).isFalse();
        assertThat(CssClasses.validClassName("")).isFalse();
        assertThat(CssClasses.validClassName(".dotted")).isFalse();
    }
}
