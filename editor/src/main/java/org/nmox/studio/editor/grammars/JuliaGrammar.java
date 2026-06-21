package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Julia TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "julia.tmLanguage.json", mimeType = "text/x-julia")
@MIMEResolver.ExtensionRegistration(displayName = "Julia", mimeType = "text/x-julia", extension = {"jl"}, position = 2190)
public final class JuliaGrammar {

    private JuliaGrammar() {
    }
}
