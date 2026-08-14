/**
 * The shared toolbox — small, dependency-free classes that carry the
 * product's <em>house laws</em> so each module doesn't reinvent (and
 * drift from) them. Nothing here touches the NetBeans window system;
 * these are plain Java and Swing helpers that every module may use.
 *
 * <p>Start with the four you will meet everywhere:
 * <ul>
 *   <li>{@link org.nmox.studio.core.util.AtomicFiles} — every workspace
 *       file in the product is written as temp-sibling + atomic move,
 *       so a crash mid-write can never leave a half-file.</li>
 *   <li>{@link org.nmox.studio.core.util.SelfWriteTracker} — after a
 *       save, remembers the file's mtime+size so a change notification
 *       can tell "that was me" from "someone else edited this".</li>
 *   <li>{@link org.nmox.studio.core.util.FilePulse} — a 1.5&nbsp;s
 *       stat-poll over one file; how the studios notice hand edits and
 *       git checkouts while their tab is open.</li>
 *   <li>{@link org.nmox.studio.core.util.PlainTables} — disables
 *       Swing's surprise HTML rendering in labels/tables. Any string
 *       that came from a file or the network must go through this, or
 *       a {@code <html><img src>} title makes the IDE fetch a URL at
 *       paint time (a real bug class, fixed product-wide in v1.306).</li>
 * </ul>
 *
 * <p>Also here: {@link org.nmox.studio.core.util.Popups} (context menus
 * that act on the CLICKED item, not the selected one),
 * {@link org.nmox.studio.core.util.WrapLayout} (a FlowLayout that tells
 * the truth about its wrapped height), and
 * {@link org.nmox.studio.core.util.IdeWorkspaceFiles} (the naming rule
 * that identifies the product's own {@code .nmox*.json} files).
 */
package org.nmox.studio.core.util;
