package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the D TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "d.tmLanguage.json", mimeType = "text/x-d")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_DGrammar_LOADER", mimeType = "text/x-d", extension = {"d", "di"}, position = 2433)
@org.openide.util.NbBundle.Messages("LBL_DGrammar_LOADER=D")
public final class DGrammar {

    private DGrammar() {
    }
}
