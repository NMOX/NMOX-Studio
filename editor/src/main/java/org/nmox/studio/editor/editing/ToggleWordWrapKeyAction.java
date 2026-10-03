package org.nmox.studio.editor.editing;

import java.awt.event.ActionEvent;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorActionRegistration;
import org.netbeans.editor.BaseAction;

/**
 * The key half of View ▸ Word Wrap: an editor-kit action, so ⌥Z (Alt+Z)
 * can be an EDITOR keybinding. It has to be one. A global shortcut fires
 * only after the focused editor declined the key, and on macOS the editor
 * does not decline ⌥Z: it types Ω ({@link TypedEcho} has the mechanism),
 * so a global chord would toggle the wrap and leave a character behind.
 * Bound in the editor's keymap, the press is consumed here and the typed
 * echo is swallowed.
 *
 * <p>Everything it does is {@link ToggleWordWrapAction#press}.
 */
@EditorActionRegistration(name = ToggleWordWrapKeyAction.NAME)
public class ToggleWordWrapKeyAction extends BaseAction {

    /** The kit action name the keybinding file names. */
    public static final String NAME = "nmox-toggle-word-wrap";

    public ToggleWordWrapKeyAction() {
        super(NAME);
    }

    @Override
    public void actionPerformed(ActionEvent evt, JTextComponent target) {
        if (target == null) {
            return;
        }
        if (TypedEcho.arms(evt)) {
            TypedEcho.swallowNext(target);
        }
        ToggleWordWrapAction.press(target);
    }
}
