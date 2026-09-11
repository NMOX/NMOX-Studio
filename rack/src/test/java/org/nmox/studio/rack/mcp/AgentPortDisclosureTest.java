package org.nmox.studio.rack.mcp;

import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Agent Port's disclosure renders as the wrapped paragraph it is.
 *
 * <p>This sentence is the one the dialog exists to make a person read: what a
 * connected agent can see, and that it can change nothing. v2.84.0 gave it a
 * width-bounded HTML body so it WRAPS instead of laying itself out on one
 * line and being clipped mid-word by the dialog's screen clamp. v2.86.0's
 * markup-render sweep then wrapped the same value in
 * {@code PlainText.plain}, which prepends a space so Swing declines to parse
 * it — and a German walk photographed the result: a screenful of literal
 * {@code <html><body style='width: 720'><b>…} running off the dialog behind a
 * horizontal scrollbar.
 *
 * <p>The sweep also wrote {@code PLAIN-LABEL-EXEMPT} at that line, so the
 * comment and the code disagreed and the comment was right. Both halves are
 * tested here, on the real label: it must RENDER (a guard is a regression),
 * and nothing spliced into it may become markup (an exemption is a claim,
 * and a claim is a test).
 */
class AgentPortDisclosureTest {

    private static final String TOOLS = "diagnostics, ide_context, live_runs";

    @Test
    @DisplayName("the disclosure label is parsed as HTML, so its body width wraps the sentence")
    void theLabelRendersItsMarkup() {
        JLabel label = AgentPortAction.disclosureLabel(57511, TOOLS);
        assertThat(BasicHTML.isHTMLString(label.getText()))
                .as("Swing decides by the FIRST characters; a guard's leading space defeats it")
                .isTrue();
        assertThat(label.getClientProperty(BasicHTML.propertyKey))
                .as("BasicHTML installs the view at setText — absent means the tags paint as text")
                .isNotNull();
    }

    @Test
    @DisplayName("the width-bounded body survives, unitless, so the paragraph actually wraps")
    void theBodyKeepsItsUnitlessWidth() {
        String html = AgentPortAction.disclosureHtml(57511, TOOLS);
        // Swing's CSS honours "width: 720" for a body and ignores "720px"
        assertThat(html).contains("width: " + AgentPortAction.LABEL_WIDTH)
                .doesNotContain(AgentPortAction.LABEL_WIDTH + "px");
    }

    @Test
    @DisplayName("a tool name cannot become markup inside the one authored <html> here")
    void splicedToolNamesAreEscaped() {
        String html = AgentPortAction.disclosureHtml(57511, "<img src=\"http://evil/x\">");
        assertThat(html).as("the exemption's claim, held by construction")
                .doesNotContain("<img").contains("&lt;img");
        assertThat(BasicHTML.isHTMLString(AgentPortAction.disclosureLabel(
                57511, "<img src=\"http://evil/x\">").getText())).isTrue();
    }

    @Test
    @DisplayName("the port number reaches the sentence a person reads")
    void thePortIsNamed() {
        assertThat(AgentPortAction.disclosureHtml(57511, TOOLS)).contains("57511");
    }
}
