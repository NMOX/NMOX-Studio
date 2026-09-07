package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Ada TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "ada.tmLanguage.json", mimeType = "text/x-ada")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_AdaGrammar_LOADER", mimeType = "text/x-ada", extension = {"ads", "adb", "ada"}, position = 2445)
@org.openide.util.NbBundle.Messages("LBL_AdaGrammar_LOADER=Ada")
public final class AdaGrammar {

    private AdaGrammar() {
    }
}
