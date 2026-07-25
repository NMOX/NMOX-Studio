package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Tact TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extension — the Contract Kit scaffolds
 * these files, so the editor must speak them.
 */
@GrammarRegistration(grammar = "tact.tmLanguage.json", mimeType = "text/x-tact")
@MIMEResolver.ExtensionRegistration(displayName = "Tact", mimeType = "text/x-tact", extension = {"tact"}, position = 2454)
public final class TactGrammar {

    private TactGrammar() {
    }
}
