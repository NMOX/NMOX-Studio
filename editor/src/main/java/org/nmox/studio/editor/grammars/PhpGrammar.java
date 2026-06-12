package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Php TextMate grammar (pinned from VS Code 1.95.0,
 * see NOTICE-grammars.md) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "php.tmLanguage.json", mimeType = "text/x-php5")
@MIMEResolver.ExtensionRegistration(displayName = "Php", mimeType = "text/x-php5", extension = {"php"})
public final class PhpGrammar {

    private PhpGrammar() {
    }
}
