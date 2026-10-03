package org.nmox.studio.editor.editing;

import java.awt.event.ActionEvent;
import javax.swing.Action;
import javax.swing.text.EditorKit;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorActionRegistration;
import org.netbeans.editor.BaseAction;
import org.netbeans.editor.BaseKit;

/**
 * Format, on VS Code's macOS chord ⇧⌥F — and nothing typed after it.
 *
 * <p>3.2.0 bound ⇧⌥F straight to the editor's own {@code format} action.
 * On macOS an Option chord also arrives as a typed character
 * ({@link TypedEcho}): ⇧⌥F is {@code Ï}. The file was formatted and then an
 * Ï was typed at the caret, replacing whatever was selected. Photographed
 * in the assembled app (3.6.0): {@code function nameHandler} with {@code
 * name} selected became {@code function ÏHandler}, while ⇧⌥A beside it,
 * whose action arms the echo guard, typed nothing. It went unseen because
 * no walk had ever pressed the chord: a binding was measured as resolving
 * to the action, and an action that runs is not a chord that only runs it.
 *
 * <p>So the chord is bound to this action, which swallows the one typed
 * event that belongs to the press and then runs the kit's own
 * {@code format}, whatever that is for the language — the platform's
 * reformat, or the product's Prettier-aware one where a kit replaces it.
 * The menu row and the platform's own chord (⌃⇧F) are untouched: they do
 * not type.
 */
@EditorActionRegistration(name = FormatChordAction.NAME)
public class FormatChordAction extends BaseAction {

    /** The kit action name the keybinding file names. */
    public static final String NAME = "nmox-format-on-option-chord";

    public FormatChordAction() {
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
        Action format = formatOf(target);
        if (format != null && format.isEnabled()) {
            format.actionPerformed(evt);
        }
    }

    /** The {@code format} action of the kit behind {@code target}, or null when its kit has none. */
    static Action formatOf(JTextComponent target) {
        EditorKit kit = target.getUI() == null ? null : target.getUI().getEditorKit(target);
        if (kit instanceof BaseKit base) {
            return base.getActionByName(BaseKit.formatAction);
        }
        if (kit != null) {
            for (Action action : kit.getActions()) {
                if (BaseKit.formatAction.equals(action.getValue(Action.NAME))) {
                    return action;
                }
            }
        }
        return null;
    }
}
