package org.nmox.studio.ui.util;

import java.awt.Dimension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DialogFitTest {

    @Test
    @DisplayName("A body wider than the screen is clamped to the fraction; one that fits keeps its natural size")
    void clamps() {
        Dimension screen = new Dimension(1372, 892);
        Dimension wide = DialogFit.fit(new Dimension(1500, 700), screen);
        assertThat(wide.width).as("clamped to the fraction of the screen width").isEqualTo((int) (1372 * DialogFit.FRACTION));
        assertThat(wide.height).as("the height already fit").isEqualTo(700);
        assertThat(DialogFit.fit(new Dimension(600, 400), screen)).isEqualTo(new Dimension(600, 400));
        assertThat(DialogFit.fit(new Dimension(1500, 700), screen).width).as("never the whole screen: the dialog chrome needs room")
                .isLessThan(1372);
    }

    @Test
    @DisplayName("The floor holds on a tiny screen — an unreadable body is worse than an overflowing one")
    void floor() {
        assertThat(DialogFit.fit(new Dimension(800, 600), new Dimension(300, 200)))
                .isEqualTo(new Dimension(DialogFit.FLOOR.width, DialogFit.FLOOR.height));
    }
}
