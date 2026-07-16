package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Elm TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "elm.tmLanguage.json", mimeType = "text/x-elm")
@MIMEResolver.ExtensionRegistration(displayName = "Elm", mimeType = "text/x-elm", extension = {"elm"}, position = 2435)
public final class ElmGrammar {

    private ElmGrammar() {
    }
}
