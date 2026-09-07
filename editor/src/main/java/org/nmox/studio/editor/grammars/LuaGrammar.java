package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Lua TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "lua.tmLanguage.json", mimeType = "text/x-lua")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_LuaGrammar_LOADER", mimeType = "text/x-lua", extension = {"lua"}, position = 2240)
@org.openide.util.NbBundle.Messages("LBL_LuaGrammar_LOADER=Lua")
public final class LuaGrammar {

    private LuaGrammar() {
    }
}
