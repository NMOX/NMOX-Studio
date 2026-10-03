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
 * divider (3.5), and a right-to-left sweep exchanges the sides of a
 * horizontal split THE PRODUCT MARKED, keeps its leading side's width and
 * flips its resize weight, undone by a sweep back (3.5.12, ledger 127
 * decided by two right-to-left readers; opt-in since 3.5.13, after 3.5.12
 * exchanged the platform's own panes and broke the ones addressed by slot); the
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

    /** A pane the product built knowing its sides may change places. */
    private static JSplitPane following(JPanel first, JPanel second) {
        return TextDirection.sidesFollowReader(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, first, second));
    }

    /** Give the pane's window a new width the way the toolkit does: size, lay out, then the resize event. */
    private static void resize(JPanel root, JSplitPane split, int width) {
        root.setSize(width, 600);
        layOut(root);
        split.dispatchEvent(new java.awt.event.ComponentEvent(split, java.awt.event.ComponentEvent.COMPONENT_RESIZED));
        layOut(root);
    }

    private static void onEdt(org.assertj.core.api.ThrowingConsumer<Void> body) throws Exception {
        // a resize posts an event when the pane has a listener, and a mirrored pane
        // has one: on the event thread the body and that event cannot interleave
        javax.swing.SwingUtilities.invokeAndWait(() -> body.accept(null));
    }

    @Test
    @DisplayName("a right-to-left sweep puts a marked split's first side on the right, as wide as its author made it")
    void aMarkedSplitMirrorsRightToLeft() throws Exception {
        System.setProperty(TextDirection.FORCE, "true");
        onEdt(x -> {
            JPanel tree = sized(129, 400);
            JPanel work = sized(600, 400);
            JSplitPane split = following(tree, work);
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
            assertThat(tree.getWidth()).as("as wide as before").isBetween(authored - 1, authored + 1);
            assertThat(tree.getX()).as("at the right edge").isGreaterThan(work.getX());
            assertThat(split.getResizeWeight()).as("the work area, now on the left, is the side that grows").isEqualTo(1.0);
            assertThat(SplitShapes.isMirrored(split)).isTrue();
        });
    }

    @Test
    @DisplayName("an UNMARKED horizontal split keeps its slots right-to-left: the platform's panes are addressed by slot (3.5.13)")
    void anUnmarkedSplitKeepsItsSlots() throws Exception {
        System.setProperty(TextDirection.FORCE, "true");
        onEdt(x -> {
            JPanel results = sized(129, 400);
            JPanel preview = sized(600, 400);
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, results, preview);
            split.setDividerLocation(280);
            JPanel root = holding(split);
            show(root);
            int authored = results.getWidth();

            RightToLeft.apply(root);
            layOut(root);

            assertThat(split.getLeftComponent()).isSameAs(results);
            assertThat(split.getRightComponent()).isSameAs(preview);
            assertThat(results.getWidth()).isEqualTo(authored);
            assertThat(SplitShapes.isMirrored(split)).isFalse();

            // what Find in Projects does when Show Preview is switched off: 3.5.12
            // had exchanged the sides, so this removed the results tree instead
            split.setRightComponent(null);
            assertThat(split.getLeftComponent()).as("the results are still there").isSameAs(results);
            assertThat(preview.getParent()).as("and the preview is what went").isNull();
        });
    }

    @Test
    @DisplayName("a sweep back to left-to-right undoes the exchange, the divider and the weight")
    void aSweepBackUndoesIt() throws Exception {
        System.setProperty(TextDirection.FORCE, "true");
        onEdt(x -> {
            JPanel tree = sized(129, 400);
            JPanel work = sized(600, 400);
            JSplitPane split = following(tree, work);
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
            assertThat(tree.getWidth()).isBetween(275, 281);
            assertThat(split.getResizeWeight()).isEqualTo(0.0);
            assertThat(SplitShapes.isMirrored(split)).isFalse();
            assertThat(split.getComponentListeners()).as("nothing is left listening").isEmpty();
        });
    }

    @Test
    @DisplayName("a second right-to-left sweep changes nothing: sides, width and weight stay")
    void aSecondSweepIsSteady() throws Exception {
        System.setProperty(TextDirection.FORCE, "true");
        onEdt(x -> {
            JPanel tree = sized(129, 400);
            JSplitPane split = following(tree, sized(600, 400));
            split.setDividerLocation(280);
            JPanel root = holding(split);
            show(root);
            RightToLeft.apply(root);
            layOut(root);
            int once = tree.getWidth();
            assertThat(SplitShapes.disturbedBy(root, split.getComponentOrientation()))
                    .as("a settled pane is not recorded again").isEmpty();

            RightToLeft.apply(root);
            RightToLeft.apply(root);
            layOut(root);

            assertThat(split.getRightComponent()).isSameAs(tree);
            assertThat(tree.getWidth()).isEqualTo(once);
            assertThat(split.getResizeWeight()).isEqualTo(1.0);
            assertThat(split.getComponentListeners()).as("one controller, not one per sweep").hasSize(1);
        });
    }

    @Test
    @DisplayName("a pane oriented before it has a width takes its mirrored divider when it is first sized, by the toolkit's own resize event")
    void anUnsizedPaneMirrorsWhenSized() throws Exception {
        System.setProperty(TextDirection.FORCE, "true");
        onEdt(x -> {
            JPanel tree = sized(129, 400);
            JPanel work = sized(600, 400);
            JSplitPane split = following(tree, work);
            split.setDividerLocation(280);
            JPanel root = holding(split);
            // never shown: no width yet
            RightToLeft.apply(root);
            assertThat(split.getRightComponent()).isSameAs(tree);
            assertThat(split.getDividerLocation()).as("until it has a width, the author's value stands").isEqualTo(280);

            show(root); // the window opens
            resize(root, split, 1000);

            assertThat(tree.getWidth()).as("the list's width, measured from the right").isBetween(275, 281);
            assertThat(tree.getX()).isGreaterThan(work.getX());
        });
    }

    @Test
    @DisplayName("mirrored before it was sized, then swept back before it was ever shown: left-to-right, as its author built it")
    void unsizedThenSweptBack() throws Exception {
        System.setProperty(TextDirection.FORCE, "true");
        onEdt(x -> {
            JPanel tree = sized(129, 400);
            JPanel work = sized(600, 400);
            JSplitPane split = following(tree, work);
            split.setDividerLocation(280);
            JPanel root = holding(split);
            RightToLeft.apply(root); // a tab open and never shown, in Hebrew

            System.setProperty(TextDirection.FORCE, "false");
            RightToLeft.apply(root); // the language switched back
            show(root);
            resize(root, split, 1000);

            assertThat(split.getLeftComponent()).isSameAs(tree);
            assertThat(tree.getWidth()).as("3.5.12 left a listener behind that mirrored this pane: 709").isBetween(275, 281);
            assertThat(split.getComponentListeners()).isEmpty();
        });
    }

    @Test
    @DisplayName("a mirrored pane keeps its leading side's width through a resize, whether or not the split-pane UI shares the space out")
    void theLeadingWidthSurvivesResizes() throws Exception {
        System.setProperty(TextDirection.FORCE, "true");
        onEdt(x -> {
            JPanel tree = sized(129, 400);
            JSplitPane split = following(tree, sized(600, 400));
            split.setDividerLocation(280);
            split.setResizeWeight(0);
            JPanel root = holding(split);
            // mirrored at one width before its first paint, then given another: the UI
            // keeps the absolute location there, which 3.5.12 trusted (779 at 1200)
            root.setSize(700, 600);
            layOut(root);
            RightToLeft.apply(root);
            resize(root, split, 1200);
            assertThat(tree.getWidth()).isBetween(275, 281);

            show(root);
            resize(root, split, 1400);
            assertThat(tree.getWidth()).isBetween(275, 281);
            resize(root, split, 800);
            assertThat(tree.getWidth()).isBetween(275, 281);
        });
    }

    @Test
    @DisplayName("a pane whose author shares new space between its sides shares it the same way mirrored")
    void aSharedWeightIsMirrored() throws Exception {
        System.setProperty(TextDirection.FORCE, "true");
        onEdt(x -> {
            JPanel tree = sized(129, 400);
            JSplitPane split = following(tree, sized(600, 400));
            split.setDividerLocation(400);
            split.setResizeWeight(0.4); // the first side takes two fifths of any new space
            JPanel root = holding(split);
            show(root);
            RightToLeft.apply(root);
            layOut(root);
            assertThat(split.getResizeWeight()).as("so the other side, now on the left, takes three fifths")
                    .isCloseTo(0.6, org.assertj.core.data.Offset.offset(1e-9));
            int before = tree.getWidth();

            resize(root, split, 1500); // 500 wider

            assertThat(tree.getWidth()).as("two fifths of 500").isBetween(before + 195, before + 205);
        });
    }

    @Test
    @DisplayName("a divider nobody set gives the leading side its preferred width and its share, as left-to-right")
    void anUnsetDividerMirrors() throws Exception {
        System.setProperty(TextDirection.FORCE, "true");
        onEdt(x -> {
            JPanel tree = sized(129, 400);
            JPanel work = sized(600, 400);
            JSplitPane split = following(tree, work);
            split.setResizeWeight(0);
            JPanel root = holding(split);
            show(root);
            assertThat(split.getDividerLocation()).isNotEqualTo(280);

            RightToLeft.apply(root);
            layOut(root);

            assertThat(split.getRightComponent()).isSameAs(tree);
            assertThat(tree.getWidth()).as("its preferred width, on the right").isBetween(125, 133);
        });
    }

    @Test
    @DisplayName("a divider nobody set, in a pane mirrored before it was ever laid out: the leading side still gets its preferred width")
    void anUnsetDividerNeverLaidOut() throws Exception {
        System.setProperty(TextDirection.FORCE, "true");
        onEdt(x -> {
            JPanel tree = sized(129, 400);
            JPanel work = sized(600, 400);
            JSplitPane split = following(tree, work);
            split.setResizeWeight(0);
            JPanel root = holding(split);
            assertThat(split.getDividerLocation()).as("the fixture: never set, never laid out").isEqualTo(-1);

            RightToLeft.apply(root);
            show(root);
            resize(root, split, 1000);

            assertThat(split.getRightComponent()).isSameAs(tree);
            assertThat(tree.getWidth()).isBetween(125, 133);
        });
    }

    @Test
    @DisplayName("the share of new space is worked out here when the split-pane UI does not: a pane resized before its first paint")
    void theShareBeforeTheFirstPaint() throws Exception {
        System.setProperty(TextDirection.FORCE, "true");
        onEdt(x -> {
            JPanel tree = sized(129, 400);
            JSplitPane split = following(tree, sized(600, 400));
            split.setDividerLocation(280);
            split.setResizeWeight(0.4);
            JPanel root = holding(split);
            root.setSize(700, 600);
            layOut(root); // laid out, never painted: the UI keeps absolute locations
            RightToLeft.apply(root);
            layOut(root);
            int before = tree.getWidth();
            assertThat(before).isBetween(275, 281);

            resize(root, split, 1200); // 500 wider

            assertThat(tree.getWidth()).as("two fifths of 500, as its author shared it").isBetween(before + 195, before + 205);
        });
    }

    @Test
    @DisplayName("a divider the user dragged is mirrored where it was dragged to, and a drag while mirrored is kept on the way back")
    void aDraggedDividerMirrors() throws Exception {
        System.setProperty(TextDirection.FORCE, "true");
        onEdt(x -> {
            JPanel tree = sized(129, 400);
            JSplitPane split = following(tree, sized(600, 400));
            split.setDividerLocation(280);
            JPanel root = holding(split);
            show(root);
            split.setDividerLocation(412);
            layOut(root);
            int dragged = tree.getWidth();

            RightToLeft.apply(root);
            layOut(root);
            assertThat(tree.getWidth()).isBetween(dragged - 1, dragged + 1);
            assertThat(split.getRightComponent()).isSameAs(tree);

            // dragged again while mirrored: the list is made 150 wide
            split.setDividerLocation(1000 - split.getDividerSize() - 150);
            layOut(root);
            assertThat(tree.getWidth()).isBetween(149, 151);
            System.setProperty(TextDirection.FORCE, "false");
            RightToLeft.apply(root);
            layOut(root);
            assertThat(split.getLeftComponent()).isSameAs(tree);
            assertThat(tree.getWidth()).as("150 wide on the left as it was on the right").isBetween(149, 151);
        });
    }

    @Test
    @DisplayName("a marked pane inside a surface that keeps its authored direction keeps its sides")
    void insideAKeptSurfaceNothingMirrors() throws Exception {
        System.setProperty(TextDirection.FORCE, "true");
        onEdt(x -> {
            JPanel tree = sized(129, 400);
            JSplitPane split = following(tree, sized(600, 400));
            split.setDividerLocation(280);
            JPanel code = TextDirection.keepLeftToRight(new JPanel(new BorderLayout()));
            code.add(split, BorderLayout.CENTER);
            JPanel root = new JPanel(new BorderLayout());
            root.add(code, BorderLayout.CENTER);
            show(root);

            RightToLeft.apply(root);
            layOut(root);

            assertThat(split.getComponentOrientation().isLeftToRight()).as("the fixture: the pane stayed left-to-right").isTrue();
            assertThat(split.getLeftComponent()).isSameAs(tree);
            assertThat(SplitShapes.isMirrored(split)).isFalse();
        });
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
