package org.nmox.studio.editor.design;

import java.awt.Color;
import javax.swing.text.PlainDocument;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkType;

/**
 * The picker's hyperlink half (v1.229.0), on a plain document — the
 * span answers only inside a color literal, covers the whole literal,
 * refuses absurdly large documents, and advertises the goto gesture
 * the platform routes ⌘-clicks through. The dialog half is Swing and
 * lives outside the testable surface; the replacement FORMAT is
 * pinned in {@link CssColorsTest}.
 */
class CssColorHyperlinkTest {

    private static PlainDocument doc(String text) throws Exception {
        PlainDocument d = new PlainDocument();
        d.insertString(0, text, null);
        return d;
    }

    @Test
    @DisplayName("the span exists exactly inside a literal and covers all of it")
    void spanInsideLiteral() throws Exception {
        String css = "a{color:#336699; background: tomato}";
        PlainDocument d = doc(css);
        CssColorHyperlink link = new CssColorHyperlink();
        int hexStart = css.indexOf("#336699");

        assertThat(link.isHyperlinkPoint(d, hexStart + 3, HyperlinkType.GO_TO_DECLARATION)).isTrue();
        assertThat(link.getHyperlinkSpan(d, hexStart + 3, HyperlinkType.GO_TO_DECLARATION))
                .containsExactly(hexStart, hexStart + "#336699".length());

        int tomato = css.indexOf("tomato");
        assertThat(link.getHyperlinkSpan(d, tomato, HyperlinkType.GO_TO_DECLARATION))
                .containsExactly(tomato, tomato + "tomato".length());

        // outside any literal: no hyperlink
        assertThat(link.isHyperlinkPoint(d, 0, HyperlinkType.GO_TO_DECLARATION)).isFalse();
        assertThat(link.getHyperlinkSpan(d, 0, HyperlinkType.GO_TO_DECLARATION)).isNull();
    }

    @Test
    @DisplayName("the scan is cached by document version: same doc, no edit → same list instance (v1.234.0)")
    void scanCachedByDocumentVersion() throws Exception {
        PlainDocument d = doc("a{color:#336699}");
        var first = CssColorHyperlink.spansFor(d);
        var again = CssColorHyperlink.spansFor(d);
        assertThat(again)
                .as("⌘-hover calls this per MOUSE MOVE on the EDT — an unchanged "
                        + "document must not pay a fresh getText + four regex passes")
                .isSameAs(first);

        d.insertString(d.getLength(), " b{c:tomato}", null);
        var after = CssColorHyperlink.spansFor(d);
        assertThat(after).isNotSameAs(first);
        assertThat(after).hasSize(2); // the edit's new literal is seen
    }

    @Test
    @DisplayName("the seeded color is the literal's color")
    void spanCarriesColor() throws Exception {
        PlainDocument d = doc("a{c:rgb(255, 0, 0)}");
        CssColors.ColorSpan span = CssColorHyperlink.spanAt(d, "a{c:rgb".length());
        assertThat(span).isNotNull();
        assertThat(span.color()).isEqualTo(new Color(255, 0, 0));
    }

    @Test
    @DisplayName("an absurdly large document refuses to scan per keystroke")
    void oversizedDocRefused() throws Exception {
        PlainDocument d = doc("#fff ".repeat(120_000)); // 600k chars > cap
        assertThat(CssColorHyperlink.spanAt(d, 1)).isNull();
    }

    @Test
    @DisplayName("advertises GO_TO_DECLARATION and a tooltip naming the gesture")
    void contractSurface() throws Exception {
        CssColorHyperlink link = new CssColorHyperlink();
        assertThat(link.getSupportedHyperlinkTypes())
                .containsExactly(HyperlinkType.GO_TO_DECLARATION);
        assertThat(link.getTooltipText(doc("a{}"), 0, HyperlinkType.GO_TO_DECLARATION))
                .contains("Pick a color");
    }

    @Test
    @DisplayName("a click outside any literal is a no-op, not an exception")
    void clickOutsideLiteralNoOp() throws Exception {
        // no span at offset 0 → performClickAction returns before any UI
        new CssColorHyperlink().performClickAction(
                doc("a{color:#fff}"), 0, HyperlinkType.GO_TO_DECLARATION);
    }
}
