package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Scala TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "scala.tmLanguage.json", mimeType = "text/x-scala")
@MIMEResolver.ExtensionRegistration(displayName = "Scala", mimeType = "text/x-scala", extension = {"scala", "sc", "sbt"}, position = 2370)
public final class ScalaGrammar {

    private ScalaGrammar() {
    }
}
