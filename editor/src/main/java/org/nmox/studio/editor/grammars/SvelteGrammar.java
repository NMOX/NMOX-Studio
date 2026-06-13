package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Svelte TextMate grammar (see NOTICE-grammars.md for the
 * pinned upstream) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "svelte.tmLanguage.json", mimeType = "text/x-svelte")
@MIMEResolver.ExtensionRegistration(displayName = "Svelte", mimeType = "text/x-svelte", extension = {"svelte"})
public final class SvelteGrammar {

    private SvelteGrammar() {
    }
}
