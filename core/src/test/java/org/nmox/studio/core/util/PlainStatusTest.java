package org.nmox.studio.core.util;

import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlainStatusTest {

    @Test
    @DisplayName("a status text that would read as markup is defeated by Swing's own check; ordinary text passes untouched")
    void headNeverReadsAsMarkup() {
        String hostile = "<html><img src=\"http://evil/x\"> failed";
        assertThat(BasicHTML.isHTMLString(hostile)).as("the premise: Swing would render this").isTrue();
        assertThat(BasicHTML.isHTMLString(PlainStatus.text(hostile))).isFalse();
        assertThat(BasicHTML.isHTMLString(PlainStatus.text("  <HTML>upper"))).isFalse();
        assertThat(BasicHTML.isHTMLString(PlainStatus.text("<htmlx"))).isFalse();
        assertThat(PlainStatus.text("Saved to style.css")).isEqualTo("Saved to style.css");
        assertThat(PlainStatus.text(null)).isEmpty();
        JLabel label = new JLabel(PlainStatus.text(hostile));
        assertThat(label.getClientProperty(BasicHTML.propertyKey)).as("no HTML view installed").isNull();
    }
}
