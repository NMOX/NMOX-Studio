package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Haskell TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "haskell.tmLanguage.json", mimeType = "text/x-haskell")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_HaskellGrammar_LOADER", mimeType = "text/x-haskell", extension = {"hs"}, position = 2140)
@org.openide.util.NbBundle.Messages("LBL_HaskellGrammar_LOADER=Haskell")
public final class HaskellGrammar {

    private HaskellGrammar() {
    }
}
