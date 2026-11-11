package org.nmox.studio.infra.ops.ui;

import java.awt.geom.Rectangle2D;

/**
 * Where the HUD is looking: a zoom and an offset between the layout's
 * world and the canvas's pixels ({@code screen = world * zoom + pan}).
 * Pure arithmetic, no Swing: the canvas steps it, reads it when it
 * paints, hit-tests and reports accessible bounds, and a test can assert
 * every rule here without a window.
 *
 * <p>Two modes. Until the user moves it the camera FITS: every relayout
 * frames the view's content, and at any size where the content already
 * fits it is the identity, so the picture is exactly the layout's. The
 * first wheel, drag or zoom key makes it MANUAL, and it then stays where
 * it was put (clamped so the content cannot be lost off an edge) until
 * Fit is asked for or the view changes.
 *
 * <p>The Infra Designer's canvas set the conventions and this one keeps
 * them: the wheel zooms about the cursor, a drag on empty space pans,
 * the range is {@value #MIN_ZOOM}–{@value #MAX_ZOOM}, and a fit never
 * enlarges past natural size.
 */
public final class HudCamera {

    public static final double MIN_ZOOM = 0.35;
    public static final double MAX_ZOOM = 2.5;
    /** One press of a zoom key. */
    public static final double KEY_STEP = 1.25;
    /** One notch of a wheel. */
    public static final double WHEEL_STEP = 1.1;
    /** How much of the content must stay on screen, in pixels, on each axis. */
    static final double KEEP = 80;
    /** Screen room a fit leaves around the content for what is drawn at a fixed size: labels below, chrome above. */
    static final double PAD_X = 28;
    static final double PAD_TOP = 30;
    static final double PAD_BOTTOM = 52;

    /** Zoom is kept in thousandths so the spring's half-a-unit "arrived" rule means half a thousandth. */
    private static final double ZOOM_UNITS = 1000;

    private final Motion.Spring zoom = new Motion.Spring(ZOOM_UNITS);
    private final Motion.Spring panX = new Motion.Spring(0);
    private final Motion.Spring panY = new Motion.Spring(0);
    private boolean manual;

    // ---- reading ----

    public double zoom() {
        return zoom.value() / ZOOM_UNITS;
    }

    public double panX() {
        return panX.value();
    }

    public double panY() {
        return panY.value();
    }

    /** The zoom it is heading for (equal to {@link #zoom()} when settled). */
    public double targetZoom() {
        return zoom.target() / ZOOM_UNITS;
    }

    public double sx(double worldX) {
        return worldX * zoom() + panX();
    }

    public double sy(double worldY) {
        return worldY * zoom() + panY();
    }

    public double wx(double screenX) {
        return (screenX - panX()) / zoom();
    }

    public double wy(double screenY) {
        return (screenY - panY()) / zoom();
    }

    /** True once the user has moved it; a relayout no longer refits. */
    public boolean manual() {
        return manual;
    }

    /** True when it is heading for (or at) the untransformed picture. */
    public boolean identity() {
        return Math.abs(zoom.target() - ZOOM_UNITS) < 0.5 && Math.abs(panX.target()) < 0.5 && Math.abs(panY.target()) < 0.5;
    }

    public boolean settled() {
        return zoom.settled() && panX.settled() && panY.settled();
    }

    // ---- moving ----

