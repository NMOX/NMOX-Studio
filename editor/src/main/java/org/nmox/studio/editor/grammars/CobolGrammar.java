package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the COBOL TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "cobol.tmLanguage.json", mimeType = "text/x-cobol")
// The capital spellings too: COBOL sources that came from a mainframe are
// named in capitals, and the platform matches by case off Windows.
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_CobolGrammar_LOADER", mimeType = "text/x-cobol", extension = {"cob", "cbl", "cpy", "COB", "CBL", "CPY"}, position = 2448)
@org.openide.util.NbBundle.Messages("LBL_CobolGrammar_LOADER=COBOL")
public final class CobolGrammar {

    private CobolGrammar() {
    }
}
