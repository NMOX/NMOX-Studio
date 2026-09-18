/**
 * Translations in a WEB PROJECT — the user's catalogs, not the IDE's own
 * bundles (v2.177.0). What lives here:
 *
 * <ul>
 * <li>{@link org.nmox.studio.editor.i18n.I18nCatalogs} — detection (a
 * fixed rule order over the file tree) and the parsers (nested and flat
 * JSON, gettext {@code .po}, XLIFF 1.2 / 2.0 through a hardened DOM),
 * into one immutable {@code Catalogs} record that also says which rule
 * chose the source locale and whether the census was complete.</li>
 * <li>{@link org.nmox.studio.editor.i18n.I18nUsage} — where the source
 * files reference keys, every framework's lookup shape, dynamic lookups
 * recorded as dynamic.</li>
 * <li>{@link org.nmox.studio.editor.i18n.I18nCheck} — missing, identical
 * to source, placeholder mismatch (the one that is a bug), and the
 * twice-guarded unused report.</li>
 * <li>{@link org.nmox.studio.editor.i18n.IcuArgs} — the argument set of a
 * message across dialects.</li>
 * <li>{@link org.nmox.studio.editor.i18n.CheckTranslationsAction} — the
 * Tools-menu door: named lane, {@code DiagnosticsBus} under {@code i18n},
 * one status sentence.</li>
 * <li>{@link org.nmox.studio.editor.i18n.I18nKeys} with the completion
 * provider and the ⌘-click hyperlink — the editor side of the same
 * catalogs.</li>
 * </ul>
 *
 * <p>RCP mechanisms: an {@code @ActionReference} into {@code Menu/Tools},
 * {@code @MimeRegistration} rows for completion and hyperlinks (every one
 * positioned), a {@code RequestProcessor} for the disk work. The cores are
 * pure over {@code Path} and {@code String}: no Swing, no bundle, no spawn
 * — a catalog is read, never executed. Reading order: IcuArgs, I18nCatalogs,
 * I18nUsage, I18nCheck, then the action.
 */
package org.nmox.studio.editor.i18n;
