package org.nmox.studio.rack.ui;

import javax.swing.JScrollBar;
import javax.swing.JScrollPane;

/**
 * Where a scrolled view starts reading (v2.162.0). A left-to-right view
 * starts at the left edge, and Swing's zero scroll position already means
 * that. A MIRRORED view (the RTL work of v2.148.0 orients every window)
 * reads from the right, and zero is then the FAR end: the docs forge's
 * Hebrew and Arabic pictures of the Task Rack opened on the tail of every
 * device — MAESTRO reading "RO", KVASIR "IR" — because the rack is wider
 * than its viewport and the window had opened at the wrong end of it.
 *
 * <p>The decision is a pure function of the scrollbar's own numbers, so it
 * is tested without a laid-out window: a real {@code JScrollPane} recomputes
 * its model during layout and flips value-to-position under RTL, which makes
 * a headless assertion on the widget measure Swing rather than this rule.
 */
public final class Scrolls {

    private Scrolls() {
    }

    /**
     * The scroll value a reader of this direction starts at: the minimum
     * left-to-right, the far end right-to-left. A view no wider than its
     * viewport has nowhere to go and stays at the minimum.
     */
    public static int logicalStart(boolean leftToRight, int min, int max, int extent) {
        return leftToRight ? min : Math.max(min, max - extent);
    }

    /** Puts the pane's horizontal view at the side its language reads from. */
    public static void toLogicalStart(JScrollPane pane) {
        if (pane == null) {
            return;
        }
        JScrollBar bar = pane.getHorizontalScrollBar();
        if (bar == null) {
            return;
        }
        bar.setValue(logicalStart(pane.getComponentOrientation().isLeftToRight(),
                bar.getMinimum(), bar.getMaximum(), bar.getVisibleAmount()));
    }
}
