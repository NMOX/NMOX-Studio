package org.nmox.studio.ui.browser.fx;

import org.nmox.studio.core.http.LoopbackUrls;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import netscape.javascript.JSObject;
import org.nmox.studio.ui.browser.devtools.BrowserUrls;
import org.nmox.studio.ui.browser.devtools.ConsoleModel;
import org.nmox.studio.ui.browser.devtools.DevScripts;
import org.nmox.studio.ui.browser.devtools.JsBridge;
import org.nmox.studio.ui.browser.devtools.NetworkModel;

/**
 * The Browser tab's engine host: a Swing panel whose center is a
 * {@link JFXPanel} carrying a JavaFX {@link WebView}, with a Swing
 * toolbar (URL bar, back/forward, reload/stop, progress, zoom,
 * DevTools toggle) and a collapsible {@link DevToolsPanel} bottom
 * split. We own the engine directly (v1.206.0) because WebView
 * exposes NO built-in inspector and no remote-debug protocol — the
 * DevTools are built on {@code executeScript} plus an injected JS
 * instrumentation bridge.
 *
 * <p><b>The three-thread contract</b> (every method here states its
 * thread):
 * <ul>
 * <li><b>EDT</b> — all Swing: toolbar, DevTools pane, this panel's
 *     construction. Never touches {@code engine} directly.</li>
 * <li><b>FX Application Thread</b> — ALL WebEngine/WebView access,
 *     reached only via {@code Platform.runLater}. The engine field is
 *     written once there (init is the first queued FX task, so every
 *     later runLater sees it).</li>
 * <li><b>Page JS</b> — runs inside the engine; talks back only
 *     through {@link JsBridge}, whose upcalls arrive ON the FX thread
 *     and are marshaled to the EDT before touching any model.</li>
 * </ul>
 *
 * <p><b>The bridge is a strong reference on purpose</b>:
 * {@code JSObject.setMember} holds Java objects weakly — without the
 * {@code bridge} field a GC would silently disconnect the DevTools
 * (see {@link JsBridge}). The bridge is re-installed on every
 * successful load because each new document is a fresh JS world.
 */
public final class FxBrowserPanel extends JPanel {

    /** Hears page-title changes on the EDT (the TC renames its tab). */
    public interface TitleListener {

        void titleChanged(String title);
    }

    private final JFXPanel fxPanel = new JFXPanel();
    private final ConsoleModel console = new ConsoleModel();
    private final NetworkModel network = new NetworkModel();
    /** STRONG bridge reference — see the class javadoc. */
    private final JsBridge bridge = new JsBridge(SwingUtilities::invokeLater, console, network);
    private final DevToolsPanel devTools;
    private final JSplitPane split;
    private final JTextField urlField = new JTextField();
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JLabel zoomLabel = new JLabel("100%");
    private final JToggleButton devToolsToggle = new JToggleButton("DevTools");
    private final TitleListener titleListener;

    /** FX-thread-only after init. */
    private WebEngine engine;
    private WebView webView;
    /** EDT-only. */
    private double zoom = 1.0;

    /** EDT. Builds the chrome and queues engine init on the FX thread. */
    public FxBrowserPanel(TitleListener titleListener) {
        super(new BorderLayout());
        this.titleListener = titleListener;
        // the FX toolkit is up (JFXPanel ctor started it); closing the
        // tab must not shut it down — it is a process-wide singleton
        Platform.setImplicitExit(false);

        devTools = new DevToolsPanel(console, network, this::runScript);
        add(toolbar(), BorderLayout.NORTH);
        split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, fxPanel, devTools);
        split.setResizeWeight(0.68);
        split.setDividerSize(0);
        devTools.setVisible(false);
        add(split, BorderLayout.CENTER);

