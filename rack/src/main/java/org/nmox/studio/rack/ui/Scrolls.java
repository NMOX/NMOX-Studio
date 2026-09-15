package org.nmox.studio.rack.ui;

import java.awt.Point;

import javax.swing.JScrollPane;
import javax.swing.JViewport;

/**
 * Where a scrolled view starts reading (v2.162.0).
 *
 * <p>A rack faceplate is painted GEOMETRY, and geometry does not mirror
 * (the v2.148.0 decision: a device's jacks, meters and transport keep their
 * shape in every language). So the rack's content begins at its left edge
 * in every direction — but in a mirrored window Swing's zero scroll VALUE
 * is the far end of it, and the Hebrew and Arabic pictures of the Task Rack
 * opened on the tail of every device: MAESTRO reading "RO", KVASIR "IR".
 * Setting the viewport's POSITION says what is meant regardless of the
 * value-to-position flip a right-to-left scrollbar applies.
 */
public final class Scrolls {

    private Scrolls() {
    }

    /**
     * Puts a scrolled view at the start of its content — the left edge —
     * keeping whatever vertical position it had. A view narrower than its
     * viewport is already there and does not move.
     */
    public static void toContentStart(JScrollPane pane) {
        if (pane == null) {
            return;
        }
        JViewport viewport = pane.getViewport();
        if (viewport == null) {
            return;
        }
        // getViewPosition builds a fresh Point, so it is never null (SpotBugs
        // named the guard that said otherwise)
        viewport.setViewPosition(new Point(0, viewport.getViewPosition().y));
    }
}
