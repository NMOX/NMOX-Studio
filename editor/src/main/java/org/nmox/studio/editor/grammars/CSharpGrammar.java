package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the CSharp TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "csharp.tmLanguage.json", mimeType = "text/x-csharp")
@MIMEResolver.ExtensionRegistration(displayName = "CSharp", mimeType = "text/x-csharp", extension = {"cs"})
public final class CSharpGrammar {

    private CSharpGrammar() {
    }
}
