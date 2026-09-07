package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the SCSS TextMate grammar (pinned from VS Code 1.95.0,
 * see NOTICE-grammars.md). Indented .sass has its OWN grammar and
 * mime since v2.20.0 ({@link SassGrammar}) — it shared this one,
 * approximately, from v1.4.x to v2.19.x.
 */
@GrammarRegistration(grammar = "scss.tmLanguage.json", mimeType = "text/x-scss")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_ScssGrammar_LOADER", mimeType = "text/x-scss", extension = {"scss"}, position = 2380)
@org.openide.util.NbBundle.Messages("LBL_ScssGrammar_LOADER=SCSS")
public final class ScssGrammar {

    private ScssGrammar() {
    }
}
