package org.nmox.studio.editor.lsp;

import java.awt.event.ActionEvent;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import org.netbeans.api.editor.EditorActionRegistration;
import org.netbeans.editor.BaseAction;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkProviderExt;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkType;

/**
 * Go to Declaration for Angular templates (v1.219.0): ⌘B / Navigate ▸
 * Go to Declaration on an identifier in {@code .component.html} asks
 * ngserver for {@code textDocument/definition} and jumps — typically
 * into the component class.
 *
 * <p><b>Why a mime-registered action.</b> On CSL panes the platform's
 * global goto-declaration never consults the hyperlink-provider chain
 * for this mime (instrumented live: our registered
 * {@link NgTemplateHyperlinkEnabler} was never even instantiated by the
 * gesture, and wire capture showed no definition request leaving the
 * IDE). Registering the action name FOR THE MIME puts us in the kit's
 * action map that the menu wrapper and the keybinding actually resolve
 * — the same route the per-mime toggle-comment rides. The navigation
 * itself is delegated to the platform LSP client's provider, whose
 * click path performs the request and opens the result.
 */
@EditorActionRegistration(name = "ng-goto-declaration",
        mimeType = "text/x-ng-template",
        popupPath = "", popupPosition = 90)
public class NgTemplateGotoDeclaration extends BaseAction {

    public NgTemplateGotoDeclaration() {
        super("ng-goto-declaration");
    }

    @Override
    public void actionPerformed(ActionEvent evt, JTextComponent target) {
        if (target == null) {
            return;
        }
        Document doc = target.getDocument();
        int offset = target.getCaretPosition();
        if (NgTemplateHyperlinkEnabler.identifierSpan(doc, offset) == null) {
            return; // caret not in a word: nothing to ask the server about
        }
        HyperlinkProviderExt lsp = NgTemplateHyperlinkEnabler.lspProvider();
        if (lsp != null) {
            lsp.performClickAction(doc, offset, HyperlinkType.GO_TO_DECLARATION);
        }
    }
}
