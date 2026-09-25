/**
 * The editor's accessibility touches (3.2.0).
 *
 * <p>What lives here: {@code EditorAccessibleNames}, which gives each editor
 * pane its file's name so a screen reader announces "Editor for app.js"
 * where the platform said "Editor for null".
 *
 * <p>The RCP mechanism: an {@code @OnStart} runnable adds one listener to
 * the editor module's {@code EditorRegistry}, the registry every editor
 * pane joins when it first takes focus. Read {@code EditorAccessibleNames}
 * first; its test builds a real pane over a real DataObject.
 */
package org.nmox.studio.editor.a11y;
