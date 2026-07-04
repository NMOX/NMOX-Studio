package org.nmox.studio.dbstudio.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.nmox.studio.dbstudio.engine.DbBackend;
import org.nmox.studio.dbstudio.engine.Passwords;
import org.nmox.studio.dbstudio.engine.QueryResult;
import org.nmox.studio.dbstudio.io.DbWorkspaceIO;
import org.nmox.studio.dbstudio.model.ColumnInfo;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.nmox.studio.dbstudio.model.TableInfo;
import org.nmox.studio.dbstudio.search.DbSearchProvider;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.windows.TopComponent;

/**
 * DB Studio: the database-management tab. Connections on the left —
 * each expanding lazily into its containers (tables/views on SQL
 * engines, collections on MongoDB, databases on CouchDB) and their
 * columns — a console on the right whose language follows the active
 * connection's engine family (SQL highlighting for JDBC engines, JSON
 * for document engines), and tabbed results below: one grid or message
 * per executed statement plus a History tab of the last 50 runs.
 *
 * <p>The connection list persists as {@code .nmoxdb.json} beside the
 * aimed project, exactly like the rack patch, the infra design, and the
 * API workspace — and by construction it never carries a password
 * (those live in the OS keychain via {@link Passwords}).
 *
 * <p>Threading: every engine call ({@code open/close/test/
 * listContainers/columns/runConsole}) happens on the module's
 * {@link RequestProcessor}; results are marshalled back with
 * {@code SwingUtilities.invokeLater}, mirroring the engine's "never on
 * the EDT" contract.
 */
@TopComponent.Description(preferredID = "DbStudioTopComponent",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS)
@TopComponent.Registration(mode = "editor", openAtStartup = true, position = 250)
@ActionID(category = "Window", id = "org.nmox.studio.dbstudio.ui.DbStudioTopComponent")
@org.openide.awt.ActionReferences({
    @ActionReference(path = "Menu/Window", position = 258),
    @ActionReference(path = "Shortcuts", name = "DS-7")
})
@TopComponent.OpenActionRegistration(displayName = "#CTL_DbStudioAction",
        preferredID = "DbStudioTopComponent")
@Messages({
    "CTL_DbStudioAction=DB Studio",
    "CTL_DbStudioTopComponent=DB Studio",
    "HINT_DbStudioTopComponent=Database management: connections, schema tree, SQL/document console"
})
public final class DbStudioTopComponent extends TopComponent {

    /** The module's worker pool — all engine calls run here, never on the EDT. */
    static final RequestProcessor RP = new RequestProcessor("DB Studio", 3);

    private static final Color OK_GREEN = new Color(0x4E, 0xC9, 0x8B);
    private static final Color FAIL_RED = new Color(0xE2, 0x4B, 0x4A);
    private static final Color ACCENT = new Color(0x1D, 0x9E, 0x75);
    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final List<ConnectionSpec> specs = new ArrayList<>();
    /** At most one live backend per spec id; entries leave on disconnect/remove/close. */
    private final Map<String, DbBackend> backends = new ConcurrentHashMap<>();
    /** Containers the tree has fetched, by spec id — feeds Quick Search, never re-fetched there. */
    private final Map<String, List<TableInfo>> containerCache = new ConcurrentHashMap<>();
    private final Set<String> connecting = ConcurrentHashMap.newKeySet();
    private final Set<Object> loadingNodes = ConcurrentHashMap.newKeySet();
    private final ConsoleHistory history = new ConsoleHistory();

    private final JTree tree = new JTree();
    private final JButton editButton = new JButton("Edit");
    private final JButton removeButton = new JButton("Remove");
    private final JButton testButton = new JButton("Test");
    private final JButton connectButton = new JButton("Connect");

    private final JEditorPane console = new JEditorPane();
    private final JButton runButton = new JButton("RUN");
    private final JButton cancelButton = new JButton("Cancel");
    private final JSpinner limitSpinner = new JSpinner(new SpinnerNumberModel(200, 1, 1_000_000, 100));
    private final JLabel statusLabel = new JLabel(" ");

