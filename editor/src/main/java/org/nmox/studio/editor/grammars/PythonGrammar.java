package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Python TextMate grammar (pinned from VS Code 1.95.0,
 * see NOTICE-grammars.md) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "python.tmLanguage.json", mimeType = "text/x-python")
@MIMEResolver.ExtensionRegistration(displayName = "Python", mimeType = "text/x-python", extension = {"py", "pyw"})
public final class PythonGrammar {

    private PythonGrammar() {
    }
}
