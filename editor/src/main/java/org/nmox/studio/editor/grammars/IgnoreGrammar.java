package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Ignore TextMate grammar (see NOTICE-grammars.md for the
 * pinned upstream) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "ignore.tmLanguage.json", mimeType = "text/x-ignore")
@MIMEResolver.ExtensionRegistration(displayName = "Ignore Files", mimeType = "text/x-ignore", extension = {"gitignore", "dockerignore", "npmignore", "eslintignore", "prettierignore", "gcloudignore", "gitattributes"}, position = 2150)
public final class IgnoreGrammar {

    private IgnoreGrammar() {
    }
}
