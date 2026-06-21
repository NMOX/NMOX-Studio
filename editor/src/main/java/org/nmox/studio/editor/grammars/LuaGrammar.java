package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Lua TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "lua.tmLanguage.json", mimeType = "text/x-lua")
@MIMEResolver.ExtensionRegistration(displayName = "Lua", mimeType = "text/x-lua", extension = {"lua"}, position = 2240)
public final class LuaGrammar {

    private LuaGrammar() {
    }
}
