package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Tcl TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "tcl.tmLanguage.json", mimeType = "text/x-tcl")
@MIMEResolver.ExtensionRegistration(displayName = "Tcl", mimeType = "text/x-tcl", extension = {"tcl", "tk"}, position = 2443)
public final class TclGrammar {

    private TclGrammar() {
    }
}
