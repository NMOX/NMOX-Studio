package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Perl TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "perl.tmLanguage.json", mimeType = "text/x-perl")
@MIMEResolver.ExtensionRegistration(displayName = "Perl", mimeType = "text/x-perl", extension = {"pl", "pm", "t"})
public final class PerlGrammar {

    private PerlGrammar() {
    }
}
