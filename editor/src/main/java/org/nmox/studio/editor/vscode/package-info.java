/**
 * Tools ▸ Import VS Code Settings…: a switcher's own VS Code preferences
 * brought across once, when they ask.
 *
 * <p><b>What lives here.</b>
 * {@link org.nmox.studio.editor.vscode.VsCodeUserSettings} is the pure
 * half: where a user settings file is on each system, which settings it
 * recognises and what each becomes here (exactly, nearly, or not at all,
 * with the reason), and the apply loop over a seam of preference homes.
 * Everything it does not recognise is counted and never named: the file
 * is the user's and holds tokens and paths.
 * {@code ProductHomes} writes each change through the setter the
 * product's own menu or Options panel uses for that preference;
 * {@code ImportVsCodeSettingsAction} finds and reads the file off the
 * event thread, and {@code ImportVsCodeSettingsSheet} shows the rows with
 * a checkbox each and writes only on Apply.
 *
 * <p><b>Which RCP mechanism.</b> A plain {@code @ActionRegistration} in
 * the Tools menu (and a VS Code command title in Quick Search, in the ui
 * module); the editor's all-languages preferences through
 * {@code MimeLookup}; the platform autosave module reached by name.
 *
 * <p><b>Reading order.</b> VsCodeUserSettings and its test (the mappings
 * and the no-leak rule) → ImportVsCodeSettingsAction (prepare/present)
 * → ImportVsCodeSettingsSheet → ProductHomes (where each value lands).
 */
package org.nmox.studio.editor.vscode;
