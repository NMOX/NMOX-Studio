package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Scheme TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions — the R7RS/Guile family; Racket
 * keeps its own .rkt grammar.
 */
@GrammarRegistration(grammar = "scheme.tmLanguage.json", mimeType = "text/x-scheme")
@MIMEResolver.ExtensionRegistration(displayName = "Scheme", mimeType = "text/x-scheme", extension = {"scm", "ss", "sld", "sps"}, position = 2444)
public final class SchemeGrammar {

    private SchemeGrammar() {
    }
}
