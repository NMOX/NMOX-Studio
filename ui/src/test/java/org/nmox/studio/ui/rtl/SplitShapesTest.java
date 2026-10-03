package org.nmox.studio.ui.rtl;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;
import javax.swing.JSplitPane;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.nmox.studio.core.util.TextDirection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The first orientation sweep over a window must not cost a split pane its
 * divider (3.5), and a right-to-left sweep exchanges a HORIZONTAL split's
 * sides, mirrors its divider and flips its resize weight, undone by a sweep
 * back (3.5.12, ledger 127 decided by two right-to-left readers); the
 * bundled runtime's {@code JSplitPane.setComponentOrientation} gets each of
 * these wrong on its own (see {@link SplitShapes}).
 */
class SplitShapesTest {

    @AfterEach
    void clearForce() {
        System.clearProperty(TextDirection.FORCE);
    }

    /** A pane whose preferred width is far from where its author put the divider. */
    private static JPanel sized(int w, int h) {
        JPanel p = new JPanel();
        p.setPreferredSize(new Dimension(w, h));
        p.setMinimumSize(new Dimension(20, 20));
        return p;
    }

    /** Headless, nothing has a peer and {@code validate()} lays nothing out: do it by hand. */
    private static void layOut(java.awt.Container c) {
        c.doLayout();
        for (java.awt.Component child : c.getComponents()) {
            if (child instanceof java.awt.Container k) {
                layOut(k);
            }
        }
    }

    /** Lay the tree out and paint it once, the way a window that has been shown has been. */
    private static void show(JPanel root) {
        root.setSize(1000, 600);
        layOut(root);
        BufferedImage img = new BufferedImage(1000, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            root.paint(g);
        } finally {
            g.dispose();
        }
        layOut(root);
    }

    private static JPanel holding(JSplitPane split) {
        JPanel root = new JPanel(new BorderLayout());
        root.add(split, BorderLayout.CENTER);
        return root;
    }

    @Test
    @DisplayName("a left-to-right sweep leaves the divider where its author put it")
    void theDividerSurvivesTheFirstSweep() {
        System.setProperty(TextDirection.FORCE, "false");
        JPanel tree = sized(129, 400);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tree, sized(600, 400));
        split.setDividerLocation(280);
        JPanel root = holding(split);
        show(root);
        int authored = tree.getWidth();
        assertThat(authored).as("as authored, before any sweep").isBetween(275, 280);

        RightToLeft.apply(root);
        layOut(root);

