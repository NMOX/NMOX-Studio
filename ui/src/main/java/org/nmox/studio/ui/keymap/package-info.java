/**
 * The sixth keymap profile, <b>VS Code</b>, and the two actions it needs.
 *
 * <p>The profile is data, and it is generated: the Keymaps/VSCode and
 * Editors/…/Keybindings/VSCode regions of this module's layer, and the
 * keybinding files in the {@code keymap} resource folder beside it, are what
 * {@code scripts/generate-vscode-keymap.sh} produces by laying
 * {@code scripts/vscode-keymap/chords.txt} over the default profile of the
 * assembled cluster. The generator and its reasons are
 * {@code VsCodeKeymapProfile} in the application module's tests; start there.
 *
 * <p>The code here is small:
 * <ul>
 *   <li>{@link org.nmox.studio.ui.keymap.UseVsCodeKeymapAction} switches to
 *       the profile from Quick Search, through
 *       {@link org.nmox.studio.ui.keymap.KeymapProfiles}, which does what
 *       the Options dialog's Apply does;</li>
 *   <li>{@link org.nmox.studio.ui.keymap.StartOrContinueDebuggingAction} is
 *       F5 as VS Code means it, one key for Start and Continue, bound by
 *       this profile alone.</li>
 * </ul>
 * The profile's display name lives in the {@code names} bundle, which has no
 * locale siblings because it is a name.
 */
package org.nmox.studio.ui.keymap;
