package org.nmox.studio.editor.editing;

import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import javax.swing.JTextField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An Option chord on macOS also types a character. The guard swallows
 * exactly that one typed event and no other: these run a real text field
 * through real key events, which is where a typed character is inserted.
 */
class TypedEchoTest {

    /** A text field whose key and focus handling can be run without a focused window. */
    @SuppressWarnings("serial")
    private static final class Field extends JTextField {

        Field(String text) {
            super(text);
        }

        void key(KeyEvent e) {
            processKeyEvent(e);
        }

        void focus(FocusEvent e) {
            processFocusEvent(e);
        }
    }

    private static KeyEvent typed(JTextField field, char c) {
        return new KeyEvent(field, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, c);
    }

    private static KeyEvent released(JTextField field, int code) {
        return new KeyEvent(field, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED);
    }

    @Test
    @DisplayName("a chord types its character when Alt is held without Ctrl or Meta, and only then")
    void whichChordsType() {
        assertThat(TypedEcho.follows(ActionEvent.ALT_MASK)).isTrue();
        assertThat(TypedEcho.follows(ActionEvent.ALT_MASK | ActionEvent.SHIFT_MASK)).isTrue();
        assertThat(TypedEcho.follows(0)).isFalse();
        assertThat(TypedEcho.follows(ActionEvent.SHIFT_MASK)).isFalse();
        // Cmd+Opt posts no typed event; Ctrl+Alt is AltGr, whose character the user means
        assertThat(TypedEcho.follows(ActionEvent.ALT_MASK | ActionEvent.META_MASK)).isFalse();
        assertThat(TypedEcho.follows(ActionEvent.ALT_MASK | ActionEvent.CTRL_MASK)).isFalse();
    }

    @Test
    @DisplayName("without the guard the typed echo lands in the text: the control")
    void theControlTypes() {
        Field field = new Field("ab");
        field.setCaretPosition(2);
        field.key(typed(field, 'Å'));
        assertThat(field.getText()).isEqualTo("abÅ");
    }

    @Test
    @DisplayName("the guard swallows the next typed character, and the one after it is typed as usual")
    void swallowsExactlyOne() {
        Field field = new Field("ab");
        field.setCaretPosition(2);
        TypedEcho.swallowNext(field);
        KeyEvent echo = typed(field, 'Å');
        field.key(echo);
        assertThat(echo.isConsumed()).isTrue();
        assertThat(field.getText()).isEqualTo("ab");
        field.key(typed(field, 'c'));
        assertThat(field.getText()).isEqualTo("abc");
        assertThat(field.getKeyListeners()).isEmpty();
    }

    @Test
    @DisplayName("a chord that typed nothing disarms the guard at its key release")
    void releaseDisarms() {
        Field field = new Field("ab");
        field.setCaretPosition(2);
        TypedEcho.swallowNext(field);
        field.key(released(field, KeyEvent.VK_A));
        field.key(typed(field, 'c'));
        assertThat(field.getText()).isEqualTo("abc");
        assertThat(field.getKeyListeners()).isEmpty();
        assertThat(field.getFocusListeners()).noneMatch(l -> l.getClass().getName().contains("TypedEcho"));
    }

    @Test
    @DisplayName("losing the focus disarms it too, and a held key arms it only once")
    void focusLossDisarmsAndRepeatDoesNotStack() {
        Field field = new Field("ab");
        field.setCaretPosition(2);
        int before = field.getKeyListeners().length;
        TypedEcho.swallowNext(field);
        TypedEcho.swallowNext(field);
        assertThat(field.getKeyListeners()).hasSize(before + 1);
        field.focus(new FocusEvent(field, FocusEvent.FOCUS_LOST));
        assertThat(field.getKeyListeners()).hasSize(before);
        field.key(typed(field, 'c'));
        assertThat(field.getText()).isEqualTo("abc");
        // and it can be armed again afterwards
        TypedEcho.swallowNext(field);
        assertThat(field.getKeyListeners()).hasSize(before + 1);
    }
}
