/**
 * Emmet expansion (⌥⌘E): {@code div.card>ul>li*3} becomes markup,
 * {@code m10} becomes {@code margin: 10px;}. Two pure engines —
 * {@link org.nmox.studio.editor.emmet.Emmet} for markup,
 * {@link org.nmox.studio.editor.emmet.CssEmmet} for stylesheets — and
 * one editor action that dispatches by MIME.
 *
 * <p>Design stance worth copying: the grammar is a WRITTEN-DOWN subset
 * (the class javadoc lists what is deliberately out), expansion is
 * all-or-nothing (an unparseable abbreviation leaves the text
 * untouched rather than half-expanding), and CSS matching is
 * exact-match only because a wrong fuzzy guess MUTATES the stylesheet.
 * The action also folds auto-pair closers at the caret (v1.332.0) —
 * the kind of editor-reality detail unit tests miss and walks catch.
 */
package org.nmox.studio.editor.emmet;
