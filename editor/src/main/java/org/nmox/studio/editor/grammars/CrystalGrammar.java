package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Crystal TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "crystal.tmLanguage.json", mimeType = "text/x-crystal")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_CrystalGrammar_LOADER", mimeType = "text/x-crystal", extension = {"cr"}, position = 2050)
@org.openide.util.NbBundle.Messages("LBL_CrystalGrammar_LOADER=Crystal")
public final class CrystalGrammar {

    private CrystalGrammar() {
    }
}
