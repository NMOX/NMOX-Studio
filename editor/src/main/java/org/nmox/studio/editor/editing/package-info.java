/**
 * The editing gestures a VS Code user's hands reach for and the platform
 * does not have: Toggle Block Comment and Expand Line Selection.
 *
 * <p><b>What lives here.</b> Each gesture is a pure rule and a thin action
 * over it. {@link org.nmox.studio.editor.editing.BlockComments} holds the
 * block delimiters of every language and the wrap/unwrap rule, with its
 * refusals; {@link org.nmox.studio.editor.editing.LineSelection} is the
 * whole-line selection rule. The actions
 * ({@link org.nmox.studio.editor.editing.ToggleBlockCommentAction},
 * {@link org.nmox.studio.editor.editing.ExpandLineSelectionAction}) read
 * the editor, apply the answer and say a refusal on the status line.
 * {@link org.nmox.studio.editor.editing.TypedEcho} keeps an Option chord
 * on macOS from also typing its character.
 *
 * <p><b>Which RCP mechanism.</b> Editor-kit actions registered at the ROOT
 * of {@code Editors/Actions} ({@code @EditorActionRegistration} with no
 * mime type), which every kit inherits, bound by keybinding files under
 * the root {@code Editors/Keybindings/<profile>/Defaults} in the layer
 * (one per chord, so each rides exactly the keymap profiles that leave its
 * chord free).
 *
 * <p><b>Reading order.</b> BlockComments and its test (the rules and the
 * refusals) → ToggleBlockCommentAction (bounded read, one undo step) →
 * LineSelection → TypedEcho (why an Alt chord needs it) → the
 * {@code vscode-*-keybindings.xml} files and their layer registrations.
 * Strings are in this package's hand-written {@code Bundle.properties},
 * read with {@code NbBundle.getMessage}; do not add {@code @Messages} here.
 */
package org.nmox.studio.editor.editing;
