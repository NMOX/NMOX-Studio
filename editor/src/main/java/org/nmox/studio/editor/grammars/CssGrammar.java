package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the CSS TextMate grammar (pinned from VS Code 1.95.0,
 * see NOTICE-grammars.md) and its file extension. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "css.tmLanguage.json", mimeType = "text/css")
@MIMEResolver.ExtensionRegistration(displayName = "CSS", mimeType = "text/css", extension = {"css"}, position = 2060)
public final class CssGrammar {

    private CssGrammar() {
    }
}
