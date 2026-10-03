/**
 * Quick Search into the file being edited: VS Code's Go to Symbol in
 * Editor.
 *
 * <p><b>What lives here.</b>
 * {@link org.nmox.studio.editor.symbols.search.FileSymbols} is the rule
 * (which outline items answer a query, in what order, how many);
 * {@link org.nmox.studio.editor.symbols.search.FileSymbolSearchProvider}
 * is what Quick Search calls: it reads the active editor's text, bounded
 * and off the event thread, extracts the outline the Navigator shows and
 * hands the matching rows back;
 * {@link org.nmox.studio.editor.symbols.search.GoToSymbolInFileAction} is
 * the Navigate-menu row that opens Quick Search with {@code @} typed.
 *
 * <p><b>Which RCP mechanism.</b> The Quick Search SPI: a
 * {@code SearchProvider} instance in a category folder under the layer's
 * root {@code QuickSearch} folder, with a {@code position}, a localizing
 * bundle for the category's name, and a {@code command}. The command is
 * the platform's way to search one category alone, and it is a WORD
 * followed by a space ({@code CommandEvaluator}'s pattern is
 * {@code (\w+)(\s+)(.+)}), so {@code @} cannot be one: {@code m name}
 * searches only this category, and {@code @name} is understood by the
 * provider itself while the other categories see the same text.
 *
 * <p><b>Reading order.</b> FileSymbols and its test → the provider →
 * {@code layer.xml}'s {@code QuickSearch/FileSymbols} folder → the action.
 * The project-wide twin is one package up
 * ({@link org.nmox.studio.editor.symbols.NmoxSymbolProvider}). Strings are
 * in this package's hand-written {@code Bundle.properties}, which the
 * layer also names for the category; do not add {@code @Messages} here.
 */
package org.nmox.studio.editor.symbols.search;
