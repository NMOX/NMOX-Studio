package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Elm TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "elm.tmLanguage.json", mimeType = "text/x-elm")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_ElmGrammar_LOADER", mimeType = "text/x-elm", extension = {"elm"}, position = 2435)
@org.openide.util.NbBundle.Messages("LBL_ElmGrammar_LOADER=Elm")
public final class ElmGrammar {

    private ElmGrammar() {
    }
}
