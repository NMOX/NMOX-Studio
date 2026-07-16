package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the ReScript TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "rescript.tmLanguage.json", mimeType = "text/x-rescript")
@MIMEResolver.ExtensionRegistration(displayName = "ReScript", mimeType = "text/x-rescript", extension = {"res", "resi"}, position = 2436)
public final class ReScriptGrammar {

    private ReScriptGrammar() {
    }
}
