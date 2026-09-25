/**
 * Line blame — who last changed the caret's line, as a note on the status
 * line (3.2.0).
 *
 * <p><b>What lives here.</b>
 * {@link org.nmox.studio.editor.blame.BlamePorcelain} reads
 * {@code git blame --porcelain} into one commit per line (pure);
 * {@link org.nmox.studio.editor.blame.RelativeTime} says "3 days ago" in the
 * reader's language with real plural forms (pure);
 * {@link org.nmox.studio.editor.blame.LineBlame} runs the one bounded spawn
 * per file version on its own lane, caches the answer and lets only the
 * newest request answer; {@link org.nmox.studio.editor.blame.BlameStatusLine}
 * is the label, and {@link org.nmox.studio.editor.blame.BlamePrefs} +
 * {@link org.nmox.studio.editor.blame.ToggleLineBlameAction} are the
 * preference and its View-menu checkbox.
 *
 * <p><b>Which RCP mechanism.</b> A {@code StatusLineElementProvider}
 * registered with {@code @ServiceProvider} (the git chip's and the problem
 * count's shape), following the focused editor through
 * {@code EditorRegistry}; the click reaches the git module's own Annotate
 * through {@code GitStatusLine.showAnnotations}.
 *
 * <p><b>Reading order.</b> BlamePorcelain → RelativeTime → LineBlame (the
 * cache key, the generation rule, what never spawns) → BlameStatusLine
 * (retarget, debounce, the unsaved rule).
 */
package org.nmox.studio.editor.blame;
