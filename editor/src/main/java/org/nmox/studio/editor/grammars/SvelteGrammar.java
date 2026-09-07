package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Svelte TextMate grammar (see NOTICE-grammars.md for the
 * pinned upstream) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "svelte.tmLanguage.json", mimeType = "text/x-svelte")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_SvelteGrammar_LOADER", mimeType = "text/x-svelte", extension = {"svelte"}, position = 2400)
@org.openide.util.NbBundle.Messages("LBL_SvelteGrammar_LOADER=Svelte")
public final class SvelteGrammar {

    private SvelteGrammar() {
    }
}
