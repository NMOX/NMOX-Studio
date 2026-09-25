/**
 * Merge conflicts, resolved where they are written (3.2.0).
 *
 * <p><b>What lives here.</b>
 * {@link org.nmox.studio.editor.conflicts.MergeConflicts} is the pure core:
 * it finds the blocks git writes into a file it could not merge
 * ({@code <<<<<<<}, an optional diff3 {@code |||||||}, {@code =======},
 * {@code >>>>>>>}, each at a line start and exactly seven characters long),
 * refuses every shape it would have to guess at, and says what each of VS
 * Code's three choices puts in a block's place.
 * {@link org.nmox.studio.editor.conflicts.ConflictWatcher} attaches to every
 * document an editor opens, tints each block's two sides and puts a warning
 * on its {@code <<<<<<<} line; {@link org.nmox.studio.editor.conflicts.ConflictFix}
 * is one of the warning's three fixes — Accept Current Change, Accept
 * Incoming Change, Accept Both Changes — each ONE undoable edit that
 * refuses, out loud, when the block changed after it was offered.
 *
 * <p><b>Which RCP mechanism.</b> A {@code HighlightsLayerFactory}
 * registered for every mime (a root {@code MimeRegistration}, as the ghost
 * text does), which is also the per-document hook; the platform's
 * {@code HintsController} for the warning and its fixes (the channel the
 * rack's findings and the commit-summary warning use); highlight colorings
 * under {@code Editors/FontsColors/<profile>/Defaults} so a theme can
 * change the tints. With {@code nmox -w} as git's mergetool
 * ({@code mergetool.nmox.cmd 'nmox -w "$MERGED"'}), {@code git mergetool}
 * opens each conflicted file here and carries on when its tab closes.
 *
 * <p><b>Reading order.</b> MergeConflicts (the shapes and the three
 * choices) → ConflictFix (the one guarded, atomic edit) → ConflictWatcher
 * (the scan lane, the tints, the hints, the registration).
 */
package org.nmox.studio.editor.conflicts;
