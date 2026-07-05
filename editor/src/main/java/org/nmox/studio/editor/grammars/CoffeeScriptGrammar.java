package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the CoffeeScript TextMate grammar (see NOTICE-grammars.md
 * for the pinned upstream) and the {@code .coffee} / {@code .litcoffee}
 * / {@code .cson} extensions. The platform's textmate-lexer module does
 * the tokenizing and theme mapping. The grammar's {@code source.js}
 * include resolves through {@link EmbeddedScopeGrammars}; registering
 * {@code source.coffee} also un-prunes the coffee fences the pug, scss,
 * vue and svelte grammars already reference.
 *
 * <p>Grammar-only mime: no CSL {@code @LanguageRegistration} — comment
 * toggling comes from {@code LanguageComments}, keyword completion from
 * {@code PolyglotCompletionProvider}, and the Navigator outline from
 * {@code OutlineModel}, all keyed on {@code text/coffeescript}.
 */
@GrammarRegistration(grammar = "coffeescript.tmLanguage.json", mimeType = "text/coffeescript")
@MIMEResolver.ExtensionRegistration(displayName = "CoffeeScript", mimeType = "text/coffeescript", extension = {"coffee", "litcoffee", "cson"}, position = 2510)
public final class CoffeeScriptGrammar {

    private CoffeeScriptGrammar() {
    }
}
