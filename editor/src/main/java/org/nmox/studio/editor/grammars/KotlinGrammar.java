package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Kotlin TextMate grammar (see NOTICE-grammars.md for
 * provenance) and its file extensions.
 */
@GrammarRegistration(grammar = "kotlin.tmLanguage.json", mimeType = "text/x-kotlin")
@MIMEResolver.ExtensionRegistration(displayName = "Kotlin", mimeType = "text/x-kotlin", extension = {"kt", "kts"})
public final class KotlinGrammar {

    private KotlinGrammar() {
    }
}
