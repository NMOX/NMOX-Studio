package org.nmox.studio.rack.ui.controls;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An LCD's shown text is what the panel paints in either mode (v2.162.0).
 * The docs forge read KVASIR's three-line verdict through {@code getText()},
 * which is the single-line field only, and saw a blank faceplate over a live
 * one; {@code getShownText()} is the read that matches the picture.
 */
class LcdShownTextTest {

    @Test
    @DisplayName("a multi-line LCD shows its appended lines while getText stays the single-line field")
    void multiLineShowsItsLines() {
        LcdDisplay lcd = new LcdDisplay(430, 3);
        lcd.appendLine("# Diagnosis");
        lcd.appendLine("The test expected 'ok' and got 'okay'.");
        assertThat(lcd.getText()).isEmpty();
        assertThat(lcd.getShownText())
                .contains("# Diagnosis")
                .contains("The test expected 'ok' and got 'okay'.");
    }

    @Test
    @DisplayName("a single-line LCD shows exactly its text")
    void singleLineShowsItsText() {
        LcdDisplay lcd = new LcdDisplay(200, 1);
        lcd.setText("READY");
        assertThat(lcd.getShownText()).isEqualTo("READY");
    }
}
