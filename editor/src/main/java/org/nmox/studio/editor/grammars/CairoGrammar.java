package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Cairo (Starknet) TextMate grammar (see NOTICE-grammars.md
 * for provenance) and its file extension.
 */
@GrammarRegistration(grammar = "cairo.tmLanguage.json", mimeType = "text/x-cairo")
@MIMEResolver.ExtensionRegistration(displayName = "Cairo", mimeType = "text/x-cairo", extension = {"cairo"}, position = 2451)
public final class CairoGrammar {

    private CairoGrammar() {
    }
}
