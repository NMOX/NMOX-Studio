package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Clojure TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "clojure.tmLanguage.json", mimeType = "text/x-clojure")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_ClojureGrammar_LOADER", mimeType = "text/x-clojure", extension = {"clj", "cljs", "cljc", "edn"}, position = 2030)
@org.openide.util.NbBundle.Messages("LBL_ClojureGrammar_LOADER=Clojure")
public final class ClojureGrammar {

    private ClojureGrammar() {
    }
}
