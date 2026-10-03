/**
 * A repository's own snippets: the {@code .vscode/*.code-snippets}
 * files a team commits, offered in the completion list and inserted
 * with their tab stops. The fourth of a repository's {@code .vscode}
 * files the product reads as data, after {@code tasks.json},
 * {@code launch.json} and {@code settings.json}.
 *
 * <p><b>What lives here</b>, in reading order:
 * <ol>
 * <li>{@link org.nmox.studio.editor.snippets.SnippetBody} — VS Code's
 *     snippet grammar, parsed into a small tree (text, tab stops,
 *     variables, transforms). Pure, bounded, and it refuses by name.</li>
 * <li>{@link org.nmox.studio.editor.snippets.SnippetVariables} and
 *     {@code SnippetTransforms} — what {@code $TM_FILENAME} is, and
 *     {@code /regex/format/flags} applied against a clock, because the
 *     regular expression came with a clone.</li>
 * <li>{@link org.nmox.studio.editor.snippets.SnippetTemplates} — the
 *     tree translated into the platform's code-template language. Read
 *     its javadoc for the target grammar and for what VS Code has that
 *     the engine has not.</li>
 * <li>{@link org.nmox.studio.editor.snippets.VsCodeSnippets} — a
 *     snippet file's text into snippets, leaving out by name what cannot
 *     be honoured.</li>
 * <li>{@link org.nmox.studio.editor.snippets.ProjectSnippets} — the
 *     disk: which folders, how many files, how large, off the event
 *     thread, cached per file version.</li>
 * <li>{@link org.nmox.studio.editor.snippets.ProjectSnippetCompletionProvider},
 *     its item, {@code SnippetInsertion} and
 *     {@link org.nmox.studio.editor.snippets.SnippetTemplateProcessor} —
 *     the thin editor half.</li>
 * </ol>
 *
 * <p><b>Which RCP mechanisms</b>: a {@code CompletionProvider} and a
 * {@code CodeTemplateProcessorFactory}, both registered for the root
 * mime type so every editor gets them; the code-template engine
 * ({@code CodeTemplateManager.createTemporary(…).insert(…)}) for tab
 * stops, mirrors and undo.
 *
 * <p>The files are read, never run: no Workspace Trust is asked.
 */
package org.nmox.studio.editor.snippets;
