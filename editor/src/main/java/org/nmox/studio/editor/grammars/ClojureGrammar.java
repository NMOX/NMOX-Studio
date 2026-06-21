package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Clojure TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "clojure.tmLanguage.json", mimeType = "text/x-clojure")
@MIMEResolver.ExtensionRegistration(displayName = "Clojure", mimeType = "text/x-clojure", extension = {"clj", "cljs", "cljc", "edn"}, position = 2030)
public final class ClojureGrammar {

    private ClojureGrammar() {
    }
}
