package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Nginx TextMate grammar (see NOTICE-grammars.md for the
 * pinned upstream) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "nginx.tmLanguage.json", mimeType = "text/x-nginx-conf")
@MIMEResolver.ExtensionRegistration(displayName = "nginx", mimeType = "text/x-nginx-conf", extension = {"nginx"})
public final class NginxGrammar {

    private NginxGrammar() {
    }
}
