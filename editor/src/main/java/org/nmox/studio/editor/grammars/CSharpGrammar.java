package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the CSharp TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "csharp.tmLanguage.json", mimeType = "text/x-csharp")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_CSharpGrammar_LOADER", mimeType = "text/x-csharp", extension = {"cs"}, position = 2020)
@org.openide.util.NbBundle.Messages("LBL_CSharpGrammar_LOADER=CSharp")
public final class CSharpGrammar {

    private CSharpGrammar() {
    }
}
