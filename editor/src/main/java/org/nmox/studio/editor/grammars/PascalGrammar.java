package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Pascal TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "pascal.tmLanguage.json", mimeType = "text/x-pascal")
@MIMEResolver.ExtensionRegistration(displayName = "Pascal", mimeType = "text/x-pascal", extension = {"pas", "pp", "lpr"}, position = 2446)
public final class PascalGrammar {

    private PascalGrammar() {
    }
}
