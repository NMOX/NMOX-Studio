package org.nmox.studio.editor.editing;

import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.JComponent;

/**
 * An Option chord on macOS also TYPES: ⇧⌥A is the character Å and ⌥Z is Ω,
 * delivered as a KEY_TYPED right after the KEY_PRESSED that fired the
 * action. The editor would insert it, and with a selection it would
 * replace what was selected with that one character.
 *
 * <p>Read from the platform, not assumed. macOS posts a typed event for
 * every printable press that does not hold ⌘. The editor's default
 * typed-key action accepts it: {@code BaseKit.isValidDefaultTypedAction}
 * rejects a typed character only when Ctrl is held, or (on macOS) ⌘, or
 * (elsewhere) Alt, so Option+letter is ordinary typing there, as it must
 * be for the keyboards that reach braces through Option. The keymap's own
 * "ignore the next typed" after an Alt chord sits behind
 * {@code netbeans.editor.keymap.compatible}, which is off (all from
 * RELEASE310's editor-lib bytecode).
 *
 * <p>So an action bound to an Alt chord asks {@link #follows} and, when
 * it does, {@link #swallowNext} consumes exactly the one typed event that
 * belongs to the chord: a key listener sees a key event before the
 * component's key bindings do, and it takes itself off at that event, at
 * the next key release, or when focus leaves, whichever comes first, so
 * it can never eat a character somebody meant to type.
 */
final class TypedEcho {

    private static final String ARMED = "nmox.editing.typedEcho";

    private TypedEcho() {
    }

    /**
     * Whether the chord behind an action event will also arrive as a typed
     * character: Alt held without Ctrl or Meta. (Ctrl+Alt is AltGr on
     * Windows, and a chord holding Ctrl or ⌘ posts nothing the editor would
     * insert.)
     */
    static boolean follows(int actionModifiers) {
        return (actionModifiers & ActionEvent.ALT_MASK) != 0
                && (actionModifiers & (ActionEvent.CTRL_MASK | ActionEvent.META_MASK)) == 0;
    }

    /** Consumes the next KEY_TYPED on {@code target}, once. */
    static void swallowNext(JComponent target) {
        if (Boolean.TRUE.equals(target.getClientProperty(ARMED))) {
            return; // a held key repeats the chord before its release
        }
        target.putClientProperty(ARMED, Boolean.TRUE);
        Guard guard = new Guard(target);
        target.addKeyListener(guard);
        target.addFocusListener(guard.onFocus);
    }

    private static final class Guard extends KeyAdapter {

        private final JComponent target;
        private final FocusAdapter onFocus = new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                disarm();
            }
        };

        Guard(JComponent target) {
            this.target = target;
        }

        @Override
        public void keyTyped(KeyEvent e) {
            e.consume();
            disarm();
        }

        @Override
        public void keyReleased(KeyEvent e) {
            disarm();
        }

        private void disarm() {
            target.removeKeyListener(this);
            target.removeFocusListener(onFocus);
            target.putClientProperty(ARMED, null);
        }
    }
}
