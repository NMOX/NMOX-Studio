package org.nmox.studio.core.util;

import javax.swing.JTextArea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlainDialogsTest {

    @Test
    @DisplayName("a message that looks like markup stays characters: read-only, wrapping, named, whole")
    void plainNeverInterprets() {
        String hostile = "Could not open x.js — <html><img src=\"http://evil/x\"> " + "long ".repeat(30) + "\nsecond line";
        JTextArea area = PlainDialogs.plain(hostile, "Message");
        assertThat(area.getText()).isEqualTo(hostile);
        assertThat(area.isEditable()).isFalse();
        assertThat(area.getLineWrap()).isTrue();
        assertThat(area.getWrapStyleWord()).isTrue();
        assertThat(area.isFocusable()).as("never a focus stop — the safe-default button keeps the keyboard").isFalse();
        assertThat(area.getAccessibleContext().getAccessibleName()).isEqualTo("Message");
        assertThat(area.getAccessibleContext().getAccessibleText().getCharCount()).isEqualTo(hostile.length());
    }

    @Test
    @DisplayName("columns follow the longest line within a sane band; null text is empty")
    void sizing() {
        assertThat(PlainDialogs.plain("short", "n").getColumns()).isEqualTo(24);
        assertThat(PlainDialogs.plain("x".repeat(200) + "\ny", "n").getColumns()).isEqualTo(72);
        assertThat(PlainDialogs.plain("x".repeat(40), "n").getColumns()).isEqualTo(40);
        assertThat(PlainDialogs.plain(null, "n").getText()).isEmpty();
    }
}
