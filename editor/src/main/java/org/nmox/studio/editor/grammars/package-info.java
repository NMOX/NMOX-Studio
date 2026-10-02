/**
 * The TextMate grammar layer: vendored {@code .tmLanguage.json}
 * files (each sha256-pinned in NOTICE) registered so the platform's
 * TM4E engine highlights the long tail of languages. The pattern per
 * language is tiny — a registration class points at the grammar file
 * and claims a MIME type — but four hard-won rules apply:
 * <ul>
 *   <li>A grammar alone does NOT make a MIME type usable: without a
 *       CSL language/kit the editor falls back to the plain kit and
 *       every MimeLookup feature silently dies (v1.217.0). The CSL
 *       halves live in {@code editor.languages}.</li>
 *   <li>One malformed vendored grammar can break every grammar that
 *       includes it — the gate tests parse the whole family, not just
 *       the newest file (v1.210.0).</li>
 *   <li>The engine is TM4E over joni, not the Oniguruma the grammars
 *       were written for: joni refuses a look-behind of variable
 *       length, and the editor throws where that rule is needed
 *       (3.5.4, an Elixir file opened empty). After adding or bumping
 *       a grammar run {@code scripts/rewrite-grammar-lookbehinds.py},
 *       {@code scripts/name-grammar-dependencies.py} and
 *       {@code scripts/stub-dangling-grammar-includes.py}; the gates
 *       beside this package name what each one has to do.</li>
 *   <li>TM4E loads the grammars a grammar includes by a walk that
 *       misses includes inside captures and behind look-alike rules;
 *       {@code GrammarDependenciesLoadGateTest} prints the fix.</li>
 * </ul>
 * Suffix-based MIME resolution is declarative
 * ({@code @MIMEResolver.ExtensionRegistration}); the one content-based
 * resolver here ({@code NgTemplateResolver}) documents why position
 * matters.
 */
package org.nmox.studio.editor.grammars;
