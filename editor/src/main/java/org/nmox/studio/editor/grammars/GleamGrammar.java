package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Gleam TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extension.
 */
@GrammarRegistration(grammar = "gleam.tmLanguage.json", mimeType = "text/x-gleam")
@MIMEResolver.ExtensionRegistration(displayName = "Gleam", mimeType = "text/x-gleam", extension = {"gleam"}, position = 2431)
public final class GleamGrammar {

    private GleamGrammar() {
    }
}
