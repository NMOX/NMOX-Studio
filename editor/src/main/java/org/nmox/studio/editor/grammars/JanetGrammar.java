package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Janet TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "janet.tmLanguage.json", mimeType = "text/x-janet")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_JanetGrammar_LOADER", mimeType = "text/x-janet", extension = {"janet", "jdn"}, position = 2450)
@org.openide.util.NbBundle.Messages("LBL_JanetGrammar_LOADER=Janet")
public final class JanetGrammar {

    private JanetGrammar() {
    }
}