        Platform.runLater(this::initFx);
    }

    /** EDT. The Swing toolbar row. */
    private JPanel toolbar() {
        JPanel bar = new JPanel(new BorderLayout(4, 0));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        left.add(navButton("←", "Back", () -> history(-1)));
        left.add(navButton("→", "Forward", () -> history(+1)));
        left.add(navButton("⟳", "Reload", () -> onFx(() -> engine.reload())));
        left.add(navButton("✕", "Stop", () -> onFx(() -> engine.getLoadWorker().cancel())));
        bar.add(left, BorderLayout.WEST);

        urlField.addActionListener(e -> {
            String url = BrowserUrls.normalize(urlField.getText());
            if (url != null) {
                loadUrl(url);
            }
        });
        bar.add(urlField, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        progress.setPreferredSize(new Dimension(70, 14));
        progress.setVisible(false);
        right.add(progress);
        // responsive presets (v1.228.0): cap the page to a device viewport
        // so CSS breakpoints fire exactly as in a window of that size
        javax.swing.JComboBox<ViewportPresets.Preset> viewport =
                new javax.swing.JComboBox<>(
                        ViewportPresets.ALL.toArray(new ViewportPresets.Preset[0]));
        viewport.setToolTipText("Responsive preview: constrain the page to a device "
                + "viewport (CSS pixels). Full uses the whole window.");
        viewport.addActionListener(e -> {
            ViewportPresets.Preset p =
                    (ViewportPresets.Preset) viewport.getSelectedItem();
            if (p != null) {
                onFx(() -> applyViewport(p));
            }
        });
        right.add(viewport);
        right.add(navButton("−", "Zoom out", () -> setZoom(zoom / 1.2)));
        right.add(zoomLabel);
        right.add(navButton("+", "Zoom in", () -> setZoom(zoom * 1.2)));
        right.add(navButton("1:1", "Reset zoom", () -> setZoom(1.0)));
        devToolsToggle.setToolTipText("Show or hide the developer tools");
        devToolsToggle.addActionListener(e -> setDevToolsVisible(devToolsToggle.isSelected()));
        right.add(devToolsToggle);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private static JButton navButton(String text, String tip, Runnable action) {
        JButton b = new JButton(text);
        b.setToolTipText(tip);
        b.addActionListener(e -> action.run());
        return b;
    }

    /** FX thread. One-time engine construction (first queued FX task). */
    private void initFx() {
        webView = new WebView();
        engine = webView.getEngine();
        engine.titleProperty().addListener((obs, old, title)
                -> SwingUtilities.invokeLater(() -> titleListener.titleChanged(title)));
        engine.locationProperty().addListener((obs, old, loc) -> {
            lastLocation = loc; // volatile write on FX; read by the EDT
            SwingUtilities.invokeLater(() -> {
                if (!urlField.isFocusOwner() && loc != null) {
                    urlField.setText(loc);
                }
            });
        });
        engine.getLoadWorker().progressProperty().addListener((obs, old, p) -> {
            int pct = (int) Math.round(p.doubleValue() * 100);
            SwingUtilities.invokeLater(() -> {
                progress.setValue(pct);
                progress.setVisible(pct > 0 && pct < 100);
            });
        });
        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                installBridge();
            } else if (state == Worker.State.FAILED) {
                String loc = engine.getLocation();
                long at = System.currentTimeMillis();
                SwingUtilities.invokeLater(()
                        -> console.add("error", "Load failed: " + loc, at));
            }
        });
        // the WebView sits centered in a neutral backdrop so a viewport
        // preset can cap it to a device size (v1.228.0); Full = fill
        javafx.scene.layout.StackPane viewportPane =
                new javafx.scene.layout.StackPane(webView);
        viewportPane.setStyle("-fx-background-color: #2b2b2b;");
        fxPanel.setScene(new Scene(viewportPane));
    }

    /** FX thread. Caps the WebView to a device viewport, or frees it. */
    private void applyViewport(ViewportPresets.Preset preset) {
        if (preset.full()) {
            webView.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            webView.setPrefSize(-1, -1); // computed = fill the pane
        } else {
            webView.setPrefSize(preset.width(), preset.height());
            webView.setMaxSize(preset.width(), preset.height());
        }
    }

    /**
     * FX thread. Installs the bridge + instrumentation into the page
     * that just finished loading. Every new document is a fresh JS
     * world, so this runs per load; DevScripts.INSTALL is idempotent
     * within one document (the {@code __nmoxDevInstalled} guard).
     */
    private void installBridge() {
        try {
            JSObject window = (JSObject) engine.executeScript("window");
            window.setMember("nmoxBridge", bridge);
            engine.executeScript(DevScripts.INSTALL);
        } catch (RuntimeException ex) {
            // a document with no JS context (e.g. about:blank edge
            // states) simply goes uninstrumented — fail soft
        }
    }

    /** EDT. Loads a URL (already scheme-complete). */
    public void loadUrl(String url) {
        urlField.setText(url);
        if (LoopbackUrls.needsProbe(url)) {
            // localhost URLs first learn which loopback stack actually
            // answers (the WebView loader dials only the first-resolved
            // address; ng serve binds only [::1] — see LoopbackUrls).
            // Socket probes must not ride the EDT; the single-thread RP
            // keeps rapid re-loads FIFO so the last request wins.
            LOOPBACK_RP.post(() -> {
                String target = LoopbackUrls.resolve(url);
                onFx(() -> engine.load(target));
            });
        } else {
            onFx(() -> engine.load(url));
        }
    }

    /** One lane so overlapping localhost probes stay ordered. */
    private static final org.openide.util.RequestProcessor LOOPBACK_RP =
            new org.openide.util.RequestProcessor("Browser Loopback Probe", 1);

    /** EDT. Shows/hides the DevTools split. */
    public void setDevToolsVisible(boolean visible) {
        devToolsToggle.setSelected(visible);
        devTools.setVisible(visible);
        split.setDividerSize(visible ? 6 : 0);
        if (visible) {
            split.setDividerLocation(0.62);
        }
        split.revalidate();
    }

    /** EDT. Toggles the DevTools split (the toolbar button's action). */
    public void toggleDevTools() {
        setDevToolsVisible(!devTools.isVisible());
    }

    /** EDT. Back (-1) / forward (+1) with index bounds respected. */
    private void history(int offset) {
        onFx(() -> {
            WebHistory h = engine.getHistory();
            int target = h.getCurrentIndex() + offset;
            if (target >= 0 && target < h.getEntries().size()) {
                h.go(offset);
            }
        });
    }

    /** EDT. Clamped zoom applied to the WebView on the FX thread. */
    private void setZoom(double z) {
        zoom = Math.max(0.25, Math.min(4.0, z));
        zoomLabel.setText(Math.round(zoom * 100) + "%");
        double apply = zoom;
        onFx(() -> webView.setZoom(apply));
    }

    /**
     * Any thread. The {@link org.nmox.studio.ui.browser.devtools.ScriptRunner}
     * implementation: run on FX, answer on EDT, errors as messages
     * never throws.
     */
    private void runScript(String js, Consumer<String> onResult, Consumer<String> onError) {
        onFx(() -> {
            try {
                Object r = engine.executeScript(js);
                // Every cap inside the injected script is PAGE-controlled
                // (a page can redefine JSON.stringify to hand back
                // hundreds of MB). Cap here, on our side of the border,
                // before the string is copied to the EDT — the parsers'
                // own MAX_INPUT check comes too late to stop the
                // allocation. Truncated JSON simply fails to parse, and
                // the parsers answer honestly empty.
                SwingUtilities.invokeLater(() -> onResult.accept(cap(String.valueOf(r))));
            } catch (RuntimeException ex) {
                String msg = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                SwingUtilities.invokeLater(() -> onError.accept(cap(msg)));
            }
        });
    }

    /**
     * EDT. Stops the page for good: cancels any load and navigates to
     * about:blank, which tears down the document's timers, media and
     * pending requests.
     *
     * <p>Without this, closing the Browser tab left the page RUNNING —
     * JS timers firing, audio playing, requests in flight, the bridge
     * still feeding models nobody can see — until the IDE itself quit.
     * Called from the TopComponent's componentClosed (v1.208.0 review).
     */
    public void stopEngine() {
        onFx(() -> {
            try {
                engine.getLoadWorker().cancel();
                engine.load("about:blank");
            } catch (RuntimeException ignored) {
                // teardown is best-effort; a dying engine is still dead
            }
        });
        devTools.stopTimers();
        stopSaveReload();
    }

    // ---- save-to-reload (v1.228.0, the Senior Web Designer pass) --------

    /**
     * The designer's tightest loop is save → see. When a web file
     * (html/css/js family) is written anywhere and the Browser is
     * showing a LOCAL page (localhost/127.0.0.1 — a dev server or
     * IGNITION's static serve), the page reloads by itself, coalesced
     * 400 ms after the last save so a Save All is one reload. Remote
     * pages never auto-reload: a save of yours has nothing to do with
     * a page you're merely reading.
     */
    private static final java.util.Set<String> RELOAD_EXTS = java.util.Set.of(
            "html", "htm", "css", "scss", "less", "js", "mjs", "ts", "svg", "json");
    /** The engine's last reported location (FX writes, EDT reads). */
    private volatile String lastLocation;
    private final javax.swing.Timer reloadCoalesce = coalescedReload();
    private final org.openide.filesystems.FileChangeListener saveListener =
            new org.openide.filesystems.FileChangeAdapter() {
                @Override
                public void fileChanged(org.openide.filesystems.FileEvent fe) {
                    String ext = fe.getFile().getExt().toLowerCase(java.util.Locale.ROOT);
                    if (RELOAD_EXTS.contains(ext)) {
                        SwingUtilities.invokeLater(FxBrowserPanel.this::maybeScheduleReload);
                    }
                }
            };
    private boolean saveListenerInstalled;

    private javax.swing.Timer coalescedReload() {
        javax.swing.Timer t = new javax.swing.Timer(400,
                e -> onFx(() -> {
                    try {
                        engine.reload();
                    } catch (RuntimeException ignored) {
                        // best effort; a broken engine has bigger problems
                    }
                }));
        t.setRepeats(false);
        return t;
    }

    /**
     * EDT. Arms the coalescer only when the shown page is local — the
     * {@link LocalUrls} host check against the location the ENGINE last
     * reported, not the url field's draft text (v1.234.0 review: while
     * the user types a candidate URL in the focused field, the shown
     * page is what a save should or shouldn't reload).
     */
    private void maybeScheduleReload() {
        if (LocalUrls.isLocal(lastLocation)) {
            reloadCoalesce.restart();
        }
    }

    /** EDT. Called from the TopComponent when the tab opens. */
    public void startSaveReload() {
        if (!saveListenerInstalled) {
            org.openide.filesystems.FileUtil.addFileChangeListener(saveListener);
            saveListenerInstalled = true;
        }
    }

    /** EDT. Symmetric detach — the listener dies with the tab. */
    private void stopSaveReload() {
        if (saveListenerInstalled) {
            org.openide.filesystems.FileUtil.removeFileChangeListener(saveListener);
            saveListenerInstalled = false;
        }
        reloadCoalesce.stop();
    }

    /** Ceiling on anything crossing back from the page (see runScript). */
    private static String cap(String s) {
        return s.length() <= org.nmox.studio.ui.browser.devtools.JsonLite.MAX_INPUT
                ? s
                : s.substring(0, org.nmox.studio.ui.browser.devtools.JsonLite.MAX_INPUT)
                        + "…[truncated]";
    }

    /**
     * Queues work on the FX thread. Safe ordering: {@code initFx} was
     * queued first in the constructor, so {@code engine} is always
     * non-null by the time any of these run.
     */
    private void onFx(Runnable body) {
        Platform.runLater(body);
    }
}
