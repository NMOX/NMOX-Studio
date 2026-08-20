package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * The indented Sass dialect as its own language (v2.20.0, closing the
 * oldest Known-Issues line: ".sass shares the SCSS grammar —
 * approximate highlighting"). The grammar is the canonical indented
 * one (TheRealSyler/vscode-sass-indented, MIT, sha256-pinned in
 * NOTICE-grammars.md) — whitespace-significant, no braces, no
 * semicolons, which is exactly why the SCSS grammar could only ever
 * approximate it.
 *
 * <p>The css-family surfaces that scan LINES ride along (color
 * swatches, the ⌘-click picker, design-token var( completion,
 * property completion, spellcheck, the // comment toggle, Compile to
 * CSS). Deliberately OUT, each for the same reason — their output or
 * premise is braced CSS: Emmet expansion (emits {@code prop: value;}),
 * Prettier (no indented-sass support), the Navigator outline (matches
 * {@code selector '{'}), and stylelint-lsp (needs the project to
 * configure a customSyntax for indented files; a server the config
 * cannot parse is noise, not lint).
 */
@GrammarRegistration(grammar = "sass.tmLanguage.json", mimeType = "text/x-sass")
@MIMEResolver.ExtensionRegistration(displayName = "Sass (indented)", mimeType = "text/x-sass", extension = {"sass"}, position = 2379)
public final class SassGrammar {

    private SassGrammar() {
    }
}
