package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Racket TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "racket.tmLanguage.json", mimeType = "text/x-racket")
@MIMEResolver.ExtensionRegistration(displayName = "Racket", mimeType = "text/x-racket", extension = {"rkt"}, position = 2434)
public final class RacketGrammar {

    private RacketGrammar() {
    }
}
