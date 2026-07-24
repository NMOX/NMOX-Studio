package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Move (Sui/Aptos) TextMate grammar (see NOTICE-grammars.md
 * for provenance) and its file extension.
 */
@GrammarRegistration(grammar = "move.tmLanguage.json", mimeType = "text/x-move")
@MIMEResolver.ExtensionRegistration(displayName = "Move", mimeType = "text/x-move", extension = {"move"}, position = 2452)
public final class MoveGrammar {

    private MoveGrammar() {
    }
}
