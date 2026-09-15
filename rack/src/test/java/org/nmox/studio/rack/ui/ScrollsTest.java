package org.nmox.studio.rack.ui;

import java.awt.ComponentOrientation;
import java.awt.Dimension;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A mirrored view opens where its reader starts (v2.162.0): the forge's
 * Hebrew and Arabic Task Rack pictures showed every device face cut off,
 * because a right-to-left rack wider than its viewport opened at the far
 * end. Mutation-proven: starting a right-to-left pane at zero fails
 * {@code mirroredPaneStartsAtTheFarSide} by name.
 */
class ScrollsTest {

    private static JScrollPane wideScrollPane(ComponentOrientation orientation) {
        JPanel wide = new JPanel();
        wide.setPreferredSize(new Dimension(2_000, 200));
        JScrollPane pane = new JScrollPane(wide);
        pane.setSize(500, 200);
        pane.applyComponentOrientation(orientation);
        pane.doLayout();
        pane.getViewport().doLayout();
        pane.getHorizontalScrollBar().setValues(0, 500, 0, 2_000);
        return pane;
    }

    @Test
    @DisplayName("a mirrored pane starts at the side a right-to-left reader reads from")
    void mirroredPaneStartsAtTheFarSide() {
        JScrollPane pane = wideScrollPane(ComponentOrientation.RIGHT_TO_LEFT);
        Scrolls.toLogicalStart(pane);
        JScrollBarState bar = JScrollBarState.of(pane);
        assertThat(bar.value())
                .as("the rightmost column is where the reading starts")
                .isEqualTo(bar.maximum() - bar.visible());
        assertThat(bar.value()).isGreaterThan(0);
    }

    @Test
    @DisplayName("a left-to-right pane starts at zero")
    void leftToRightPaneStartsAtZero() {
        JScrollPane pane = wideScrollPane(ComponentOrientation.LEFT_TO_RIGHT);
        pane.getHorizontalScrollBar().setValue(800);
        Scrolls.toLogicalStart(pane);
        assertThat(pane.getHorizontalScrollBar().getValue()).isZero();
    }

    @Test
    @DisplayName("a view narrower than its viewport has nowhere to go")
    void narrowViewStaysPut() {
        JPanel narrow = new JPanel();
        narrow.setPreferredSize(new Dimension(100, 100));
        JScrollPane pane = new JScrollPane(narrow);
        pane.setSize(500, 200);
        pane.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        pane.doLayout();
        Scrolls.toLogicalStart(pane); // must not throw, must not move anything
        assertThat(pane.getHorizontalScrollBar().getValue()).isZero();
    }

    /** The three numbers the rule reads, captured so the assertion names them. */
    private record JScrollBarState(int value, int maximum, int visible) {

        static JScrollBarState of(JScrollPane pane) {
            javax.swing.JScrollBar bar = pane.getHorizontalScrollBar();
            return new JScrollBarState(bar.getValue(), bar.getMaximum(), bar.getVisibleAmount());
        }
    }
}
