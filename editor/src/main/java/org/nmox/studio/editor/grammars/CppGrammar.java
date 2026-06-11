package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Cpp TextMate grammar (pinned from VS Code 1.95.0,
 * see NOTICE-grammars.md) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "cpp.tmLanguage.json", mimeType = "text/x-cpp")
@MIMEResolver.ExtensionRegistration(displayName = "Cpp", mimeType = "text/x-cpp", extension = {"cpp", "cc", "cxx", "hpp", "hh"})
public final class CppGrammar {

    private CppGrammar() {
    }
}
