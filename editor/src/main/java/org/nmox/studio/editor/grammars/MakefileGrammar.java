package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Makefile TextMate grammar (see NOTICE-grammars.md for the
 * pinned upstream) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "makefile.tmLanguage.json", mimeType = "text/x-makefile")
@MIMEResolver.ExtensionRegistration(displayName = "Makefile", mimeType = "text/x-makefile", extension = {"mk"})
public final class MakefileGrammar {

    private MakefileGrammar() {
    }
}
