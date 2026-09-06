package org.nmox.studio.editor.present;

import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.nmox.studio.core.util.Chords;
import org.openide.awt.StatusDisplayer;
import org.openide.util.Utilities;

/**
 * View ▸ Show Keystrokes (v2.87.0, the developer evangelist's third
 * presenting grant): the chord you just pressed, large, at the bottom of
 * the window for a second and a half — so the room sees ⌘S, ⌥⌘G, ⇧⌘O as
 * you live-code, the way a screencast key overlay does, without a second
 * app. What it shows is deliberately narrow: chords with ⌘/⌃/⌥ and the
 * function/escape keys ONLY. Plain typing never appears — a projector
 * showing every character would echo the password you type into a
 * terminal or a token into a field; ⇧ alone is typing too (a capital
 * letter), and a bare modifier press is nothing yet. The listener is the
 * toolkit's own {@link AWTEventListener} on KEY_PRESSED, installed only
 * while the mode is on, removed the moment it is off; non-persistent,
 * like Presentation Mode — a presentation is temporary.
 */
public final class KeystrokeHud {

    private static final AWTEventListener LISTENER = KeystrokeHud::onEvent;
    private static volatile boolean on;
    private static KeystrokeOverlay overlay;

    private KeystrokeHud() {
    }

    public static boolean isOn() {
        return on;
    }

    /** EDT-safe: installs or removes the toolkit listener and says so on the status line. */
    public static void setOn(boolean enable) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setOn(enable));
            return;
        }
        if (enable == on) {
            return;
        }
        on = enable;
        if (enable) {
            Toolkit.getDefaultToolkit().addAWTEventListener(LISTENER, AWTEvent.KEY_EVENT_MASK);
            StatusDisplayer.getDefault().setStatusText("Show Keystrokes on — chords with ⌘, ⌃ or ⌥ and function keys appear at the bottom of the window; plain typing never does");
        } else {
            Toolkit.getDefaultToolkit().removeAWTEventListener(LISTENER);
            if (overlay != null) {
                overlay.hideNow();
            }
            StatusDisplayer.getDefault().setStatusText("Show Keystrokes off");
        }
    }

    private static void onEvent(AWTEvent event) {
        if (!(event instanceof KeyEvent ke) || ke.getID() != KeyEvent.KEY_PRESSED) {
            return;
        }
        if (!shows(ke.getModifiersEx(), ke.getKeyCode())) {
            return;
        }
        String text = label(ke.getModifiersEx(), ke.getKeyCode(), Utilities.isMac());
        if (overlay == null) {
            overlay = new KeystrokeOverlay();
        }
        overlay.show(text);
    }

    /**
     * The showing rule: a chord carrying ⌘ (META), ⌃ (CTRL) or ⌥ (ALT), or a
     * function key / Escape on its own. A bare modifier press shows nothing;
     * plain and ⇧-only keys are typing and never show.
     */
    public static boolean shows(int modifiersEx, int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_META, KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_ALT_GRAPH, KeyEvent.VK_SHIFT,
                 KeyEvent.VK_UNDEFINED -> {
                return false;
            }
            default -> { }
        }
        boolean chord = (modifiersEx & (KeyEvent.META_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK)) != 0;
        boolean function = (keyCode >= KeyEvent.VK_F1 && keyCode <= KeyEvent.VK_F24) || keyCode == KeyEvent.VK_ESCAPE;
        return chord || function;
    }

    /** "⌥⌘G" from a key event's modifiers and key code, through the one chord vocabulary. */
    public static String label(int modifiersEx, int keyCode, boolean mac) {
        String keyName = Utilities.keyToString(KeyStroke.getKeyStroke(keyCode, 0));
        return Chords.human((modifiersEx & KeyEvent.CTRL_DOWN_MASK) != 0,
                (modifiersEx & KeyEvent.ALT_DOWN_MASK) != 0,
                (modifiersEx & KeyEvent.SHIFT_DOWN_MASK) != 0,
                (modifiersEx & KeyEvent.META_DOWN_MASK) != 0,
                keyName, mac);
    }

    /** The same chord pressed again reads "⌘Z ×3" rather than flashing three times. */
    public static String coalesce(String last, int repeats, String now) {
        return now.equals(last) && repeats >= 1 ? now + " ×" + (repeats + 1) : now;
    }
}
