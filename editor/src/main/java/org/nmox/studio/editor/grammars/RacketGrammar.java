package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Racket TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "racket.tmLanguage.json", mimeType = "text/x-racket")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_RacketGrammar_LOADER", mimeType = "text/x-racket", extension = {"rkt"}, position = 2434)
@org.openide.util.NbBundle.Messages("LBL_RacketGrammar_LOADER=Racket")
public final class RacketGrammar {

    private RacketGrammar() {
    }
}
