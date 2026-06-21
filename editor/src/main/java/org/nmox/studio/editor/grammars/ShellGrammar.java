package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Shell TextMate grammar (pinned from VS Code 1.95.0,
 * see NOTICE-grammars.md) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "shell.tmLanguage.json", mimeType = "text/sh")
@MIMEResolver.ExtensionRegistration(displayName = "Shell", mimeType = "text/sh", extension = {"sh", "bash", "zsh"}, position = 2390)
public final class ShellGrammar {

    private ShellGrammar() {
    }
}
