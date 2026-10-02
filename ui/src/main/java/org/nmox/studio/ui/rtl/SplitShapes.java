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
 * <p>Whether a horizontal split SHOULD mirror for a right-to-left reader is a
 * design question with its own ledger entry (127). It is not decided here by
 * a runtime patch release.
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

    /** Put each recorded pane's children and divider back. */
    static void restore(List<Shape> shapes) {
        for (Shape s : shapes) {
            JSplitPane split = s.split();
            if (split.getLeftComponent() != s.left() || split.getRightComponent() != s.right()) {
                // Out first, then in, and out BY IDENTITY. Adding a component
                // that is still a child of the pane makes Swing remove it from
                // its old slot in the middle of the add, and the pane then
                // nulls the field it has just assigned. That is how the
                // runtime's own exchange leaves getLeftComponent() null while
                // the component sits in the pane, so asking the pane which
                // child is where cannot be trusted here.
                if (s.left() != null) {
                    split.remove(s.left());
                }
                if (s.right() != null) {
                    split.remove(s.right());
                }
                split.setLeftComponent(s.left());
                split.setRightComponent(s.right());
            }
            // a divider nobody ever set stays unset and takes the preferred
            // sizes, as it would have; one that was set is named again, which
            // is what tells the layout not to reset on its next pass
            if (s.divider() >= 0) {
                split.setDividerLocation(s.divider());
            }
        }
    }
}
