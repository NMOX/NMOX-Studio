package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the R TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "r.tmLanguage.json", mimeType = "text/x-r")
@MIMEResolver.ExtensionRegistration(displayName = "R", mimeType = "text/x-r", extension = {"r"}, position = 2340)
public final class RGrammar {

    private RGrammar() {
    }
}
