package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Swift TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "swift.tmLanguage.json", mimeType = "text/x-swift")
@MIMEResolver.ExtensionRegistration(displayName = "Swift", mimeType = "text/x-swift", extension = {"swift"})
public final class SwiftGrammar {

    private SwiftGrammar() {
    }
}