    /**
     * Zooms by {@code factor} keeping the world point under the screen
     * point where it is. Works from where the camera is HEADING, so two
     * quick presses compound instead of the second cancelling the first.
     *
     * @return false when the zoom was already at its floor or ceiling
     */
    public boolean zoomAbout(double screenX, double screenY, double factor, boolean animate) {
        double z0 = zoom.target() / ZOOM_UNITS;
        double z1 = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z0 * factor));
        if (Math.abs(z1 - z0) < 1e-9) {
            return false;
        }
        double worldX = (screenX - panX.target()) / z0;
        double worldY = (screenY - panY.target()) / z0;
        zoom.setTarget(z1 * ZOOM_UNITS);
        panX.setTarget(screenX - worldX * z1);
        panY.setTarget(screenY - worldY * z1);
        manual = true;
        if (!animate) {
            snap();
        }
        return true;
    }

    public void panBy(double dx, double dy, boolean animate) {
        panX.setTarget(panX.target() + dx);
        panY.setTarget(panY.target() + dy);
        manual = true;
        if (!animate) {
            snap();
        }
    }

    /**
     * Frames {@code content} (world coordinates, the nodes' own circles)
     * in a {@code width} × {@code height} canvas and returns to fit mode.
     * Never enlarges: content that fits at natural size is shown exactly
     * as the layout placed it.
     */
    public void fit(Rectangle2D content, double width, double height, boolean animate) {
        manual = false;
        double z = 1;
        double px = 0;
        double py = 0;
        if (content != null && content.getWidth() > 0 && content.getHeight() > 0 && width > 0 && height > 0) {
            double roomX = Math.max(40, width - 2 * PAD_X);
            double roomY = Math.max(40, height - PAD_TOP - PAD_BOTTOM);
            boolean inside = content.getMinX() >= PAD_X && content.getMaxX() <= width - PAD_X
                    && content.getMinY() >= PAD_TOP && content.getMaxY() <= height - PAD_BOTTOM;
            if (!inside) {
                z = Math.max(MIN_ZOOM, Math.min(1, Math.min(roomX / content.getWidth(), roomY / content.getHeight())));
                px = PAD_X + (roomX - content.getWidth() * z) / 2 - content.getMinX() * z;
                py = PAD_TOP + (roomY - content.getHeight() * z) / 2 - content.getMinY() * z;
            }
        }
        zoom.setTarget(z * ZOOM_UNITS);
        panX.setTarget(px);
        panY.setTarget(py);
        if (!animate) {
            snap();
        }
    }

    /**
     * Keeps a manual camera from losing the content: at least
     * {@value #KEEP} pixels of it (or all of it, when it is smaller)
     * stay inside the canvas on each axis.
     */
    public void clamp(Rectangle2D content, double width, double height) {
        if (content == null || content.isEmpty() || width <= 0 || height <= 0) {
            return;
        }
        double z = zoom.target() / ZOOM_UNITS;
        double keepX = Math.min(KEEP, content.getWidth() * z);
        double keepY = Math.min(KEEP, content.getHeight() * z);
        // screen extent of the content at the target: [min*z + pan, max*z + pan]
        double minPanX = keepX - content.getMaxX() * z;          // its right edge at least keepX in from the left
        double maxPanX = width - keepX - content.getMinX() * z;  // its left edge at least keepX in from the right
        double minPanY = keepY - content.getMaxY() * z;
        double maxPanY = height - keepY - content.getMinY() * z;
        double px = Math.max(minPanX, Math.min(maxPanX, panX.target()));
        double py = Math.max(minPanY, Math.min(maxPanY, panY.target()));
        if (px != panX.target() || py != panY.target()) {
            boolean still = settled();
            panX.setTarget(px);
            panY.setTarget(py);
            if (still) {
                snap();
            }
        }
    }

    /**
     * Pans the least distance that brings a node (world centre and
     * radius) wholly inside the canvas with a margin — the keyboard's
     * selection must never move somewhere the eye cannot follow. Does
     * not change the mode: revealing is the HUD's move, not the user's.
     *
     * @return true when it had to move
     */
    public boolean reveal(double worldX, double worldY, double worldR, double width, double height, boolean animate) {
        double z = zoom.target() / ZOOM_UNITS;
        double margin = 36;
        double x = worldX * z + panX.target();
        double y = worldY * z + panY.target();
        double r = worldR * z;
        double dx = 0;
        double dy = 0;
        if (x - r < margin) {
            dx = margin - (x - r);
        } else if (x + r > width - margin) {
            dx = (width - margin) - (x + r);
        }
        if (y - r < margin) {
            dy = margin - (y - r);
        } else if (y + r > height - margin - 30) {
            dy = (height - margin - 30) - (y + r);
        }
        if (dx == 0 && dy == 0) {
            return false;
        }
        panX.setTarget(panX.target() + dx);
        panY.setTarget(panY.target() + dy);
        if (!animate) {
            snap();
        }
        return true;
    }

    /** Leaves manual mode without moving; the next relayout fits. */
    public void release() {
        manual = false;
    }

    // ---- time ----

    public void step(double dt) {
        zoom.step(dt, Motion.CAMERA_K);
        panX.step(dt, Motion.CAMERA_K);
        panY.step(dt, Motion.CAMERA_K);
    }

    /** Arrive now. */
    public void snap() {
        zoom.snap();
        panX.snap();
        panY.snap();
    }
}
