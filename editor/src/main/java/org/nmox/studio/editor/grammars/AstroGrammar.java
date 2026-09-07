package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Astro TextMate grammar (see NOTICE-grammars.md for the
 * pinned upstream) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "astro.tmLanguage.json", mimeType = "text/x-astro")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_AstroGrammar_LOADER", mimeType = "text/x-astro", extension = {"astro"}, position = 2000)
@org.openide.util.NbBundle.Messages("LBL_AstroGrammar_LOADER=Astro")
public final class AstroGrammar {

    private AstroGrammar() {
    }
}
