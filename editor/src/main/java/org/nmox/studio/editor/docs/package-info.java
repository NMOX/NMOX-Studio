/**
 * Tools for a project's own documentation (3.2.0).
 *
 * <p>What lives here: {@code MarkdownLinks}, the pure checker that reads a
 * project's Markdown and says which relative links and images go nowhere
 * and which {@code #heading} fragments name no heading (GitHub's anchor
 * rule), and {@code CheckMarkdownLinksAction}, Tools ▸ Check Markdown
 * Links…, which walks the aimed project and publishes the findings.
 *
 * <p>The RCP mechanism: an annotation-registered action in the Tools menu
 * that publishes to the rack's {@code DiagnosticsBus}, the same transport
 * that puts squiggles in the editor and rows in Action Items for every
 * other checker. Read {@code MarkdownLinks} first; its tests are the rules.
 */
package org.nmox.studio.editor.docs;
