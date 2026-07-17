package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Haxe TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extension. The .hxml build files keep their
 * own upstream grammar and are deliberately unclaimed here.
 */
@GrammarRegistration(grammar = "haxe.tmLanguage.json", mimeType = "text/x-haxe")
@MIMEResolver.ExtensionRegistration(displayName = "Haxe", mimeType = "text/x-haxe", extension = {"hx"}, position = 2449)
public final class HaxeGrammar {

    private HaxeGrammar() {
    }
}
