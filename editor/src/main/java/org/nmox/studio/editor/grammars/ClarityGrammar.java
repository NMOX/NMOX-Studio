package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Clarity TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extension — the Contract Kit scaffolds
 * these files, so the editor must speak them.
 */
@GrammarRegistration(grammar = "clarity.tmLanguage.json", mimeType = "text/x-clarity")
@MIMEResolver.ExtensionRegistration(displayName = "Clarity", mimeType = "text/x-clarity", extension = {"clar"}, position = 2455)
public final class ClarityGrammar {

    private ClarityGrammar() {
    }
}
