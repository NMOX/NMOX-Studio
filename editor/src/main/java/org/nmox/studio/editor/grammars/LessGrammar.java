package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Less TextMate grammar (pinned from VS Code 1.95.0,
 * see NOTICE-grammars.md) and its file extension.
 */
@GrammarRegistration(grammar = "less.tmLanguage.json", mimeType = "text/x-less")
@MIMEResolver.ExtensionRegistration(displayName = "Less", mimeType = "text/x-less", extension = {"less"}, position = 2210)
public final class LessGrammar {

    private LessGrammar() {
    }
}
