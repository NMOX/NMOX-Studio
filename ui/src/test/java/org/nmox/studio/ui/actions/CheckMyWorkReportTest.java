package org.nmox.studio.ui.actions;

import javax.swing.JTextArea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Check My Work report is plain text (v2.85.0): checkpoint labels
 * and hints come from the catalog — drop-in data from anywhere — and a
 * String message becomes JLabels per wrapped fragment, where a fragment
 * beginning with {@code <html>} renders (the v1.306.0 class). A text
 * area never interprets markup, and reads whole to a screen reader.
 */
class CheckMyWorkReportTest {

    @Test
    @DisplayName("a hint that looks like markup is shown as characters, in one read-only wrapping area with a name")
    void reportIsPlainText() {
        String hostile = "2 of 3 checks pass.\n\n  ✗ You added <html><img src=\"http://evil/x\"> a thing\n      "
                + "the hint <html><b>bold</b> " + "long ".repeat(40) + "\n";
        JTextArea area = CheckMyWorkAction.reportComponent(hostile);
        assertThat(area.getText()).as("verbatim — nothing interpreted, nothing fetched").isEqualTo(hostile);
        assertThat(area.isEditable()).isFalse();
        assertThat(area.getLineWrap()).as("wraps instead of fragmenting").isTrue();
        assertThat(area.getWrapStyleWord()).isTrue();
        assertThat(area.getAccessibleContext().getAccessibleName()).isEqualTo("Check My Work report");
        assertThat(area.getAccessibleContext().getAccessibleText().getCharCount())
                .as("the whole report is one accessible text").isEqualTo(hostile.length());
    }
}
