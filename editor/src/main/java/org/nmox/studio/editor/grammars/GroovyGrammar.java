package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Groovy TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "groovy.tmLanguage.json", mimeType = "text/x-groovy")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_GroovyGrammar_LOADER", mimeType = "text/x-groovy", extension = {"groovy", "gvy"}, position = 2120)
@org.openide.util.NbBundle.Messages("LBL_GroovyGrammar_LOADER=Groovy")
public final class GroovyGrammar {

    private GroovyGrammar() {
    }
}
