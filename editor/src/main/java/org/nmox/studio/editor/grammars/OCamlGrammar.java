package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the OCaml TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "ocaml.tmLanguage.json", mimeType = "text/x-ocaml")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_OCamlGrammar_LOADER", mimeType = "text/x-ocaml", extension = {"ml", "mli"}, position = 2270)
@org.openide.util.NbBundle.Messages("LBL_OCamlGrammar_LOADER=OCaml")
public final class OCamlGrammar {

    private OCamlGrammar() {
    }
}
