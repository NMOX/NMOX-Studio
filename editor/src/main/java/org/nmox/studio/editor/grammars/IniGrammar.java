package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Ini TextMate grammar (see NOTICE-grammars.md for the
 * pinned upstream) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "ini.tmLanguage.json", mimeType = "text/x-ini")
@MIMEResolver.ExtensionRegistration(displayName = "INI / EditorConfig", mimeType = "text/x-ini", extension = {"ini", "cfg", "editorconfig", "npmrc", "gitconfig"})
public final class IniGrammar {

    private IniGrammar() {
    }
}
