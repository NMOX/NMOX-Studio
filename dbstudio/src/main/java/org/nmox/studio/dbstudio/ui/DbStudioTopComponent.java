package org.nmox.studio.dbstudio.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.netbeans.api.db.explorer.ConnectionManager;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.nmox.studio.dbstudio.engine.DbBackend;
import org.nmox.studio.dbstudio.engine.EditGate;
import org.nmox.studio.dbstudio.engine.EditSession;
import org.nmox.studio.dbstudio.engine.ExplainQueries;
import org.nmox.studio.dbstudio.engine.JdbcUrlDialects;
import org.nmox.studio.dbstudio.engine.Passwords;
import org.nmox.studio.dbstudio.engine.QueryResult;
import org.nmox.studio.dbstudio.engine.ResultExports;
import org.nmox.studio.dbstudio.engine.ServicesBackend;
import org.nmox.studio.dbstudio.io.DbWorkspaceIO;
import org.nmox.studio.dbstudio.io.EnvConnections;
import org.nmox.studio.dbstudio.io.WorkspaceEdits;
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
 * <p>Below the workspace connections the tree carries a <b>Services</b>
 * branch mirroring the NetBeans Database Explorer: every connection
 * registered in the Services window appears here, browsable and
 * runnable through {@link ServicesBackend} wrapping the explorer's own
 * live {@code java.sql.Connection}. NetBeans owns those connections'
 * drivers, credentials and lifecycle — Edit/Remove/Test are disabled
 * for them and disconnecting is done in the Services window; the branch
 * follows {@code ConnectionManager}'s connectionsChanged events while
 * the tab is open.
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
    /** Synthesized display specs for the Services branch, ids prefixed {@code nb:} — never persisted. */
    private final List<ConnectionSpec> serviceSpecs = new ArrayList<>();
    /** The live NetBeans explorer connection behind each Services spec id. */
    private final Map<String, DatabaseConnection> serviceConnections = new ConcurrentHashMap<>();
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
    private final JButton explainButton = new JButton("EXPLAIN");
    private final JButton cancelButton = new JButton("Cancel");
    private final JButton saveQueryButton = new JButton("Save…");
    private final JComboBox<Object> savedCombo = new JComboBox<>();
    /** Guards the saved-combo's action listener during programmatic refills. */
    private boolean savedComboRefreshing;
    private final JSpinner limitSpinner = new JSpinner(new SpinnerNumberModel(200, 1, 1_000_000, 100));
    private final JLabel statusLabel = new JLabel(" ");

    /** The project's persisted console history (newest first) — mirrors .nmoxdb.json. */
    private List<DbWorkspaceIO.HistoryEntry> persistedHistory = new ArrayList<>();
    /** The project's saved queries — mirrors .nmoxdb.json. */
    private List<DbWorkspaceIO.SavedQuery> savedQueries = new ArrayList<>();
    /** Projects already offered a .env connection this session — one offer each, ever. */
    private final Set<String> envOfferedProjects = new HashSet<>();
    /** Containers already offered a Docker connection this session — one balloon each, ever. */
    private final Set<String> dockerOfferedContainers = new HashSet<>();
    /** True while a docker container probe is in flight — probes never overlap. */
    private boolean dockerProbeInFlight;
    /** A probe result finished while the tab was hidden; released on next showing. */
    private final org.nmox.studio.dbstudio.io.DockerDbOffers.Hold dockerHold =
            new org.nmox.studio.dbstudio.io.DockerDbOffers.Hold();
    /** Discriminates our own .nmoxdb.json writes from foreign edits (git pull, hand edit). */
    private final org.nmox.studio.dbstudio.io.ExternalEdits externalEdits =
            new org.nmox.studio.dbstudio.io.ExternalEdits();
    /** Watches .nmoxdb.json while the tab is open; null when closed. */
    private org.nmox.studio.rack.engine.FileWatcher workspaceWatcher;
    /** True while the add/edit connection dialog is up — external reloads wait. */
    private boolean connectionDialogOpen;
    /** A foreign .nmoxdb.json version seen while busy; re-checked when free. */
    private org.nmox.studio.dbstudio.io.ExternalEdits.Stamp deferredExternalStamp;
    /** The rack's coalesced manifest batches (.env included); attached while open. */
    private java.util.function.Consumer<java.util.List<java.nio.file.Path>> manifestListener;
    /** Re-checks EXPLAIN's enablement as the console text changes. */
    private final DocumentListener consoleTextListener = new DocumentListener() {
        @Override
        public void insertUpdate(DocumentEvent e) {
            refreshActions();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            refreshActions();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            refreshActions();
        }
    };

    private final JTabbedPane resultsTabs = new JTabbedPane();
    private final DefaultListModel<ConsoleHistory.Entry> historyModel = new DefaultListModel<>();
    private final JList<ConsoleHistory.Entry> historyList = new JList<>(historyModel);

    private final org.nmox.studio.rack.model.Rack.Listener rackListener;
    private boolean rackListenerAttached;
    /** Follows the Services window's connection list while the tab is open. */
    private final org.netbeans.api.db.explorer.ConnectionListener servicesListener =
            () -> SwingUtilities.invokeLater(this::refreshServicesBranch);
    private boolean servicesListenerAttached;
    /** The tree node heading the Services branch; recreated by every rebuildTree. */
    private DefaultMutableTreeNode servicesBranchNode;
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
        attachServicesListener();
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
        explainButton.setEnabled(false);
        explainButton.addActionListener(e -> explain());
        bar.add(explainButton);
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
        saveQueryButton.setToolTipText("Save the console text as a named query (.nmoxdb.json)");
        saveQueryButton.addActionListener(e -> saveCurrentQuery());
        bar.add(saveQueryButton);
        savedCombo.setRenderer(new SavedQueryRenderer());
        savedCombo.setToolTipText("Saved queries — selecting one loads it into the console");
        savedCombo.addActionListener(e -> savedQueryPicked());
        bar.add(savedCombo);
        bar.addSeparator();
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        bar.add(statusLabel);
        panel.add(bar, BorderLayout.NORTH);

        console.setFont(MONO);
        // EXPLAIN follows the console text; setEditorKit swaps the document,
        // so the listener re-attaches on every document change
        console.getDocument().addDocumentListener(consoleTextListener);
        console.addPropertyChangeListener("document", e -> {
            if (e.getOldValue() instanceof javax.swing.text.Document old) {
                old.removeDocumentListener(consoleTextListener);
            }
            if (e.getNewValue() instanceof javax.swing.text.Document doc) {
                doc.addDocumentListener(consoleTextListener);
            }
            refreshActions();
        });
        panel.add(new JScrollPane(console), BorderLayout.CENTER);
        refreshSavedCombo();
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
        applyConsoleMimeFor(spec);
        int limit = (Integer) limitSpinner.getValue();
        console.setText(peekTextFor(spec, info, limit));
        if (PeekQueries.runnableAgainst(spec, info)) {
            run();
        } else {
            status("Console queries \"" + spec.database() + "\" — Edit the connection's "
                    + "database to \"" + info.name() + "\" to query it", FAIL_RED);
        }
    }

    /**
     * The peek text for a container: the engine dialect when DB Studio
     * knows it; otherwise (a Services connection to Derby, Oracle, …)
     * the SQL-standard shape with the URL-inferred identifier quote.
     */
    private String peekTextFor(ConnectionSpec spec, TableInfo info, int limit) {
        if (spec.engine() != null) {
            return PeekQueries.consoleTextFor(spec.engine(), info, limit);
        }
        DatabaseConnection connection = serviceConnections.get(spec.id());
        String quote = JdbcUrlDialects.identifierQuote(
                connection == null ? null : connection.getDatabaseURL());
        return PeekQueries.consoleTextFor(quote, info, limit);
    }

    private void run() {
        runText(console.getText());
    }

    /**
     * Runs the engine's EXPLAIN wrapping of the console text through
     * the normal run path — the plan lands in the results tabs like any
     * other statement (and, starting with EXPLAIN, its grid naturally
     * stays read-only).
     */
    private void explain() {
        ConnectionSpec spec = activeSpec();
        if (spec == null || spec.engine() == null
                || !ExplainQueries.explainable(spec.engine(), console.getText())) {
            return; // the button is disabled in these states; belt and braces
        }
        runText(ExplainQueries.explain(spec.engine(), console.getText()));
    }

    private void runText(String text) {
        if (running) {
            return;
        }
        ConnectionSpec spec = activeSpec();
        if (spec == null) {
            status("Select a connection first", FAIL_RED);
            return;
        }
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
            List<QueryResult> results;
            if (backend == null) { // a Services connection that just left the explorer
                results = List.of(new QueryResult(List.of(), List.of(), 0, -1, false, 0,
                        "Connection no longer exists in the Services window", text));
            } else {
                String openError = backend.isOpen() ? null : backend.open();
                results = openError == null
                        ? backend.runConsole(text, limit)
                        : List.of(new QueryResult(List.of(), List.of(), 0, -1, false, 0,
                                openError, text));
            }
            List<TabContent> tabs = gateAll(backend, spec, results);
            long totalMs = System.currentTimeMillis() - started;
            SwingUtilities.invokeLater(() -> {
                running = false;
                cancelButton.setEnabled(false);
                history.add(text, engineLabel(spec), System.currentTimeMillis());
                recordRun(text, engineLabel(spec));
                refreshHistory();
                showResults(spec, tabs, totalMs);
                refreshActions(); // the run may have opened the connection
                tree.repaint();
                recheckDeferredExternal(); // a foreign .nmoxdb.json may have waited on us
            });
        });
    }

    /** One result plus its editability verdict — what a results tab shows. */
    private record TabContent(QueryResult result, EditGate.Decision decision) {
    }

    /**
     * The editability verdict for each result, computed off-EDT right
     * after the run (metadata lookups ride the still-warm backend).
     * Only result grids get a verdict; updates and errors carry null.
     */
    private List<TabContent> gateAll(DbBackend backend, ConnectionSpec spec,
            List<QueryResult> results) {
        List<TabContent> tabs = new ArrayList<>();
        for (QueryResult result : results) {
            EditGate.Decision decision = null;
            if (result.isResultSet() && backend != null) {
                List<TableInfo> containers =
                        containerCache.getOrDefault(spec.id(), List.of());
                decision = EditGate.decide(spec.engine(), backend.kind(), result,
                        containers, backend::columns);
            }
            tabs.add(new TabContent(result, decision));
        }
        return tabs;
    }

    private void cancel() {
        ConnectionSpec spec = activeSpec();
        DbBackend backend = spec == null ? null : backends.get(spec.id());
        if (backend != null) {
            RP.post(backend::cancel); // RP has spare threads while a run blocks one
            status("Cancel requested…", Color.GRAY);
        }
    }

    private void showResults(ConnectionSpec spec, List<TabContent> tabs, long totalMs) {
        while (resultsTabs.getTabCount() > 1) {
            resultsTabs.removeTabAt(0); // everything but the trailing History tab
        }
        int failed = 0;
        for (int i = 0; i < tabs.size(); i++) {
            TabContent content = tabs.get(i);
            if (content.result().isError()) {
                failed++;
            }
            resultsTabs.insertTab(tabTitle(i, content.result()), null,
                    resultTab(spec, content), content.result().statement(),
                    resultsTabs.getTabCount() - 1);
        }
        if (!tabs.isEmpty()) {
            resultsTabs.setSelectedIndex(0);
        }
        String summary = tabs.size() + (tabs.size() == 1 ? " statement" : " statements")
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

    private JComponent resultTab(ConnectionSpec spec, TabContent content) {
        JPanel panel = new JPanel(new BorderLayout());
        fillResultPanel(panel, spec, content);
        return panel;
    }

    /**
     * (Re)builds one results tab in place — Apply refreshes the grid by
     * re-running the query and refilling the same panel with the fresh
     * truth.
     */
    private void fillResultPanel(JPanel panel, ConnectionSpec spec, TabContent content) {
        panel.removeAll();
        QueryResult result = content.result();
        JLabel header = new JLabel(headerText(result));
        header.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        if (result.isError()) {
            header.setForeground(FAIL_RED);
        }
        panel.add(header, BorderLayout.NORTH);
        if (result.isResultSet()) {
            EditGate.Decision decision = content.decision();
            if (decision != null && decision.editable()) {
                panel.add(editableGrid(panel, spec, content), BorderLayout.CENTER);
            } else {
                JTable table = new JTable(new ResultsTableModel(result));
                table.setFont(MONO);
                table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); // wide results scroll, not squash
                String reason = decision == null ? "Read-only" : decision.reason();
                table.setToolTipText(reason);
                panel.add(new JScrollPane(table), BorderLayout.CENTER);
                panel.add(readOnlyStrip(result, reason), BorderLayout.SOUTH);
            }
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
        panel.revalidate();
        panel.repaint();
    }

    /** The grid plus the edit strip: dirty-cell tint, pending chip, Apply…/Revert, exports. */
    private JComponent editableGrid(JPanel tabPanel, ConnectionSpec spec, TabContent content) {
        EditSession session = content.decision().session();
        JLabel chip = new JLabel("No pending edits");
        chip.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        JButton applyButton = new JButton("Apply…");
        applyButton.setEnabled(false);
        applyButton.setToolTipText("Preview and run the UPDATE statements for the pending edits");
        JButton revertButton = new JButton("Revert");
        revertButton.setEnabled(false);
        revertButton.setToolTipText("Forget every pending edit");
        EditableResultsModel model = new EditableResultsModel(session, () -> {
            int dirty = session.dirtyCount();
            chip.setText(dirty == 0 ? "No pending edits"
                    : dirty + (dirty == 1 ? " edit pending" : " edits pending"));
            chip.setForeground(dirty == 0 ? Color.GRAY : ACCENT);
            applyButton.setEnabled(dirty > 0);
            revertButton.setEnabled(dirty > 0);
        });
        JTable table = new JTable(model);
        table.setFont(MONO);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        table.setDefaultRenderer(String.class, new DirtyCellRenderer(model));
        table.setToolTipText("Double-click a cell to edit; Apply… previews the UPDATEs first");
        applyButton.addActionListener(e -> applyEdits(tabPanel, spec, content, model));
        revertButton.addActionListener(e -> model.revertAll());

        JToolBar strip = new JToolBar();
        strip.setFloatable(false);
        chip.setForeground(Color.GRAY);
        strip.add(chip);
        strip.add(javax.swing.Box.createHorizontalGlue());
        strip.add(applyButton);
        strip.add(revertButton);
        strip.addSeparator();
        addExportButtons(strip, content.result());

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(new JScrollPane(table), BorderLayout.CENTER);
        wrapper.add(strip, BorderLayout.SOUTH);
        return wrapper;
    }

    /** The read-only grid's strip: the honest reason on the left, exports on the right. */
    private JComponent readOnlyStrip(QueryResult result, String reason) {
        JToolBar strip = new JToolBar();
        strip.setFloatable(false);
        JLabel why = new JLabel(reason);
        why.setForeground(Color.GRAY);
        why.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        strip.add(why);
        strip.add(javax.swing.Box.createHorizontalGlue());
        addExportButtons(strip, result);
        return strip;
    }

    private void addExportButtons(JToolBar strip, QueryResult result) {
        boolean hasRows = result.rowCount() > 0;
        JButton csvButton = new JButton("CSV");
        csvButton.setEnabled(hasRows);
        csvButton.setToolTipText(hasRows ? "Export this grid as CSV (UTF-8)"
                : "No rows to export");
        csvButton.addActionListener(e -> exportResult(result, true));
        JButton jsonButton = new JButton("JSON");
        jsonButton.setEnabled(hasRows);
        jsonButton.setToolTipText(hasRows ? "Export this grid as JSON (UTF-8)"
                : "No rows to export");
        jsonButton.addActionListener(e -> exportResult(result, false));
        strip.add(csvButton);
        strip.add(jsonButton);
    }

    /** Save-dialog then off-EDT write; the balloon carries the full path. */
    private void exportResult(QueryResult result, boolean csv) {
        String extension = csv ? ".csv" : ".json";
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(csv ? "Export as CSV" : "Export as JSON");
        chooser.setSelectedFile(new File(projectDir(),
                ResultExports.suggestedBaseName(result.statement()) + extension));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = chooser.getSelectedFile();
        RP.post(() -> {
            try {
                String text = csv ? ResultExports.toCsv(result) : ResultExports.toJson(result);
                Files.writeString(target.toPath(), text, StandardCharsets.UTF_8);
                SwingUtilities.invokeLater(() -> {
                    status("Exported " + result.rowCount() + " row(s) → "
                            + target.getName(), OK_GREEN);
                    balloon("Exported " + result.rowCount() + " row(s)",
                            target.getAbsolutePath(), true);
                });
            } catch (IOException | RuntimeException ex) {
                SwingUtilities.invokeLater(() -> {
                    status("Export failed: " + ex.getMessage(), FAIL_RED);
                    balloon("Export failed", ex.getMessage(), false);
                });
            }
        });
    }

    /**
     * The Apply gesture: build the UPDATEs (any {@link UpdateBuilder}
     * refusal lands verbatim in the status bar), preview them in a
     * modal dialog, then run them one at a time through the backend's
     * normal console path — on the first failure the rest stay unrun
     * and the dirty state is kept for retry. On success the original
     * query re-runs and the tab refills with fresh truth; the session
     * is gone with the old grid.
     */
    private void applyEdits(JPanel tabPanel, ConnectionSpec spec, TabContent content,
            EditableResultsModel model) {
        if (running) {
            status("A run is in flight — try again when it finishes", FAIL_RED);
            return;
        }
        EditSession session = model.session();
        List<String> statements;
        try {
            statements = session.statements(spec.engine());
        } catch (IllegalArgumentException refusal) {
            status(refusal.getMessage(), FAIL_RED);
            return;
        }
        if (statements.isEmpty()) {
            return; // Apply is disabled when clean; belt and braces
        }
        if (!ApplyPreviewDialog.confirm(statements, session.dirtyRowCount(),
                session.table().name())) {
            return;
        }
        int limit = (Integer) limitSpinner.getValue();
        running = true;
        refreshActions();
        status("Applying " + statements.size() + " update(s)…", Color.GRAY);
        RP.post(() -> {
            DbBackend backend = backendFor(spec);
            String failure = null;
            int applied = 0;
            if (backend == null) {
                failure = "Connection no longer exists in the Services window";
            } else {
                String openError = backend.isOpen() ? null : backend.open();
                if (openError != null) {
                    failure = openError;
                } else {
                    for (String statement : statements) {
                        List<QueryResult> outcome = backend.runConsole(statement, 1);
                        QueryResult first = outcome.isEmpty() ? null : outcome.get(0);
                        if (first == null || first.isError()) {
                            failure = first == null
                                    ? "The statement did not execute" : first.error();
                            break;
                        }
                        applied++;
                    }
                }
            }
            if (failure != null) {
                String reason = failure;
                int done = applied;
                SwingUtilities.invokeLater(() -> {
                    running = false;
                    refreshActions();
                    status("Apply stopped: " + reason, FAIL_RED);
                    balloon("Apply stopped after " + done + " of " + statements.size()
                            + " update(s)", reason + " — your edits are kept; fix and retry.",
                            false);
                    recheckDeferredExternal();
                });
                return;
            }
            // fresh truth: re-run the original query, re-gate, refill the tab
            List<QueryResult> fresh = backend.runConsole(content.result().statement(), limit);
            QueryResult freshResult = fresh.isEmpty() ? content.result() : fresh.get(0);
            List<TabContent> regated = gateAll(backend, spec, List.of(freshResult));
            SwingUtilities.invokeLater(() -> {
                running = false;
                refreshActions();
                fillResultPanel(tabPanel, spec, regated.get(0));
                status("Applied " + statements.size() + " update(s) — grid refreshed",
                        OK_GREEN);
                balloon("Applied " + statements.size() + " update(s) to "
                        + session.table().name(), null, true);
                recheckDeferredExternal();
            });
        });
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

    // ---- persistent history + saved queries (.nmoxdb.json) ----

    /** Appends one run to the persisted history and saves the workspace. */
    private void recordRun(String text, String engine) {
        persistedHistory = WorkspaceEdits.withRun(persistedHistory,
                new DbWorkspaceIO.HistoryEntry(text, engine, System.currentTimeMillis()));
        saveWorkspace();
    }

    /** "Save query…": name prompt (default = the text's first 30 chars), replace-by-name. */
    private void saveCurrentQuery() {
        String text = console.getText();
        if (ConsoleMimes.isPlaceholderOrBlank(text)) {
            status("Nothing to save — the console is empty", FAIL_RED);
            return;
        }
        NotifyDescriptor.InputLine input =
                new NotifyDescriptor.InputLine("Name:", "Save Query");
        input.setInputText(WorkspaceEdits.defaultName(text));
        if (DialogDisplayer.getDefault().notify(input) != NotifyDescriptor.OK_OPTION) {
            return;
        }
        String name = input.getInputText().strip();
        if (name.isEmpty()) {
            status("A saved query needs a name", FAIL_RED);
            return;
        }
        ConnectionSpec spec = activeSpec();
        savedQueries = WorkspaceEdits.withSaved(savedQueries,
                new DbWorkspaceIO.SavedQuery(name, text, spec == null ? "" : engineLabel(spec)));
        saveWorkspace();
        refreshSavedCombo();
        status("Saved \"" + name + "\"", OK_GREEN);
    }

    /** Selecting a saved query loads it into the console; the combo snaps back to its label. */
    private void savedQueryPicked() {
        if (savedComboRefreshing) {
            return;
        }
        if (savedCombo.getSelectedItem() instanceof DbWorkspaceIO.SavedQuery query) {
            console.setText(query.text());
            status("Loaded \"" + query.name() + "\"", Color.GRAY);
            SwingUtilities.invokeLater(() -> {
                savedComboRefreshing = true;
                try {
                    savedCombo.setSelectedIndex(0);
                } finally {
                    savedComboRefreshing = false;
                }
            });
        }
    }

    private void refreshSavedCombo() {
        savedComboRefreshing = true;
        try {
            savedCombo.removeAllItems();
            savedCombo.addItem(savedQueries.isEmpty()
                    ? "No saved queries" : "Saved queries…");
            for (DbWorkspaceIO.SavedQuery query : savedQueries) {
                savedCombo.addItem(query);
            }
            savedCombo.setSelectedIndex(0);
            savedCombo.setEnabled(!savedQueries.isEmpty());
            savedCombo.setMaximumSize(savedCombo.getPreferredSize());
        } finally {
            savedComboRefreshing = false;
        }
    }

    // ---- the .env connection offer ----

    /**
     * When the aimed project carries a {@code .env} with database
     * config that isn't already a workspace connection, offer — once
     * per project per session, as a quiet balloon, never a modal — to
     * prefill the Add Connection dialog from it. The password rides
     * the dialog's password field only; saving stores it in the OS
     * keychain, exactly like a typed one.
     */
    private void offerEnvConnection() {
        File dir = projectDir();
        File envFile = new File(dir, ".env");
        if (!envFile.isFile() || !envOfferedProjects.add(dir.getAbsolutePath())) {
            return;
        }
        String content;
        try {
            content = Files.readString(envFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            return; // no signal, no offer
        }
        EnvConnections.fromEnv(content).ifPresent(suggestion -> {
            if (WorkspaceEdits.alreadyConfigured(suggestion, specs)) {
                return;
            }
            org.openide.awt.NotificationDisplayer.getDefault().notify(
                    "Found database config in .env",
                    javax.swing.UIManager.getIcon("OptionPane.informationIcon"),
                    "Create a \"" + suggestion.database() + "\" ("
                    + suggestion.engine().displayName()
                    + ") connection? Click to review — nothing is saved until you confirm.",
                    e -> createFromSuggestion(suggestion),
                    org.openide.awt.NotificationDisplayer.Priority.LOW);
        });
    }

    private void createFromSuggestion(EnvConnections.Suggestion suggestion) {
        ConnectionSpec spec = showConnectionDialog(() -> ConnectionDialog.showSuggested(suggestion));
        if (spec == null) {
            return;
        }
        specs.add(spec);
        saveWorkspace();
        rebuildTree();
        publishSearch();
        selectConnection(spec.id());
        status("Added " + spec.name() + " from .env", OK_GREEN);
    }

    // ---- the Docker connection offer ----

    /**
     * When Docker runs a database container the workspace isn't wired
     * to, offer — quiet balloon, at most
     * {@link org.nmox.studio.dbstudio.io.DockerDbOffers#MAX_OFFERS_PER_REFRESH}
     * per probe, once per container per session — to prefill the Add
     * Connection dialog. Every rule (engine inference, port mapping,
     * already-configured suppression, the cap) lives in the tested
     * {@code DockerDbOffers} core; this method only probes and shows.
     * No Docker daemon or CLI → an empty container list → total
     * silence. Runs when the tab becomes visible (componentShowing —
     * NOT componentOpened: this default-open tab opens hidden at
     * startup) and on every workspace reload (project switch), never
     * overlapping itself. A probe that finishes while the tab is hidden
     * is held by {@code dockerHold} — never ballooned unseen, guard
     * unconsumed — and released on the next showing.
     */
    private void offerDockerConnections() {
        if (dockerProbeInFlight) {
            return;
        }
        java.util.concurrent.CompletableFuture<java.util.List<
                org.nmox.studio.rack.docker.DockerClient.ContainerInfo>> probe;
        try {
            probe = org.nmox.studio.rack.docker.DockerClient.getDefault().containers();
        } catch (RuntimeException | LinkageError rackUnavailable) {
            return; // no rack (tests, stripped platform) — silence
        }
        dockerProbeInFlight = true;
        probe.whenComplete((containers, error) -> SwingUtilities.invokeLater(() -> {
            dockerProbeInFlight = false;
            if (error != null || containers == null || !isOpened()) {
                return; // daemon trouble or a since-closed tab — silence
            }
            // a hidden tab holds instead of showing: a balloon nobody can
            // see would expire unseen and burn the once-per-session guard
            showDockerOffers(dockerHold.onProbe(containers, isShowing()));
        }));
    }

    private void showDockerOffers(
            java.util.List<org.nmox.studio.rack.docker.DockerClient.ContainerInfo> containers) {
        List<ConnectionSpec> existing = new ArrayList<>(specs);
        existing.addAll(serviceSpecs);
        for (org.nmox.studio.dbstudio.io.DockerDbOffers.Offer offer
                : org.nmox.studio.dbstudio.io.DockerDbOffers.plan(
                        containers, existing, dockerOfferedContainers)) {
            dockerOfferedContainers.add(offer.containerId());
            org.openide.awt.NotificationDisplayer.getDefault().notify(
                    "Database container running in Docker",
                    javax.swing.UIManager.getIcon("OptionPane.informationIcon"),
                    org.nmox.studio.dbstudio.io.DockerDbOffers.offerText(offer)
                    + " Click to review — nothing is saved until you confirm.",
                    e -> createFromDockerOffer(offer),
                    org.openide.awt.NotificationDisplayer.Priority.LOW);
        }
    }

    private void createFromDockerOffer(org.nmox.studio.dbstudio.io.DockerDbOffers.Offer offer) {
        ConnectionSpec spec = showConnectionDialog(() -> ConnectionDialog.showPrefilled(
                org.nmox.studio.dbstudio.io.DockerDbOffers.suggestion(offer),
                offer.containerName() + " (docker)"));
        if (spec == null) {
            return;
        }
        specs.add(spec);
        saveWorkspace();
        rebuildTree();
        publishSearch();
        selectConnection(spec.id());
        status("Added " + spec.name() + " from Docker", OK_GREEN);
    }

    // ---- .env changes re-arm the offer ----

    /**
     * A {@code .env} in the aimed project changed on disk (the rack's
     * manifest pulse, coalesced): the once-per-session offer guard
     * resets so the next natural moment re-offers, and — when the tab
     * is actually in front of the user — the same quiet offer flow
     * re-runs once right away. Bounded: one coalesced batch is one
     * listener call is one reset and at most one balloon, and
     * {@code offerEnvConnection}'s alreadyConfigured check still
     * suppresses noise.
     */
    private void envChangedOnDisk() {
        envOfferedProjects.remove(projectDir().getAbsolutePath());
        if (isOpened() && isShowing()) {
            offerEnvConnection();
        }
    }

    private void attachManifestListener() {
        if (manifestListener != null) {
            return;
        }
        java.util.function.Consumer<java.util.List<java.nio.file.Path>> listener = batch -> {
            // rack watcher thread: filter here, marshal only real .env hits
            if (EnvConnections.touchesEnv(batch)) {
                SwingUtilities.invokeLater(this::envChangedOnDisk);
            }
        };
        try {
            org.nmox.studio.rack.service.RackService.getDefault().addManifestListener(listener);
            manifestListener = listener;
        } catch (RuntimeException | LinkageError ignored) {
            // rack unavailable (tests, stripped platform): no manifest events
        }
    }

    private void detachManifestListener() {
        if (manifestListener == null) {
            return;
        }
        try {
            org.nmox.studio.rack.service.RackService.getDefault()
                    .removeManifestListener(manifestListener);
        } catch (RuntimeException | LinkageError ignored) {
            // already unavailable — nothing to detach from
        }
        manifestListener = null;
    }

    // ---- .nmoxdb.json edited outside the studio ----

    /**
     * Watches the project's {@code .nmoxdb.json} while the tab is open.
     * The watcher reports on its own thread; the stamp is taken there
     * (never stat on the EDT) and the verdict is decided on the EDT by
     * the tested {@link org.nmox.studio.dbstudio.io.ExternalEdits}
     * core. Only the project-root file matters — nested .nmoxdb.json
     * files in a monorepo belong to their own aims.
     */
    private void restartWorkspaceWatcher() {
        stopWorkspaceWatcher();
        File dir = projectDir();
        File workspaceFile = new File(dir, org.nmox.studio.dbstudio.io.DbWorkspaceIO.FILENAME);
        org.nmox.studio.rack.engine.FileWatcher watcher;
        try {
            watcher = org.nmox.studio.rack.engine.FileWatcher.forFilenames(dir, 2_000,
                    Set.of(org.nmox.studio.dbstudio.io.DbWorkspaceIO.FILENAME), batch -> {
                        if (!batch.contains(workspaceFile.toPath())) {
                            return;
                        }
                        org.nmox.studio.dbstudio.io.ExternalEdits.Stamp stamp =
                                org.nmox.studio.dbstudio.io.ExternalEdits.Stamp.of(workspaceFile);
                        SwingUtilities.invokeLater(() -> handleExternalStamp(stamp));
                    });
        } catch (RuntimeException | LinkageError rackUnavailable) {
            return; // no rack (tests, stripped platform): no watcher
        }
        watcher.start();
        workspaceWatcher = watcher;
    }

    private void stopWorkspaceWatcher() {
        if (workspaceWatcher != null) {
            workspaceWatcher.stop();
            workspaceWatcher = null;
        }
    }

    /**
     * EDT: reacts to a {@code .nmoxdb.json} stamp per the
     * {@code ExternalEdits} verdict. Nothing in the studio's persisted
     * state is ever dirty (every change saves immediately), so a
     * foreign version reloads silently — UNLESS a connection dialog is
     * up or a run is in flight, in which case the version waits and is
     * re-checked the moment the studio is free. Never a modal, never a
     * clobber; a version already reacted to stays quiet (bounded).
     */
    private void handleExternalStamp(org.nmox.studio.dbstudio.io.ExternalEdits.Stamp onDisk) {
        if (!isOpened()) {
            return; // a closed tab reacts to nothing
        }
        switch (externalEdits.check(onDisk, connectionDialogOpen || running)) {
            case RELOAD -> {
                deferredExternalStamp = null;
                reloadWorkspace();
                balloon("Reloaded " + org.nmox.studio.dbstudio.io.DbWorkspaceIO.FILENAME,
                        "The file changed outside DB Studio — connections, history and "
                        + "saved queries follow it.", true);
            }
            case DEFER -> deferredExternalStamp = onDisk;
            case NONE -> {
            }
        }
    }

    /**
     * After the busy state ends (dialog closed, run finished): if a
     * foreign version waited, re-stat off-EDT — the file may have
     * changed again, including by our own just-completed save, which
     * the core then correctly ignores — and re-decide.
     */
    private void recheckDeferredExternal() {
        if (deferredExternalStamp == null) {
            return;
        }
        deferredExternalStamp = null;
        File workspaceFile = new File(projectDir(),
                org.nmox.studio.dbstudio.io.DbWorkspaceIO.FILENAME);
        RP.post(() -> {
            org.nmox.studio.dbstudio.io.ExternalEdits.Stamp stamp =
                    org.nmox.studio.dbstudio.io.ExternalEdits.Stamp.of(workspaceFile);
            SwingUtilities.invokeLater(() -> handleExternalStamp(stamp));
        });
    }

    /**
     * Runs one of the modal connection dialogs with the busy flag held,
     * so an external .nmoxdb.json reload can never yank the tree out
     * from under an open dialog; any deferred version is re-checked as
     * soon as the dialog closes. Note the deliberate ordering when the
     * dialog is confirmed: the caller's save runs first, making our
     * version the newest — a foreign edit made WHILE the dialog was
     * open loses to the user's explicit confirmation (last writer
     * wins, exactly like the rest of the studio's persistence).
     */
    private ConnectionSpec showConnectionDialog(
            java.util.function.Supplier<ConnectionSpec> dialog) {
        connectionDialogOpen = true;
        try {
            return dialog.get();
        } finally {
            connectionDialogOpen = false;
            recheckDeferredExternal();
        }
    }

    // ---- the console follows the active connection's engine ----

    private void applyConsoleMime(DbEngine engine) {
        applyConsoleMime(ConsoleMimes.mimeFor(engine.kind()), ConsoleMimes.placeholderFor(engine));
    }

    /**
     * Engine-aware for workspace specs; a Services connection whose
     * dialect DB Studio doesn't model (null engine — Derby, Oracle, …)
     * is still JDBC, so its console speaks SQL.
     */
    private void applyConsoleMimeFor(ConnectionSpec spec) {
        if (spec.engine() != null) {
            applyConsoleMime(spec.engine());
        } else {
            applyConsoleMime(ConsoleMimes.SQL_MIME, "SELECT …;");
        }
    }

    private void applyConsoleMime(String mime, String placeholder) {
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
            console.setText(replaceable ? placeholder : text);
        } else if (replaceable) {
            console.setText(placeholder);
        }
    }

    // ---- connect / disconnect / test ----

    private void toggleConnect() {
        ConnectionSpec spec = selectedConnection();
        if (spec == null) {
            return;
        }
        DefaultMutableTreeNode node = findConnectionNode(spec.id());
        if (isServicesSpec(spec)) {
            // NetBeans owns this connection's lifecycle — no disconnect here
            // (that's the Services window's job); Connect (re)opens and
            // refreshes the container list.
            connect(spec, node, true);
            return;
        }
        DbBackend backend = backends.get(spec.id());
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
            String error;
            List<TableInfo> containers;
            if (backend == null) { // a Services connection that just left the explorer
                error = "Connection no longer exists in the Services window";
                containers = List.of();
            } else {
                error = backend.open();
                containers = error == null ? backend.listContainers() : List.of();
            }
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
        if (spec == null || isServicesSpec(spec)) {
            return; // Services entries: Test is disabled, NetBeans owns the probe
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

    /**
     * The one live backend per spec id; created (with its keychain read)
     * off-EDT. Services specs wrap the NetBeans explorer's connection
     * instead — no keychain involved — and yield null when that
     * connection has just left the Services window (callers surface a
     * "no longer exists" message).
     */
    private DbBackend backendFor(ConnectionSpec spec) {
        return backends.computeIfAbsent(spec.id(), id -> {
            if (id.startsWith(ServicesBackend.ID_PREFIX)) {
                DatabaseConnection connection = serviceConnections.get(id);
                return connection == null ? null : new ServicesBackend(connection);
            }
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
        // the Services branch always trails the workspace connections
        servicesBranchNode = new DefaultMutableTreeNode(ServicesBranch.INSTANCE);
        servicesBranchNode.add(new DefaultMutableTreeNode("Loading…"));
        root.add(servicesBranchNode);
        tree.setModel(new DefaultTreeModel(root));
        refreshActions();
        refreshServicesBranch();
    }

    /**
     * Fills the Services branch from the NetBeans Database Explorer:
     * the connection list is read off-EDT (first touch loads the
     * explorer's registry), specs are synthesized per connection, and
     * the branch refills on the EDT. Runs at every tree rebuild and on
     * every ConnectionManager connectionsChanged event; backends and
     * cached containers of connections that left the explorer are
     * dropped (dropping a {@link ServicesBackend} never closes the
     * underlying connection — NetBeans owns it).
     */
    private void refreshServicesBranch() {
        RP.post(() -> {
            List<ConnectionSpec> synthesized = new ArrayList<>();
            Map<String, DatabaseConnection> byId = new HashMap<>();
            for (DatabaseConnection connection : servicesConnections()) {
                ConnectionSpec spec = ServicesBackend.specFor(connection);
                synthesized.add(spec);
                byId.put(spec.id(), connection);
            }
            SwingUtilities.invokeLater(() -> {
                serviceSpecs.clear();
                serviceSpecs.addAll(synthesized);
                serviceConnections.clear();
                serviceConnections.putAll(byId);
                backends.keySet().removeIf(id ->
                        id.startsWith(ServicesBackend.ID_PREFIX) && !byId.containsKey(id));
                containerCache.keySet().removeIf(id ->
                        id.startsWith(ServicesBackend.ID_PREFIX) && !byId.containsKey(id));
                DefaultMutableTreeNode branch = servicesBranchNode;
                if (branch == null) {
                    return;
                }
                branch.removeAllChildren();
                if (synthesized.isEmpty()) {
                    branch.add(new DefaultMutableTreeNode(
                            "No connections in the Services window yet"));
                } else {
                    for (ConnectionSpec spec : synthesized) {
                        DefaultMutableTreeNode node = new DefaultMutableTreeNode(spec);
                        node.add(new DefaultMutableTreeNode("Loading…"));
                        branch.add(node);
                    }
                }
                ((DefaultTreeModel) tree.getModel()).nodeStructureChanged(branch);
                refreshActions();
            });
        });
    }

    /** The explorer's registered connections; empty when it is unavailable (tests, stripped platform). */
    private static List<DatabaseConnection> servicesConnections() {
        try {
            return Arrays.asList(ConnectionManager.getDefault().getConnections());
        } catch (RuntimeException | LinkageError unavailable) {
            return List.of();
        }
    }

    private static boolean isServicesSpec(ConnectionSpec spec) {
        return spec != null && spec.id().startsWith(ServicesBackend.ID_PREFIX);
    }

    /** The engine badge/history label; Services connections may have no modeled engine. */
    private static String engineLabel(ConnectionSpec spec) {
        return spec.engine() != null ? spec.engine().displayName() : "JDBC";
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
        DefaultMutableTreeNode workspaceHit = findConnectionChild(root, specId);
        if (workspaceHit != null) {
            return workspaceHit;
        }
        DefaultMutableTreeNode branch = servicesBranchNode;
        return branch == null ? null : findConnectionChild(branch, specId);
    }

    private static DefaultMutableTreeNode findConnectionChild(
            DefaultMutableTreeNode parent, String specId) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
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
            applyConsoleMimeFor(spec);
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
        for (ConnectionSpec spec : serviceSpecs) {
            if (spec.id().equals(activeSpecId)) {
                return spec;
            }
        }
        return null;
    }

    private void refreshActions() {
        ConnectionSpec spec = selectedConnection();
        boolean selected = spec != null;
        boolean services = isServicesSpec(spec);
        // Services entries are owned by the NetBeans explorer: no edit,
        // no remove, no test from here — the tooltip says where to go.
        editButton.setEnabled(selected && !services);
        removeButton.setEnabled(selected && !services);
        testButton.setEnabled(selected && !services);
        String managedElsewhere = services ? "Managed in the Services window" : null;
        editButton.setToolTipText(managedElsewhere);
        removeButton.setToolTipText(managedElsewhere);
        testButton.setToolTipText(managedElsewhere);
        connectButton.setEnabled(selected);
        DbBackend backend = selected ? backends.get(spec.id()) : null;
        // Services connections never show Disconnect: NetBeans owns the lifecycle
        connectButton.setText(!services && backend != null && backend.isOpen()
                ? "Disconnect" : "Connect");
        connectButton.setToolTipText(services
                ? "Connect through NetBeans (drivers and credentials live in the Services window)"
                : null);
        // RUN gates on having a target: an always-armed button that silently
        // no-ops reads as broken. The tooltip says why it's off.
        ConnectionSpec active = activeSpec();
        boolean runnable = !running && active != null;
        runButton.setEnabled(runnable);
        runButton.setToolTipText(runnable
                ? "Execute the console against the active connection"
                : running ? "A run is in flight" : "Select a connection first");
        refreshExplain(active);
    }

    /**
     * EXPLAIN arms only when it can actually deliver a plan: a live SQL
     * connection with a modeled dialect and a console holding a
     * SELECT/WITH statement. Every off state says why in the tooltip.
     */
    private void refreshExplain(ConnectionSpec active) {
        DbBackend backend = active == null ? null : backends.get(active.id());
        boolean open = backend != null && backend.isOpen();
        boolean explainable = !running && active != null && open
                && ExplainQueries.explainable(active.engine(), console.getText());
        explainButton.setEnabled(explainable);
        if (explainable) {
            explainButton.setToolTipText("Show the engine's query plan for the console text");
        } else if (running) {
            explainButton.setToolTipText("A run is in flight");
        } else if (active == null) {
            explainButton.setToolTipText("Select a connection first");
        } else if (active.engine() == null
                || active.engine().kind() != DbEngine.Kind.SQL) {
            explainButton.setToolTipText("EXPLAIN applies to SQL engines");
        } else if (!open) {
            explainButton.setToolTipText("Connect first — EXPLAIN needs a live connection");
        } else {
            explainButton.setToolTipText("EXPLAIN applies to SELECT/WITH statements");
        }
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
        ConnectionSpec spec = showConnectionDialog(() -> ConnectionDialog.show(null));
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
        if (spec == null || isServicesSpec(spec)) {
            return; // Services entries are edited in the Services window
        }
        ConnectionSpec updated = showConnectionDialog(() -> ConnectionDialog.show(spec));
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
        if (spec == null || isServicesSpec(spec)) {
            return; // Services entries are removed in the Services window
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
        DbWorkspaceIO.LoadOutcome outcome = DbWorkspaceIO.loadWorkspaceGuarded(projectDir());
        DbWorkspaceIO.Workspace workspace = outcome.workspace();
        if (outcome.backup() != null) {
            // corrupt file: the IO layer copied it aside BEFORE handing us the
            // empty fallback (the next save can't clobber it) — say so
            try {
                org.openide.awt.NotificationDisplayer.getDefault().notify(
                        "Couldn't read " + DbWorkspaceIO.FILENAME + " — starting empty",
                        javax.swing.UIManager.getIcon("OptionPane.warningIcon"),
                        "The unreadable original was kept at " + outcome.backup().getName() + ".",
                        null);
            } catch (RuntimeException | LinkageError ignored) {
                // notifications unavailable (tests, stripped platform)
            }
        }
        specs.addAll(workspace.connections());
        persistedHistory = new ArrayList<>(workspace.history());
        savedQueries = new ArrayList<>(workspace.saved());
        // reseed the History tab from the persisted entries (stored newest
        // first; adding oldest-first rebuilds that order)
        history.clear();
        for (int i = persistedHistory.size() - 1; i >= 0; i--) {
            DbWorkspaceIO.HistoryEntry entry = persistedHistory.get(i);
            history.add(entry.text(), entry.engine(), entry.at());
        }
        refreshHistory();
        refreshSavedCombo();
        rebuildTree();
        publishSearch();
        status(specs.isEmpty() ? " "
                : specs.size() + (specs.size() == 1 ? " connection" : " connections"), Color.GRAY);
        // the freshly loaded version is now "ours" — only later foreign
        // writes should trigger the external-reload flow
        externalEdits.recordOwn(org.nmox.studio.dbstudio.io.ExternalEdits.Stamp.of(
                new File(projectDir(), org.nmox.studio.dbstudio.io.DbWorkspaceIO.FILENAME)));
        offerEnvConnection();
        if (isOpened()) {
            restartWorkspaceWatcher(); // the project dir may have changed
            offerDockerConnections();
        }
    }

    private boolean saveFailureNotified;

    private void saveWorkspace() {
        try {
            File dir = projectDir();
            DbWorkspaceIO.save(dir, new DbWorkspaceIO.Workspace(
                    specs, persistedHistory, savedQueries));
            externalEdits.recordOwn(org.nmox.studio.dbstudio.io.ExternalEdits.Stamp.of(
                    new File(dir, DbWorkspaceIO.FILENAME)));
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

    private void attachServicesListener() {
        if (servicesListenerAttached) {
            return;
        }
        try {
            ConnectionManager.getDefault().addConnectionListener(servicesListener);
            servicesListenerAttached = true;
        } catch (RuntimeException | LinkageError unavailable) {
            // DB explorer absent (tests, stripped platform): the branch stays empty
        }
    }

    @Override
    public void componentOpened() {
        if (!rackListenerAttached) {
            attachRackListener();
        }
        attachServicesListener();
        attachManifestListener();
        restartWorkspaceWatcher();
        refreshServicesBranch(); // the Services list may have changed while closed
        // the Docker offer probe waits for componentShowing — a default-open
        // tab is opened at startup while still hidden behind the others
    }

    /**
     * The tab is actually about to be seen (unlike componentOpened,
     * which fires at startup while this default-open tab is hidden
     * behind the others — the v1.35.0 click-through found the Docker
     * offer balloons expiring unseen there, guard consumed, offer lost
     * for the session). A plan held from a probe that finished while
     * hidden shows now; otherwise a fresh probe runs — its balloons can
     * be seen, so only now may the once-per-container guard be spent.
     */
    @Override
    public void componentShowing() {
        java.util.List<org.nmox.studio.rack.docker.DockerClient.ContainerInfo> held =
                dockerHold.onShowing();
        if (held.isEmpty()) {
            offerDockerConnections();
        } else {
            showDockerOffers(held); // the probe already ran — don't re-probe
        }
    }

    @Override
    public void componentClosed() {
        // a closed tab reacts to nothing: watcher and manifest listener go
        stopWorkspaceWatcher();
        detachManifestListener();
        deferredExternalStamp = null;
        dockerHold.clear(); // a held Docker plan is stale by reopen time
        // drops our Services backends too — their close() is a reference-drop
        // no-op; the NetBeans explorer keeps its connections
        closeAllBackends();
        if (servicesListenerAttached) {
            try {
                ConnectionManager.getDefault().removeConnectionListener(servicesListener);
            } catch (RuntimeException | LinkageError ignored) {
                // already unavailable — nothing to detach from
            }
            servicesListenerAttached = false;
        }
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
                String badge = isServicesSpec(spec)
                        ? (spec.engine() != null
                                ? "Services · " + spec.engine().displayName() : "Services")
                        : spec.engine().displayName();
                setText("<html>" + (connected ? "<b>" : "") + esc(spec.name())
                        + (connected ? "</b>" : "")
                        + " <font color='#8a8a8a'>(" + esc(badge)
                        + ")</font></html>");
            } else if (userObject instanceof ServicesBranch) {
                setText("<html><b>Services</b> <font color='#8a8a8a'>"
                        + "(NetBeans Database Explorer)</font></html>");
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

    /**
     * Tints cells holding uncommitted edits — the dirty color is the
     * table background blended toward amber so it reads on both dark
     * and light themes.
     */
    private static final class DirtyCellRenderer
            extends javax.swing.table.DefaultTableCellRenderer {

        private static final Color AMBER = new Color(0xC9, 0x93, 0x2B);

        private final EditableResultsModel model;

        DirtyCellRenderer(EditableResultsModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus,
                    row, column);
            if (!isSelected) {
                boolean dirty = model.isDirty(table.convertRowIndexToModel(row),
                        table.convertColumnIndexToModel(column));
                setBackground(dirty ? blend(table.getBackground(), AMBER)
                        : table.getBackground());
            }
            return this;
        }

        private static Color blend(Color base, Color tint) {
            return new Color(
                    (base.getRed() * 65 + tint.getRed() * 35) / 100,
                    (base.getGreen() * 65 + tint.getGreen() * 35) / 100,
                    (base.getBlue() * 65 + tint.getBlue() * 35) / 100);
        }
    }

    /** Saved-query combo entries: the name, plus a grey engine badge. */
    private static final class SavedQueryRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof DbWorkspaceIO.SavedQuery query) {
                setText("<html>" + esc(query.name())
                        + (query.engine().isEmpty() ? ""
                                : " <font color='#8a8a8a'>[" + esc(query.engine()) + "]</font>")
                        + "</html>");
                setToolTipText(query.text());
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

    /**
     * User object heading the Services branch — a marker type, because
     * a plain String would render as a grey placeholder and read as
     * loading state.
     */
    private static final class ServicesBranch {

        static final ServicesBranch INSTANCE = new ServicesBranch();

        private ServicesBranch() {
        }
    }
}
