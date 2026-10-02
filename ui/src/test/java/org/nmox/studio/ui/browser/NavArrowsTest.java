package org.nmox.studio.ui.browser;

import java.awt.ComponentOrientation;
import javax.swing.JButton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Back points to where the reader's line starts, and turns with the window. */
class NavArrowsTest {

    @Test
    @DisplayName("Back points left for a left-to-right reader and right for a right-to-left one; Forward the other way")
    void theGlyphs() {
        assertThat(NavArrows.back(true)).isEqualTo("←");
        assertThat(NavArrows.forward(true)).isEqualTo("→");
        assertThat(NavArrows.back(false)).isEqualTo("→");
        assertThat(NavArrows.forward(false)).isEqualTo("←");
    }

    @Test
    @DisplayName("the buttons turn when their window's direction does, and turn back")
    void theButtonsFollow() {
        JButton back = new JButton("?");
        JButton forward = new JButton("?");

        NavArrows.follow(back, forward);
        assertThat(back.getText()).isEqualTo("←");
        assertThat(forward.getText()).isEqualTo("→");

        back.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        forward.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        assertThat(back.getText()).isEqualTo("→");
        assertThat(forward.getText()).isEqualTo("←");

        back.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        forward.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        assertThat(back.getText()).isEqualTo("←");
        assertThat(forward.getText()).isEqualTo("→");
    }
}
