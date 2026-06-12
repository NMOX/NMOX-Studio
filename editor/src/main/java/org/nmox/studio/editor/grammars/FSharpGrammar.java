package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the FSharp TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "fsharp.tmLanguage.json", mimeType = "text/x-fsharp")
@MIMEResolver.ExtensionRegistration(displayName = "FSharp", mimeType = "text/x-fsharp", extension = {"fs", "fsx"})
public final class FSharpGrammar {

    private FSharpGrammar() {
    }
}
