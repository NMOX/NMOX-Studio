package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Dart TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "dart.tmLanguage.json", mimeType = "text/x-dart")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_DartGrammar_LOADER", mimeType = "text/x-dart", extension = {"dart"}, position = 2070)
@org.openide.util.NbBundle.Messages("LBL_DartGrammar_LOADER=Dart")
public final class DartGrammar {

    private DartGrammar() {
    }
}
