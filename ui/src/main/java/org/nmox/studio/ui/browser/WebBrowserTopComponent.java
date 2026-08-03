package org.nmox.studio.ui.browser;

import java.awt.BorderLayout;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import org.nmox.studio.core.spi.LiveServings;
import org.nmox.studio.ui.browser.devtools.BrowserUrls;
import org.nmox.studio.ui.browser.fx.FxAvailability;
import org.nmox.studio.ui.browser.fx.FxBrowserPanel;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

/**
 * The in-app web browser (⌥⌘4) — since v1.206.0 we own the JavaFX
 * WebView engine directly ({@link FxBrowserPanel}) instead of
 * wrapping the platform's HtmlBrowser: WebView exposes NO built-in
 * inspector and no remote-debug protocol, so real developer tools
 * (Console/DOM/Network/Storage/Vue tabs) had to be built on
 * {@code executeScript} plus an injected JS bridge — which requires
 * the engine in our hands. The bundled runtime carries OpenJFX; a dev
 * build on a plain JDK degrades to an honest in-window explanation,
 * never a broken pane (the v1.199.0 degradation story, unchanged).
 *
 * <p>Laws: ZERO boot cost — the browser (and the JavaFX platform it
 * spins up) initializes on first open, not at startup; the component
 * opens at a live serving when one exists, else the home page. The
 * page title (untrusted, capped at 30 chars) names the tab.
 */
@TopComponent.Description(preferredID = "WebBrowserTopComponent",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS)
@TopComponent.Registration(mode = "editor", openAtStartup = true, position = 355)
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
    "HINT_WebBrowserTopComponent=The in-app web browser with developer tools"
})
public final class WebBrowserTopComponent extends TopComponent {

    private FxBrowserPanel browser;
    private String pendingUrl;

    public WebBrowserTopComponent() {
        setName(Bundle.CTL_WebBrowserTopComponent());
        setToolTipText(Bundle.HINT_WebBrowserTopComponent());
        setLayout(new BorderLayout());
    }

    @Override
    protected void componentOpened() {
        // Deliberately empty. Since v1.211.0 this tab is open by DEFAULT so a
        // newcomer discovers it, which means componentOpened now fires during
        // startup — and building here would boot the whole JavaFX platform and
        // fetch the home page before the window has even painted, breaking the
        // zero-work-at-boot law (v1.38.0: a hidden default-open tab must cost
        // nothing until it is looked at). All of it moved to componentShowing,
        // the same idiom DB Studio has used since v1.35.1.
    }

    /** v1.235.0: the aim is this window's ambient selection (ledger 29). */
    private final org.nmox.studio.rack.service.AimFollower aimFollower =
            new org.nmox.studio.rack.service.AimFollower(n ->
                    setActivatedNodes(new org.openide.nodes.Node[]{n}));

    @Override
    protected void componentHidden() {
        aimFollower.hidden();
    }

    @Override
    protected void componentShowing() {
        // before the build-once guard: the ambient selection follows
        // EVERY showing, not just the first
        aimFollower.showing();
        if (browser != null || getComponentCount() > 0) {
            return;
        }
        if (!FxAvailability.available()) {
            add(unavailablePanel(), BorderLayout.CENTER);
            return;
        }
        browser = new FxBrowserPanel(title -> setDisplayName(BrowserUrls.tabTitle(title)));
        add(browser, BorderLayout.CENTER);
        // save → see: local pages reload themselves on web-file saves
        // (v1.228.0); detached symmetrically in stopEngine when the tab
        // closes
        browser.startSaveReload();
        // DevTools toggle chord (unadvertised — the toolbar button is
        // the contract; docked TopComponents don't reliably receive
        // every chord, the v1.204.0 irc lesson)
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke("meta alt I"), "nmox.devtools.toggle");
        getActionMap().put("nmox.devtools.toggle", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                browser.toggleDevTools();
            }
        });
        String target = pendingUrl != null ? pendingUrl : startUrl();
        pendingUrl = null;
        if (target != null) {
            browser.loadUrl(target);
        }
    }

    @Override
    protected void componentClosed() {
        aimFollower.closed();
        // Closing the tab must actually stop the page: a WebView whose
        // Swing wrapper is discarded keeps running its timers, audio and
        // requests, and keeps feeding the DevTools models, until the IDE
        // quits (v1.208.0 review finding). The panel is rebuilt from
        // scratch on the next componentOpened.
        if (browser != null) {
            browser.stopEngine();
            browser = null;
        }
        removeAll();
    }

    /** EDT. Points the (possibly not-yet-opened) browser at a URL. */
    void showUrl(String url) {
        if (browser == null) {
            pendingUrl = url;
        } else {
            browser.loadUrl(url);
        }
        open();
        requestActive();
    }

    boolean engineAvailable() {
        return FxAvailability.available();
    }

    /** The home page a bare open lands on (v1.204.0, David's pick). */
    static final String HOME_URL = "https://news.ycombinator.com/";

    /**
     * The most useful first page: the aimed project's live dev server
     * when one is running (the LiveServings facade — soft dependency,
     * null without the rack), else the home page.
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
        // no dev server running: a home page beats an empty pane —
        // SCOPE/facade-routed opens still land on their own URL via
        // showUrl, so this only decides what a bare ⌥⌘4 shows
        return HOME_URL;
    }

    private static JPanel unavailablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel note = new JLabel("<html><div style='width:420px'>"
                + "<b>The embedded browser needs the bundled runtime.</b><br><br>"
                + "Installed builds of NMOX Studio ship a Java runtime that "
                + "includes JavaFX, which powers this window's WebKit engine "
                + "and its developer tools. This launch is running on a plain "
                + "JDK without JavaFX (typical for a dev build with --jdkhome), "
                + "so pages open in your system browser instead — every "
                + "Open-in-Browser action still works.</div></html>",
                SwingConstants.CENTER);
        note.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        panel.add(note, BorderLayout.CENTER);
        return panel;
    }
}
