package org.nmox.studio.editor.angular;

import java.awt.event.ActionEvent;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorActionRegistration;
import org.netbeans.editor.BaseAction;
import org.openide.awt.StatusDisplayer;

/**
 * Go to Component (⌥⌘B in templates, plus the editor popup): jump from
 * a {@code <app-hero>} tag to the component that declares the selector
 * — via {@link NgSelectors}' own index, no language service required
 * (the Angular-top arc, 2026-08-11).
 *
 * <p><b>Why a fresh chord.</b> The v1.219.0 {@code D-B} mime chord no
 * longer dispatches at all — a property-gated probe showed the action
 * never fires (silent regression, ledgered). Rather than re-bisect the
 * platform's keybinding dispatch, this action rides the registration
 * idiom that is live-proven TODAY: the ⌥⌘E Emmet pattern —
 * {@code @EditorActionRegistration} plus a {@code DA-} binding in the
 * mime's Keybindings folder, with a popup entry so the mouse path
 * exists regardless of any chord.
 */
@EditorActionRegistration(name = "nmox-ng-goto-component",
        mimeType = "text/x-ng-template",
        popupPath = "", popupPosition = 96) // 95 = the v1.313 switcher (ledger 80)
public class NgGoToComponentAction extends BaseAction {

    public NgGoToComponentAction() {
        super("nmox-ng-goto-component");
    }

    @Override
    public void actionPerformed(ActionEvent evt, JTextComponent target) {
        if (target == null) {
            return;
        }
        if (!NgSelectorHyperlink.jumpToSelector(
                target.getDocument(), target.getCaretPosition())) {
            StatusDisplayer.getDefault().setStatusText(
                    "Place the caret on a component tag like <app-hero> to jump"
                    + " to its component");
        }
    }
}
