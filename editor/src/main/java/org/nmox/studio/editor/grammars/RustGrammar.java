package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Rust TextMate grammar (pinned from VS Code 1.95.0,
 * see NOTICE-grammars.md) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "rust.tmLanguage.json", mimeType = "text/x-rust")
@MIMEResolver.ExtensionRegistration(displayName = "Rust", mimeType = "text/x-rust", extension = {"rs"}, position = 2360)
public final class RustGrammar {

    private RustGrammar() {
    }
}
