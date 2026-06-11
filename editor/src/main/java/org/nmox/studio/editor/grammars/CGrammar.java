package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the C TextMate grammar (pinned from VS Code 1.95.0,
 * see NOTICE-grammars.md) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "c.tmLanguage.json", mimeType = "text/x-c")
@MIMEResolver.ExtensionRegistration(displayName = "C", mimeType = "text/x-c", extension = {"c", "h"})
public final class CGrammar {

    private CGrammar() {
    }
}
