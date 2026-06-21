package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Zig TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "zig.tmLanguage.json", mimeType = "text/x-zig")
@MIMEResolver.ExtensionRegistration(displayName = "Zig", mimeType = "text/x-zig", extension = {"zig"}, position = 2430)
public final class ZigGrammar {

    private ZigGrammar() {
    }
}
