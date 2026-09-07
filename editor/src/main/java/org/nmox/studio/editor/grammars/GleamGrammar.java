package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Gleam TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extension.
 */
@GrammarRegistration(grammar = "gleam.tmLanguage.json", mimeType = "text/x-gleam")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_GleamGrammar_LOADER", mimeType = "text/x-gleam", extension = {"gleam"}, position = 2431)
@org.openide.util.NbBundle.Messages("LBL_GleamGrammar_LOADER=Gleam")
public final class GleamGrammar {

    private GleamGrammar() {
    }
}
