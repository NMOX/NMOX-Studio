package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Nim TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "nim.tmLanguage.json", mimeType = "text/x-nim")
@MIMEResolver.ExtensionRegistration(displayName = "Nim", mimeType = "text/x-nim", extension = {"nim", "nims", "nimble"}, position = 2432)
public final class NimGrammar {

    private NimGrammar() {
    }
}
