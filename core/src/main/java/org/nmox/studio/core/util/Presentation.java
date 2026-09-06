package org.nmox.studio.core.util;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The product-wide "presenting" state (v2.87.0): one boolean every window
 * that can make itself legible from the back of a room may follow. The
 * editor module flips it from View ▸ Presentation Mode and bumps its own
 * editors; the in-app Browser follows by zooming its page. Living in core
 * so that neither module needs the other. Listeners are called on the
 * flipping thread with the new value; a late subscriber reads
 * {@link #isOn()} for the current state. Never persisted — a presentation
 * is temporary, and a restart is back to normal.
 */
public final class Presentation {

    /** The Browser's factor while presenting — a page at 150% reads from the back row. */
    public static final double BROWSER_ZOOM = 1.5;

    private static volatile boolean on;
    private static final List<Consumer<Boolean>> LISTENERS = new CopyOnWriteArrayList<>();

    private Presentation() {
    }

    public static boolean isOn() {
        return on;
    }

    /** Sets the state; listeners hear only real changes. */
    public static void setOn(boolean enable) {
        if (enable == on) {
            return;
        }
        on = enable;
        for (Consumer<Boolean> l : LISTENERS) {
            l.accept(enable);
        }
    }

    public static void addListener(Consumer<Boolean> l) {
        LISTENERS.add(l);
    }

    public static void removeListener(Consumer<Boolean> l) {
        LISTENERS.remove(l);
    }

    /** Test seam: how many listeners are attached (symmetry proofs). */
    static int listenerCount() {
        return LISTENERS.size();
    }

    /**
     * The Browser's zoom while presenting: the user's own zoom times
     * {@link #BROWSER_ZOOM} — a page already zoomed for a small pane keeps
     * its ratio; leaving restores exactly what the user had.
     */
    public static double browserZoom(double userZoom, boolean presenting) {
        return presenting ? userZoom * BROWSER_ZOOM : userZoom;
    }
}
