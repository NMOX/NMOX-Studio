package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Odin TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "odin.tmLanguage.json", mimeType = "text/x-odin")
@MIMEResolver.ExtensionRegistration(displayName = "Odin", mimeType = "text/x-odin", extension = {"odin"}, position = 2447)
public final class OdinGrammar {

    private OdinGrammar() {
    }
}
