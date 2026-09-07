package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Solidity TextMate grammar (see NOTICE-grammars.md for
 * the pinned upstream) and the {@code .sol} extension. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 *
 * <p>Grammar-only mime: no CSL {@code @LanguageRegistration} — comment
 * toggling comes from {@code LanguageComments}, keyword completion from
 * {@code PolyglotCompletionProvider}, and the Navigator outline from
 * {@code OutlineModel}, all keyed on {@code text/x-solidity}.
 */
@GrammarRegistration(grammar = "solidity.tmLanguage.json", mimeType = "text/x-solidity")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_SolidityGrammar_LOADER", mimeType = "text/x-solidity", extension = {"sol"}, position = 2500)
@org.openide.util.NbBundle.Messages("LBL_SolidityGrammar_LOADER=Solidity")
public final class SolidityGrammar {

    private SolidityGrammar() {
    }
}
