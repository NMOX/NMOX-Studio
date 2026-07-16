package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the PureScript TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "purescript.tmLanguage.json", mimeType = "text/x-purescript")
@MIMEResolver.ExtensionRegistration(displayName = "PureScript", mimeType = "text/x-purescript", extension = {"purs"}, position = 2437)
public final class PureScriptGrammar {

    private PureScriptGrammar() {
    }
}
