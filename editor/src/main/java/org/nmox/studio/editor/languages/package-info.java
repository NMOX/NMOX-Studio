/**
 * The CSL (Common Scripting Language) side of language support: for
 * each TextMate-highlighted MIME type, a small {@code DefaultLanguageConfig}
 * subclass that gives the editor its comment toggle, brace character
 * pairs, and keyword completion. These classes look almost identical
 * on purpose — one per language, ~40 lines.
 *
 * <p><b>The one law with teeth:</b> a config's
 * {@code getLexerLanguage} must resolve through {@code Lexers.find},
 * never bare {@code Language.find} on its own MIME — the bare call can
 * re-enter this very config and overflow the stack (v1.110.0, caught
 * live). {@code LexerIdiomGateTest} fails the build on any bare call,
 * which is why every file here spells it the same way.
 */
package org.nmox.studio.editor.languages;
