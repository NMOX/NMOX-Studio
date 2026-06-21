package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Lisp TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "commonlisp.tmLanguage.json", mimeType = "text/x-lisp")
@MIMEResolver.ExtensionRegistration(displayName = "Lisp", mimeType = "text/x-lisp", extension = {"lisp", "cl", "asd", "el"}, position = 2230)
public final class LispGrammar {

    private LispGrammar() {
    }
}
