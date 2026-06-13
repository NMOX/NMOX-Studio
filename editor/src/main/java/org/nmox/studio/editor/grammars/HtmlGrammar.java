package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;

/**
 * Registers the HTML TextMate grammar (pinned from VS Code 1.95.0,
 * see NOTICE-grammars.md). The text/html MIME resolution and editor
 * are owned by WebFileSupport; this only supplies the tokens.
 */
@GrammarRegistration(grammar = "html.tmLanguage.json", mimeType = "text/html")
public final class HtmlGrammar {

    private HtmlGrammar() {
    }
}
