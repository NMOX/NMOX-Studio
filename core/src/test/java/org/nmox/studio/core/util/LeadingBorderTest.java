package org.nmox.studio.core.util;

import java.awt.ComponentOrientation;
import java.awt.Insets;
import javax.swing.JLabel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A margin named for the reader moves with the reader's direction. */
class LeadingBorderTest {

    @Test
    @DisplayName("the leading margin is on the left for a left-to-right reader and on the right for a right-to-left one")
    void followsTheDirection() {
        JLabel label = new JLabel("hint");
        label.setBorder(new LeadingBorder(2, 18, 3, 12));

        assertThat(label.getInsets()).isEqualTo(new Insets(2, 18, 3, 12));

        label.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        assertThat(label.getInsets()).as("no new border: the same one, asked again").isEqualTo(new Insets(2, 12, 3, 18));

        label.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        assertThat(label.getInsets()).isEqualTo(new Insets(2, 18, 3, 12));
    }

    @Test
    @DisplayName("with no component to ask, it is left-to-right")
    void noComponent() {
        assertThat(new LeadingBorder(1, 2, 3, 4).getBorderInsets(null, new Insets(0, 0, 0, 0)))
                .isEqualTo(new Insets(1, 2, 3, 4));
    }
}
