package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the SCSS TextMate grammar (pinned from VS Code 1.95.0,
 * see NOTICE-grammars.md). Indented .sass files share it: imperfect
 * for the indented syntax, far better than plain text.
 */
@GrammarRegistration(grammar = "scss.tmLanguage.json", mimeType = "text/x-scss")
@MIMEResolver.ExtensionRegistration(displayName = "SCSS", mimeType = "text/x-scss", extension = {"scss", "sass"})
public final class ScssGrammar {

    private ScssGrammar() {
    }
}
