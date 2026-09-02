/**
 * Sticky scroll — the declarations you scrolled out of sight, pinned above
 * the text.
 *
 * <p><b>What lives here.</b>
 * {@link org.nmox.studio.editor.sticky.StickyScrollSideBar} paints the
 * enclosing chain of the first visible line (up to three rows of the
 * source's own text, editor font, aligned past the gutter; click to jump);
 * {@link org.nmox.studio.editor.sticky.StickyScrollSideBarFactory} is what
 * the platform instantiates per editor; {@link org.nmox.studio.editor.sticky.StickyPrefs}
 * + {@link org.nmox.studio.editor.sticky.ToggleStickyScrollAction} are the
 * preference and its View-menu checkbox. The pure core —
 * {@link org.nmox.studio.editor.outline.StickyScope} — lives beside the
 * outline it derives ranges from (it reuses the outline's code-blanking).
 *
 * <p><b>Which RCP mechanism.</b> The editor side-bar SPI, North location:
 * a root {@code Editors/SideBar} instance with {@code location=North},
 * {@code scrollable=false}, and an int position after the editor toolbar
 * — the same shape the v2.59.0 minimap uses on the East.
 *
 * <p><b>Reading order.</b> StickyScope (ranges per family, the chain) →
 * StickyScrollSideBar (reindex on edits, rechain on scroll, collapse to
 * zero height when silent) → the factory + layer.xml → the toggle.
 */
package org.nmox.studio.editor.sticky;
