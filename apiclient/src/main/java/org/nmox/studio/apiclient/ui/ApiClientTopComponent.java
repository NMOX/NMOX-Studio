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

    private final JTable paramsTable = new JTable();
    private final JTable headersTable = new JTable();
    private final JTextArea bodyArea = new JTextArea();
    private final JComboBox<AuthType> authCombo = new JComboBox<>(AuthType.values());
    // A JPasswordField, not a plaintext JTextField (v1.97.0): the token
    // is a secret, so it neither echoes on screen nor is written to the
    // committable .nmoxapi.json — it lives in the OS keychain.
    private final javax.swing.JPasswordField authField = new javax.swing.JPasswordField();
    private final JTable testsTable = new JTable();

    private final JLabel statusLabel = new JLabel(" ");
    private final JTextArea responseBody = new JTextArea();
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
        return bar;
    }

    // ---- left: collections tree ----

    private JPanel buildTree() {
        JPanel panel = new JPanel(new BorderLayout());
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setRootVisible(false);
        tree.addTreeSelectionListener(e -> onTreeSelect());
        panel.add(new JScrollPane(tree), BorderLayout.CENTER);

        JToolBar tools = new JToolBar();
        tools.setFloatable(false);
        JButton addCol = new JButton("+ Collection");
        addCol.addActionListener(e -> addCollection());
        JButton addReq = new JButton("+ Request");
        addReq.addActionListener(e -> addRequest());
        JButton del = new JButton("Delete");
        del.addActionListener(e -> deleteSelected());
        tools.add(addCol);
        tools.add(addReq);
        tools.add(del);
        panel.add(tools, BorderLayout.SOUTH);
        return panel;
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
                ((DefaultTreeModel) tree.getModel()).reload();
                restoreSelection();
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
        panel.add(statusLabel, BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        responseBody.setEditable(false);
        responseBody.setFont(MONO);
        tabs.addTab("Body", new JScrollPane(responseBody));
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
                ApiResponse response = client.send(request, vars);
                List<TestRunner.Result> results = TestRunner.run(request, response);
                // the pretty re-parse belongs HERE: on the EDT it froze
                // the paint thread for megabyte bodies (ledger 52a)
                String display = response.reached()
                        ? WorkspaceIO.prettyForDisplay(response.body()) : null;
                delivered = true;
                SwingUtilities.invokeLater(() -> showResponse(response, results, display));
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
        if (!r.reached()) {
            boolean cancelled = "cancelled".equals(r.error());
            statusLabel.setForeground(cancelled ? Color.GRAY : FAIL_RED);
            statusLabel.setText(cancelled ? "Cancelled  ·  " + r.millis() + "ms"
                    : "No route — " + r.error() + "  ·  " + r.millis() + "ms");
            responseBody.setText(cancelled ? "" : r.error());
        } else {
            statusLabel.setForeground(r.ok() ? OK_GREEN : FAIL_RED);
            statusLabel.setText(r.status() + "  ·  " + r.millis() + "ms  ·  " + humanBytes(r.bytes())
                    + (r.truncated() ? "  ·  body truncated at " + humanBytes(r.bytes()) : ""));
            responseBody.setText(display == null ? r.body() : display);
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
        loading = true;
        current = r;
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
        loading = false;
    }

    private void restoreSelection() {
        if (current == null) {
            return;
        }
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        java.util.Enumeration<javax.swing.tree.TreeNode> en = root.depthFirstEnumeration();
        while (en.hasMoreElements()) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) en.nextElement();
            if (n.getUserObject() == current) {
                tree.setSelectionPath(new TreePath(n.getPath()));
                return;
            }
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

    private void deleteSelected() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node == null) {
            return;
        }
        Object obj = node.getUserObject();
        if (obj instanceof Request r) {
            workspace.collections.forEach(c -> c.requests.remove(r));
        } else if (obj instanceof Collection c) {
            workspace.collections.remove(c);
        } else {
            return;
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
        loading = true;
        workspace = loaded;
        current = null;
        selfWrites.noteSync(new File(dir, WorkspaceIO.FILENAME));
        DefaultComboBoxModel<String> envs = new DefaultComboBoxModel<>();
        workspace.environments.forEach(e -> envs.addElement(e.name));
        envCombo.setModel(envs);
        if (!workspace.activeEnvironment.isEmpty()) {
            envCombo.setSelectedItem(workspace.activeEnvironment);
        }
        rebuildTree();
        loading = false;
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
                } else {
                    r.authToken = org.nmox.studio.apiclient.api.ApiSecrets.read(r.id);
                }
            }
            final boolean rewrite = migrated;
            SwingUtilities.invokeLater(() -> {
                if (rewrite && workspace == ws) {
                    save(); // rewrites .nmoxapi.json without any authToken
                }
                if (current != null && authField != null) {
                    loading = true;
                    authField.setText(current.authToken);
                    loading = false;
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
                secrets.add(new String[]{r.id, r.authToken == null ? "" : r.authToken});
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
            loading = true;
            DefaultComboBoxModel<String> envs = new DefaultComboBoxModel<>();
            workspace.environments.forEach(env -> envs.addElement(env.name));
            envCombo.setModel(envs);
            envCombo.setSelectedItem(fresh.name);
            loading = false;
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
