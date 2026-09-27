package org.nmox.studio.rack.ui.controls;

import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleState;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openide.NotifyDescriptor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An editable LCD is edited from the keyboard exactly as with the mouse
 * (3.4). SOLDER's command, TAIL's path, the HTTP console's URL, REFLEX's
 * filter and ~16 more were double-click only and unfocusable, so a keyboard
 * user could not set them at all. Enter and F2 open the same editor a
 * double-click opens, the edit is offered as the accessible action, and a
 * read-only display stays status: no focus, no action, no editor.
 */
class LcdKeyboardEditTest {

    private final List<String> asked = new ArrayList<>();

    @AfterEach
    void restore() {
        LcdDisplay.resetPrompter();
    }

    /** Answers the editor as a user typing {@code typed} and pressing OK would. */
    private void answer(String typed) {
        LcdDisplay.prompter = line -> {
            asked.add(line.getInputText());
            line.setInputText(typed);
            return NotifyDescriptor.OK_OPTION;
        };
    }

    private static void press(LcdDisplay lcd, String key) {
        Object name = lcd.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(key));
        assertThat(name).as(key + " is bound on an editable LCD").isNotNull();
        lcd.getActionMap().get(name).actionPerformed(new ActionEvent(lcd, ActionEvent.ACTION_PERFORMED, key));
    }

    private static LcdDisplay editable() {
        LcdDisplay lcd = new LcdDisplay(200, 1);
        lcd.setText("npm run build");
        lcd.setEditable("Command");
        return lcd;
    }

    @Test
    @DisplayName("an editable LCD is a control: it takes focus and says it is editable")
    void editableTakesFocus() {
        LcdDisplay lcd = editable();
        assertThat(lcd.isFocusable()).isTrue();
        assertThat(lcd.getAccessibleContext().getAccessibleStateSet().contains(AccessibleState.EDITABLE)).isTrue();
    }

    @Test
    @DisplayName("Enter opens the editor seeded with the current text, and the new value lands")
    void enterEdits() {
        LcdDisplay lcd = editable();
        int[] fired = {0};
        lcd.addEditListener(() -> fired[0]++);
        answer("npm test");

        press(lcd, "ENTER");

        assertThat(asked).containsExactly("npm run build");
        assertThat(lcd.getText()).isEqualTo("npm test");
        assertThat(fired[0]).as("the device hears the edit, as after a double-click").isEqualTo(1);
    }

    @Test
    @DisplayName("F2 opens the same editor")
    void f2Edits() {
        LcdDisplay lcd = editable();
        answer("make");
        press(lcd, "F2");
        assertThat(lcd.getText()).isEqualTo("make");
    }

    @Test
    @DisplayName("a double-click still edits, through the same path")
    void doubleClickEdits() {
        LcdDisplay lcd = editable();
        answer("cargo build");
        lcd.dispatchEvent(new MouseEvent(lcd, MouseEvent.MOUSE_CLICKED, 0L, 0, 5, 5, 2, false));
        assertThat(lcd.getText()).isEqualTo("cargo build");
    }

    @Test
    @DisplayName("the edit is the accessible action, and doing it opens the editor")
    void accessibleActionEdits() throws Exception {
        LcdDisplay lcd = editable();
        answer("go build ./...");
        AccessibleAction action = lcd.getAccessibleContext().getAccessibleAction();
        assertThat(action).isNotNull();
        assertThat(action.getAccessibleActionCount()).isEqualTo(1);
        assertThat(action.getAccessibleActionDescription(0)).isNotBlank();

        boolean[] done = {false};
        SwingUtilities.invokeAndWait(() -> done[0] = action.doAccessibleAction(0));

        assertThat(done[0]).isTrue();
        assertThat(lcd.getText()).isEqualTo("go build ./...");
    }

    @Test
    @DisplayName("Cancel leaves the text and tells nobody")
    void cancelChangesNothing() {
        LcdDisplay lcd = editable();
        int[] fired = {0};
        lcd.addEditListener(() -> fired[0]++);
        LcdDisplay.prompter = line -> {
            line.setInputText("rm -rf /");
            return NotifyDescriptor.CANCEL_OPTION;
        };
        press(lcd, "ENTER");
        assertThat(lcd.getText()).isEqualTo("npm run build");
        assertThat(fired[0]).isZero();
    }

    @Test
    @DisplayName("a read-only LCD is status: no focus, no action, no editor from any key")
    void readOnlyStaysStatus() {
        LcdDisplay lcd = new LcdDisplay(200, 1);
        lcd.setText("READY");
        answer("changed");

        assertThat(lcd.isFocusable()).isFalse();
        assertThat(lcd.getAccessibleContext().getAccessibleAction()).isNull();
        assertThat(lcd.edit()).isFalse();
        lcd.dispatchEvent(new MouseEvent(lcd, MouseEvent.MOUSE_CLICKED, 0L, 0, 5, 5, 2, false));
        assertThat(asked).as("no editor opened").isEmpty();
        assertThat(lcd.getText()).isEqualTo("READY");
    }
}
