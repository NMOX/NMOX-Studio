package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the .http/.rest request-file TextMate grammar (see
 * NOTICE-grammars.md for provenance) — the REST Client dialect API
 * Studio imports, so the files highlight in the editor too.
 * Grammar-only citizenship by design: no CSL registration for a
 * grammar-only mime (the v1.110.0 lexer law's sibling rule).
 */
@GrammarRegistration(grammar = "http.tmLanguage.json", mimeType = "text/x-http-request")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_HttpFileGrammar_LOADER", mimeType = "text/x-http-request", extension = {"http", "rest"}, position = 2457)
@org.openide.util.NbBundle.Messages("LBL_HttpFileGrammar_LOADER=HTTP Request File")
public final class HttpFileGrammar {

    private HttpFileGrammar() {
    }
}
