package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the PureScript TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "purescript.tmLanguage.json", mimeType = "text/x-purescript")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_PureScriptGrammar_LOADER", mimeType = "text/x-purescript", extension = {"purs"}, position = 2437)
@org.openide.util.NbBundle.Messages("LBL_PureScriptGrammar_LOADER=PureScript")
public final class PureScriptGrammar {

    private PureScriptGrammar() {
    }
}
