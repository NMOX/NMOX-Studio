package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Php TextMate grammar (pinned from VS Code 1.95.0,
 * see NOTICE-grammars.md) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "php.tmLanguage.json", mimeType = "text/x-php5")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_PhpGrammar_LOADER", mimeType = "text/x-php5", extension = {"php"}, position = 2290)
@org.openide.util.NbBundle.Messages("LBL_PhpGrammar_LOADER=Php")
public final class PhpGrammar {

    private PhpGrammar() {
    }
}
