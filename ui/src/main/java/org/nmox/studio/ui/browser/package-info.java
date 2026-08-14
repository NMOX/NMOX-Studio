/**
 * The in-app Browser (⌥⌘4): a JavaFX {@code WebView} hosted inside a
 * Swing {@code TopComponent} via {@code JFXPanel}. Two threads matter
 * everywhere here — the Swing EDT and the FX Application Thread — and
 * every crossing is explicit ({@code Platform.runLater} one way,
 * {@code SwingUtilities.invokeLater} back).
 *
 * <p>Since the v1.357–v1.360 arc the Browser is <b>source-aware</b>:
 * Pick element captures a click in the live page and walks it back to
 * the HTML file and LINE that produced it ({@code PageSourceResolver}
 * maps URL→file only through vouched channels; {@code
 * HtmlSourceLocator} finds the element line with comments/scripts
 * neutralized), and Edit Style… previews inline then lands the
 * declaration in the source stylesheet the page's own cascade chose.
 * Remote pages and generated elements refuse honestly rather than
 * guess. DevTools lives in the {@code devtools} subpackage — the
 * WebView ships no inspector, so those panes are built over injected
 * JavaScript with a deliberately narrow bridge.
 */
package org.nmox.studio.ui.browser;
