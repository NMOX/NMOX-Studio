package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Aiken TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extension — the Contract Kit scaffolds
 * these files, so the editor must speak them.
 */
@GrammarRegistration(grammar = "aiken.tmLanguage.json", mimeType = "text/x-aiken")
@MIMEResolver.ExtensionRegistration(displayName = "Aiken", mimeType = "text/x-aiken", extension = {"ak"}, position = 2453)
public final class AikenGrammar {

    private AikenGrammar() {
    }
}
