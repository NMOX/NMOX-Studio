package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Liquid TextMate grammar (see NOTICE-grammars.md for the
 * pinned upstream) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "liquid.tmLanguage.json", mimeType = "text/x-liquid")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_LiquidGrammar_LOADER", mimeType = "text/x-liquid", extension = {"liquid"}, position = 2220)
@org.openide.util.NbBundle.Messages("LBL_LiquidGrammar_LOADER=Liquid")
public final class LiquidGrammar {

    private LiquidGrammar() {
    }
}
