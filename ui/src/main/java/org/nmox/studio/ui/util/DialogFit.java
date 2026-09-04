package org.nmox.studio.ui.util;

import java.awt.Dimension;
import java.awt.Toolkit;
import javax.swing.JComponent;

/**
 * Keeps a dialog's scrolling body inside the screen. The v2.69.6 walk on a
 * 1372-pt display found What's New and Report a Problem wider than the
 * screen — their Close/Cancel buttons sat past the right edge, because a
 * {@code JTextArea(rows, 88 columns)} plus the platform's dialog chrome
 * asks for more than the display has. The body's preferred size is now
 * clamped to a fraction of the screen (the platform packs the dialog
 * around it); the natural size wins when it already fits.
 */
public final class DialogFit {

    /** The share of the screen a dialog body may ask for. */
    public static final double FRACTION = 0.8;
    /** Below this a body is unreadable regardless of the screen. */
    static final Dimension FLOOR = new Dimension(320, 200);

    private DialogFit() {
    }

    /** Pure: the natural size clamped to {@link #FRACTION} of the screen, never below the floor. */
    public static Dimension fit(Dimension natural, Dimension screen) {
        int w = Math.min(natural.width, (int) (screen.width * FRACTION));
        int h = Math.min(natural.height, (int) (screen.height * FRACTION));
        return new Dimension(Math.max(w, FLOOR.width), Math.max(h, FLOOR.height));
    }

    /** Applies {@link #fit} to the component's preferred size against the default screen. */
    public static <T extends JComponent> T toScreen(T body) {
        body.setPreferredSize(fit(body.getPreferredSize(), Toolkit.getDefaultToolkit().getScreenSize()));
        return body;
    }
}
