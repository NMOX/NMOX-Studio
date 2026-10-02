package org.nmox.studio.ui.rtl;

import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JSplitPane;

/**
 * A split pane comes out of an orientation sweep the way it went in.
 *
 * <p>{@code JSplitPane.setComponentOrientation} in the runtime the product
 * bundles (JDK 25.0.4, read from its bytecode) does more than record a
 * direction. Whenever the new orientation is not {@code equals} to the old
 * one it puts both children back into the pane: swapped when the new
 * direction is right-to-left, in place otherwise. Putting a child back makes
 * the layout forget what it had, and its next pass falls to the children's
 * preferred sizes.
 *
 * <p>A component is born with {@code ComponentOrientation.UNKNOWN}, which is
 * left-to-right in everything but identity. So the first sweep over any
 * window, in English as much as in Hebrew, was a "change" for every split
 * pane in it, and each one lost the divider its author gave it. The first
 * Windows and Linux walks photographed the result and a throwaway-home walk
 * on a Mac matched it: DB Studio opened with its SQL console one line tall
 * and its connection tree 129 pixels wide, where the code says 240 and 280.
 * Nothing had been reported, because a developer's own workspace has
 * connection names long enough to make the preferred width look deliberate.
 *
 * <p>Right-to-left has further effects in that runtime: a VERTICAL split has
 * its top and bottom exchanged, which no reading direction asks for; the
 * exchange leaves {@code getLeftComponent()} answering null for a component
 * that is in the pane; and a switch back to a left-to-right language does
 * not undo any of it. So
 * this class takes the whole question out of the runtime's hands. Before a
 * sweep it records every split pane the sweep is about to disturb; afterwards
 * each one gets its children back where they were and its divider where it
 * was. What a split pane looks like is then the same on every JDK, and the
 * same as the pictures in the documentation.
 *
 * <p>Whether a horizontal split SHOULD mirror for a right-to-left reader was
 * ledger 127's question, and 3.5.12 answers it: yes. Two readers, one of
 * Hebrew and one of Arabic, were shown DB Studio and API Studio both ways
 * and preferred the mirrored layout for both, for the same reasons: the
 * tabs, toolbars and labels already begin at the right edge, so the list a
 * person starts from belongs there; and as shipped, the platform's file tree
 * and the studio's own list stood side by side on the left as one crowded
 * sidebar. So a HORIZONTAL split under a right-to-left orientation has its
 * two sides exchanged, its divider mirrored and its resize weight flipped,
 * by this class rather than by the runtime, which gets it wrong three ways
 * (above). A vertical split is never touched. A live switch back to a
 * left-to-right language undoes all three. The platform's own docking
 * (the explorer modes on the window's left edge) is the window system's and
 * stays where it is; both readers weighed that and preferred the studios
 * mirrored anyway.
 */
final class SplitShapes {

    /** One split pane as it stood before the sweep. */
    record Shape(JSplitPane split, Component left, Component right, int divider) {
    }

    private SplitShapes() {
    }

    /**
     * Every split pane under {@code root} that setting {@code next} would
     * disturb: those whose orientation is not already equal to it. A pane
     * already oriented is left out, so a steady-state sweep records nothing.
     */
    static List<Shape> disturbedBy(Component root, ComponentOrientation next) {
        List<Shape> out = new ArrayList<>();
        collect(root, next, out);
        return out;
    }

    private static void collect(Component c, ComponentOrientation next, List<Shape> out) {
        if (c instanceof JSplitPane split && !next.equals(split.getComponentOrientation())) {
            out.add(new Shape(split, split.getLeftComponent(), split.getRightComponent(),
                    split.getDividerLocation()));
        }
        if (c instanceof Container k) {
            for (Component child : k.getComponents()) {
                collect(child, next, out);
            }
        }
    }

    /** Marks a horizontal pane whose sides this class has exchanged for a right-to-left reader. */
    static final String MIRRORED = "nmox.split.mirrored";

    /**
     * Put each recorded pane back: as it was, or, for a horizontal pane whose
     * reading direction has changed, with its sides exchanged (3.5.12).
     *
     * @param next the orientation the sweep applied
     */
    static void restore(List<Shape> shapes, ComponentOrientation next) {
        for (Shape s : shapes) {
            JSplitPane split = s.split();
            boolean mirrorWanted = !next.isLeftToRight() && split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT;
            boolean mirrored = split.getClientProperty(MIRRORED) != null;
            if (mirrorWanted == mirrored) {
                place(split, s.left(), s.right());
                // a divider nobody ever set stays unset and takes the preferred
                // sizes, as it would have; one that was set is named again, which
                // is what tells the layout not to reset on its next pass
                if (s.divider() >= 0) {
                    split.setDividerLocation(s.divider());
                }
                continue;
            }
            // the reading direction changed under a horizontal pane: exchange
            // its sides, flip which side grows, and mirror the divider
            place(split, s.right(), s.left());
            split.setResizeWeight(1 - split.getResizeWeight());
            split.putClientProperty(MIRRORED, mirrorWanted ? Boolean.TRUE : null);
            if (s.divider() >= 0) {
                mirrorDivider(split, s.divider());
            }
        }
    }

    /**
     * Out first, then in, and out BY IDENTITY. Adding a component that is
     * still a child of the pane makes Swing remove it from its old slot in
     * the middle of the add, and the pane then nulls the field it has just
     * assigned. That is how the runtime's own exchange leaves
     * getLeftComponent() null while the component sits in the pane, so
     * asking the pane which child is where cannot be trusted here.
     */
    private static void place(JSplitPane split, Component left, Component right) {
        if (split.getLeftComponent() == left && split.getRightComponent() == right) {
            return;
        }
        if (left != null) {
            split.remove(left);
        }
        if (right != null) {
            split.remove(right);
        }
        split.setLeftComponent(left);
        split.setRightComponent(right);
    }

    /**
     * The divider measured from the other edge: the author's 280 for a list
     * on the left becomes width − divider − 280 for the same list on the
     * right. A pane that has no width yet (a window still opening) takes the
     * mirrored divider the first time it is given one, and only then; until
     * then the author's value stands, which the layout reads as a left-side
     * width and the first resize corrects.
     */
    private static void mirrorDivider(JSplitPane split, int authored) {
        Runnable put = () -> split.setDividerLocation(
                Math.max(0, split.getWidth() - split.getDividerSize() - authored));
        if (split.getWidth() > 0) {
            put.run();
            return;
        }
        split.setDividerLocation(authored);
        split.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (split.getWidth() <= 0) {
                    return;
                }
                split.removeComponentListener(this);
                put.run();
            }
        });
    }
}
