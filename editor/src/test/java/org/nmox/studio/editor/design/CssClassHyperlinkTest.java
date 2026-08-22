package org.nmox.studio.editor.design;

import javax.swing.text.PlainDocument;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkType;

/**
 * The markup class jump's document plumbing (v2.27.0), on a plain
 * document — spans only inside {@code class="…"} values, prose
 * refused, the oversized-document guard honest. The click's
 * region-then-project resolution rides {@link CssClasses} truths
 * pinned in {@link CssClassesTest}.
 */
class CssClassHyperlinkTest {

    private static PlainDocument doc(String text) throws Exception {
        PlainDocument d = new PlainDocument();
        d.insertString(0, text, null);
        return d;
    }

    @Test
    @DisplayName("the span exists exactly inside a class attribute value")
    void spanPlumbing() throws Exception {
        String html = "<p class=\"hero big\">hero prose</p>";
        PlainDocument d = doc(html);
        CssClassHyperlink link = new CssClassHyperlink();
        int inAttr = html.indexOf("hero") + 1;

        assertThat(link.getSupportedHyperlinkTypes())
                .containsExactly(HyperlinkType.GO_TO_DECLARATION);
        assertThat(link.isHyperlinkPoint(d, inAttr, HyperlinkType.GO_TO_DECLARATION)).isTrue();
        assertThat(link.getHyperlinkSpan(d, inAttr, HyperlinkType.GO_TO_DECLARATION))
                .containsExactly(html.indexOf("hero"), html.indexOf("hero") + 4);
        assertThat(link.getTooltipText(d, inAttr, HyperlinkType.GO_TO_DECLARATION))
                .contains("class's rule");

        int inProse = html.indexOf("hero prose") + 1;
        assertThat(link.isHyperlinkPoint(d, inProse, HyperlinkType.GO_TO_DECLARATION)).isFalse();
    }

    @Test
    @DisplayName("a document past the scan ceiling refuses instead of scanning")
    void oversizedDocumentRefuses() throws Exception {
        PlainDocument d = doc("<p class=\"hero\">" + "x".repeat(500_001) + "</p>");
        CssClassHyperlink link = new CssClassHyperlink();
        assertThat(link.isHyperlinkPoint(d, 12, HyperlinkType.GO_TO_DECLARATION)).isFalse();
    }
}
