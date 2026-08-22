package org.nmox.studio.editor.fullstack;

import javax.swing.text.PlainDocument;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkType;

/**
 * The shared jump skeleton through both fullstack hyperlinks
 * (v2.31.0), on plain documents — spans only in their recognized
 * contexts, the edit-version cache follows edits, oversized documents
 * refuse, and the click path runs to its honest refusal with no
 * project attached (the plumbing's whole surface, headless).
 */
class ProjectJumpHyperlinkTest {

    private static PlainDocument doc(String text) throws Exception {
        PlainDocument d = new PlainDocument();
        d.insertString(0, text, null);
        return d;
    }

    @Test
    @DisplayName("env hyperlink: span in the accessor, prose refused, tooltip honest")
    void envSpanPlumbing() throws Exception {
        String js = "const u = process.env.DATABASE_URL; log(DATABASE_URL);";
        PlainDocument d = doc(js);
        EnvKeyHyperlink link = new EnvKeyHyperlink();
        int in = js.indexOf("DATABASE_URL") + 2;
        assertThat(link.getSupportedHyperlinkTypes())
                .containsExactly(HyperlinkType.GO_TO_DECLARATION);
        assertThat(link.isHyperlinkPoint(d, in, HyperlinkType.GO_TO_DECLARATION)).isTrue();
        assertThat(link.getHyperlinkSpan(d, in, HyperlinkType.GO_TO_DECLARATION))
                .containsExactly(js.indexOf("DATABASE_URL"), js.indexOf("DATABASE_URL") + 12);
        assertThat(link.getTooltipText(d, in, HyperlinkType.GO_TO_DECLARATION))
                .contains(".env");
        int prose = js.lastIndexOf("DATABASE_URL") + 2;
        assertThat(link.isHyperlinkPoint(d, prose, HyperlinkType.GO_TO_DECLARATION)).isFalse();
        // the click path with no project: runs to its refusal, no throw
        link.performClickAction(d, in, HyperlinkType.GO_TO_DECLARATION);
    }

    @Test
    @DisplayName("route hyperlink: span in a client call, cache follows edits, oversize refuses")
    void routeSpanPlumbing() throws Exception {
        String js = "await fetch('/api/users');";
        PlainDocument d = doc(js);
        FetchRouteHyperlink link = new FetchRouteHyperlink();
        int in = js.indexOf("/api/users") + 3;
        assertThat(link.isHyperlinkPoint(d, in, HyperlinkType.GO_TO_DECLARATION)).isTrue();
        assertThat(link.getHyperlinkSpan(d, in, HyperlinkType.GO_TO_DECLARATION))
                .containsExactly(js.indexOf("/api/users"), js.indexOf("/api/users") + 10);
        assertThat(link.getTooltipText(d, in, HyperlinkType.GO_TO_DECLARATION))
                .contains("route");
        // cached hover, then an edit must not serve stale text
        assertThat(link.isHyperlinkPoint(d, in, HyperlinkType.GO_TO_DECLARATION)).isTrue();
        d.remove(0, d.getLength());
        d.insertString(0, "log('/api/users');", null);
        assertThat(link.isHyperlinkPoint(d, 8, HyperlinkType.GO_TO_DECLARATION))
                .as("the cache must not serve the pre-edit text")
                .isFalse();
        link.performClickAction(d, 8, HyperlinkType.GO_TO_DECLARATION);

        PlainDocument big = doc("await fetch('/x');" + "y".repeat(500_001));
        assertThat(new FetchRouteHyperlink()
                .isHyperlinkPoint(big, 14, HyperlinkType.GO_TO_DECLARATION))
                .as("a document past the scan ceiling refuses")
                .isFalse();
    }
}
