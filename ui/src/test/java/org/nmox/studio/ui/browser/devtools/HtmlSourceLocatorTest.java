package org.nmox.studio.ui.browser.devtools;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.ui.browser.devtools.DomSnapshotParser.DomNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Inspect-to-source, half two (v1.357.0): a DOM element maps to the
 * source line that produced it — by unique id when it has one, by
 * document-order tag counting when it doesn't — and REFUSES (-1) for
 * elements the source cannot honestly contain.
 */
class HtmlSourceLocatorTest {

    /** A tiny snapshot tree mirroring the fixture page below. */
    private static DomNode tree() {
        DomNode html = node("html", "", List.of());
        DomNode body = node("body", "", List.of(1));
        DomNode div1 = node("div", "", List.of(1, 0));
        DomNode div2 = node("div", "", List.of(1, 1));
        DomNode p = node("p", "intro", List.of(1, 1, 0));
        html.children.add(node("head", "", List.of(0)));
        html.children.add(body);
        body.children.add(div1);
        body.children.add(div2);
        div2.children.add(p);
        return html;
    }

    private static DomNode node(String tag, String id, List<Integer> path) {
        // package-private ctor — this test lives beside the parser
        return new DomNode(tag, id, "", List.of(), new java.util.ArrayList<>(path));
    }

    private static final String PAGE = """
            <html>
            <head><title>t</title></head>
            <body>
              <div class="first">one</div>
              <div class="second">
                <p id="intro">hello</p>
              </div>
            </body>
            </html>
            """;

    @Test
    @DisplayName("an id-carrying element is found by its id attribute")
    void byId() {
        DomNode root = tree();
        DomNode p = root.children.get(1).children.get(1).children.get(0);
        assertThat(HtmlSourceLocator.lineOf(PAGE, p, root)).isEqualTo(6);
    }

    @Test
    @DisplayName("an id-less element is found as the Nth same-tag occurrence in document order")
    void byNthTag() {
        DomNode root = tree();
        DomNode div2 = root.children.get(1).children.get(1);
        assertThat(HtmlSourceLocator.lineOf(PAGE, div2, root)).isEqualTo(5);
        DomNode div1 = root.children.get(1).children.get(0);
        assertThat(HtmlSourceLocator.lineOf(PAGE, div1, root)).isEqualTo(4);
    }

    @Test
    @DisplayName("a tag inside a comment or a script body is not an element")
    void commentsAndScriptsNeutralized() {
        String page = """
                <html><body>
                <!-- <div>ghost</div> -->
                <script>var s = '<div>fake</div>';</script>
                <div>real</div>
                </body></html>
                """;
        DomNode html = node("html", "", List.of());
        DomNode body = node("body", "", List.of(0));
        DomNode div = node("div", "", List.of(0, 1));
        html.children.add(body);
        body.children.add(node("script", "", List.of(0, 0)));
        body.children.add(div);
        assertThat(HtmlSourceLocator.lineOf(page, div, html))
                .as("the ghost and fake divs must not shadow the real one")
                .isEqualTo(4);
    }

    @Test
    @DisplayName("script-generated elements refuse instead of jumping somewhere wrong")
    void scriptGeneratedRefuses() {
        // the DOM holds THREE divs but the source only two: the third is
        // script-made and must map to -1, not to a wrong line
        String page = "<html><body>\n<div>a</div>\n<div>b</div>\n</body></html>\n";
        DomNode html = node("html", "", List.of());
        DomNode body = node("body", "", List.of(0));
        html.children.add(body);
        DomNode d1 = node("div", "", List.of(0, 0));
        DomNode d2 = node("div", "", List.of(0, 1));
        DomNode d3 = node("div", "", List.of(0, 2));
        body.children.add(d1);
        body.children.add(d2);
        body.children.add(d3);
        assertThat(HtmlSourceLocator.lineOf(page, d3, html)).isEqualTo(-1);
        assertThat(HtmlSourceLocator.lineOf(page, d2, html)).isEqualTo(3);
    }

    @Test
    @DisplayName("neutralize preserves line structure exactly")
    void neutralizePreservesLines() {
        String page = "<a>\n<!-- x\ny -->\n<b>\n";
        String n = HtmlSourceLocator.neutralize(page);
        assertThat(n.chars().filter(c -> c == '\n').count())
                .isEqualTo(page.chars().filter(c -> c == '\n').count());
        assertThat(n).doesNotContain("x");
    }
}
