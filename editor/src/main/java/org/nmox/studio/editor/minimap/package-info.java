/**
 * The minimap — a document silhouette beside every editor's scrollbar.
 *
 * <p><b>What lives here.</b> {@link org.nmox.studio.editor.minimap.MinimapModel}
 * is the pure geometry (line shapes, row height, y↔line, bar spans);
 * {@link org.nmox.studio.editor.minimap.MinimapSideBar} is the Swing strip
 * that paints it and scrolls the editor on click/drag;
 * {@link org.nmox.studio.editor.minimap.MinimapSideBarFactory} is what the
 * platform instantiates per editor pane;
 * {@link org.nmox.studio.editor.minimap.MinimapPrefs} +
 * {@link org.nmox.studio.editor.minimap.ToggleMinimapAction} are the one
 * preference and its View-menu checkbox.
 *
 * <p><b>Which RCP mechanism.</b> The editor side-bar SPI: an instance under
 * the root {@code Editors/SideBar} layer folder with {@code location=East},
 * {@code scrollable=false}, and an int {@code position} (the platform's
 * error stripe registers the same way at 7000 — decompiled from
 * editor-errorstripe's layer). A root registration reaches EVERY editor,
 * platform-owned mimes included.
 *
 * <p><b>Reading order.</b> MinimapModel (the rules, all unit-tested) →
 * MinimapSideBar (listeners symmetric in addNotify/removeNotify, edits
 * coalesced by one timer, reads under Document.render, bounded) →
 * MinimapSideBarFactory + layer.xml (the registration) → the toggle.
 */
package org.nmox.studio.editor.minimap;
