package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Pascal TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "pascal.tmLanguage.json", mimeType = "text/x-pascal")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_PascalGrammar_LOADER", mimeType = "text/x-pascal", extension = {"pas", "pp", "lpr"}, position = 2446)
@org.openide.util.NbBundle.Messages("LBL_PascalGrammar_LOADER=Pascal")
public final class PascalGrammar {

    private PascalGrammar() {
    }
}
