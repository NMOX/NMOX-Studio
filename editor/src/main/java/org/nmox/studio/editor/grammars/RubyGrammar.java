package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Ruby TextMate grammar (pinned from VS Code 1.95.0,
 * see NOTICE-grammars.md) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "ruby.tmLanguage.json", mimeType = "text/x-ruby")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_RubyGrammar_LOADER", mimeType = "text/x-ruby", extension = {"rb", "rake"}, position = 2350)
@org.openide.util.NbBundle.Messages("LBL_RubyGrammar_LOADER=Ruby")
public final class RubyGrammar {

    private RubyGrammar() {
    }
}
