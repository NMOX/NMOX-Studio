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
    @DisplayName("content built after the sweep takes the direction of what it is added to")
    void aNewChildIsAdopted() {
        System.setProperty(TextDirection.FORCE, "true");
        JPanel window = new JPanel();
        RightToLeft.apply(window);
        // a row built the way the Workbench builds one on every refresh:
        // bottom-up, and added last
        JPanel row = new JPanel();
        JLabel title = new JLabel("Task Rack");
        row.add(title);
        assertThat(row.getComponentOrientation().isLeftToRight()).as("a new component has no direction").isTrue();
        window.add(row);

        assertThat(RightToLeft.adopt(window, row)).isTrue();

        assertThat(row.getComponentOrientation().isLeftToRight()).isFalse();
        assertThat(title.getComponentOrientation().isLeftToRight()).isFalse();
        assertThat(RightToLeft.adopt(window, row)).as("once is enough").isFalse();
    }

    @Test
    @DisplayName("what is added to a left-to-right container is left alone: a painted surface, marked text, a tree not yet swept")
    void aLeftToRightParentHandsNothingOn() {
        System.setProperty(TextDirection.FORCE, "true");
        JPanel painted = new JPanel(); // never swept: stands for a faceplate, or a tree still being built
        JLabel jack = new JLabel("OUT");
        painted.add(jack);

        assertThat(RightToLeft.adopt(painted, jack)).isFalse();
        assertThat(jack.getComponentOrientation().isLeftToRight()).isTrue();
        assertThat(RightToLeft.adopt(null, jack)).isFalse();
        assertThat(RightToLeft.adopt(painted, null)).isFalse();
    }

    @Test
    @DisplayName("text marked left-to-right in every language stays so when it arrives later, and its neighbours do not")
    void markedTextStaysLeftToRight() {
        System.setProperty(TextDirection.FORCE, "true");
        JPanel window = new JPanel();
        RightToLeft.apply(window);
        JPanel pane = new JPanel();
        javax.swing.JTextArea json = TextDirection.keepLeftToRight(new javax.swing.JTextArea("{\"url\": 1}"));
        JLabel caption = new JLabel("caption");
        pane.add(json);
        pane.add(caption);
        window.add(pane);

        RightToLeft.adopt(window, pane);

        assertThat(caption.getComponentOrientation().isLeftToRight()).isFalse();
        assertThat(json.getComponentOrientation().isLeftToRight()).as("code reads the same in every language").isTrue();
    }

    @Test
    @DisplayName("a split pane arriving later keeps its children in their places and its divider")
    void aSplitArrivingLaterKeepsItsShape() {
        System.setProperty(TextDirection.FORCE, "true");
        JPanel window = new JPanel();
        RightToLeft.apply(window);
        JLabel tree = new JLabel("tree");
        JLabel editor = new JLabel("editor");
        javax.swing.JSplitPane split = new javax.swing.JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT, tree, editor);
        split.setSize(600, 300);
        split.setDividerLocation(180);
        window.add(split);

        RightToLeft.adopt(window, split);

        assertThat(split.getComponentOrientation().isLeftToRight()).isFalse();
        assertThat(split.getLeftComponent()).isSameAs(tree);
        assertThat(split.getRightComponent()).isSameAs(editor);
        assertThat(split.getDividerLocation()).isEqualTo(180);
    }

    @Test
    @DisplayName("what a tree adds while it is being oriented is left to that pass")
    void noSecondPassFromInside() {
        System.setProperty(TextDirection.FORCE, "true");
        JPanel window = new JPanel();
        RightToLeft.apply(window);
        JLabel late = new JLabel("late");
        boolean[] adoptedFromInside = new boolean[1];
        // a container that adds a child when it is told its direction, as the
        // runtime's split pane re-adds both of its own
        JPanel readds = new JPanel() {
            @Override
            public void setComponentOrientation(java.awt.ComponentOrientation o) {
                super.setComponentOrientation(o);
                if (!o.isLeftToRight() && late.getParent() == null) {
                    add(late);
                    adoptedFromInside[0] = RightToLeft.adopt(this, late);
                }
            }
        };
        window.add(readds);

        assertThat(RightToLeft.adopt(window, readds)).isTrue();

        assertThat(adoptedFromInside[0]).as("the pass in progress reaches it; a second one is not started").isFalse();
        assertThat(late.getComponentOrientation().isLeftToRight()).as("and it does reach it").isFalse();
    }

    @Test
    @DisplayName("the toolkit is asked for container events only while the interface runs right-to-left, and then an add is enough")
    void listensOnlyWhenRightToLeft() {
        java.awt.Toolkit toolkit = java.awt.Toolkit.getDefaultToolkit();
        int before = toolkit.getAWTEventListeners(java.awt.AWTEvent.CONTAINER_EVENT_MASK).length;
        try {
            System.setProperty(TextDirection.FORCE, "true");
            RightToLeft.listenForChildren();
            RightToLeft.listenForChildren(); // asked twice, installed once
            assertThat(toolkit.getAWTEventListeners(java.awt.AWTEvent.CONTAINER_EVENT_MASK)).hasSize(before + 1);

            JPanel window = new JPanel();
            RightToLeft.apply(window);
            JPanel row = new JPanel();
            JLabel title = new JLabel("Task Rack");
            row.add(title);
            window.add(row); // nothing else: the toolkit's own event does it

            assertThat(row.getComponentOrientation().isLeftToRight()).isFalse();
            assertThat(title.getComponentOrientation().isLeftToRight()).isFalse();
        } finally {
            System.setProperty(TextDirection.FORCE, "false");
            RightToLeft.listenForChildren();
        }
        assertThat(toolkit.getAWTEventListeners(java.awt.AWTEvent.CONTAINER_EVENT_MASK))
                .as("a left-to-right build costs the toolkit nothing").hasSize(before);
        JPanel window = new JPanel();
        JLabel late = new JLabel("x");
        window.add(late);
        assertThat(late.getComponentOrientation().isLeftToRight()).isTrue();
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
