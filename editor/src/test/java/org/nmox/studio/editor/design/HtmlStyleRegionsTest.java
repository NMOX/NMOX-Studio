package org.nmox.studio.editor.design;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The HTML style-region boundary (v2.22.0): swatches and the picker
 * reach HTML, but ONLY inside <style> blocks and style="…" attribute
 * values — in HTML, bare `tomato` is prose. The tests pin the regions'
 * exact offsets (a shifted span paints the wrong characters), both
 * quote styles, the comment blanking, and — hardest to notice missing
 * — that prose colors stay unswatched.
 */
class HtmlStyleRegionsTest {

    @Test
    @DisplayName("prose colors never swatch; style regions do, at exact offsets")
    void proseVsStyle() {
        String html = "<p>tomato soup with #336699 sky</p>\n"
                + "<h1 style=\"color: tomato\">hi</h1>\n"
                + "<style>\n.x { background: #336699; }\n</style>\n";
        List<CssColors.ColorSpan> spans = HtmlStyleRegions.scan(html);
        assertThat(spans).hasSize(2);
        for (CssColors.ColorSpan s : spans) {
            String literal = html.substring(s.start(), s.end());
            assertThat(literal).isIn("tomato", "#336699");
            // the exact-offset law: the span's text IS the literal
        }
        // the prose tomato (offset 3) and prose hex are untouched
        assertThat(HtmlStyleRegions.inStyle(html, 3)).isFalse();
        assertThat(HtmlStyleRegions.inStyle(html, html.indexOf("#336699"))).isFalse();
        assertThat(HtmlStyleRegions.inStyle(html, html.indexOf("color: tomato") + 8)).isTrue();
    }

    @Test
    @DisplayName("single-quoted style attributes region too")
    void singleQuotes() {
        String html = "<div style='color:#ff0000'>x</div>";
        List<CssColors.ColorSpan> spans = HtmlStyleRegions.scan(html);
        assertThat(spans).hasSize(1);
        assertThat(html.substring(spans.get(0).start(), spans.get(0).end()))
                .isEqualTo("#ff0000");
    }

    @Test
    @DisplayName("commented-out style blocks and attributes contribute nothing")
    void commentsBlanked() {
        String html = "<!-- <style>.x{color:tomato}</style> -->\n"
                + "<!-- <p style=\"color:#123456\">x</p> -->\n"
                + "<em>plain</em>";
        assertThat(HtmlStyleRegions.scan(html)).isEmpty();
    }

    @Test
    @DisplayName("a case-insensitive STYLE block regions like a lowercase one")
    void caseInsensitive() {
        String html = "<STYLE>.y { color: rgb(1, 2, 3); }</STYLE>";
        assertThat(HtmlStyleRegions.scan(html)).hasSize(1);
    }
    @Test
    @DisplayName("tokens declared in one region resolve var() usages in another")
    void tokensAcrossRegions() {
        String html = "<style>:root { --brand: #1E90FF; }</style>\n"
                + "<h1 style=\"color: var(--brand)\">x</h1>\n"
                + "<p>prose var(--brand) stays prose</p>";
        java.util.Map<String, CssTokens.Token> d = HtmlStyleRegions.declarations(html);
        assertThat(d).containsKey("--brand");
        assertThat(html.substring(d.get("--brand").offset()))
                .startsWith("--brand");
        java.util.List<CssColors.ColorSpan> spans =
                HtmlStyleRegions.varUsageColorSpans(html);
        // the attribute usage resolves; the PROSE var(--brand) does not
        assertThat(spans).hasSize(1);
        assertThat(html.substring(spans.get(0).start(), spans.get(0).end()))
                .isEqualTo("--brand");
        assertThat(spans.get(0).start())
                .isGreaterThan(html.indexOf("style=\"color"));
    }

    @Test
    @DisplayName("the project scan reads tokens from .html style blocks")
    void scanReadsHtml(@org.junit.jupiter.api.io.TempDir java.io.File dir) throws Exception {
        java.io.File f = new java.io.File(dir, "page.html");
        java.nio.file.Files.writeString(f.toPath(),
                "<style>:root { --ink: #222222; }</style>\n"
                + "<p>--fake: red is prose, not a declaration</p>");
        java.util.List<CssTokens.ProjectToken> tokens = CssTokens.scanProject(dir);
        assertThat(tokens).anySatisfy(t -> {
            assertThat(t.name()).isEqualTo("--ink");
            assertThat(t.value()).isEqualTo("#222222");
        });
        assertThat(tokens).noneSatisfy(t -> assertThat(t.name()).isEqualTo("--fake"));
    }

}
