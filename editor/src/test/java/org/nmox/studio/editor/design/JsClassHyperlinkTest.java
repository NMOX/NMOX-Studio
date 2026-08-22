package org.nmox.studio.editor.design;

import javax.swing.text.PlainDocument;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkType;

/**
 * The JS class jump's document plumbing (v2.30.0), on a plain document
 * — the span answers only inside a recognized call's string, the
 * edit-version cache serves hovers, and oversized documents refuse.
 * The click's project half rides {@link CssClasses#scanProject} whose
 * truths live in {@link CssClassesTest}.
 */
class JsClassHyperlinkTest {

    private static PlainDocument doc(String text) throws Exception {
        PlainDocument d = new PlainDocument();
        d.insertString(0, text, null);
        return d;
    }

    @Test
    @DisplayName("the span exists exactly inside a recognized call's class name")
    void spanPlumbing() throws Exception {
        String js = "document.querySelector('.hero'); log('.hero');";
        PlainDocument d = doc(js);
        JsClassHyperlink link = new JsClassHyperlink();
        int inCall = js.indexOf("hero") + 1;

        assertThat(link.getSupportedHyperlinkTypes())
                .containsExactly(HyperlinkType.GO_TO_DECLARATION);
        assertThat(link.isHyperlinkPoint(d, inCall, HyperlinkType.GO_TO_DECLARATION)).isTrue();
        assertThat(link.getHyperlinkSpan(d, inCall, HyperlinkType.GO_TO_DECLARATION))
                .containsExactly(js.indexOf("hero"), js.indexOf("hero") + 4);
        assertThat(link.getTooltipText(d, inCall, HyperlinkType.GO_TO_DECLARATION))
                .contains("class's rule");

        int inLog = js.lastIndexOf("hero") + 1;
        assertThat(link.isHyperlinkPoint(d, inLog, HyperlinkType.GO_TO_DECLARATION)).isFalse();
        assertThat(link.getHyperlinkSpan(d, inLog, HyperlinkType.GO_TO_DECLARATION)).isNull();
    }

    @Test
    @DisplayName("edits move the cached text; hovers between edits reuse it")
    void cacheFollowsEdits() throws Exception {
        PlainDocument d = doc("el.closest('.old')");
        JsClassHyperlink link = new JsClassHyperlink();
        int at = 14;
        assertThat(link.isHyperlinkPoint(d, at, HyperlinkType.GO_TO_DECLARATION)).isTrue();
        // hover again, no edit: served from the version cache
        assertThat(link.isHyperlinkPoint(d, at, HyperlinkType.GO_TO_DECLARATION)).isTrue();
        d.remove(0, d.getLength());
        d.insertString(0, "notACall('.old')", null);
        assertThat(link.isHyperlinkPoint(d, 12, HyperlinkType.GO_TO_DECLARATION))
                .as("the cache must not serve the pre-edit text")
                .isFalse();
    }
}
