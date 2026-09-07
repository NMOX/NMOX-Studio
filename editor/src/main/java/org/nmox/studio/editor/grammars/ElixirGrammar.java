package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Elixir TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "elixir.tmLanguage.json", mimeType = "text/x-elixir")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_ElixirGrammar_LOADER", mimeType = "text/x-elixir", extension = {"ex", "exs"}, position = 2080)
@org.openide.util.NbBundle.Messages("LBL_ElixirGrammar_LOADER=Elixir")
public final class ElixirGrammar {

    private ElixirGrammar() {
    }
}
