package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Odin TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "odin.tmLanguage.json", mimeType = "text/x-odin")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_OdinGrammar_LOADER", mimeType = "text/x-odin", extension = {"odin"}, position = 2447)
@org.openide.util.NbBundle.Messages("LBL_OdinGrammar_LOADER=Odin")
public final class OdinGrammar {

    private OdinGrammar() {
    }
}
