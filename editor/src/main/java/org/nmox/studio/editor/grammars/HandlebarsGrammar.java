package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Handlebars TextMate grammar (see NOTICE-grammars.md for the
 * pinned upstream) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "handlebars.tmLanguage.json", mimeType = "text/x-handlebars")
@MIMEResolver.ExtensionRegistration(displayName = "Handlebars", mimeType = "text/x-handlebars", extension = {"hbs", "handlebars"}, position = 2130)
public final class HandlebarsGrammar {

    private HandlebarsGrammar() {
    }
}
