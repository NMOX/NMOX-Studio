package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Crystal TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "crystal.tmLanguage.json", mimeType = "text/x-crystal")
@MIMEResolver.ExtensionRegistration(displayName = "Crystal", mimeType = "text/x-crystal", extension = {"cr"}, position = 2050)
public final class CrystalGrammar {

    private CrystalGrammar() {
    }
}
