package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the V (vlang) TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "vlang.tmLanguage.json", mimeType = "text/x-vlang")
@MIMEResolver.ExtensionRegistration(displayName = "V", mimeType = "text/x-vlang", extension = {"v", "vsh", "vv"}, position = 2438)
public final class VGrammar {

    private VGrammar() {
    }
}
