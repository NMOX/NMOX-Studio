package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Json TextMate grammar (pinned from VS Code 1.95.0,
 * see NOTICE-grammars.md) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "json.tmLanguage.json", mimeType = "text/x-json")
@MIMEResolver.ExtensionRegistration(displayName = "Json", mimeType = "text/x-json", extension = {"json"})
public final class JsonGrammar {

    private JsonGrammar() {
    }
}
