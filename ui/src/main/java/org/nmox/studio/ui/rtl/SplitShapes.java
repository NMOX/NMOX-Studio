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
 *
 * <p><b>Only panes the product marks</b> (3.5.13,
 * {@link org.nmox.studio.core.util.TextDirection#sidesFollowReader}).
 * 3.5.12 exchanged every horizontal pane, the platform's own with them,
 * and the platform addresses a side by its slot: Find in Projects removed
 * its results tree where it meant to remove its preview, the refactoring
 * preview replaced the list of usages, the diff view's connectors pointed
 * at the wrong sides, and a divider the platform saves flipped on each
 * use. A review that replayed the platform's own calls against this class
 * found it within hours. An unmarked pane is put back as it was built.
 */
final class SplitShapes {

    /** One split pane as it stood before the sweep. */
    record Shape(JSplitPane split, Component left, Component right, int divider) {
    }

    private SplitShapes() {
    }

    /**
     * Every split pane under {@code root} the sweep to {@code next} has
     * something to do for: those whose orientation is not already equal to
     * it, and those marked to follow the reader whose sides do not yet
     * stand where {@code next} puts them. A pane already settled is left
     * out, so a steady-state sweep records nothing.
     */
    static List<Shape> disturbedBy(Component root, ComponentOrientation next) {
        List<Shape> out = new ArrayList<>();
        collect(root, next, out);
        return out;
    }

    private static void collect(Component c, ComponentOrientation next, List<Shape> out) {
        if (c instanceof JSplitPane split
                && (!next.equals(split.getComponentOrientation())
                    || (marked(split) && isMirrored(split) == next.isLeftToRight()))) {
            out.add(new Shape(split, split.getLeftComponent(), split.getRightComponent(),
                    split.getDividerLocation()));
        }
        if (c instanceof Container k) {
            for (Component child : k.getComponents()) {
                collect(child, next, out);
            }
        }
    }

    /** Holds a pane's {@link Mirror} while its sides stand exchanged for a right-to-left reader. */
    static final String MIRRORED = "nmox.split.mirrored";

    static boolean isMirrored(JSplitPane split) {
        return split.getClientProperty(MIRRORED) instanceof Mirror;
    }

    private static boolean marked(JSplitPane split) {
        return Boolean.TRUE.equals(split.getClientProperty(
                org.nmox.studio.core.util.TextDirection.SIDES_FOLLOW_READER));
    }

    /**
     * Whether a pane's sides should stand exchanged NOW: it is marked
     * ({@link org.nmox.studio.core.util.TextDirection#sidesFollowReader}),
     * horizontal, complete, and itself runs right-to-left. The pane's own
     * orientation is asked, after the sweep and after
     * {@link PaintedSurfaces} put back what must stay left-to-right, so a
     * pane inside a surface that keeps its authored direction keeps its
     * sides too.
     */
    static boolean wantsMirror(JSplitPane split) {
        return marked(split)
                && split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT
                && !split.getComponentOrientation().isLeftToRight();
    }

    /**
     * Put each recorded pane back: as it was, or, for a pane marked to
     * follow the reader whose reading direction has changed, with its sides
     * exchanged (3.5.12, made opt-in in 3.5.13).
     */
    static void restore(List<Shape> shapes) {
        for (Shape s : shapes) {
            JSplitPane split = s.split();
            Mirror mirror = split.getClientProperty(MIRRORED) instanceof Mirror m ? m : null;
            boolean wanted = wantsMirror(split) && s.left() != null && s.right() != null;
            if (wanted == (mirror != null)) {
                place(split, s.left(), s.right());
                if (mirror != null) {
                    mirror.apply();
                } else if (s.divider() >= 0) {
                    // a divider nobody ever set stays unset and takes the preferred
                    // sizes, as it would have; one that was set is named again, which
                    // is what tells the layout not to reset on its next pass
                    split.setDividerLocation(s.divider());
                }
            } else if (wanted) {
                Mirror.install(s);
            } else {
                mirror.uninstall(s);
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
     * One mirrored pane: its sides exchanged, and the width of its LEADING
     * side (the pane's first component, now on the right) kept as its
     * author and its user left it.
     *
     * <p>A divider is a distance from the left edge, so "the list is 280
     * wide" is {@code width - divider - 280} once the list stands on the
     * right, and that is only true at the width it was computed at. 3.5.12
     * computed it once. A pane mirrored before it had its final size (a tab
     * not yet shown, a window still opening, a look-and-feel change: the
     * split-pane UI then keeps the absolute location and ignores its resize
     * weight) came out with the leading side hundreds of pixels wide or
     * narrow. This holds the leading width instead and derives the divider
     * from it on every resize: the leading side takes the share of new
     * space its author gave the first side ({@code setResizeWeight}), as it
     * does left-to-right. A divider moved by anything else (a drag, the
     * UI's own arithmetic) is read back as the new leading width.
     */
    static final class Mirror extends java.awt.event.ComponentAdapter
            implements java.beans.PropertyChangeListener {

        private final JSplitPane split;
        /** The author's resize weight: the first side's share of new space. */
        private final double firstShare;
        /** The leading side's width; negative until the pane has a width and a divider nobody set is settled. */
        private double lead;
        /** The pane's width when {@link #lead} was last true; 0 before it has one. */
        private int widthThen;
        private boolean applying;

        private Mirror(JSplitPane split, double firstShare, int authoredDivider) {
            this.split = split;
            this.firstShare = firstShare;
            this.lead = authoredDivider; // a divider IS the first side's width, left-to-right
        }

        /** Exchange the sides of the pane {@code s} recorded, and keep them so. */
        static void install(Shape s) {
            JSplitPane split = s.split();
            Mirror m = new Mirror(split, split.getResizeWeight(), s.divider());
            place(split, s.right(), s.left());
            split.setResizeWeight(1 - m.firstShare);
            split.putClientProperty(MIRRORED, m);
            split.addComponentListener(m);
            split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, m);
            if (split.getWidth() > 0) {
                m.widthThen = split.getWidth();
                m.apply();
            } else if (s.divider() >= 0) {
                // no width yet: the author's number stands, read from the left,
                // until the first resize gives this something to measure from
                split.setDividerLocation(s.divider());
            }
        }

        /** Put the first side back on the left, as wide as it now is, and stop. */
        void uninstall(Shape s) {
            split.removeComponentListener(this);
            split.removePropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, this);
            split.putClientProperty(MIRRORED, null);
            // s was recorded mirrored: its right side is the pane's first
            place(split, s.right(), s.left());
            split.setResizeWeight(firstShare);
            if (lead >= 0) {
                split.setDividerLocation((int) Math.round(lead));
            }
        }

        /** The divider for the leading width, at the pane's width now; nothing while it has none. */
        void apply() {
            int width = split.getWidth();
            if (width <= 0) {
                return;
            }
            int room = Math.max(0, width - split.getDividerSize());
            if (lead < 0) {
                // nobody set a divider: left-to-right the first side would take its
                // preferred width and its share of what is left over
                Component first = split.getRightComponent();
                Component second = split.getLeftComponent();
                int firstWants = first == null ? 0 : first.getPreferredSize().width;
                int secondWants = second == null ? 0 : second.getPreferredSize().width;
                lead = firstWants + firstShare * (room - firstWants - secondWants);
            }
            lead = Math.max(0, Math.min(room, lead));
            widthThen = width;
            applying = true;
            try {
                split.setDividerLocation((int) Math.round(room - lead));
            } finally {
                applying = false;
            }
        }

        @Override
        public void componentResized(java.awt.event.ComponentEvent e) {
            int width = split.getWidth();
            if (width <= 0) {
                return;
            }
            if (widthThen > 0 && lead >= 0) {
                lead += firstShare * (width - widthThen);
            }
            apply();
        }

        @Override
        public void propertyChange(java.beans.PropertyChangeEvent e) {
            int width = split.getWidth();
            if (applying || width <= 0 || widthThen <= 0
                    || !(e.getNewValue() instanceof Integer location) || location < 0) {
                return;
            }
            if (width != widthThen) {
                // the pane has a new width and this is the UI sharing the space
                // out as it lays the pane out, which says nothing about where
                // the user wants the divider: two resizes can be laid out before
                // the first one's event arrives, and reading the divider then
                // took a stale location for a drag. The share is this class's
                // to work out, and the resize event that follows applies it.
                if (lead >= 0) {
                    lead += firstShare * (width - widthThen);
                }
                widthThen = width;
                return;
            }
            // at the same width the divider moved because somebody moved it
            lead = Math.max(0, width - split.getDividerSize() - location);
        }
    }
}