    private final JTabbedPane resultsTabs = new JTabbedPane();
    private final DefaultListModel<ConsoleHistory.Entry> historyModel = new DefaultListModel<>();
    private final JList<ConsoleHistory.Entry> historyList = new JList<>(historyModel);

    private final org.nmox.studio.rack.model.Rack.Listener rackListener;
    private boolean rackListenerAttached;
    private String activeSpecId;
    /** True while a console run is in flight; gates RUN and re-entry. */
    private boolean running;
    private String consoleMime = "";

    public DbStudioTopComponent() {
        setName(Bundle.CTL_DbStudioTopComponent());
        setToolTipText(Bundle.HINT_DbStudioTopComponent());
        setLayout(new BorderLayout());

        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                buildConsolePanel(), buildResultsPanel());
        right.setDividerLocation(240);
        JSplitPane center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildConnectionsPanel(), right);
        center.setDividerLocation(280);
        add(center, BorderLayout.CENTER);

        rackListener = new org.nmox.studio.rack.model.Rack.Listener() {
            @Override
            public void projectChanged() {
                SwingUtilities.invokeLater(DbStudioTopComponent.this::reloadWorkspace);
            }
        };
        attachRackListener();
        reloadWorkspace();
    }

    // ---- left: connections tree + toolbar ----

    private JPanel buildConnectionsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new DbTreeRenderer());
        tree.addTreeSelectionListener(e -> onTreeSelect());
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) {
                DefaultMutableTreeNode node =
                        (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
                Object userObject = node.getUserObject();
                if (userObject instanceof ConnectionSpec spec && hasPlaceholder(node)) {
                    connect(spec, node, false);
                } else if (userObject instanceof TableInfo info && hasPlaceholder(node)) {
                    ConnectionSpec spec = specOf(node);
                    if (spec != null) {
                        loadColumns(spec, info, node);
                    }
                }
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {
            }
        });
        // double-click a table/collection/database → peek at its data: fill
        // the console with an engine-appropriate query and run it. The same
        // gesture every serious DB tool honors; without it the tree feels dead.
        tree.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.getClickCount() != 2) {
                    return;
                }
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null) {
                    return;
                }
                DefaultMutableTreeNode node =
                        (DefaultMutableTreeNode) path.getLastPathComponent();
                if (node.getUserObject() instanceof TableInfo info) {
                    tree.setSelectionPath(path);
                    peek(info);
                }
            }
        });
        panel.add(new JScrollPane(tree), BorderLayout.CENTER);

        JPanel tools = new JPanel(new java.awt.GridLayout(2, 1));
        JToolBar row1 = new JToolBar();
        row1.setFloatable(false);
        JButton addButton = new JButton("Add");
        addButton.addActionListener(e -> addConnection());
        editButton.addActionListener(e -> editSelected());
        removeButton.addActionListener(e -> removeSelected());
        row1.add(addButton);
        row1.add(editButton);
        row1.add(removeButton);
        JToolBar row2 = new JToolBar();
        row2.setFloatable(false);
        testButton.addActionListener(e -> testSelected());
        connectButton.addActionListener(e -> toggleConnect());
        row2.add(testButton);
        row2.add(connectButton);
        tools.add(row1);
        tools.add(row2);
        panel.add(tools, BorderLayout.SOUTH);
        refreshActions();
        return panel;
    }

    // ---- right top: the console ----

    private JPanel buildConsolePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        runButton.setForeground(ACCENT);
        runButton.setToolTipText("Execute the console against the active connection");
        runButton.addActionListener(e -> run());
        bar.add(runButton);
        bar.addSeparator();
        bar.add(new JLabel(" Limit: "));
        limitSpinner.setMaximumSize(limitSpinner.getPreferredSize());
        limitSpinner.setToolTipText("Fetch at most this many rows per result set");
        bar.add(limitSpinner);
        bar.addSeparator();
        cancelButton.setEnabled(false);
        cancelButton.setToolTipText("Best-effort cancel of the running statement");
        cancelButton.addActionListener(e -> cancel());
        bar.add(cancelButton);
        bar.addSeparator();
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        bar.add(statusLabel);
        panel.add(bar, BorderLayout.NORTH);

        console.setFont(MONO);
        panel.add(new JScrollPane(console), BorderLayout.CENTER);
        return panel;
    }

    // ---- right bottom: results + history ----

    private JComponent buildResultsPanel() {
        historyList.setCellRenderer(new HistoryRenderer());
        historyList.setToolTipText("Double-click an entry to load it back into the console");
        historyList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int index = historyList.locationToIndex(e.getPoint());
                if (e.getClickCount() == 2 && index >= 0) {
                    console.setText(historyModel.get(index).text());
                    status("Loaded from history", Color.GRAY);
                }
            }
        });
        // History stays the LAST tab; result tabs are inserted before it per run
        resultsTabs.addTab("History", new JScrollPane(historyList));
        return resultsTabs;
    }

    // ---- running the console ----

    /**
     * Double-click peek: put an engine-appropriate preview query for the
     * container into the console and run it. For a CouchDB database node
     * that the spec isn't aimed at, fill the console but explain instead of
     * silently querying the wrong database.
     */
    private void peek(TableInfo info) {
        ConnectionSpec spec = selectedConnection();
        if (spec == null || running) {
            return;
        }
        activeSpecId = spec.id();
        applyConsoleMime(spec.engine());
        int limit = (Integer) limitSpinner.getValue();
        console.setText(PeekQueries.consoleTextFor(spec.engine(), info, limit));
        if (PeekQueries.runnableAgainst(spec, info)) {
            run();
        } else {
            status("Console queries \"" + spec.database() + "\" — Edit the connection's "
                    + "database to \"" + info.name() + "\" to query it", FAIL_RED);
        }
    }

    private void run() {
        if (running) {
            return;
        }
        ConnectionSpec spec = activeSpec();
        if (spec == null) {
            status("Select a connection first", FAIL_RED);
            return;
        }
        String text = console.getText();
        if (ConsoleMimes.isPlaceholderOrBlank(text)) {
            status("Nothing to run", FAIL_RED);
            return;
        }
        int limit = (Integer) limitSpinner.getValue();
        running = true;
        refreshActions();
        cancelButton.setEnabled(true);
        status("Running…", Color.GRAY);
        long started = System.currentTimeMillis();
        RP.post(() -> {
            DbBackend backend = backendFor(spec);
            String openError = backend.isOpen() ? null : backend.open();
            List<QueryResult> results = openError == null
                    ? backend.runConsole(text, limit)
                    : List.of(new QueryResult(List.of(), List.of(), 0, -1, false, 0,
                            openError, text));
            long totalMs = System.currentTimeMillis() - started;
            SwingUtilities.invokeLater(() -> {
                running = false;
                cancelButton.setEnabled(false);
                history.add(text, spec.engine().displayName(), System.currentTimeMillis());
                refreshHistory();
                showResults(results, totalMs);
                refreshActions(); // the run may have opened the connection
                tree.repaint();
            });
        });
    }

    private void cancel() {
        ConnectionSpec spec = activeSpec();
        DbBackend backend = spec == null ? null : backends.get(spec.id());
        if (backend != null) {
            RP.post(backend::cancel); // RP has spare threads while a run blocks one
            status("Cancel requested…", Color.GRAY);
        }
    }

    private void showResults(List<QueryResult> results, long totalMs) {
        while (resultsTabs.getTabCount() > 1) {
            resultsTabs.removeTabAt(0); // everything but the trailing History tab
        }
        int failed = 0;
        for (int i = 0; i < results.size(); i++) {
            QueryResult result = results.get(i);
            if (result.isError()) {
                failed++;
            }
            resultsTabs.insertTab(tabTitle(i, result), null, resultTab(result),
                    result.statement(), resultsTabs.getTabCount() - 1);
        }
        if (!results.isEmpty()) {
            resultsTabs.setSelectedIndex(0);
        }
        String summary = results.size() + (results.size() == 1 ? " statement" : " statements")
                + (failed > 0 ? " · " + failed + " failed" : "")
                + " · " + totalMs + " ms";
        status(summary, failed > 0 ? FAIL_RED : OK_GREEN);
    }

    private static String tabTitle(int index, QueryResult result) {
        String base = "#" + (index + 1);
        if (result.isError()) {
            return base + " ✗";
        }
        if (result.isResultSet()) {
            return base + " · " + result.rowCount() + (result.truncated() ? "+" : "") + " rows";
        }
        return base + " · " + result.updateCount() + " updated";
    }

    private JComponent resultTab(QueryResult result) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel header = new JLabel(headerText(result));
        header.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        if (result.isError()) {
            header.setForeground(FAIL_RED);
        }
        panel.add(header, BorderLayout.NORTH);
        if (result.isResultSet()) {
            JTable table = new JTable(new ResultsTableModel(result));
            table.setFont(MONO);
            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); // wide results scroll, not squash
            panel.add(new JScrollPane(table), BorderLayout.CENTER);
        } else {
            JTextArea message = new JTextArea(result.isError()
                    ? result.error()
                    : result.updateCount() + " row(s) affected");
            message.setEditable(false);
            message.setFont(MONO);
            message.setLineWrap(true);
            message.setWrapStyleWord(true);
            if (result.isError()) {
                message.setForeground(FAIL_RED);
            }
            panel.add(new JScrollPane(message), BorderLayout.CENTER);
        }
        return panel;
    }

    private static String headerText(QueryResult result) {
        String statement = result.statement() == null ? ""
                : result.statement().replaceAll("\\s+", " ").trim();
        if (statement.length() > 80) {
            statement = statement.substring(0, 77) + "…";
        }
        StringBuilder sb = new StringBuilder(statement);
        sb.append("   ·   ").append(result.elapsedMs()).append(" ms");
        if (result.isResultSet() && result.truncated()) {
            sb.append("   ·   first ").append(result.rowCount()).append(" rows only");
        }
        return sb.toString();
    }

    private void refreshHistory() {
        historyModel.clear();
        history.entries().forEach(historyModel::addElement);
    }

    // ---- the console follows the active connection's engine ----

    private void applyConsoleMime(DbEngine engine) {
        String mime = ConsoleMimes.mimeFor(engine.kind());
        String text = console.getText();
        boolean replaceable = ConsoleMimes.isPlaceholderOrBlank(text);
        if (!mime.equals(consoleMime)) {
            try {
                console.setEditorKit(org.openide.text.CloneableEditorSupport.getEditorKit(mime));
            } catch (RuntimeException | LinkageError kitUnavailable) {
                // stripped platform / tests: monospaced plain text still works
                console.setContentType("text/plain");
            }
            consoleMime = mime;
            console.setFont(MONO);
            console.setText(replaceable ? ConsoleMimes.placeholderFor(engine) : text);
        } else if (replaceable) {
            console.setText(ConsoleMimes.placeholderFor(engine));
        }
    }

    // ---- connect / disconnect / test ----

    private void toggleConnect() {
        ConnectionSpec spec = selectedConnection();
        if (spec == null) {
            return;
        }
        DbBackend backend = backends.get(spec.id());
        DefaultMutableTreeNode node = findConnectionNode(spec.id());
        if (backend != null && backend.isOpen()) {
            backends.remove(spec.id());
            RP.post(backend::close);
            if (node != null) {
                setPlaceholder(node, "Loading…");
                tree.collapsePath(new TreePath(node.getPath()));
            }
            status("Disconnected " + spec.name(), Color.GRAY);
            refreshActions();
            tree.repaint();
        } else {
            connect(spec, node, true);
        }
    }

    /**
     * Opens the backend and lists its containers, off-EDT; on success the
     * tree node fills and (when {@code expand}) unfolds.
     */
    private void connect(ConnectionSpec spec, DefaultMutableTreeNode node, boolean expand) {
        if (!connecting.add(spec.id())) {
            return; // already on its way
        }
        status("Connecting to " + spec.name() + "…", Color.GRAY);
        RP.post(() -> {
            DbBackend backend = backendFor(spec);
            String error = backend.open();
            List<TableInfo> containers = error == null ? backend.listContainers() : List.of();
            SwingUtilities.invokeLater(() -> {
                connecting.remove(spec.id());
                if (error != null) {
                    status("Connect failed: " + error, FAIL_RED);
                    balloon("Connect failed: " + spec.name(), error, false);
                    if (node != null) {
                        setPlaceholder(node, "not connected");
                    }
                } else {
                    containerCache.put(spec.id(), containers);
                    if (node != null) {
                        fillContainers(node, containers);
                        if (expand) {
                            tree.expandPath(new TreePath(node.getPath()));
                        }
                    }
                    status("Connected: " + spec.name() + " — " + containers.size()
                            + (containers.size() == 1 ? " container" : " containers"), OK_GREEN);
                    publishSearch();
                }
                refreshActions();
                tree.repaint();
            });
        });
    }

    private void testSelected() {
        ConnectionSpec spec = selectedConnection();
        if (spec == null) {
            return;
        }
        status("Testing " + spec.name() + "…", Color.GRAY);
        RP.post(() -> {
            char[] password = Passwords.read(spec.id());
            DbBackend backend = DbBackend.create(spec, password);
            if (password != null) {
                Arrays.fill(password, '\0');
            }
            String error = backend.test();
            backend.close();
            SwingUtilities.invokeLater(() -> {
                status(error == null
                        ? "OK: " + spec.name() + " is reachable"
                        : "Test failed: " + error, error == null ? OK_GREEN : FAIL_RED);
                balloon("Test " + spec.name() + (error == null ? ": reachable" : ": failed"),
                        error, error == null);
            });
        });
    }

    /** The one live backend per spec id; created (with its keychain read) off-EDT. */
    private DbBackend backendFor(ConnectionSpec spec) {
        return backends.computeIfAbsent(spec.id(), id -> {
            char[] password = Passwords.read(id);
            try {
                return DbBackend.create(spec, password); // the backend copies the array
            } finally {
                if (password != null) {
                    Arrays.fill(password, '\0');
                }
            }
        });
    }

    private void closeAllBackends() {
        List<DbBackend> doomed = new ArrayList<>(backends.values());
        backends.clear();
        if (!doomed.isEmpty()) {
            RP.post(() -> doomed.forEach(DbBackend::close));
        }
    }

    // ---- tree building and lazy loading ----

    private void rebuildTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Connections");
        for (ConnectionSpec spec : specs) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(spec);
            node.add(new DefaultMutableTreeNode("Loading…"));
            root.add(node);
        }
        if (specs.isEmpty()) {
            // first-run guidance: an empty tree with greyed buttons reads as
            // broken; say what to do instead
            root.add(new DefaultMutableTreeNode(
                    "No connections yet — click Add below to create one"));
            status("Add a connection, then double-click a table to peek at its data",
                    Color.GRAY);
        }
        tree.setModel(new DefaultTreeModel(root));
        refreshActions();
    }

    private static boolean hasPlaceholder(DefaultMutableTreeNode node) {
        return node.getChildCount() == 1
                && ((DefaultMutableTreeNode) node.getChildAt(0)).getUserObject() instanceof String;
    }

    private void setPlaceholder(DefaultMutableTreeNode node, String text) {
        node.removeAllChildren();
        node.add(new DefaultMutableTreeNode(text));
        ((DefaultTreeModel) tree.getModel()).nodeStructureChanged(node);
    }

    private void fillContainers(DefaultMutableTreeNode node, List<TableInfo> containers) {
        node.removeAllChildren();
        if (containers.isEmpty()) {
            node.add(new DefaultMutableTreeNode("(empty)"));
        }
        for (TableInfo container : containers) {
            DefaultMutableTreeNode child = new DefaultMutableTreeNode(container);
            child.add(new DefaultMutableTreeNode("Loading…"));
            node.add(child);
        }
        ((DefaultTreeModel) tree.getModel()).nodeStructureChanged(node);
    }

    private void loadColumns(ConnectionSpec spec, TableInfo info, DefaultMutableTreeNode node) {
        if (!loadingNodes.add(node)) {
            return;
        }
        RP.post(() -> {
            DbBackend backend = backends.get(spec.id());
            List<ColumnInfo> columns = backend != null && backend.isOpen()
                    ? backend.columns(info) : List.<ColumnInfo>of();
            SwingUtilities.invokeLater(() -> {
                loadingNodes.remove(node);
                node.removeAllChildren();
                if (columns.isEmpty()) {
                    node.add(new DefaultMutableTreeNode("(no columns)"));
                } else {
                    columns.forEach(column -> node.add(new DefaultMutableTreeNode(column)));
                }
                ((DefaultTreeModel) tree.getModel()).nodeStructureChanged(node);
            });
        });
    }

    private DefaultMutableTreeNode findConnectionNode(String specId) {
        if (!(tree.getModel().getRoot() instanceof DefaultMutableTreeNode root)) {
            return null;
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
            if (child.getUserObject() instanceof ConnectionSpec spec
                    && spec.id().equals(specId)) {
                return child;
            }
        }
        return null;
    }

    /** The connection owning the current selection, walking up from any depth. */
    private ConnectionSpec selectedConnection() {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return null;
        }
        return specOf((DefaultMutableTreeNode) path.getLastPathComponent());
    }

    private static ConnectionSpec specOf(DefaultMutableTreeNode node) {
        for (DefaultMutableTreeNode n = node; n != null;
                n = (DefaultMutableTreeNode) n.getParent()) {
            if (n.getUserObject() instanceof ConnectionSpec spec) {
                return spec;
            }
        }
        return null;
    }

    private void onTreeSelect() {
        ConnectionSpec spec = selectedConnection();
        if (spec != null && !spec.id().equals(activeSpecId)) {
            activeSpecId = spec.id();
            applyConsoleMime(spec.engine());
        }
        refreshActions();
    }

    private ConnectionSpec activeSpec() {
        if (activeSpecId == null) {
            return null;
        }
        for (ConnectionSpec spec : specs) {
            if (spec.id().equals(activeSpecId)) {
                return spec;
            }
        }
        return null;
    }

    private void refreshActions() {
        ConnectionSpec spec = selectedConnection();
        boolean selected = spec != null;
        editButton.setEnabled(selected);
        removeButton.setEnabled(selected);
        testButton.setEnabled(selected);
        connectButton.setEnabled(selected);
        DbBackend backend = selected ? backends.get(spec.id()) : null;
        connectButton.setText(backend != null && backend.isOpen() ? "Disconnect" : "Connect");
        // RUN gates on having a target: an always-armed button that silently
        // no-ops reads as broken. The tooltip says why it's off.
        boolean runnable = !running && activeSpec() != null;
        runButton.setEnabled(runnable);
        runButton.setToolTipText(runnable
                ? "Execute the console against the active connection"
                : running ? "A run is in flight" : "Select a connection first");
    }

    /**
     * Feedback that cannot be missed: async outcomes (test, connect) land as
     * balloon notifications in addition to the status strip. Failures carry
     * the error; successes are one quiet line.
     */
    private static void balloon(String title, String detail, boolean ok) {
        javax.swing.Icon icon = javax.swing.UIManager.getIcon(
                ok ? "OptionPane.informationIcon" : "OptionPane.errorIcon");
        org.openide.awt.NotificationDisplayer.getDefault().notify(
                title, icon, detail == null ? "" : detail, null,
                ok ? org.openide.awt.NotificationDisplayer.Priority.LOW
                   : org.openide.awt.NotificationDisplayer.Priority.NORMAL);
    }

    // ---- CRUD ----

    private void addConnection() {
        ConnectionSpec spec = ConnectionDialog.show(null);
        if (spec == null) {
            return;
        }
        specs.add(spec);
        saveWorkspace();
        rebuildTree();
        publishSearch();
        selectConnection(spec.id());
    }

    private void editSelected() {
        ConnectionSpec spec = selectedConnection();
        if (spec == null) {
            return;
        }
        ConnectionSpec updated = ConnectionDialog.show(spec);
        if (updated == null) {
            return;
        }
        specs.replaceAll(s -> s.id().equals(spec.id()) ? updated : s);
        // settings changed: the old backend and its container cache are stale
        DbBackend stale = backends.remove(spec.id());
        if (stale != null) {
            RP.post(stale::close);
        }
        containerCache.remove(spec.id());
        saveWorkspace();
        rebuildTree();
        publishSearch();
        selectConnection(spec.id());
    }

    private void removeSelected() {
        ConnectionSpec spec = selectedConnection();
        if (spec == null) {
            return;
        }
        NotifyDescriptor.Confirmation confirm = new NotifyDescriptor.Confirmation(
                "Remove connection \"" + spec.name() + "\"? Its stored password is deleted too.",
                "Remove Connection", NotifyDescriptor.OK_CANCEL_OPTION);
        if (DialogDisplayer.getDefault().notify(confirm) != NotifyDescriptor.OK_OPTION) {
            return;
        }
        DbBackend backend = backends.remove(spec.id());
        RP.post(() -> {
            if (backend != null) {
                backend.close();
            }
            Passwords.delete(spec.id()); // keyring may block — off the EDT
        });
        specs.removeIf(s -> s.id().equals(spec.id()));
        containerCache.remove(spec.id());
        if (spec.id().equals(activeSpecId)) {
            activeSpecId = null;
        }
        saveWorkspace();
        rebuildTree();
        publishSearch();
        status("Removed " + spec.name(), Color.GRAY);
    }

    // ---- Quick Search entry points ----

    /**
     * Selects the connection with the given spec id, scrolling it into
     * view. Best-effort no-op when it isn't in the tree (e.g. the
     * workspace changed since the hit was indexed).
     */
    public void selectConnection(String specId) {
        DefaultMutableTreeNode node = findConnectionNode(specId);
        if (node == null) {
            return;
        }
        TreePath path = new TreePath(node.getPath());
        tree.setSelectionPath(path);
        tree.scrollPathToVisible(path);
    }

    /**
     * Selects {@code tableName} under the given connection when its
     * containers are already loaded in the tree; otherwise falls back to
     * selecting the connection itself. Used by Quick Search.
     */
    public void selectTable(String specId, String tableName) {
        DefaultMutableTreeNode connectionNode = findConnectionNode(specId);
        if (connectionNode == null || tableName == null) {
            return;
        }
        for (int i = 0; i < connectionNode.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) connectionNode.getChildAt(i);
            if (child.getUserObject() instanceof TableInfo info
                    && tableName.equals(info.name())) {
                TreePath path = new TreePath(child.getPath());
                tree.expandPath(new TreePath(connectionNode.getPath()));
                tree.setSelectionPath(path);
                tree.scrollPathToVisible(path);
                return;
            }
        }
        selectConnection(specId);
    }

    private void publishSearch() {
        DbSearchProvider.publish(new ArrayList<>(specs), new HashMap<>(containerCache));
    }

    // ---- persistence (RackService idiom, same as apiclient/infra) ----

    private File projectDir() {
        try {
            File dir = org.nmox.studio.rack.service.RackService.getDefault()
                    .getRack().getProjectDir();
            if (dir != null && dir.isDirectory()) {
                return dir;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // rack unavailable (tests, stripped platform)
        }
        return new File(System.getProperty("user.home"));
    }

    private void reloadWorkspace() {
        closeAllBackends();
        containerCache.clear();
        connecting.clear();
        activeSpecId = null;
        specs.clear();
        specs.addAll(DbWorkspaceIO.load(projectDir()));
        rebuildTree();
        publishSearch();
        status(specs.isEmpty() ? " "
                : specs.size() + (specs.size() == 1 ? " connection" : " connections"), Color.GRAY);
    }

    private boolean saveFailureNotified;

    private void saveWorkspace() {
        try {
            DbWorkspaceIO.save(projectDir(), specs);
            saveFailureNotified = false;
        } catch (Exception ex) {
            // a failed save never interrupts editing — but never lose work silently
            java.util.logging.Logger.getLogger(DbStudioTopComponent.class.getName())
                    .log(java.util.logging.Level.WARNING, "DB workspace save failed", ex);
            if (!saveFailureNotified) {
                saveFailureNotified = true;
                org.openide.awt.NotificationDisplayer.getDefault().notify(
                        "DB Studio can't save its connections",
                        javax.swing.UIManager.getIcon("OptionPane.warningIcon"),
                        "Changes are not being persisted: " + ex.getMessage(),
                        null);
            }
        }
    }

    private void status(String message, Color color) {
        statusLabel.setForeground(color);
        statusLabel.setText(message);
        org.openide.awt.StatusDisplayer.getDefault().setStatusText(message);
    }

    // ---- lifecycle ----

    private void attachRackListener() {
        try {
            org.nmox.studio.rack.service.RackService.getDefault()
                    .getRack().addListener(rackListener);
            rackListenerAttached = true;
        } catch (RuntimeException | LinkageError ignored) {
            // rack unavailable (tests, stripped platform): no project switches to follow
        }
    }

    @Override
    public void componentOpened() {
        if (!rackListenerAttached) {
            attachRackListener();
        }
    }

    @Override
    public void componentClosed() {
        closeAllBackends();
        if (rackListenerAttached) {
            try {
                org.nmox.studio.rack.service.RackService.getDefault()
                        .getRack().removeListener(rackListener);
            } catch (RuntimeException | LinkageError ignored) {
                // already unavailable — nothing to detach from
            }
            rackListenerAttached = false;
        }
    }

    // ---- renderers ----

    /** Connection: name + grey engine badge (bold while connected); container: name + kind; column: name : type [PK]. */
    private final class DbTreeRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree t, Object value, boolean sel,
                boolean expanded, boolean leaf, int row, boolean focus) {
            super.getTreeCellRendererComponent(t, value, sel, expanded, leaf, row, focus);
            Object userObject = value instanceof DefaultMutableTreeNode node
                    ? node.getUserObject() : null;
            if (userObject instanceof ConnectionSpec spec) {
                DbBackend backend = backends.get(spec.id());
                boolean connected = backend != null && backend.isOpen();
                setText("<html>" + (connected ? "<b>" : "") + esc(spec.name())
                        + (connected ? "</b>" : "")
                        + " <font color='#8a8a8a'>(" + spec.engine().displayName()
                        + ")</font></html>");
            } else if (userObject instanceof TableInfo info) {
                String kind = info.type() == null || "TABLE".equalsIgnoreCase(info.type())
                        ? "" : " <font color='#8a8a8a'>(" + esc(info.type().toLowerCase(java.util.Locale.ROOT)) + ")</font>";
                setText("<html>" + esc(info.name()) + kind + "</html>");
            } else if (userObject instanceof ColumnInfo column) {
                setText("<html>" + esc(column.name())
                        + " <font color='#8a8a8a'>: " + esc(column.typeName()) + "</font>"
                        + (column.primaryKey() ? "  <b>[PK]</b>" : "") + "</html>");
            } else if (userObject instanceof String placeholder) {
                setText("<html><i><font color='#8a8a8a'>" + esc(placeholder)
                        + "</font></i></html>");
            }
            return this;
        }
    }

    /** History entries: time, engine badge, first line of the run text. */
    private static final class HistoryRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ConsoleHistory.Entry entry) {
                String firstLine = entry.text().strip();
                int newline = firstLine.indexOf('\n');
                if (newline >= 0) {
                    firstLine = firstLine.substring(0, newline) + " …";
                }
                if (firstLine.length() > 90) {
                    firstLine = firstLine.substring(0, 87) + "…";
                }
                setText(TIME.format(Instant.ofEpochMilli(entry.timestamp()))
                        + "  [" + entry.engine() + "]  " + firstLine);
                setFont(MONO);
            }
            return this;
        }
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
