package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Elixir TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "elixir.tmLanguage.json", mimeType = "text/x-elixir")
@MIMEResolver.ExtensionRegistration(displayName = "Elixir", mimeType = "text/x-elixir", extension = {"ex", "exs"}, position = 2080)
public final class ElixirGrammar {

    private ElixirGrammar() {
    }
}
