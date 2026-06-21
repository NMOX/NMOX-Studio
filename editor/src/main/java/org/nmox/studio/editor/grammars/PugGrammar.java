package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Pug TextMate grammar (see NOTICE-grammars.md for the
 * pinned upstream) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "pug.tmLanguage.json", mimeType = "text/x-pug")
@MIMEResolver.ExtensionRegistration(displayName = "Pug", mimeType = "text/x-pug", extension = {"pug", "jade"}, position = 2320)
public final class PugGrammar {

    private PugGrammar() {
    }
}