        assertThat(tree.getWidth()).as("the connection tree, after the first sweep").isEqualTo(authored);
        assertThat(split.getLeftComponent()).isSameAs(tree);
    }

    @Test
    @DisplayName("a vertical split keeps its top on top and its height, in either direction")
    void aVerticalSplitIsNotExchanged() {
        for (String rtl : new String[] {"false", "true"}) {
            System.setProperty(TextDirection.FORCE, rtl);
            JPanel console = sized(600, 30);
            JPanel results = sized(600, 300);
            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, console, results);
            split.setDividerLocation(240);
            JPanel root = holding(split);
            show(root);
            int authored = console.getHeight();
            assertThat(authored).as("as authored (rtl=%s)", rtl).isBetween(235, 240);

            RightToLeft.apply(root);
            layOut(root);

            assertThat(split.getTopComponent()).as("the console stays on top (rtl=%s)", rtl).isSameAs(console);
            assertThat(split.getBottomComponent()).as("the results stay below (rtl=%s)", rtl).isSameAs(results);
            assertThat(console.getHeight()).as("the console's height (rtl=%s)", rtl).isEqualTo(authored);
        }
    }

    @Test
    @DisplayName("a right-to-left sweep puts a horizontal split's first side on the right, as wide as its author made it")
    void aHorizontalSplitMirrorsRightToLeft() {
        System.setProperty(TextDirection.FORCE, "true");
        JPanel tree = sized(129, 400);
        JPanel work = sized(600, 400);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tree, work);
        split.setDividerLocation(280);
        split.setResizeWeight(0); // the list keeps its width; the work area takes the rest
        JPanel root = holding(split);
        show(root);
        int authored = tree.getWidth();
        assertThat(authored).isBetween(275, 280);

        RightToLeft.apply(root);
        layOut(root);

        assertThat(split.getComponentOrientation().isLeftToRight()).as("the pane itself is oriented").isFalse();
        assertThat(split.getLeftComponent()).as("the work area is now on the left").isSameAs(work);
        assertThat(split.getRightComponent()).as("and the list on the right").isSameAs(tree);
        assertThat(tree.getWidth()).as("as wide as before").isEqualTo(authored);
        assertThat(tree.getX()).as("at the right edge").isGreaterThan(work.getX());
        assertThat(split.getResizeWeight()).as("the work area, now on the left, is the side that grows").isEqualTo(1.0);
        assertThat(split.getClientProperty(SplitShapes.MIRRORED)).isNotNull();
    }

    @Test
    @DisplayName("a sweep back to left-to-right undoes the exchange, the divider and the weight")
    void aSweepBackUndoesIt() {
        System.setProperty(TextDirection.FORCE, "true");
        JPanel tree = sized(129, 400);
        JPanel work = sized(600, 400);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tree, work);
        split.setDividerLocation(280);
        split.setResizeWeight(0);
        JPanel root = holding(split);
        show(root);
        RightToLeft.apply(root);
        layOut(root);
        assertThat(split.getRightComponent()).isSameAs(tree);

        System.setProperty(TextDirection.FORCE, "false");
        RightToLeft.apply(root);
        layOut(root);

        assertThat(split.getComponentOrientation().isLeftToRight()).isTrue();
        assertThat(split.getLeftComponent()).isSameAs(tree);
        assertThat(split.getRightComponent()).isSameAs(work);
        assertThat(tree.getWidth()).isBetween(275, 280);
        assertThat(split.getResizeWeight()).isEqualTo(0.0);
        assertThat(split.getClientProperty(SplitShapes.MIRRORED)).isNull();
    }

    @Test
    @DisplayName("a pane oriented before it has a width takes its mirrored divider when it is first sized")
    void anUnsizedPaneMirrorsWhenSized() throws Exception {
        System.setProperty(TextDirection.FORCE, "true");
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            JPanel tree = sized(129, 400);
            JPanel work = sized(600, 400);
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tree, work);
            split.setDividerLocation(280);
            JPanel root = holding(split);
            // never shown: no width yet
            RightToLeft.apply(root);
            assertThat(split.getRightComponent()).isSameAs(tree);
            assertThat(split.getDividerLocation()).as("until it has a width, the author's value stands").isEqualTo(280);

            show(root); // the window opens: the pane is sized, and its resize listener runs
            for (java.awt.event.ComponentListener l : split.getComponentListeners()) {
                l.componentResized(new java.awt.event.ComponentEvent(split, java.awt.event.ComponentEvent.COMPONENT_RESIZED));
            }
            layOut(root);
            assertThat(tree.getWidth()).as("the list's width, measured from the right").isBetween(275, 280);
            assertThat(tree.getX()).isGreaterThan(work.getX());
            assertThat(split.getComponentListeners()).as("the listener was for once").isEmpty();
        });
    }

    @Test
    @DisplayName("a divider the user dragged is mirrored where it was dragged to")
    void aDraggedDividerMirrors() {
        System.setProperty(TextDirection.FORCE, "true");
        JPanel tree = sized(129, 400);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tree, sized(600, 400));
        split.setDividerLocation(280);
        JPanel root = holding(split);
        show(root);
        split.setDividerLocation(412);
        layOut(root);
        int dragged = tree.getWidth();

        RightToLeft.apply(root);
        layOut(root);
        assertThat(tree.getWidth()).isEqualTo(dragged);
        assertThat(split.getRightComponent()).isSameAs(tree);
    }

    @Test
    @DisplayName("where the user dragged the divider is where it stays")
    void aDraggedDividerSurvives() {
        System.setProperty(TextDirection.FORCE, "false");
        JPanel tree = sized(129, 400);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tree, sized(600, 400));
        split.setDividerLocation(280);
        JPanel root = holding(split);
        show(root);
        split.setDividerLocation(412); // what a drag does
        layOut(root);
        int dragged = tree.getWidth();
        assertThat(dragged).isBetween(407, 412);

        RightToLeft.apply(root);
        layOut(root);

        assertThat(tree.getWidth()).isEqualTo(dragged);
    }

    @Test
    @DisplayName("a pane already oriented is not recorded, and a divider never set stays unset")
    void steadyStateTouchesNothing() {
        System.setProperty(TextDirection.FORCE, "false");
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sized(129, 400), sized(600, 400));
        JPanel root = holding(split);
        assertThat(split.getDividerLocation()).as("never set").isEqualTo(-1);

        RightToLeft.apply(root);
        assertThat(split.getDividerLocation()).as("still never set after the first sweep").isEqualTo(-1);
        assertThat(SplitShapes.disturbedBy(root, split.getComponentOrientation()))
                .as("a second sweep in the same direction has nothing to record").isEmpty();
    }
}
