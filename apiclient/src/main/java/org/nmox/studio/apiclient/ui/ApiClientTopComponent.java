package org.nmox.studio.apiclient.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.nmox.studio.apiclient.api.ApiClient;
import org.nmox.studio.apiclient.api.ApiResponse;
import org.nmox.studio.apiclient.api.TestRunner;
import org.nmox.studio.apiclient.api.WorkspaceIO;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;
import org.nmox.studio.apiclient.model.ApiModel.Collection;
import org.nmox.studio.apiclient.model.ApiModel.Environment;
import org.nmox.studio.apiclient.model.ApiModel.Request;
import org.nmox.studio.apiclient.model.ApiModel.Workspace;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.windows.TopComponent;

/**
 * API Studio: a Postman-style tab for building, saving, sending, and
 * testing HTTP requests. Collections on the left, a request builder in
 * the center (method, URL, params/headers/body/auth/tests), the
 * response below, and an environment selector whose {@code {{vars}}}
 * let one request travel from localhost to prod. The workspace persists
 * as {@code .nmoxapi.json} beside the aimed project, exactly like the
 * rack patch and the infra design — and it follows the rack's aim
 * mid-session: re-aiming saves pending edits to the old project, then
 * rebinds workspace, file pulse, and offers to the new one.
 */
@TopComponent.Description(preferredID = "ApiClientTopComponent",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS)
@TopComponent.Registration(mode = "editor", openAtStartup = true, position = 350)
@ActionID(category = "Window", id = "org.nmox.studio.apiclient.ui.ApiClientTopComponent")
@org.openide.awt.ActionReferences({
    @ActionReference(path = "Menu/Window", position = 265),
    // Cmd+Alt (DA-) — the studio row lives in the one digit family no
    // shipped module claims. The old chord opened a platform window
    // instead of this one: ⇧⌘8 was the platform's Palette. Keymaps-profile
    // registrations beat Shortcuts-folder ones, so a layer-only audit
    // misses these; WindowShortcutsTest pins the reserved list.
    @ActionReference(path = "Shortcuts", name = "DA-8")
})
@TopComponent.OpenActionRegistration(displayName = "#CTL_ApiClientAction",
        preferredID = "ApiClientTopComponent")
@Messages({
    "CTL_ApiClientAction=API Studio",
    "CTL_ApiClientTopComponent=API Studio",
    "HINT_ApiClientTopComponent=Postman-style API management and testing"
})
public final class ApiClientTopComponent extends TopComponent {

    // sends, rebind reads, and registry pokes are short bounded jobs: they
    // share one small pool instead of a raw thread per click
    private static final RequestProcessor RP = new RequestProcessor("API Studio", 2);

    /**
     * Sends ride their OWN interruptible lane. On the shared two-slot
     * RP, two hung sends silently wedged everything else queued there —
     * re-aim follows, workspace loads, serving refreshes — until the
     * 30s request timeouts fired. Housekeeping must never queue behind
     * the network. {@code interruptThread=true} is what makes Cancel
     * real: {@code Task.cancel()} interrupts a running send and
     * {@code HttpClient.send} unblocks with InterruptedException.
     */
    private static final RequestProcessor SEND_RP =
            new RequestProcessor("API Studio Send", 4, true);

    /** EDT-confined: the in-flight send, so Cancel can reach it. */
    private RequestProcessor.Task inFlight;
    /**
     * Workspace writes ride their own single-throughput lane, NOT
     * {@link #RP} — RP's throughput 2 could interleave two writes
     * (debt #16; the careful parts are documented on the lane class).
     */
    private static final org.nmox.studio.apiclient.api.SaveLane SAVES =
            new org.nmox.studio.apiclient.api.SaveLane("API Studio workspace saves");

    private static final Color OK_GREEN = new Color(0x4E, 0xC9, 0x8B);
    private static final Color FAIL_RED = new Color(0xE2, 0x4B, 0x4A);
    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private Workspace workspace;
    private Request current;
    private final ApiClient client = new ApiClient();
    private final Timer saveDebounce;
    private boolean loading;

    /**
     * Runs a UI-mutating body with the {@code loading} guard raised, and
     * GUARANTEES the guard falls again. The v1.263.0 rename bug's worst
     * symptom was not the exception — it was the aborted bindRequest
     * leaving {@code loading} stuck true, which silently killed every
     * edit-recording listener from then on: a UI bug promoted to data
     * loss. Removing that trigger fixed the instance; this helper fixes
     * the CLASS — any future throw inside a guarded body can no longer
     * wedge the guard. The only lawful way to raise the flag
     * (source-gated by LoadingGuardShapeTest).
     */
    private void withLoading(Runnable body) {
        loading = true;
        try {
            body.run();
        } finally {
            loading = false;
        }
    }

    /** Rack servings → the {{baseUrl}} offer; null when the rack is absent. */
    private org.nmox.studio.apiclient.api.ServingBridge servingBridge;
    /** One offer per (url + project) per session — recorded at offer time. */
    private final java.util.Set<String> offeredBaseUrls = new java.util.HashSet<>();
    /** Polls .nmoxapi.json for foreign edits while the tab is open. */
    private org.nmox.studio.apiclient.api.WorkspaceFilePulse filePulse;
    /** Distinguishes our own saves from foreign edits. */
    private final org.nmox.studio.core.util.SelfWriteTracker selfWrites =
            new org.nmox.studio.core.util.SelfWriteTracker();
    /** Follows the rack's mid-session re-aims; see onProjectReaimed. */
    private final org.nmox.studio.core.spi.ProjectAim.Listener rackListener;
    private boolean rackListenerAttached;
    /** The storm-law core deciding which re-aims load and which loads bind. */
    private final org.nmox.studio.apiclient.api.ProjectRebind rebind;

    private final JTree tree = new JTree();
    private final JComboBox<String> envCombo = new JComboBox<>();
    private final JComboBox<String> methodCombo =
            new JComboBox<>(new String[]{"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"});
    private final JTextField urlField = new JTextField();
    private final JTextField nameField = new JTextField();
    private final JButton sendButton = new JButton("Send");
    // send history (v1.197.0)
    private final javax.swing.DefaultListModel<org.nmox.studio.apiclient.model.SendHistory.Entry>
            historyModel = new javax.swing.DefaultListModel<>();
    private final javax.swing.JList<org.nmox.studio.apiclient.model.SendHistory.Entry>
            historyList = new javax.swing.JList<>(historyModel);

    private final JTable paramsTable = org.nmox.studio.core.util.PlainTables.disableHtml(new JTable());
    private final JTable headersTable = org.nmox.studio.core.util.PlainTables.disableHtml(new JTable());
    private final JTextArea bodyArea = new JTextArea();
    private final JComboBox<AuthType> authCombo = new JComboBox<>(AuthType.values());
    // A JPasswordField, not a plaintext JTextField (v1.97.0): the token
    // is a secret, so it neither echoes on screen nor is written to the
    // committable .nmoxapi.json — it lives in the OS keychain.
    private final javax.swing.JPasswordField authField = new javax.swing.JPasswordField();
    private final JTable testsTable = org.nmox.studio.core.util.PlainTables.disableHtml(new JTable());

    private final JLabel statusLabel = new JLabel(" ");
    private final javax.swing.JButton explainButton = new javax.swing.JButton("Explain…");
    /** The last delivered response — what Explain would disclose. */
    private ApiResponse lastResponse;
    private String lastMethod;
    private String lastUrl;
    private final JTextArea responseBody = new JTextArea();
    // response pack (v1.198.0)
    private final JTextField responseFind = new JTextField();
    private final JLabel findCount = new JLabel(" ");
    private final JTextArea responseHeaders = new JTextArea();
    private final JPanel testResults = new JPanel();
    private final JPanel standardsPanel = new JPanel();

    public ApiClientTopComponent() {
        setName(Bundle.CTL_ApiClientTopComponent());
        setToolTipText(Bundle.HINT_ApiClientTopComponent());
        setLayout(new BorderLayout());

        saveDebounce = new Timer(800, e -> save());
        saveDebounce.setRepeats(false);

        add(buildToolbar(), BorderLayout.NORTH);
        JSplitPane center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildTree(), buildEditorAndResponse());
        center.setDividerLocation(240);
        add(center, BorderLayout.CENTER);

