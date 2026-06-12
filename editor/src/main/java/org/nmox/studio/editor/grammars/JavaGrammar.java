package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Java TextMate grammar (pinned from VS Code 1.95.0,
 * see NOTICE-grammars.md) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "java.tmLanguage.json", mimeType = "text/x-java")
@MIMEResolver.ExtensionRegistration(displayName = "Java", mimeType = "text/x-java", extension = {"java"})
public final class JavaGrammar {

    private JavaGrammar() {
    }
}
