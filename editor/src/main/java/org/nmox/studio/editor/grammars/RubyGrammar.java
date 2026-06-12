package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Ruby TextMate grammar (pinned from VS Code 1.95.0,
 * see NOTICE-grammars.md) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "ruby.tmLanguage.json", mimeType = "text/x-ruby")
@MIMEResolver.ExtensionRegistration(displayName = "Ruby", mimeType = "text/x-ruby", extension = {"rb", "rake"})
public final class RubyGrammar {

    private RubyGrammar() {
    }
}
