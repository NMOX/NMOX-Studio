/**
 * The npm half of Quick Search (⌘I). {@code NpmScriptSearchProvider}
 * lists the aimed project's package.json scripts — type "dev", Enter,
 * and {@code npm run dev} starts — and is registered in this module's
 * hand {@code layer.xml} under {@code QuickSearch/NpmScripts}, because a
 * category folder's position and display name are layer attributes no
 * annotation can write.
 *
 * <p>The provider only lists; running belongs to
 * {@code org.nmox.studio.tools.npm.NpmService}, whose one lane carries
 * the Workspace Trust gate, the project's own package manager, the
 * toolbar ■ and the serving announcement. Read that class next.
 *
 * <p>The category's bundle lives here, in its own package, so it can
 * never collide with an {@code @NbBundle.Messages}-generated bundle of
 * the npm package itself (the v1.79.0 lesson).
 */
package org.nmox.studio.tools.npm.search;
