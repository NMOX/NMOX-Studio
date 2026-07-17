package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the GNU Smalltalk TextMate grammar (see NOTICE-grammars.md
 * for provenance) and its file extension.
 */
@GrammarRegistration(grammar = "smalltalk.tmLanguage.json", mimeType = "text/x-smalltalk")
@MIMEResolver.ExtensionRegistration(displayName = "Smalltalk", mimeType = "text/x-smalltalk", extension = {"st"}, position = 2441)
public final class SmalltalkGrammar {

    private SmalltalkGrammar() {
    }
}
