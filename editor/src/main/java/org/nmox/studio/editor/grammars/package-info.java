/**
 * The TextMate grammar layer: 85 vendored {@code .tmLanguage.json}
 * files (each sha256-pinned in NOTICE) registered so the platform's
 * TM4E engine highlights the long tail of languages. The pattern per
 * language is tiny — a registration class points at the grammar file
 * and claims a MIME type — but two hard-won rules apply:
 * <ul>
 *   <li>A grammar alone does NOT make a MIME type usable: without a
 *       CSL language/kit the editor falls back to the plain kit and
 *       every MimeLookup feature silently dies (v1.217.0). The CSL
 *       halves live in {@code editor.languages}.</li>
 *   <li>One malformed vendored grammar can break every grammar that
 *       includes it — the gate tests parse the whole family, not just
 *       the newest file (v1.210.0).</li>
 * </ul>
 * Suffix-based MIME resolution is declarative
 * ({@code @MIMEResolver.ExtensionRegistration}); the one content-based
 * resolver here ({@code NgTemplateResolver}) documents why position
 * matters.
 */
package org.nmox.studio.editor.grammars;
