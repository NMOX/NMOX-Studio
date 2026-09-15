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
 * <p>Pure and headless-testable: it reads the pane's own orientation and
 * its scrollbar model, nothing else.
 */
public final class Scrolls {

    private Scrolls() {
    }

    /**
     * Puts the horizontal view at the side the reader's language starts on:
     * the maximum for a right-to-left pane, zero for a left-to-right one.
     * A view narrower than its viewport has nowhere to go and stays put.
     */
    public static void toLogicalStart(JScrollPane pane) {
        if (pane == null) {
            return;
        }
        JScrollBar bar = pane.getHorizontalScrollBar();
        if (bar == null) {
            return;
        }
        int start = pane.getComponentOrientation().isLeftToRight()
                ? bar.getMinimum()
                : Math.max(bar.getMinimum(), bar.getMaximum() - bar.getVisibleAmount());
        bar.setValue(start);
    }
}
