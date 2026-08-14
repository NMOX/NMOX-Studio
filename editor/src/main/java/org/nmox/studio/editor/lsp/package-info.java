/**
 * Language Server Protocol wiring: providers that tell the platform's
 * LSP client how to launch tsserver, ngserver, rust-analyzer, gopls,
 * deno lsp and ~50 others, found on PATH via {@code ToolLocator} or
 * project-locally when the workspace is trusted.
 *
 * <p>Three platform facts this package encodes (each learned the hard
 * way, each with a gate):
 * <ul>
 *   <li>The client sends the RAW MIME type as LSP {@code languageId}
 *       unless a {@code LanguageIdResolver} rides the server
 *       description's Lookup — {@code LspLanguageIds} maps them all
 *       (v1.218.0).</li>
 *   <li>A project-local server binary is an EXECUTION of repo code:
 *       trusted workspaces only (v1.102.0).</li>
 *   <li>Some servers need init-time options injected into the client's
 *       first frame ({@code deno lsp} publishes nothing without
 *       {@code enable:true}) — see the injector seam (v1.350.0).</li>
 * </ul>
 */
package org.nmox.studio.editor.lsp;
