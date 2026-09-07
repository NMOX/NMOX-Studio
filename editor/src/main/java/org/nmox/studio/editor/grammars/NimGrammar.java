package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Nim TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "nim.tmLanguage.json", mimeType = "text/x-nim")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_NimGrammar_LOADER", mimeType = "text/x-nim", extension = {"nim", "nims", "nimble"}, position = 2432)
@org.openide.util.NbBundle.Messages("LBL_NimGrammar_LOADER=Nim")
public final class NimGrammar {

    private NimGrammar() {
    }
}
