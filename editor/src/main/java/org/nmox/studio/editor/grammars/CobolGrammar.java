package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the COBOL TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "cobol.tmLanguage.json", mimeType = "text/x-cobol")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_CobolGrammar_LOADER", mimeType = "text/x-cobol", extension = {"cob", "cbl", "cpy"}, position = 2448)
@org.openide.util.NbBundle.Messages("LBL_CobolGrammar_LOADER=COBOL")
public final class CobolGrammar {

    private CobolGrammar() {
    }
}
