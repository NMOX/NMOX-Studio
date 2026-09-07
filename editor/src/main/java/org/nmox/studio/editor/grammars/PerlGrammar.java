package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Perl TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "perl.tmLanguage.json", mimeType = "text/x-perl")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_PerlGrammar_LOADER", mimeType = "text/x-perl", extension = {"pl", "pm", "t"}, position = 2280)
@org.openide.util.NbBundle.Messages("LBL_PerlGrammar_LOADER=Perl")
public final class PerlGrammar {

    private PerlGrammar() {
    }
}
