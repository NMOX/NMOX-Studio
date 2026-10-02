package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the R TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "r.tmLanguage.json", mimeType = "text/x-r")
// "R" as well as "r": the platform matches an extension by case on macOS and
// Linux, and R's own convention is the capital (3.5.5: the R learning space's
// hello.R opened as plain text).
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_RGrammar_LOADER", mimeType = "text/x-r", extension = {"r", "R"}, position = 2340)
@org.openide.util.NbBundle.Messages("LBL_RGrammar_LOADER=R")
public final class RGrammar {

    private RGrammar() {
    }
}
