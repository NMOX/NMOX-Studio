package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Erlang TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "erlang.tmLanguage.json", mimeType = "text/x-erlang")
@MIMEResolver.ExtensionRegistration(displayName = "Erlang", mimeType = "text/x-erlang", extension = {"erl", "hrl"})
public final class ErlangGrammar {

    private ErlangGrammar() {
    }
}
