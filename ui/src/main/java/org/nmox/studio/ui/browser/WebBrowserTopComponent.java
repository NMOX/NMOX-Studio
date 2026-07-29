package org.nmox.studio.ui.browser;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import org.nmox.studio.core.spi.LiveServings;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.HtmlBrowser;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

/**
 * The in-app web browser (v1.199.0, ⌥⌘4): the platform's embedded
 * WebKit — {@code org.netbeans.core.browser.webview}, a real JavaFX
 * WebView with JavaScript, forms, history — wrapped in the standard
 * {@link HtmlBrowser} chrome (URL bar, back/forward, reload). The
 * engine lights up because the bundled runtime now carries OpenJFX;
 * a dev build on a plain JDK degrades to an honest in-window
 * explanation, never a broken pane.
 *
 * <p>Laws: ZERO boot cost — the browser (and the JavaFX platform it
 * spins up) initializes on first open, not at startup; the component
 * opens at a live serving when one exists, else the welcome note.
 */
@TopComponent.Description(preferredID = "WebBrowserTopComponent",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS)
@TopComponent.Registration(mode = "editor", openAtStartup = false, position = 355)
@ActionID(category = "Window", id = "org.nmox.studio.ui.browser.WebBrowserTopComponent")
@org.openide.awt.ActionReferences({
    @ActionReference(path = "Menu/Window", position = 267),
    @ActionReference(path = "Shortcuts", name = "DA-4")
})
@TopComponent.OpenActionRegistration(displayName = "#CTL_WebBrowserAction",
        preferredID = "WebBrowserTopComponent")
@Messages({
    "CTL_WebBrowserAction=Browser",
    "CTL_WebBrowserTopComponent=Browser",
    "HINT_WebBrowserTopComponent=The in-app web browser (embedded WebKit)"
})
public final class WebBrowserTopComponent extends TopComponent {

    private HtmlBrowser browser;
    private String pendingUrl;

    public WebBrowserTopComponent() {
        setName(Bundle.CTL_WebBrowserTopComponent());
        setToolTipText(Bundle.HINT_WebBrowserTopComponent());
        setLayout(new BorderLayout());
    }

    @Override
    protected void componentOpened() {
        if (browser != null || getComponentCount() > 0) {
            return;
        }
        HtmlBrowser.Factory embedded = EmbeddedBrowserProvider.embeddedFactory();
        if (embedded == null) {
            add(unavailablePanel(), BorderLayout.CENTER);
            return;
        }
        // toolbar on (URL bar, back/forward, reload), status line off —
        // the platform status line already exists
        browser = new HtmlBrowser(embedded, true, false);
        add(browser, BorderLayout.CENTER);
        String target = pendingUrl != null ? pendingUrl : startUrl();
        pendingUrl = null;
        if (target != null) {
            browser.setURL(safeUrl(target));
        }
    }

    /** EDT. Points the (possibly not-yet-opened) browser at a URL. */
    void showUrl(String url) {
        if (browser == null) {
            pendingUrl = url;
        } else {
            browser.setURL(safeUrl(url));
        }
        open();
        requestActive();
    }

    boolean engineAvailable() {
        return EmbeddedBrowserProvider.embeddedFactory() != null;
    }

    /**
     * The most useful first page: the aimed project's live dev server
     * when one is running (the LiveServings facade — soft dependency,
     * null without the rack), else nothing (the HtmlBrowser shows its
     * empty state and the URL bar invites typing).
     */
    private static String startUrl() {
        LiveServings servings = LiveServings.find();
        if (servings != null) {
            for (LiveServings.Serving s : servings.snapshot()) {
                if (s.url() != null && !s.url().isBlank()) {
                    return s.url();
                }
            }
        }
        return null;
    }

    private static java.net.URL safeUrl(String url) {
        try {
            return java.net.URI.create(url).toURL();
        } catch (Exception ex) {
            return null;
        }
    }

    private static JPanel unavailablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel note = new JLabel("<html><div style='width:420px'>"
                + "<b>The embedded browser needs the bundled runtime.</b><br><br>"
                + "Installed builds of NMOX Studio ship a Java runtime that "
                + "includes JavaFX, which powers this window's WebKit engine. "
                + "This launch is running on a plain JDK without JavaFX "
                + "(typical for a dev build with --jdkhome), so pages open in "
                + "your system browser instead — every Open-in-Browser action "
                + "still works.</div></html>", SwingConstants.CENTER);
        note.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        panel.add(note, BorderLayout.CENTER);
        return panel;
    }
}
