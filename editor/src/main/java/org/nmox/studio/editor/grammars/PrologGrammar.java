package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the SWI-Prolog TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions. Deliberately does NOT claim .pl —
 * Perl owns that extension here, and a wrong-language editor is worse
 * than asking Prolog users for the unambiguous .pro/.prolog spellings.
 */
@GrammarRegistration(grammar = "prolog.tmLanguage.json", mimeType = "text/x-prolog")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_PrologGrammar_LOADER", mimeType = "text/x-prolog", extension = {"pro", "prolog", "plt"}, position = 2442)
@org.openide.util.NbBundle.Messages("LBL_PrologGrammar_LOADER=Prolog")
public final class PrologGrammar {

    private PrologGrammar() {
    }
}
