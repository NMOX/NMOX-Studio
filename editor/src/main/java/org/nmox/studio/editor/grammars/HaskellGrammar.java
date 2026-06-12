package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Haskell TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "haskell.tmLanguage.json", mimeType = "text/x-haskell")
@MIMEResolver.ExtensionRegistration(displayName = "Haskell", mimeType = "text/x-haskell", extension = {"hs"})
public final class HaskellGrammar {

    private HaskellGrammar() {
    }
}