        // ⌘Enter (Ctrl+Enter elsewhere) = the Send button, from anywhere
        // in the tab — including mid-typing in the URL or body field.
        // A component-level InputMap, so it cannot collide with the
        // platform Keymaps profile (the v1.38.1 shortcut-theft class
        // lives in Shortcuts/, not here). It mirrors the button exactly:
        // while a send is in flight the same chord cancels it.
        int menuMask = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                javax.swing.KeyStroke.getKeyStroke(
                        java.awt.event.KeyEvent.VK_ENTER, menuMask),
                "nmox-send");
        getActionMap().put("nmox-send", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                send();
            }
        });
        String mod = java.awt.event.InputEvent.getModifiersExText(menuMask);
        sendButton.setToolTipText("Send the request  ("
                + mod + (mod.length() > 1 ? "+" : "") + "Enter)");

        rebind = new org.nmox.studio.apiclient.api.ProjectRebind(projectDir());
        rackListener = new org.nmox.studio.core.spi.ProjectAim.Listener() {
            @Override
            public void projectChanged() {
                SwingUtilities.invokeLater(ApiClientTopComponent.this::onProjectReaimed);
            }
        };
        // no workspace read here: the constructor runs during window-system
        // deserialization; componentOpened owns the initial load
    }

    // ---- toolbar: environment + send ----

    private JToolBar buildToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(new JLabel(" Environment: "));
        envCombo.addActionListener(e -> {
            if (!loading && envCombo.getSelectedItem() != null) {
                workspace.activeEnvironment = (String) envCombo.getSelectedItem();
                touch();
            }
        });
        bar.add(envCombo);
        JButton editEnv = new JButton("Variables…");
        editEnv.addActionListener(e -> editEnvironment());
        bar.add(editEnv);
        bar.addSeparator();
        methodCombo.setMaximumSize(methodCombo.getPreferredSize());
        methodCombo.addActionListener(e -> {
            if (!loading && current != null) {
                current.method = (String) methodCombo.getSelectedItem();
                // the tree label is "METHOD  name" — repaint it so a
                // GET->POST switch shows immediately (same one-node
                // repaint the rename uses; no reload, no reselect)
                repaintTreeLabel(current);
                touch();
            }
        });
        bar.add(methodCombo);
        urlField.getDocument().addDocumentListener(new SimpleDoc(() -> {
            if (!loading && current != null) {
                current.url = urlField.getText();
                touch();
            }
        }));
        bar.add(urlField);
        sendButton.setForeground(new Color(0x1D, 0x9E, 0x75));
        sendButton.addActionListener(e -> send());
        bar.add(sendButton);
        JButton copyCurl = new JButton("Copy curl");
        copyCurl.setToolTipText("Copy this request as the exact curl command Send would run");
        copyCurl.addActionListener(e -> copyAsCurl());
        bar.add(copyCurl);
        return bar;
    }

    /** The current request as a terminal-ready curl command, to the clipboard. */
    private void copyAsCurl() {
        if (current == null) {
            status("Select a request first.");
            return;
        }
        Environment env = workspace.active();
        Map<String, String> vars = env != null ? env.variables : Map.of();
        Request target = current;
        // the rendered command is what Send would run, auth included —
        // a lazily-unhydrated token loads NOW, for this request only
        // (v1.201.0), then the render and clipboard land back on the EDT
        RP.post(() -> {
            hydrateAuthNow(target);
            String curl = org.nmox.studio.apiclient.api.CurlCodec.render(target, vars);
            SwingUtilities.invokeLater(() -> {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new java.awt.datatransfer.StringSelection(curl), null);
                status("curl command copied"
                        + (target.authType != org.nmox.studio.apiclient.model.ApiModel.AuthType.NONE
                                && target.authToken != null && !target.authToken.isBlank()
                                ? " — includes the auth secret" : "") + ".");
                if (current == target) {
                    withLoading(() -> authField.setText(target.authToken));
                }
            });
        });
    }

    private void status(String text) {
        statusLabel.setForeground(Color.GRAY);
        statusLabel.setText(text);
    }

    /**
     * Forgets the shown response and everything Explain could disclose
     * from it. Package-visible so the re-aim law is testable without a
     * live tab (v1.172.0).
     */
    void clearResponse() {
        lastResponse = null;
        lastMethod = null;
        lastUrl = null;
        explainButton.setEnabled(false);
        responseBody.setText("");
        responseHeaders.setText("");
        statusLabel.setText(" ");
        testResults.removeAll();
        standardsPanel.removeAll();
        // the find bar counts matches in the body just wiped — leaving
        // the old count up would claim matches in a response that no
        // longer exists (v1.200.0; the v1.198.0 refind sites covered the
        // send paths, this is the clearing path)
        refindInBody();
    }

    /** Test seam: what Explain would send right now, or null when disarmed. */
    ApiResponse armedResponse() {
        return explainButton.isEnabled() ? lastResponse : null;
    }

    /**
     * Hands ORACLE a REDACTED summary of the response on screen. The
     * disclosure is assembled here, in the studio that owns the data —
     * the seam carries text only, so the rack can never widen it — and
     * the flow earns its own consent (a response body can carry customer
     * data that no other ORACLE grant ever described).
     */
    private void explainResponse() {
        org.nmox.studio.core.spi.OracleAsk oracle = org.nmox.studio.core.spi.OracleAsk.find();
        if (oracle == null || lastResponse == null) {
            status("Nothing to explain yet — send a request first.");
            return;
        }
        String title = (lastMethod == null ? "GET" : lastMethod) + " · "
                + (lastResponse.reached() ? String.valueOf(lastResponse.status()) : "no route");
        boolean started = oracle.explain(new org.nmox.studio.core.spi.OracleAsk.Disclosure(
                "api.response", title,
                org.nmox.studio.apiclient.api.ResponseDisclosure.what(lastResponse),
                org.nmox.studio.apiclient.api.ResponseDisclosure.body(
                        lastMethod, lastUrl, lastResponse),
                "What does this response mean, and what should I check first?"));
        if (!started) {
            status("ORACLE did not run — needs an API key and your consent.");
        }
    }

    /**
     * The Import… menu: the format family, the export, and — when
     * {@code ~/.nmox/api-library.d} has entries — the library section
     * (v1.297.0). A library item imports through the exact
     * {@link #importHttpFrom} implementation the chooser and the editor
     * gesture share, so the off-EDT read, the refusals, and the
     * secrets-law Authorization lift reach it by construction.
     */
    private void showImportMenu(javax.swing.JButton anchor,
            java.util.List<org.nmox.studio.apiclient.api.HttpLibrary.Entry> library) {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem curl = new javax.swing.JMenuItem("curl command…");
        curl.addActionListener(a -> importCurl());
        javax.swing.JMenuItem http = new javax.swing.JMenuItem(".http / .rest file…");
        http.addActionListener(a -> importHttpFile());
        javax.swing.JMenuItem openapi = new javax.swing.JMenuItem("OpenAPI 3 (JSON/YAML)…");
        openapi.addActionListener(a -> importOpenApi());
        javax.swing.JMenuItem postman = new javax.swing.JMenuItem("Postman Collection…");
        postman.addActionListener(a -> importPostman());
        javax.swing.JMenuItem postmanEnv = new javax.swing.JMenuItem("Postman Environment…");
        postmanEnv.addActionListener(a -> importPostmanEnv());
        javax.swing.JMenuItem insomnia = new javax.swing.JMenuItem("Insomnia Export…");
        insomnia.addActionListener(a -> importInsomnia());
        javax.swing.JMenuItem har = new javax.swing.JMenuItem("HAR capture…");
        har.addActionListener(a -> importHar());
        javax.swing.JMenuItem export = new javax.swing.JMenuItem(
                "Export collection to .http…");
        export.addActionListener(a -> exportHttp());
        menu.add(curl);
        menu.add(http);
        menu.add(openapi);
        menu.add(postman);
        menu.add(postmanEnv);
        menu.add(insomnia);
        menu.add(har);
        if (!library.isEmpty()) {
            menu.addSeparator();
            for (org.nmox.studio.apiclient.api.HttpLibrary.Entry entry : library) {
                javax.swing.JMenuItem item =
                        new javax.swing.JMenuItem(entry.name() + " · library");
                item.setToolTipText(entry.file().getAbsolutePath());
                item.addActionListener(a -> importHttpFrom(entry.file()));
                menu.add(item);
            }
        }
        menu.addSeparator();
        menu.add(export);
        menu.show(anchor, 0, anchor.getHeight());
    }

    /** Import a REST Client .http/.rest file as a whole collection. */
    private void importHttpFile() {
        java.io.File file = new org.openide.filesystems.FileChooserBuilder(
                ApiClientTopComponent.class)
                .setTitle("Import .http file")
                .setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                        ".http / .rest request files", "http", "rest"))
                .showOpenDialog();
        if (file == null) {
            return;
        }
        importHttpFrom(file);
    }

    /**
     * The one .http import implementation, shared by the chooser path
     * above and the editor gesture ({@link #importHttpFileFromEditor}) —
     * a fix to either (the off-EDT read law, refusals, the secrets-law
     * Authorization lift in the codec) reaches both by construction.
     */
    private void importHttpFrom(java.io.File file) {
        // read + parse off the EDT (the v1.108.0 Load-Patch law), apply on it
        RP.post(() -> {
            org.nmox.studio.apiclient.api.HttpFileCodec.Imported got;
            try {
                String text = java.nio.file.Files.readString(file.toPath());
                got = org.nmox.studio.apiclient.api.HttpFileCodec.parse(text);
            } catch (java.io.IOException | IllegalArgumentException ex) {
                java.awt.EventQueue.invokeLater(() ->
                        org.openide.DialogDisplayer.getDefault().notify(
                                new org.openide.NotifyDescriptor.Message(ex.getMessage(),
                                        org.openide.NotifyDescriptor.ERROR_MESSAGE)));
                return;
            }
            java.awt.EventQueue.invokeLater(() -> applyHttpImport(file, got));
        });
    }

    /**
     * The editor gesture (v1.195.0): front the tab and import this
     * on-disk .http file — no chooser. Safe against an unopened tab:
     * {@code open()} runs {@code componentOpened}, whose workspace load
     * is synchronous, so the import can never land on an unbound
     * workspace and be clobbered by the initial load.
     */
    public static void importHttpFileFromEditor(java.io.File file) {
        SwingUtilities.invokeLater(() -> {
            org.openide.windows.TopComponent tc = org.openide.windows.WindowManager
                    .getDefault().findTopComponent("ApiClientTopComponent");
            if (tc instanceof ApiClientTopComponent api) {
                api.open();
                api.requestActive();
                api.importHttpFrom(file);
            }
        });
    }

    /** Import an OpenAPI 3 JSON document as a whole collection. */
    private void importOpenApi() {
        java.io.File file = new org.openide.filesystems.FileChooserBuilder(
                ApiClientTopComponent.class)
                .setTitle("Import OpenAPI 3 (JSON or YAML)")
                .setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                        "OpenAPI documents", "json", "yaml", "yml"))
                .showOpenDialog();
        if (file == null) {
            return;
        }
        RP.post(() -> {
            org.nmox.studio.apiclient.api.OpenApiCodec.Imported got;
            try {
                got = org.nmox.studio.apiclient.api.OpenApiCodec.parse(
                        java.nio.file.Files.readString(file.toPath()));
            } catch (java.io.IOException | IllegalArgumentException ex) {
                java.awt.EventQueue.invokeLater(() ->
                        org.openide.DialogDisplayer.getDefault().notify(
                                new org.openide.NotifyDescriptor.Message(ex.getMessage(),
                                        org.openide.NotifyDescriptor.ERROR_MESSAGE)));
                return;
            }
            java.awt.EventQueue.invokeLater(() -> {
                Collection c = new Collection();
                c.name = got.title();
                c.requests.addAll(got.requests());
                workspace.collections.add(c);
                int added = 0;
                Environment env = workspace.active();
                if (env != null) {
                    for (var e : got.variables().entrySet()) {
                        if (env.variables.putIfAbsent(e.getKey(), e.getValue()) == null) {
                            added++;
                        }
                    }
                }
                rebuildTree();
                current = c.requests.get(0);
                restoreSelection();
                touch();
                StringBuilder msg = new StringBuilder("Imported \"").append(c.name)
                        .append("\": ").append(c.requests.size()).append(" request")
                        .append(c.requests.size() == 1 ? "" : "s");
                if (added > 0) {
                    msg.append(", {{baseUrl}} into ").append(env.name);
                }
                if (!got.notes().isEmpty()) {
                    msg.append(" — ").append(String.join(" ", got.notes()));
                }
                status(msg.append('.').toString());
            });
        });
    }

    private void applyHttpImport(java.io.File file,
            org.nmox.studio.apiclient.api.HttpFileCodec.Imported got) {
        Collection c = new Collection();
        c.name = file.getName();
        c.requests.addAll(got.requests());
        workspace.collections.add(c);
        // file-level @vars join the active environment, never clobbering
        int added = 0;
        Environment env = workspace.active();
        if (env != null) {
            for (var e : got.variables().entrySet()) {
                if (env.variables.putIfAbsent(e.getKey(), e.getValue()) == null) {
                    added++;
                }
            }
        }
        rebuildTree();
        current = c.requests.get(0);
        restoreSelection();
        touch();
        StringBuilder msg = new StringBuilder("Imported ")
                .append(c.requests.size()).append(" request")
                .append(c.requests.size() == 1 ? "" : "s");
        if (added > 0) {
            msg.append(", ").append(added).append(" variable")
                    .append(added == 1 ? "" : "s").append(" into ").append(env.name);
        }
        if (!got.notes().isEmpty()) {
            msg.append(" — ").append(String.join(" ", got.notes()));
        }
        status(msg.append('.').toString());
    }

    /** Import a Postman Collection v2.x export as a whole collection. */
    private void importPostman() {
        java.io.File file = new org.openide.filesystems.FileChooserBuilder(
                ApiClientTopComponent.class)
                .setTitle("Import Postman Collection (v2.1)")
                .setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                        "Postman collections", "json", "postman_collection"))
                .showOpenDialog();
        if (file == null) {
            return;
        }
        RP.post(() -> {
            org.nmox.studio.apiclient.api.PostmanCodec.Imported got;
            try {
                got = org.nmox.studio.apiclient.api.PostmanCodec.parse(
                        java.nio.file.Files.readString(file.toPath()));
            } catch (java.io.IOException | IllegalArgumentException ex) {
                java.awt.EventQueue.invokeLater(() ->
                        org.openide.DialogDisplayer.getDefault().notify(
                                new org.openide.NotifyDescriptor.Message(ex.getMessage(),
                                        org.openide.NotifyDescriptor.ERROR_MESSAGE)));
                return;
            }
            java.awt.EventQueue.invokeLater(() -> {
                Collection c = new Collection();
                c.name = got.name();
                c.requests.addAll(got.requests());
                workspace.collections.add(c);
                // collection variables join the active environment,
                // never clobbering — the .http import's law
                int added = 0;
                Environment env = workspace.active();
                if (env != null) {
                    for (var e : got.variables().entrySet()) {
                        if (env.variables.putIfAbsent(e.getKey(), e.getValue()) == null) {
                            added++;
                        }
                    }
                }
                rebuildTree();
                current = c.requests.get(0);
                restoreSelection();
                touch();
                StringBuilder msg = new StringBuilder("Imported \"").append(c.name)
                        .append("\": ").append(c.requests.size()).append(" request")
                        .append(c.requests.size() == 1 ? "" : "s");
                if (added > 0) {
                    msg.append(", ").append(added).append(" variable")
                            .append(added == 1 ? "" : "s").append(" into ").append(env.name);
                }
                if (!got.notes().isEmpty()) {
                    msg.append(" — ").append(String.join(" ", got.notes()));
                }
                status(msg.append('.').toString());
            });
        });
    }

    /**
     * Export the selected collection as a .http file — the other half
     * of the v1.166.0 import. Auth never leaves the keychain; the file
     * says so per request.
     */
    private void exportHttp() {
        Collection c = selectedCollection();
        if (c == null || c.requests.isEmpty()) {
            status("Nothing to export — select a collection with requests.");
            return;
        }
        java.io.File file = new org.openide.filesystems.FileChooserBuilder(
                ApiClientTopComponent.class)
                .setTitle("Export \"" + c.name + "\" as .http")
                .setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                        ".http request files", "http", "rest"))
                .showSaveDialog();
        if (file == null) {
            return;
        }
        java.io.File target = file.getName().contains(".") ? file
                : new java.io.File(file.getParentFile(), file.getName() + ".http");
        String text = org.nmox.studio.apiclient.api.HttpFileCodec.render(c);
        int n = c.requests.size();
        RP.post(() -> {
            try {
                java.nio.file.Files.writeString(target.toPath(), text);
            } catch (java.io.IOException ex) {
                java.awt.EventQueue.invokeLater(() ->
                        org.openide.DialogDisplayer.getDefault().notify(
                                new org.openide.NotifyDescriptor.Message(ex.getMessage(),
                                        org.openide.NotifyDescriptor.ERROR_MESSAGE)));
                return;
            }
            java.awt.EventQueue.invokeLater(() -> status("Exported " + n
                    + " request" + (n == 1 ? "" : "s") + " to " + target.getName()
                    + " — auth stays in your keychain, the file says what to re-add."));
        });
    }

    /**
     * Import a Postman environment's plain values into an API Studio
     * environment. Secret-typed values never cross (the codec drops and
     * counts them — .nmoxapi.json is committable); same-name merges
     * never clobber values you already set.
     */
    private void importPostmanEnv() {
        java.io.File file = new org.openide.filesystems.FileChooserBuilder(
                ApiClientTopComponent.class)
                .setTitle("Import Postman Environment")
                .setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                        "Postman environments", "json", "postman_environment"))
                .showOpenDialog();
        if (file == null) {
            return;
        }
        RP.post(() -> {
            org.nmox.studio.apiclient.api.PostmanCodec.ImportedEnvironment got;
            try {
                got = org.nmox.studio.apiclient.api.PostmanCodec.parseEnvironment(
                        java.nio.file.Files.readString(file.toPath()));
            } catch (java.io.IOException | IllegalArgumentException ex) {
                java.awt.EventQueue.invokeLater(() ->
                        org.openide.DialogDisplayer.getDefault().notify(
                                new org.openide.NotifyDescriptor.Message(ex.getMessage(),
                                        org.openide.NotifyDescriptor.ERROR_MESSAGE)));
                return;
            }
            java.awt.EventQueue.invokeLater(() -> {
                Environment env = null;
                for (Environment e : workspace.environments) {
                    if (e.name.equals(got.name())) {
                        env = e;
                        break;
                    }
                }
                boolean fresh = env == null;
                if (fresh) {
                    env = new Environment();
                    env.name = got.name();
                    workspace.environments.add(env);
                }
                int added = 0;
                for (var e : got.values().entrySet()) {
                    if (env.variables.putIfAbsent(e.getKey(), e.getValue()) == null) {
                        added++;
                    }
                }
                touch();
                StringBuilder msg = new StringBuilder(fresh ? "Created" : "Updated")
                        .append(" environment \"").append(env.name).append("\": ")
                        .append(added).append(" variable").append(added == 1 ? "" : "s")
                        .append(fresh ? "" : " added (existing values kept)");
                if (!got.notes().isEmpty()) {
                    msg.append(" — ").append(String.join(" ", got.notes()));
                }
                status(msg.append('.').toString());
            });
        });
    }

    /** Import an Insomnia v4 export as a whole collection. */
    private void importInsomnia() {
        java.io.File file = new org.openide.filesystems.FileChooserBuilder(
                ApiClientTopComponent.class)
                .setTitle("Import Insomnia Export (v4 JSON)")
                .setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                        "Insomnia exports", "json"))
                .showOpenDialog();
        if (file == null) {
            return;
        }
        RP.post(() -> {
            org.nmox.studio.apiclient.api.InsomniaCodec.Imported got;
            try {
                got = org.nmox.studio.apiclient.api.InsomniaCodec.parse(
                        java.nio.file.Files.readString(file.toPath()));
            } catch (java.io.IOException | IllegalArgumentException ex) {
                java.awt.EventQueue.invokeLater(() ->
                        org.openide.DialogDisplayer.getDefault().notify(
                                new org.openide.NotifyDescriptor.Message(ex.getMessage(),
                                        org.openide.NotifyDescriptor.ERROR_MESSAGE)));
                return;
            }
            java.awt.EventQueue.invokeLater(() -> {
                Collection c = new Collection();
                c.name = got.name();
                c.requests.addAll(got.requests());
                workspace.collections.add(c);
                int added = 0;
                Environment env = workspace.active();
                if (env != null) {
                    for (var e : got.variables().entrySet()) {
                        if (env.variables.putIfAbsent(e.getKey(), e.getValue()) == null) {
                            added++;
                        }
                    }
                }
                rebuildTree();
                current = c.requests.get(0);
                restoreSelection();
                touch();
                StringBuilder msg = new StringBuilder("Imported \"").append(c.name)
                        .append("\": ").append(c.requests.size()).append(" request")
                        .append(c.requests.size() == 1 ? "" : "s");
                if (added > 0) {
                    msg.append(", ").append(added).append(" variable")
                            .append(added == 1 ? "" : "s").append(" into ").append(env.name);
                }
                if (!got.notes().isEmpty()) {
                    msg.append(" — ").append(String.join(" ", got.notes()));
                }
                status(msg.append('.').toString());
            });
        });
    }

    /** Import a browser HAR capture — the Network tab becomes requests. */
    private void importHar() {
        java.io.File file = new org.openide.filesystems.FileChooserBuilder(
                ApiClientTopComponent.class)
                .setTitle("Import HAR capture")
                .setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                        "HAR captures", "har", "json"))
                .showOpenDialog();
        if (file == null) {
            return;
        }
        RP.post(() -> {
            org.nmox.studio.apiclient.api.HarCodec.Imported got;
            try {
                got = org.nmox.studio.apiclient.api.HarCodec.parse(
                        java.nio.file.Files.readString(file.toPath()));
            } catch (java.io.IOException | IllegalArgumentException ex) {
                java.awt.EventQueue.invokeLater(() ->
                        org.openide.DialogDisplayer.getDefault().notify(
                                new org.openide.NotifyDescriptor.Message(ex.getMessage(),
                                        org.openide.NotifyDescriptor.ERROR_MESSAGE)));
                return;
            }
            java.awt.EventQueue.invokeLater(() -> {
                Collection c = new Collection();
                c.name = file.getName();
                c.requests.addAll(got.requests());
                workspace.collections.add(c);
                rebuildTree();
                current = c.requests.get(0);
                restoreSelection();
                touch();
                StringBuilder msg = new StringBuilder("Imported ")
                        .append(c.requests.size()).append(" request")
                        .append(c.requests.size() == 1 ? "" : "s")
                        .append(" from the capture");
                if (!got.notes().isEmpty()) {
                    msg.append(" — ").append(String.join(" ", got.notes()));
                }
                status(msg.append('.').toString());
            });
        });
    }

    /** Paste a curl command, get a saved request — the reverse of Copy curl. */
    private void importCurl() {
        javax.swing.JTextArea area = new javax.swing.JTextArea(8, 60);
        area.setLineWrap(true);
        javax.swing.JPanel panel = new javax.swing.JPanel(new BorderLayout(0, 6));
        panel.add(new JLabel("Paste a curl command:"), BorderLayout.NORTH);
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        org.openide.DialogDescriptor dd = new org.openide.DialogDescriptor(
                panel, "Import curl");
        if (org.openide.DialogDisplayer.getDefault().notify(dd)
                != org.openide.DialogDescriptor.OK_OPTION) {
            return;
        }
        org.nmox.studio.apiclient.api.CurlCodec.Imported got;
        try {
            got = org.nmox.studio.apiclient.api.CurlCodec.parse(area.getText());
        } catch (IllegalArgumentException ex) {
            org.openide.DialogDisplayer.getDefault().notify(
                    new org.openide.NotifyDescriptor.Message(
                            ex.getMessage(), org.openide.NotifyDescriptor.ERROR_MESSAGE));
            return;
        }
        Collection c = selectedCollection();
        if (c == null) {
            if (workspace.collections.isEmpty()) {
                addCollection();
            }
            c = workspace.collections.get(workspace.collections.size() - 1);
        }
        c.requests.add(got.request());
        rebuildTree();
        current = got.request();
        restoreSelection();
        touch();
        status(got.notes().isEmpty()
                ? "Imported \"" + got.request().name + "\"."
                : "Imported with notes: " + String.join(" ", got.notes()));
    }

    // ---- left: collections tree ----

    private JPanel buildTree() {
        JPanel panel = new JPanel(new BorderLayout());
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setRootVisible(false);
        tree.addTreeSelectionListener(e -> onTreeSelect());
        // Delete also lives where the platform puts it: the context menu
        // and the Delete key — reachable regardless of panel geometry
        javax.swing.JPopupMenu treeMenu = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem duplicateItem = new javax.swing.JMenuItem("Duplicate");
        duplicateItem.addActionListener(e -> duplicateSelected());
        treeMenu.add(duplicateItem);
        javax.swing.JMenuItem renameItem = new javax.swing.JMenuItem("Rename…");
        renameItem.addActionListener(e -> renameSelected());
        treeMenu.add(renameItem);
        treeMenu.addSeparator();
        javax.swing.JMenuItem deleteItem = new javax.swing.JMenuItem("Delete");
        deleteItem.addActionListener(e -> deleteSelected());
        treeMenu.add(deleteItem);
        // Duplicate/Rename/Delete read the tree SELECTION; a right-click
        // must therefore select what it hit first — a request delete has
        // no confirm and wipes the keychain token, so acting on a stale
        // selection is silent data loss (v1.270.0 arc review)
        org.nmox.studio.core.util.Popups.selectOnTrigger(tree);
        tree.setComponentPopupMenu(treeMenu);
        tree.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("DELETE"), "nmox-delete");
        tree.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("BACK_SPACE"), "nmox-delete");
        tree.getActionMap().put("nmox-delete", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                deleteSelected();
            }
        });
        // ⌘D / Ctrl+D duplicates — the Postman muscle memory, scoped to
        // the tree so it can't shadow an editor binding elsewhere
        tree.getInputMap().put(javax.swing.KeyStroke.getKeyStroke(
                java.awt.event.KeyEvent.VK_D,
                java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                "nmox-duplicate");
        tree.getActionMap().put("nmox-duplicate", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                duplicateSelected();
            }
        });
        panel.add(new JScrollPane(tree), BorderLayout.CENTER);

        // a 2x2 grid, not a JToolBar: a toolbar clips without a chevron,
        // and four buttons cannot fit one row at minimum panel width no
        // matter the margins — the v1.167.0/v1.182.0 "Dele" class, closed
        // structurally. Every button is visible at every width.
        JPanel tools = new JPanel(new java.awt.GridLayout(2, 2, 2, 2));
        JButton addCol = new JButton("+ Collection");
        addCol.addActionListener(e -> addCollection());
        JButton addReq = new JButton("+ Request");
        addReq.addActionListener(e -> addRequest());
        // one Import… menu button: two separate buttons overflowed the
        // toolbar at the default panel width and silently hid Delete
        // (2026-07-26 gauntlet find — a JToolBar clips without a chevron)
        JButton importBtn = new JButton("Import…");
        importBtn.setToolTipText("Import curl / .http / OpenAPI / Postman / HAR, "
                + "or export a collection to .http");
        importBtn.addActionListener(e -> {
            // the library scan is file IO — off the EDT (v1.33.1), menu on
            // the callback, button disabled until it shows so two fast
            // clicks cannot stack two popups (the v1.296.0 review find,
            // applied here on day one instead of re-learned)
            importBtn.setEnabled(false);
            RP.post(() -> {
                java.util.List<org.nmox.studio.apiclient.api.HttpLibrary.Entry> library =
                        org.nmox.studio.apiclient.api.HttpLibrary.list();
                java.awt.EventQueue.invokeLater(() -> {
                    importBtn.setEnabled(true);
                    showImportMenu(importBtn, library);
                });
            });
        });
        // (menu construction lives in showImportMenu so the library scan can
        // feed it from off the EDT)
        JButton del = new JButton("Delete");
        del.addActionListener(e -> deleteSelected());
        tools.add(addCol);
        tools.add(addReq);
        tools.add(importBtn);
        tools.add(del);
        panel.add(tools, BorderLayout.SOUTH);

        // v1.197.0: the left panel is tabbed — Collections | History.
        // History is the DB Studio parity request: every send leaves a
        // findable row (authored model only; see SendHistory's law).
        javax.swing.JTabbedPane leftTabs = new javax.swing.JTabbedPane();
        leftTabs.addTab("Collections", panel);
        leftTabs.addTab("History", buildHistoryPanel());
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(leftTabs, BorderLayout.CENTER);
        return wrapper;
    }

    // ---- left panel: send history (v1.197.0) ----

    private JPanel buildHistoryPanel() {
        historyList.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list,
                    Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof org.nmox.studio.apiclient.model.SendHistory.Entry e) {
                    String when = java.time.LocalTime.ofInstant(
                            java.time.Instant.ofEpochMilli(e.timestamp),
                            java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
                    String outcome = e.status == 0 ? "failed" : String.valueOf(e.status);
                    setText(when + "  " + e.method + " " + e.url
                            + "  → " + outcome
                            + (e.durationMs > 0 ? " (" + e.durationMs + " ms)" : ""));
                }
                return this;
            }
        });
        historyList.setToolTipText("Double-click restores a send as a new request"
                + " (auth token not carried — re-enter it)");
        historyList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    restoreFromHistory(historyList.getSelectedValue());
                }
            }
        });
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem restore = new javax.swing.JMenuItem("Restore as request");
        restore.addActionListener(e -> restoreFromHistory(historyList.getSelectedValue()));
        menu.add(restore);
        javax.swing.JMenuItem clear = new javax.swing.JMenuItem("Clear history");
        clear.addActionListener(e -> clearHistory());
        menu.add(clear);
        org.nmox.studio.core.util.Popups.selectOnTrigger(historyList);
        historyList.setComponentPopupMenu(menu);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(historyList), BorderLayout.CENTER);
        return panel;
    }

    /** EDT. Repopulates the list from the workspace, newest first. */
    private void refreshHistory() {
        historyModel.clear();
        for (org.nmox.studio.apiclient.model.SendHistory.Entry e : workspace.history) {
            historyModel.addElement(e);
        }
    }

    /** EDT. Records the SENT request (not whatever is selected now). */
    private void recordHistory(Request sent, int status, long durationMs) {
        org.nmox.studio.apiclient.model.SendHistory.record(workspace.history,
                org.nmox.studio.apiclient.model.SendHistory.of(
                        System.currentTimeMillis(), sent, status, durationMs));
        refreshHistory();
        touch();
    }

    private void restoreFromHistory(org.nmox.studio.apiclient.model.SendHistory.Entry entry) {
        if (entry == null) {
            return;
        }
        if (workspace.collections.isEmpty()) {
            workspace.collections.add(new Collection());
        }
        Collection target = selectedCollection() != null
                ? selectedCollection() : workspace.collections.get(0);
        Request restored = org.nmox.studio.apiclient.model.SendHistory.restore(entry);
        target.requests.add(restored);
        rebuildTree();
        selectRequest(target.name, restored.name);
        touch();
        org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                entry.authType == AuthType.NONE
                ? "Restored from history."
                : "Restored from history — re-enter the auth token (secrets never ride history).");
    }

    private void clearHistory() {
        if (workspace.history.isEmpty()) {
            return;
        }
        // the v1.98.0 safe-default idiom: Enter must not destroy
        Object answer = org.openide.DialogDisplayer.getDefault().notify(
                new org.openide.NotifyDescriptor(
                        "Clear all " + workspace.history.size() + " history entries?",
                        "Clear history",
                        org.openide.NotifyDescriptor.YES_NO_OPTION,
                        org.openide.NotifyDescriptor.QUESTION_MESSAGE,
                        null, org.openide.NotifyDescriptor.NO_OPTION));
        if (answer != org.openide.NotifyDescriptor.YES_OPTION) {
            return;
        }
        workspace.history.clear();
        refreshHistory();
        touch();
    }

    // ---- center: request editor over response viewer ----

    private JSplitPane buildEditorAndResponse() {
        JPanel editor = new JPanel(new BorderLayout());
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.X_AXIS));
        top.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        top.add(new JLabel("Name: "));
        nameField.getDocument().addDocumentListener(new SimpleDoc(() -> {
            if (!loading && current != null) {
                current.name = nameField.getText();
                // Repaint THIS node's label only. reload() + restoreSelection()
                // re-entered the tree's own selection listener -> onTreeSelect ->
                // bindRequest -> nameField.setText() while this very document was
                // mid-notification, which Swing answers with
                // IllegalStateException("Attempt to mutate in notification") — and
                // the aborted setText left the typed name SCRAMBLED (typing
                // "HN top stories" produced "N top storiesH": the first keystroke
                // reset the caret to 0, so every later character landed in front)
                // while the whole tree collapsed. Shipped broken since API Studio
                // itself (v1.19.0); found live against the Hacker News API.
                // nodeChanged fires only treeNodesChanged: the renderer repaints,
                // no selection event, no re-entry, expansion state preserved.
                repaintTreeLabel(current);
                touch();
            }
        }));
        top.add(nameField);
        editor.add(top, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Params", new JScrollPane(paramsTable));
        tabs.addTab("Headers", new JScrollPane(headersTable));
        bodyArea.setFont(MONO);
        bodyArea.getDocument().addDocumentListener(new SimpleDoc(() -> {
            if (!loading && current != null) {
                current.body = bodyArea.getText();
                touch();
            }
        }));
        tabs.addTab("Body", new JScrollPane(bodyArea));
        tabs.addTab("Auth", buildAuthPanel());
        tabs.addTab("Tests", new JScrollPane(testsTable));
        editor.add(tabs, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editor, buildResponsePanel());
        split.setDividerLocation(300);
        return split;
    }

    private JPanel buildAuthPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.add(new JLabel("Type: "));
        authCombo.addActionListener(e -> {
            if (!loading && current != null) {
                current.authType = (AuthType) authCombo.getSelectedItem();
                touch();
            }
        });
        row.add(authCombo);
        panel.add(row);
        panel.add(new JLabel(" "));
        panel.add(new JLabel("Token (Bearer), or user:password (Basic) — {{vars}} allowed:"));
        authField.getDocument().addDocumentListener(new SimpleDoc(() -> {
            if (!loading && current != null) {
                current.authToken = new String(authField.getPassword());
                // typed value is the truth — a later lazy hydration must
                // not refill a deliberately cleared field from the keychain
                hydratedAuth.add(current.id);
                touch();
            }
        }));
        panel.add(authField);
        panel.add(new JLabel("<html><small>Stored in the OS keychain, never in "
                + ".nmoxapi.json. {{vars}} still resolve at send time.</small></html>"));
        return panel;
    }

    private JPanel buildResponsePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        JPanel north = new JPanel(new BorderLayout());
        north.add(statusLabel, BorderLayout.CENTER);
        // ORACLE is a SOFT dependency (ledger 30): with no rack in the
        // platform the lookup misses and the button simply never appears
        if (org.nmox.studio.core.spi.OracleAsk.find() != null) {
            explainButton.setToolTipText("Ask ORACLE what this response means"
                    + " (sends a redacted summary — you confirm first)");
            explainButton.setEnabled(false);
            explainButton.addActionListener(e -> explainResponse());
            north.add(explainButton, BorderLayout.EAST);
        }
        panel.add(north, BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        responseBody.setEditable(false);
        responseBody.setFont(MONO);
        tabs.addTab("Body", buildBodyTab());
        responseHeaders.setEditable(false);
        responseHeaders.setFont(MONO);
        tabs.addTab("Headers", new JScrollPane(responseHeaders));
        testResults.setLayout(new BoxLayout(testResults, BoxLayout.Y_AXIS));
        tabs.addTab("Tests", new JScrollPane(testResults));
        standardsPanel.setLayout(new BoxLayout(standardsPanel, BoxLayout.Y_AXIS));
        tabs.addTab("Standards", new JScrollPane(standardsPanel));
        panel.add(tabs, BorderLayout.CENTER);
        return panel;
    }

    // ---- response pack: find-in-body + save (v1.198.0) ----

    /** Body tab: the response text over a find bar with an honest count. */
    private JPanel buildBodyTab() {
        JPanel body = new JPanel(new BorderLayout());
        body.add(new JScrollPane(responseBody), BorderLayout.CENTER);

        JPanel bar = new JPanel(new BorderLayout(6, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        bar.add(new JLabel("Find:"), BorderLayout.WEST);
        bar.add(responseFind, BorderLayout.CENTER);
        JPanel east = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
        east.add(findCount);
        JButton saveBody = new JButton("Save…");
        saveBody.setToolTipText("Save the RAW response body to a file (not the pretty-printed view)");
        saveBody.addActionListener(e -> saveResponseBody());
        east.add(saveBody);
        bar.add(east, BorderLayout.EAST);
        body.add(bar, BorderLayout.SOUTH);

        responseFind.getDocument().addDocumentListener(new SimpleDoc(this::refindInBody));
        // Enter jumps to the next match (wrapping); Escape hands focus back
        responseFind.addActionListener(e -> jumpToNextMatch());
        responseFind.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("ESCAPE"), "nmox-find-done");
        responseFind.getActionMap().put("nmox-find-done", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                responseBody.requestFocusInWindow();
            }
        });
        // ⌘F / Ctrl+F in the body focuses the find field — the muscle memory
        responseBody.getInputMap().put(javax.swing.KeyStroke.getKeyStroke(
                java.awt.event.KeyEvent.VK_F,
                java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                "nmox-find-in-body");
        responseBody.getActionMap().put("nmox-find-in-body", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                responseFind.requestFocusInWindow();
                responseFind.selectAll();
            }
        });
        return body;
    }

    /** EDT. Re-highlights all matches and updates the count label. */
    private void refindInBody() {
        responseBody.getHighlighter().removeAllHighlights();
        String query = responseFind.getText();
        java.util.List<Integer> matches =
                ResponseSearch.matches(responseBody.getText(), query);
        for (int at : matches) {
            try {
                responseBody.getHighlighter().addHighlight(at, at + query.length(),
                        new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(
                                new Color(255, 200, 0, 120)));
            } catch (javax.swing.text.BadLocationException ex) {
                break; // text changed under us; the next refind heals it
            }
        }
        findCount.setText(query.isEmpty() ? " "
                : matches.size() + (matches.size() >= ResponseSearch.MAX_MATCHES ? "+" : "")
                + " match" + (matches.size() == 1 ? "" : "es"));
    }

    private void jumpToNextMatch() {
        java.util.List<Integer> matches =
                ResponseSearch.matches(responseBody.getText(), responseFind.getText());
        int at = ResponseSearch.next(matches, responseBody.getCaretPosition());
        if (at >= 0) {
            responseBody.setCaretPosition(at);
            responseBody.moveCaretPosition(at + responseFind.getText().length());
            responseBody.requestFocusInWindow();
        }
    }

    /**
     * Saves the RAW body of the response on screen — lastResponse.body(),
     * never the pretty-printed display text — off the EDT. A truncated
     * capture (the 8 MB response cap) is said out loud, not hidden.
     */
    private void saveResponseBody() {
        ApiResponse toSave = lastResponse;
        if (toSave == null || !toSave.reached()) {
            org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                    "No response to save — send a request first.");
            return;
        }
        java.io.File file = new org.openide.filesystems.FileChooserBuilder("api-response-save")
                .setTitle("Save response body (raw)")
                .setFilesOnly(true)
                .showSaveDialog();
        if (file == null) {
            return;
        }
        RP.post(() -> {
            try {
                java.nio.file.Files.write(file.toPath(),
                        toSave.body().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                String note = toSave.truncated()
                        ? "Saved (response was truncated at the capture cap)."
                        : "Saved " + file.getName() + ".";
                java.awt.EventQueue.invokeLater(() ->
                        org.openide.awt.StatusDisplayer.getDefault().setStatusText(note));
            } catch (java.io.IOException ex) {
                java.awt.EventQueue.invokeLater(() ->
                        org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                                "Could not save: " + ex.getMessage()));
            }
        });
    }

    // ---- sending ----

    private void send() {
        if (inFlight != null && !inFlight.isFinished()) {
            // the button reads "Cancel" — interrupt the running send
            // (the worker's failure path resets the UI); if it hadn't
            // started yet, nothing will run, so reset here
            if (inFlight.cancel()) {
                inFlight = null;
                sendButton.setText("Send");
                statusLabel.setForeground(Color.GRAY);
                statusLabel.setText("Cancelled");
            }
            return;
        }
        if (current == null) {
            return;
        }
        sendButton.setText("Cancel");
        statusLabel.setForeground(Color.GRAY);
        statusLabel.setText("Sending…");
        Environment env = workspace.active();
        Map<String, String> vars = env != null ? env.variables : Map.of();
        Request request = current;
        inFlight = SEND_RP.post(() -> {
            boolean delivered = false;
            String failure = "unexpected error";
            try {
                // already off the EDT — a first use of this request's
                // auth loads its token here, right where it's needed
                hydrateAuthNow(request);
                ApiResponse response = client.send(request, vars);
                List<TestRunner.Result> results = TestRunner.run(request, response);
                // the pretty re-parse belongs HERE: on the EDT it froze
                // the paint thread for megabyte bodies (ledger 52a)
                String display = response.reached()
                        ? WorkspaceIO.prettyForDisplay(response.body()) : null;
                delivered = true;
                SwingUtilities.invokeLater(() -> {
                    // the SENT request, not whatever is selected by now
                    recordHistory(request,
                            response.reached() ? response.status() : 0,
                            response.millis());
                    showResponse(response, results, display);
                });
            } catch (RuntimeException ex) {
                failure = String.valueOf(ex.getMessage());
                java.util.logging.Logger.getLogger(ApiClientTopComponent.class.getName())
                        .log(java.util.logging.Level.WARNING, "API send worker failed", ex);
            } finally {
                if (!delivered) {
                    // a throwing worker must never leave Send dead and the
                    // status stuck on "Sending…" until restart
                    String message = "Send failed — " + failure;
                    SwingUtilities.invokeLater(() -> {
                        recordHistory(request, 0, 0);
                        sendButton.setText("Send");
                        statusLabel.setForeground(FAIL_RED);
                        statusLabel.setText(message);
                    });
                }
            }
        });
    }

    private void showResponse(ApiResponse r, List<TestRunner.Result> results, String display) {
        sendButton.setText("Send");
        sendButton.setEnabled(true);
        // remember what Explain would send: the response actually shown
        lastResponse = r;
        lastMethod = current == null ? "GET" : current.method;
        lastUrl = current == null ? "" : current.url;
        explainButton.setEnabled(true);
        if (!r.reached()) {
            boolean cancelled = "cancelled".equals(r.error());
            statusLabel.setForeground(cancelled ? Color.GRAY : FAIL_RED);
            statusLabel.setText(cancelled ? "Cancelled  ·  " + r.millis() + "ms"
                    : "No route — " + r.error() + "  ·  " + r.millis() + "ms");
            responseBody.setText(cancelled ? "" : r.error());
            refindInBody();
        } else {
            statusLabel.setForeground(r.ok() ? OK_GREEN : FAIL_RED);
            statusLabel.setText(r.status() + "  ·  " + r.millis() + "ms  ·  " + humanBytes(r.bytes())
                    + (r.truncated() ? "  ·  body truncated at " + humanBytes(r.bytes()) : ""));
            responseBody.setText(display == null ? r.body() : display);
            refindInBody();
            responseBody.setCaretPosition(0);
        }
        StringBuilder h = new StringBuilder();
        r.headers().forEach((k, v) -> h.append(k).append(": ").append(String.join(", ", v)).append('\n'));
        responseHeaders.setText(h.toString());
        responseHeaders.setCaretPosition(0);

        testResults.removeAll();
        if (results.isEmpty()) {
            testResults.add(new JLabel("  No tests on this request."));
        }
        for (TestRunner.Result res : results) {
            JLabel line = new JLabel((res.passed() ? "  ✓  " : "  ✗  ")
                    + res.description() + "   (" + res.detail() + ")");
            line.setForeground(res.passed() ? OK_GREEN : FAIL_RED);
            testResults.add(line);
        }
        testResults.revalidate();
        testResults.repaint();

        // the security-header standards, graded on every send
        standardsPanel.removeAll();
        if (r.reached()) {
            org.nmox.studio.apiclient.api.HeaderGrader.Report report =
                    org.nmox.studio.apiclient.api.HeaderGrader.grade(r.headers());
            JLabel gradeLine = new JLabel("  Security headers grade: " + report.grade());
            gradeLine.setFont(gradeLine.getFont().deriveFont(Font.BOLD));
            gradeLine.setForeground("A".equals(report.grade()) || "B".equals(report.grade())
                    ? OK_GREEN : FAIL_RED);
            standardsPanel.add(gradeLine);
            for (var check : report.checks()) {
                String mark = switch (check.verdict()) {
                    case PASS -> "  ✓  ";
                    case WARN -> "  !  ";
                    case MISS -> "  ✗  ";
                };
                JLabel line = new JLabel(mark + check.standard() + "   — " + check.detail());
                line.setForeground(switch (check.verdict()) {
                    case PASS -> OK_GREEN;
                    case WARN -> new Color(0xE8, 0xC4, 0x4A);
                    case MISS -> FAIL_RED;
                });
                standardsPanel.add(line);
            }
        } else {
            standardsPanel.add(new JLabel("  No response — nothing to grade."));
        }
        standardsPanel.revalidate();
        standardsPanel.repaint();
    }

    // ---- tree model + selection ----

    private void rebuildTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Workspace");
        for (Collection c : workspace.collections) {
            DefaultMutableTreeNode cn = new DefaultMutableTreeNode(c);
            for (Request r : c.requests) {
                cn.add(new DefaultMutableTreeNode(r));
            }
            root.add(cn);
        }
        tree.setModel(new DefaultTreeModel(root));
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
        tree.setCellRenderer(new RequestTreeRenderer());
    }

    private void onTreeSelect() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node != null && node.getUserObject() instanceof Request r) {
            bindRequest(r);
        }
    }

    private void bindRequest(Request r) {
        current = r;
        withLoading(() -> {
            nameField.setText(r.name);
            methodCombo.setSelectedItem(r.method);
            urlField.setText(r.url);
            bodyArea.setText(r.body);
            authCombo.setSelectedItem(r.authType);
            authField.setText(r.authToken);
            paramsTable.setModel(new PairTableModel(r.params, this::touch));
            headersTable.setModel(new PairTableModel(r.headers, this::touch));
            testsTable.setModel(new TestsTableModel(r.tests, this::touch));
            TestsTableModel.install(testsTable);
        });
        hydrateAuth(r, () -> {
            if (current == r) {
                withLoading(() -> authField.setText(r.authToken));
            }
        });
    }

    /**
     * Requests whose keychain entry was consulted (or made moot by an
     * edit/migration) this session. v1.201.0: tokens hydrate LAZILY,
     * per request, on first display or use — the old bulk read at tab
     * open consulted every entry at once, which after any binary change
     * (every upgrade) meant a macOS password prompt at startup. A
     * consulted id is never re-read, so a denied prompt stays denied
     * for the session instead of re-firing on every selection.
     */
    private final java.util.Set<String> hydratedAuth
            = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Any thread → keychain read on RP → {@code onDoneEdt} on the EDT. */
    private void hydrateAuth(Request r, Runnable onDoneEdt) {
        if (r == null || hydratedAuth.contains(r.id)) {
            return;
        }
        RP.post(() -> {
            hydrateAuthNow(r);
            if (onDoneEdt != null) {
                SwingUtilities.invokeLater(onDoneEdt);
            }
        });
    }

    /**
     * Off-EDT only (keyring calls may block on OS prompts). Fills the
     * in-memory token from the keychain — but only a BLANK one, so a
     * value the user typed or an import carried is never clobbered.
     */
    private void hydrateAuthNow(Request r) {
        if (r == null || !hydratedAuth.add(r.id)) {
            return;
        }
        if (r.authToken == null || r.authToken.isBlank()) {
            String tok = org.nmox.studio.apiclient.api.ApiSecrets.read(r.id);
            if (tok != null && !tok.isEmpty()) {
                r.authToken = tok;
            }
        }
    }

    private void restoreSelection() {
        if (current == null) {
            return;
        }
        DefaultMutableTreeNode n = findNode(current);
        if (n != null) {
            tree.setSelectionPath(new TreePath(n.getPath()));
        }
    }

    /** The tree node whose user object IS {@code userObject}, else null. */
    private DefaultMutableTreeNode findNode(Object userObject) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        java.util.Enumeration<javax.swing.tree.TreeNode> en = root.depthFirstEnumeration();
        while (en.hasMoreElements()) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) en.nextElement();
            if (n.getUserObject() == userObject) {
                return n;
            }
        }
        return null;
    }

    /**
     * Repaints one node's label after its model object changed — the
     * SAFE alternative to reload()+restoreSelection() when the caller
     * may be inside a Swing notification. {@code nodeChanged} fires only
     * treeNodesChanged, so no TreeSelectionListener runs and nothing
     * re-enters the editor bindings; selection and expansion survive.
     */
    private void repaintTreeLabel(Object userObject) {
        DefaultMutableTreeNode n = findNode(userObject);
        if (n != null) {
            ((DefaultTreeModel) tree.getModel()).nodeChanged(n);
        }
    }

    /**
     * Finds and selects the request named {@code requestName} inside the
     * collection named {@code collectionName}, scrolling it into view. Used
     * by Quick Search to jump straight to a hit. Best-effort: a no-op if the
     * tree isn't built yet or nothing matches.
     */
    public void selectRequest(String collectionName, String requestName) {
        if (collectionName == null || requestName == null) {
            return;
        }
        javax.swing.tree.TreeModel model = tree.getModel();
        if (model == null || !(model.getRoot() instanceof DefaultMutableTreeNode root)) {
            return;
        }
        java.util.Enumeration<javax.swing.tree.TreeNode> en = root.depthFirstEnumeration();
        while (en.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) en.nextElement();
            if (!(node.getUserObject() instanceof Request r)) {
                continue;
            }
            if (!requestName.equals(r.name)) {
                continue;
            }
            if (node.getParent() instanceof DefaultMutableTreeNode parent
                    && parent.getUserObject() instanceof Collection c
                    && !collectionName.equals(c.name)) {
                continue;
            }
            TreePath path = new TreePath(node.getPath());
            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);
            return;
        }
    }

    // ---- CRUD ----

    private Collection selectedCollection() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node == null) {
            return workspace.collections.isEmpty() ? null : workspace.collections.get(0);
        }
        if (node.getUserObject() instanceof Collection c) {
            return c;
        }
        if (node.getUserObject() instanceof Request r) {
            for (Collection c : workspace.collections) {
                if (c.requests.contains(r)) {
                    return c;
                }
            }
        }
        return workspace.collections.isEmpty() ? null : workspace.collections.get(0);
    }

    private void addCollection() {
        Collection c = new Collection();
        c.name = "New collection";
        workspace.collections.add(c);
        rebuildTree();
        touch();
    }

    private void addRequest() {
        Collection c = selectedCollection();
        if (c == null) {
            addCollection();
            c = workspace.collections.get(workspace.collections.size() - 1);
        }
        Request r = new Request();
        c.requests.add(r);
        rebuildTree();
        current = r;
        restoreSelection();
        touch();
    }

    private void duplicateSelected() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node == null || !(node.getUserObject() instanceof Request r)) {
            status("Select a request to duplicate.");
            return;
        }
        Request copy = Request.duplicate(r);
        for (Collection c : workspace.collections) {
            int at = c.requests.indexOf(r);
            if (at >= 0) {
                c.requests.add(at + 1, copy);
                break;
            }
        }
        // the copy's auth secret goes to the keychain under ITS fresh id
        // — deliberately: duplicate-and-tweak expects working auth, and
        // the secrets law is about the FILE, not the keychain. Off the
        // EDT, keyring calls may block on OS prompts. v1.201.0: the
        // SOURCE may not be hydrated yet — load it here, in the same
        // task, so the copy carries the real token.
        final Request source = r;
        RP.post(() -> {
            hydrateAuthNow(source);
            if (source.authToken != null && !source.authToken.isEmpty()) {
                copy.authToken = source.authToken;
                org.nmox.studio.apiclient.api.ApiSecrets.save(copy.id, copy.authToken);
                hydratedAuth.add(copy.id);
            }
        });
        rebuildTree();
        current = copy;
        restoreSelection();
        touch();
        status("Duplicated as \"" + copy.name + "\".");
    }

    private void renameSelected() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node == null) {
            return;
        }
        Object obj = node.getUserObject();
        String existing = obj instanceof Collection c ? c.name
                : obj instanceof Request r ? r.name : null;
        if (existing == null) {
            return;
        }
        NotifyDescriptor.InputLine in = new NotifyDescriptor.InputLine("Name:", "Rename");
        in.setInputText(existing);
        if (DialogDisplayer.getDefault().notify(in) != NotifyDescriptor.OK_OPTION
                || in.getInputText().isBlank()) {
            return;
        }
        String name = in.getInputText().trim();
        if (obj instanceof Collection c) {
            c.name = name;
        } else {
            ((Request) obj).name = name;
        }
        rebuildTree();
        restoreSelection();
        touch();
    }

    private void deleteSelected() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node == null) {
            return;
        }
        Object obj = node.getUserObject();
        // a deleted request's auth token must leave the OS keychain with
        // it (v1.200.0, the DB Studio remove-connection parity) — the id
        // is gone from the file forever, so a kept entry is an orphaned
        // secret nothing can ever read again. Off the EDT: keyring calls
        // may block on OS prompts.
        java.util.List<String> forget = new java.util.ArrayList<>();
        if (obj instanceof Request r) {
            workspace.collections.forEach(c -> c.requests.remove(r));
            forget.add(r.id);
        } else if (obj instanceof Collection c) {
            // there is no undo here, and Delete now rides a KEY too — a
            // non-empty collection gets the v1.98.0 safe-default confirm
            // (Enter lands on No); an empty one deletes without ceremony
            if (!c.requests.isEmpty()) {
                Object answer = org.openide.DialogDisplayer.getDefault().notify(
                        new org.openide.NotifyDescriptor(
                                "Delete collection \"" + c.name + "\" and its "
                                + c.requests.size() + " request"
                                + (c.requests.size() == 1 ? "" : "s") + "?",
                                "Delete collection",
                                org.openide.NotifyDescriptor.YES_NO_OPTION,
                                org.openide.NotifyDescriptor.QUESTION_MESSAGE,
                                null, org.openide.NotifyDescriptor.NO_OPTION));
                if (answer != org.openide.NotifyDescriptor.YES_OPTION) {
                    return;
                }
            }
            c.requests.forEach(r -> forget.add(r.id));
            workspace.collections.remove(c);
        } else {
            return;
        }
        if (!forget.isEmpty()) {
            RP.post(() -> forget.forEach(
                    org.nmox.studio.apiclient.api.ApiSecrets::delete));
        }
        rebuildTree();
        touch();
    }

    private void editEnvironment() {
        Environment env = workspace.active();
        if (env == null) {
            NotifyDescriptor.InputLine name = new NotifyDescriptor.InputLine(
                    "Environment name:", "New environment");
            if (DialogDisplayer.getDefault().notify(name) != NotifyDescriptor.OK_OPTION) {
                return;
            }
            Environment fresh = new Environment();
            fresh.name = name.getInputText().isBlank() ? "env" : name.getInputText().trim();
            workspace.environments.add(fresh);
            workspace.activeEnvironment = fresh.name;
            // deferred a dispatch: a dialog opened while the previous one is
            // still disposing can stack behind the main window
            SwingUtilities.invokeLater(() -> editVariables(fresh));
            return;
        }
        editVariables(env);
    }

    private void editVariables(Environment env) {
        JTextArea area = new JTextArea(12, 40);
        area.setFont(MONO);
        StringBuilder sb = new StringBuilder();
        env.variables.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        area.setText(sb.toString());
        NotifyDescriptor d = new NotifyDescriptor(new JScrollPane(area),
                "Variables for \"" + env.name + "\"  (KEY=value per line)",
                NotifyDescriptor.OK_CANCEL_OPTION, NotifyDescriptor.PLAIN_MESSAGE, null, null);
        if (DialogDisplayer.getDefault().notify(d) == NotifyDescriptor.OK_OPTION) {
            env.variables.clear();
            for (String line : area.getText().split("\n")) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    env.variables.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            }
            touch();
        }
    }

    // ---- persistence ----

    private File projectDirOrNull() {
        // soft dependency by lookup (ledger 30): a null provider means the
        // rack is absent (plain tests, stripped platform) — no aim to read
        org.nmox.studio.core.spi.ProjectAim aim =
                org.nmox.studio.core.spi.ProjectAim.find();
        if (aim != null) {
            File dir = aim.projectDir();
            if (dir != null && dir.isDirectory()) {
                return dir;
            }
        }
        return null;
    }

    private File projectDir() {
        File dir = projectDirOrNull();
        return dir != null ? dir : new File(System.getProperty("user.home"));
    }

    /** The dir this workspace is bound to — saves and the pulse aim here. */
    private File boundDir() {
        return rebind.boundDir();
    }

    /** Disk half of a load — safe off the EDT. */
    private Workspace readWorkspace(File dir) {
        try {
            WorkspaceIO.LoadOutcome outcome = WorkspaceIO.loadGuarded(dir);
            if (outcome.backup() != null) {
                // corrupt file: the IO layer copied it aside BEFORE handing us
                // the empty fallback (the next autosave can't clobber it) — say so
                File backup = outcome.backup();
                SwingUtilities.invokeLater(() -> balloon(
                        "Couldn't read " + WorkspaceIO.FILENAME + " — starting empty",
                        "The unreadable original was kept at " + backup.getName() + ".",
                        false, null));
            }
            return outcome.workspace() != null ? outcome.workspace() : Workspace.starter();
        } catch (Exception ex) {
            return Workspace.starter();
        }
    }

    /** (Re)loads the bound project's workspace in place — EDT. */
    private void loadWorkspace() {
        // the read must see every queued write — a reload racing a save
        // still in flight on the lane must not resurrect the older file
        // (bounded ms drain; see SaveLane.flush)
        SAVES.flush(5, java.util.concurrent.TimeUnit.SECONDS);
        applyWorkspace(readWorkspace(boundDir()), boundDir());
    }

    /** EDT half of a load: binds a read workspace to the UI. */
    private void applyWorkspace(Workspace loaded, File dir) {
        workspace = loaded;
        current = null;
        loading = true;
        try {
            // A response belongs to the project it came from. Before
            // v1.171.0 keeping it across a re-aim was merely stale display;
            // with Explain… it became a disclosure path — the button would
            // still be armed with the PREVIOUS project's response body while
            // the user works in a new one. Clear it with the workspace.
            clearResponse();
            // hydration state belongs to the workspace whose ids it names
            hydratedAuth.clear();
            selfWrites.noteSync(new File(dir, WorkspaceIO.FILENAME));
            refreshHistory();
            DefaultComboBoxModel<String> envs = new DefaultComboBoxModel<>();
            workspace.environments.forEach(e -> envs.addElement(e.name));
            envCombo.setModel(envs);
            if (!workspace.activeEnvironment.isEmpty()) {
                envCombo.setSelectedItem(workspace.activeEnvironment);
            }
            rebuildTree();
        } finally {
            loading = false;
        }
        // select the first request so the editor isn't blank
        if (!workspace.collections.isEmpty() && !workspace.collections.get(0).requests.isEmpty()) {
            current = workspace.collections.get(0).requests.get(0);
            restoreSelection();
        }
        reconcileSecrets(workspace);
    }

    /**
     * Off-EDT keychain reconciliation for a freshly loaded workspace
     * (v1.97.0): each request's real auth token lives in the OS keychain
     * keyed by its id. A pre-v1.97.0 file carries a plaintext {@code
     * authToken} that {@link WorkspaceIO} left on the model as a
     * migration carrier — move it into the keychain and rewrite the file
     * without it. Every keyring call is off the EDT (it may block on OS
     * calls); the shown request's field is refreshed back on the EDT.
     */
    private void reconcileSecrets(Workspace ws) {
        java.util.List<Request> all = new java.util.ArrayList<>();
        for (var c : ws.collections) {
            all.addAll(c.requests);
        }
        RP.post(() -> {
            boolean migrated = false;
            for (Request r : all) {
                if (r.authToken != null && !r.authToken.isEmpty()) {
                    org.nmox.studio.apiclient.api.ApiSecrets.save(r.id, r.authToken);
                    migrated = true; // legacy plaintext just moved to the keychain
                    // the in-memory value IS the truth now — no read needed
                    hydratedAuth.add(r.id);
                }
                // v1.201.0: everyone else hydrates LAZILY on first
                // display/use — the old bulk read here consulted every
                // keychain entry at tab open, firing a macOS password
                // prompt at startup after any binary change (upgrades)
            }
            final boolean rewrite = migrated;
            SwingUtilities.invokeLater(() -> {
                if (rewrite && workspace == ws) {
                    save(); // rewrites .nmoxapi.json without any authToken
                }
                if (current != null && authField != null) {
                    withLoading(() -> authField.setText(current.authToken));
                }
            });
        });
    }

    private void touch() {
        if (!loading) {
            saveDebounce.restart();
        }
    }

    /** Save-lane-thread confined — only {@link #writeSnapshot} touches it. */
    private boolean saveFailureNotified;

    /**
     * EDT: snapshots the workspace to JSON here (the model is
     * EDT-confined) and hands the write to the save lane — the EDT never
     * touches the disk (debt #16). The target binds NOW to the bound
     * dir, not the live rack aim: during a re-aim the debounced edits
     * still belong to the project they were made in.
     */
    private void save() {
        File target = new File(boundDir(), WorkspaceIO.FILENAME);
        String json = WorkspaceIO.toJson(workspace);
        // Snapshot id->token on the EDT (the model is EDT-confined), push
        // to the keychain off-EDT on the same lane BEFORE the file write,
        // so the secret is durable before the tokenless JSON lands.
        java.util.List<String[]> secrets = new java.util.ArrayList<>();
        for (var c : workspace.collections) {
            for (Request r : c.requests) {
                // only requests whose token this session actually loaded
                // or edited — pushing a never-hydrated request would
                // overwrite its keychain entry with "" (v1.201.0: with
                // lazy hydration, most requests are never consulted)
                if (hydratedAuth.contains(r.id)
                        || (r.authToken != null && !r.authToken.isEmpty())) {
                    secrets.add(new String[]{r.id, r.authToken == null ? "" : r.authToken});
                }
            }
        }
        SAVES.save(() -> {
            for (String[] s : secrets) {
                org.nmox.studio.apiclient.api.ApiSecrets.save(s[0], s[1]);
            }
            writeSnapshot(target, json);
        });
    }

    /**
     * Save lane only: the write and its self-stamp are ONE task, so a
     * pulse verdict (also lane-ordered — see SaveLane.classify) can
     * never observe the write without the stamp.
     */
    private void writeSnapshot(File target, String json) {
        try {
            org.nmox.studio.core.util.AtomicFiles.writeString(target.toPath(), json);
            selfWrites.noteSync(target);
            saveFailureNotified = false;
        } catch (Exception ex) {
            // a failed autosave never interrupts editing — but a chronically
            // failing one must not lose work silently: warn once per streak
            java.util.logging.Logger.getLogger(ApiClientTopComponent.class.getName())
                    .log(java.util.logging.Level.WARNING, "API workspace autosave failed", ex);
            if (!saveFailureNotified) {
                saveFailureNotified = true;
                org.openide.awt.NotificationDisplayer.getDefault().notify(
                        "API Studio can't save its workspace",
                        javax.swing.UIManager.getIcon("OptionPane.warningIcon"),
                        "Changes are not being persisted: " + ex.getMessage(),
                        null);
            }
        }
    }

    private void attachRackListener() {
        org.nmox.studio.core.spi.ProjectAim aim =
                org.nmox.studio.core.spi.ProjectAim.find();
        if (aim == null) {
            return; // rack absent (plain tests): no project switches to follow
        }
        aim.addListener(rackListener);
        rackListenerAttached = true;
    }

    /** The initial load happened; re-opens rely on onProjectReaimed instead. */
    private boolean loadedOnce;

    @Override
    public void componentOpened() {
        if (!loadedOnce) {
            // first open after construction: load the bound project's file
            // exactly once. onProjectReaimed can't do it — its equality
            // guard sees the ctor-time aim as "already bound" and skips.
            // Must run before the serving bridge attaches: its offers read
            // the workspace.
            loadedOnce = true;
            loadWorkspace();
        }
        if (!rackListenerAttached) {
            attachRackListener();
        }
        if (servingBridge == null) {
            org.nmox.studio.core.spi.LiveServings servings =
                    org.nmox.studio.core.spi.LiveServings.find();
            if (servings != null) {
                servingBridge = new org.nmox.studio.apiclient.api.ServingBridge(
                        servings, this::onServings);
            }
            // null: rack absent (plain tests, stripped platform) — no
            // offers, no loss
        }
        if (servingBridge != null) {
            servingBridge.attach();
        }
        // a server already running when the tab opens is seen too
        pokeServingBridge();
        restartFilePulse();
        onProjectReaimed(); // the aim may have moved while the tab was closed
    }

    @Override
    public void componentClosed() {
        if (servingBridge != null) {
            servingBridge.detach();
        }
        if (rackListenerAttached) {
            org.nmox.studio.core.spi.ProjectAim aim =
                    org.nmox.studio.core.spi.ProjectAim.find();
            if (aim != null) {
                aim.removeListener(rackListener);
            }
            rackListenerAttached = false;
        }
        if (filePulse != null) {
            filePulse.stop();
            filePulse = null;
        }
        // Save ONLY when edits are pending (debounce armed = dirty; the
        // onProjectReaimed idiom). The old unconditional save round-
        // tripped the file through the unknown-key-dropping parser, so
        // a no-op open/close of the tab silently erased any fields a
        // NEWER NMOX version had written into .nmoxapi.json.
        boolean dirty = saveDebounce.isRunning();
        saveDebounce.stop();
        if (dirty) {
            save();
            // the queued write must land before the window system forgets
            // us — a bounded synchronous drain is the simplest correct
            // close flush (the lane only carries ms-scale local writes)
            SAVES.flush(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    // ---- following the rack's aim ----

    /**
     * EDT, from every rack projectChanged (and from componentOpened, to
     * catch aims that moved while the tab was closed). Until v1.35.0 the
     * workspace and pulse bound at componentOpened only, so a mid-session
     * re-aim left API Studio on the old project — this closes that gap.
     * The storm laws live in the tested {@link
     * org.nmox.studio.apiclient.api.ProjectRebind} core: re-aiming to the
     * same dir is a no-op, an event storm for one aim is one load, the
     * newest aim always wins. Edits pending in the debounce belong to the
     * OLD project, so they force-save there first (the componentClosed
     * semantics — save() targets the bound dir); the new workspace reads
     * off the EDT and binds back on it; the pulse restarts on the new
     * file with fresh self-write stamps; and the serving bridge refreshes
     * so the {{baseUrl}} offer re-evaluates for the new project (its
     * guard keys include the project dir).
     */
    private void onProjectReaimed() {
        File aimed = projectDir();
        if (!rebind.shouldLoad(aimed)) {
            return; // same aim, or this load is already in flight
        }
        if (saveDebounce.isRunning()) {
            saveDebounce.stop();
            save(); // boundDir() is still the old project's — see save()
            // an A→B→A bounce reads A's file below: the read must see the
            // write we just queued, so drain the lane before dispatching it
            SAVES.flush(5, java.util.concurrent.TimeUnit.SECONDS);
        }
        RP.post(() -> {
            Workspace loaded = readWorkspace(aimed);
            SwingUtilities.invokeLater(() -> {
                if (!rebind.shouldApply(aimed, projectDir())) {
                    return; // re-aimed again while reading — the newer aim wins
                }
                applyWorkspace(loaded, aimed);
                restartFilePulse();
                pokeServingBridge();
            });
        });
    }

    /** (Re)aims the pulse at the bound project's file; closed tab, no pulse. */
    private void restartFilePulse() {
        if (filePulse != null) {
            filePulse.stop();
            filePulse = null;
        }
        if (!isOpened()) {
            return;
        }
        filePulse = new org.nmox.studio.apiclient.api.WorkspaceFilePulse(
                new File(boundDir(), WorkspaceIO.FILENAME),
                this::onWorkspaceFileChanged);
        filePulse.start(
                org.nmox.studio.apiclient.api.WorkspaceFilePulse.DEFAULT_INTERVAL_MS);
    }

    /** A fresh registry snapshot, taken off the EDT — offers re-evaluate. */
    private void pokeServingBridge() {
        org.nmox.studio.apiclient.api.ServingBridge bridge = servingBridge;
        if (bridge == null) {
            return;
        }
        RP.post(bridge::refresh);
    }

    // ---- the {{baseUrl}} offer ----

    /** EDT, with a fresh registry snapshot: at most one balloon per (url+project). */
    private void onServings(java.util.List<org.nmox.studio.core.spi.LiveServings.Serving> servings) {
        org.nmox.studio.apiclient.api.BaseUrlOffer.Offer offer =
                org.nmox.studio.apiclient.api.BaseUrlOffer.shouldOffer(
                        servings, projectDirOrNull(), workspace, offeredBaseUrls);
        if (offer == null) {
            return;
        }
        offeredBaseUrls.add(offer.guardKey()); // offered is offered, accepted or not
        String detail = offer.createEnvironment()
                ? "Click to create environment \"" + offer.envName()
                        + "\" with {{" + offer.key() + "}} set"
                : "Click to set {{" + offer.key() + "}} in \"" + offer.envName() + "\"";
        balloon("A server is running at " + offer.url(), detail, true,
                e -> applyOffer(offer));
    }

    /** EDT, on balloon click: writes through the model and the normal save path. */
    private void applyOffer(org.nmox.studio.apiclient.api.BaseUrlOffer.Offer offer) {
        if (offer.createEnvironment()) {
            if (!workspace.environments.isEmpty()) {
                return; // environments appeared since the offer — don't second-guess them
            }
            Environment fresh = new Environment();
            fresh.name = offer.envName();
            fresh.variables.put(offer.key(), offer.url());
            workspace.environments.add(fresh);
            workspace.activeEnvironment = fresh.name;
            withLoading(() -> {
                DefaultComboBoxModel<String> envs = new DefaultComboBoxModel<>();
                workspace.environments.forEach(env -> envs.addElement(env.name));
                envCombo.setModel(envs);
                envCombo.setSelectedItem(fresh.name);
            });
        } else {
            Environment env = null;
            for (Environment candidate : workspace.environments) {
                if (candidate.name.equals(offer.envName())) {
                    env = candidate;
                }
            }
            if (env == null || org.nmox.studio.apiclient.api.BaseUrlOffer.hasBaseUrl(env)) {
                return; // renamed or set by hand since the offer — never clobber
            }
            env.variables.put(offer.key(), offer.url());
        }
        touch();
    }

    // ---- foreign .nmoxapi.json edits ----

    /**
     * Pulse thread: the workspace file's stamp moved. Our own saves are
     * filtered by the tracker, and the authoritative re-check rides the
     * save lane — it queues BEHIND any write+stamp pair the tick may
     * have raced, so a save mid-landing never counts as foreign (writes
     * used to run on the EDT, where the EDT re-check alone sufficed;
     * off-EDT writes need the lane ordering — debt #16). Edits pending
     * in the save debounce are never clobbered silently — those get the
     * Reload? balloon instead.
     */
    private void onWorkspaceFileChanged(long mtime, long size) {
        if (!selfWrites.isForeign(mtime, size)) {
            return;
        }
        SAVES.classify(() -> {
            if (!selfWrites.isForeign(mtime, size)) {
                return; // our own save, caught mid-landing by the tick
            }
            SwingUtilities.invokeLater(() -> onForeignWorkspaceEdit(mtime, size));
        });
    }

    /** EDT, after the lane-ordered verdict said "foreign": react once. */
    private void onForeignWorkspaceEdit(long mtime, long size) {
        if (!selfWrites.isForeign(mtime, size)) {
            return; // our save landed between the verdict and this dispatch
        }
        if (saveDebounce.isRunning()) {
            balloon(WorkspaceIO.FILENAME + " changed on disk — Reload?",
                    "You have unsaved edits; click to reload from disk and discard them",
                    false, e -> {
                        saveDebounce.stop();
                        loadWorkspace();
                    });
        } else {
            loadWorkspace();
            balloon("Reloaded " + WorkspaceIO.FILENAME,
                    "Picked up changes made outside the studio", true, null);
        }
    }

    /** Balloons, the Contract Studio shape — plus an optional click action. */
    private static void balloon(String title, String detail, boolean ok,
            java.awt.event.ActionListener action) {
        javax.swing.Icon icon = javax.swing.UIManager.getIcon(
                ok ? "OptionPane.informationIcon" : "OptionPane.warningIcon");
        org.openide.awt.NotificationDisplayer.getDefault().notify(
                title, icon, detail == null ? "" : detail, action,
                ok ? org.openide.awt.NotificationDisplayer.Priority.LOW
                        : org.openide.awt.NotificationDisplayer.Priority.NORMAL);
    }

    private static String humanBytes(long b) {
        return b >= 1_000_000 ? String.format("%.1f MB", b / 1_000_000.0)
                : b >= 1_000 ? (b / 1_000) + " KB" : b + " B";
    }

    /** A one-liner document listener that runs a callback on any edit. */
    private static final class SimpleDoc implements javax.swing.event.DocumentListener {
        private final Runnable onChange;

        SimpleDoc(Runnable onChange) {
            this.onChange = onChange;
        }

        @Override
        public void insertUpdate(javax.swing.event.DocumentEvent e) {
            onChange.run();
        }

        @Override
        public void removeUpdate(javax.swing.event.DocumentEvent e) {
            onChange.run();
        }

        @Override
        public void changedUpdate(javax.swing.event.DocumentEvent e) {
            onChange.run();
        }
    }
}
