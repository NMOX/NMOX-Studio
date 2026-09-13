package org.nmox.studio.ui.rtl;

import java.util.Locale;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.nmox.studio.core.util.TextDirection;

import static org.assertj.core.api.Assertions.assertThat;

class RightToLeftApplyTest {

    @AfterEach
    void clearForce() {
        System.clearProperty(TextDirection.FORCE);
    }

    @Test
    @DisplayName("applying orientation reaches the whole tree")
    void theSweepReachesTheTree() {
        System.setProperty(TextDirection.FORCE, "true");
        JPanel root = new JPanel();
        JLabel child = new JLabel("x");
        root.add(child);

        RightToLeft.apply(root);

        assertThat(root.getComponentOrientation().isLeftToRight()).isFalse();
        assertThat(child.getComponentOrientation().isLeftToRight()).isFalse();
    }

    @Test
    @DisplayName("a left-to-right build is left exactly as authored")
    void nothingMovesForAnLtrLocale() {
        System.setProperty(TextDirection.FORCE, "false");
        JPanel root = new JPanel();
        RightToLeft.apply(root);
        assertThat(root.getComponentOrientation().isLeftToRight()).isTrue();
    }

    @Test
    @DisplayName("a null component is not an error")
    void nullIsSurvivable() {
        RightToLeft.apply(null);
    }

    @Test
    @DisplayName("the direction asked for is the one the locale implies")
    void directionFollowsTheLocale() {
        assertThat(TextDirection.orientation(Locale.forLanguageTag("he")).isLeftToRight())
                .isFalse();
        assertThat(TextDirection.orientation(Locale.ENGLISH).isLeftToRight()).isTrue();
    }
}
