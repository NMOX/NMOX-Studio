package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Vue TextMate grammar (see NOTICE-grammars.md for the
 * pinned upstream) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "vue.tmLanguage.json", mimeType = "text/x-vue")
@MIMEResolver.ExtensionRegistration(displayName = "Vue", mimeType = "text/x-vue", extension = {"vue"}, position = 2420)
public final class VueGrammar {

    private VueGrammar() {
    }
}
