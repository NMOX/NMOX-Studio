package org.nmox.studio.editor.present;

import java.beans.PropertyChangeListener;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.openide.awt.StatusDisplayer;

/**
 * Presentation Mode (v2.87.0): one toggle makes every open editor legible
 * from the back of a room. It rides the platform editor's own
 * {@code "text-zoom"} client property — the Integer point-delta the
 * Alt/⌥-wheel zoom sets, read by {@code DocumentViewOp.updateTextZoom}
 * (decompiled) — so the change is LIVE and NON-PERSISTENT: a presentation
 * is temporary, and a restart is back to normal. While the mode is on, an
 * editor opened or focused after the toggle gets the same bump (an
 * {@link EditorRegistry} focus hook); toggling off clears the zoom
 * everywhere, including any live wheel fine-tuning the presenter added on
 * top. Born for the developer evangelist live-coding NMOX Studio on a
 * projector, where the default font is unreadable past the third row.
 */
public final class PresentationMode {

    /** The from-the-back-of-the-room bump, in points, added to every editor's base font. */
    public static final int DELTA_POINTS = 10;

    /** The platform editor's zoom client-property key (decompiled from DocumentViewOp). */
    static final String TEXT_ZOOM = "text-zoom";

    private static boolean on;
    private static PropertyChangeListener registryHook;

    private PresentationMode() {
    }

    public static synchronized boolean isOn() {
        return on;
    }

    /** The point-delta a given mode state applies — pure, so the arithmetic is pinned. */
    public static int zoomFor(boolean modeOn) {
        return modeOn ? DELTA_POINTS : 0;
    }

    /** Flip the mode: apply or clear the bump across every editor, live. */
    public static synchronized void setOn(boolean enable) {
        if (enable == on) {
            return;
        }
        on = enable;
        if (enable) {
            registryHook = evt -> {
                if (isOn() && EditorRegistry.FOCUS_GAINED_PROPERTY.equals(evt.getPropertyName())) {
                    apply(EditorRegistry.lastFocusedComponent(), DELTA_POINTS);
                }
            };
            EditorRegistry.addPropertyChangeListener(registryHook);
        } else if (registryHook != null) {
            EditorRegistry.removePropertyChangeListener(registryHook);
            registryHook = null;
        }
        // the product-wide state: the in-app Browser (and any later window) follows it
        org.nmox.studio.core.util.Presentation.setOn(enable);
        int delta = zoomFor(enable);
        onEdt(() -> {
            for (JTextComponent c : EditorRegistry.componentList()) {
                apply(c, delta);
            }
            OutputFont.follow(enable); // the Output window reads from the back row too
        });
        // two literal heads: PlainStatusGateTest wants every status text to BEGIN
        // with the product's own literal, and a ternary's head is a variable
        if (enable) {
            StatusDisplayer.getDefault().setStatusText("Presentation Mode on — editors and Output +" + DELTA_POINTS + " pt, Browser at " + Math.round(org.nmox.studio.core.util.Presentation.BROWSER_ZOOM * 100) + "% (⌥-wheel fine-tunes)");
        } else {
            StatusDisplayer.getDefault().setStatusText("Presentation Mode off");
        }
    }

    private static void apply(JTextComponent c, int delta) {
        if (c != null) {
            // putClientProperty fires the change DocumentViewOp listens for
            c.putClientProperty(TEXT_ZOOM, Integer.valueOf(delta));
        }
    }

    private static void onEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }
}
