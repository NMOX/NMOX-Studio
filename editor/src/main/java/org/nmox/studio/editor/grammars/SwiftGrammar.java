package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Swift TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "swift.tmLanguage.json", mimeType = "text/x-swift")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_SwiftGrammar_LOADER", mimeType = "text/x-swift", extension = {"swift"}, position = 2410)
@org.openide.util.NbBundle.Messages("LBL_SwiftGrammar_LOADER=Swift")
public final class SwiftGrammar {

    private SwiftGrammar() {
    }
}
