package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Zig TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "zig.tmLanguage.json", mimeType = "text/x-zig")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_ZigGrammar_LOADER", mimeType = "text/x-zig", extension = {"zig"}, position = 2430)
@org.openide.util.NbBundle.Messages("LBL_ZigGrammar_LOADER=Zig")
public final class ZigGrammar {

    private ZigGrammar() {
    }
}
