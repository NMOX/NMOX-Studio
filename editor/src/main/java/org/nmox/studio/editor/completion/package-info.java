/**
 * Hand-rolled code completion for the web surfaces the LSP servers do
 * not cover: HTML tags/attributes, CSS properties, JS/TS identifiers
 * and snippets, Angular template @-blocks and directives, classic
 * libraries (jQuery and friends, offered only when the project's own
 * deps or script tags carry them).
 *
 * <p>The RCP mechanism: each provider implements
 * {@code CompletionProvider}, is registered in the layer under its
 * MIME type via {@code @MimeRegistration}, and builds
 * {@code CompletionItem}s the editor infrastructure paints. Follow one
 * keystroke through {@code JavaScriptCompletionProvider} and the rest
 * of the package reads itself — providers differ only in how they
 * harvest candidates (document scan, static tables, manifest checks).
 */
package org.nmox.studio.editor.completion;
