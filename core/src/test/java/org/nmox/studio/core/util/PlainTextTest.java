package org.nmox.studio.core.util;

import javax.swing.JLabel;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlainTextTest {

    @Test
    @DisplayName("a markup head gains one space; anything else is untouched; null stays null")
    void headRule() {
        assertThat(PlainText.plain("<html><b>x</b>")).isEqualTo(" <html><b>x</b>");
        assertThat(PlainText.plain("  <HTML>x")).isEqualTo(" <HTML>x");
        assertThat(PlainText.plain("build — <html> later")).isEqualTo("build — <html> later");
        assertThat(PlainText.plain("")).isEmpty();
        assertThat(PlainText.plain(null)).isNull();
    }

    @Test
    @DisplayName("the premise: Swing reads the head only, and a leading space defeats it")
    void swingReadsTheHeadOnly() {
        assertThat(BasicHTML.isHTMLString("<html><b>x</b>")).isTrue();
        assertThat(BasicHTML.isHTMLString(PlainText.plain("<html><b>x</b>"))).isFalse();
    }

    @Test
    @DisplayName("the premise the tooltip half rests on: html.disable on the component never reaches its tooltip")
    void componentPropertyDoesNotReachTheTooltip() throws Exception {
        boolean[] rendered = new boolean[2];
        SwingUtilities.invokeAndWait(() -> {
            JLabel disabled = PlainTables.plain(new JLabel("x"));
            disabled.setToolTipText("<html><b>x</b>");
            JToolTip tip = disabled.createToolTip();
            tip.setTipText(disabled.getToolTipText());
            rendered[0] = tip.getClientProperty(BasicHTML.propertyKey) != null;

            JLabel plain = new JLabel("x");
            plain.setToolTipText(PlainText.plain("<html><b>x</b>"));
            JToolTip tip2 = plain.createToolTip();
            tip2.setTipText(plain.getToolTipText());
            rendered[1] = tip2.getClientProperty(BasicHTML.propertyKey) != null;
        });
        assertThat(rendered[0]).as("the property on the component does not stop the tooltip's html view").isTrue();
        assertThat(rendered[1]).as("the text guard does").isFalse();
    }
}
