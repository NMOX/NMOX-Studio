package org.nmox.studio.dbstudio.ui;

import org.nmox.studio.core.util.PlainText;
import org.nmox.studio.core.util.PlainTables;
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
import org.openide.DialogDescriptor;
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
    // Cmd+Alt (DA-) — the studio row lives in the one digit family no
    // shipped module claims. The old chord opened a platform window
    // instead of this one: ⇧⌘7 was the platform's Properties window. Keymaps-profile
    // registrations beat Shortcuts-folder ones, so a layer-only audit
    // misses these; WindowShortcutsTest pins the reserved list.
    @ActionReference(path = "Shortcuts", name = "DA-7")
})
@TopComponent.OpenActionRegistration(displayName = "#CTL_DbStudioAction",
        preferredID = "DbStudioTopComponent")
@Messages({
    "CTL_DbStudioAction=DB Studio",
    "CTL_DbStudioTopComponent=DB Studio",
    "HINT_DbStudioTopComponent=Database management: connections, schema tree, SQL/document console",
    // chrome (shift-2970): every user-visible English string of this window.
    // Numeric arguments are formatted {n,number,0}: MessageFormat would
    // otherwise group digits (1,234 ms) where the old concatenation printed 1234.
    "DbStudioTopComponent_treeA11y=Connections and schema",
    "DbStudioTopComponent_limitA11y=Row limit",
    "DbStudioTopComponent_savedComboA11y=Saved queries",
    "DbStudioTopComponent_historyA11y=Query history",
    "DbStudioTopComponent_statementResultA11y=Statement result",
    "DbStudioTopComponent_resultRowsA11y=Result rows",
    "DbStudioTopComponent_messageA11y=Message",
    "DbStudioTopComponent_add=Add",
    "DbStudioTopComponent_edit=Edit",
    "DbStudioTopComponent_remove=Remove",
    "DbStudioTopComponent_test=Test",
    "DbStudioTopComponent_connect=Connect",
    "DbStudioTopComponent_disconnect=Disconnect",
    "DbStudioTopComponent_run=RUN",
    "DbStudioTopComponent_explainButton=EXPLAIN",
    "DbStudioTopComponent_cancel=Cancel",
    "DbStudioTopComponent_saveQuery=Save…",
    "DbStudioTopComponent_manageSaved=Manage…",
    "DbStudioTopComponent_runTooltip=Execute the console against the active connection",
    "DbStudioTopComponent_limitLabel= Limit: ",
    "DbStudioTopComponent_limitTooltip=Fetch at most this many rows per result set",
    "DbStudioTopComponent_cancelTooltip=Best-effort cancel of the running statement",
    "DbStudioTopComponent_saveQueryTooltip=Save the console text as a named query (.nmoxdb.json)",
    "DbStudioTopComponent_savedComboTooltip=Saved queries — selecting one loads it into the console",
    "DbStudioTopComponent_historyTooltip=Double-click an entry to load it back into the console",
    "DbStudioTopComponent_loadedFromHistory=Loaded from history",
    "DbStudioTopComponent_history=History",
    "DbStudioTopComponent_peekOtherDatabase=Console queries \"{0}\" — Edit the connection''s database to \"{1}\" to query it",
    "DbStudioTopComponent_consoleEmpty=Console is empty — type a query first",
    "DbStudioTopComponent_selectConnectionFirst=Select a connection first",
    "DbStudioTopComponent_nothingToRun=Nothing to run",
    "DbStudioTopComponent_running=Running…",
    "DbStudioTopComponent_connectionGone=Connection no longer exists in the Services window",
    "DbStudioTopComponent_cancelRequested=Cancel requested…",
    "DbStudioTopComponent_statements={0,choice,0#{0,number,0} statements|1#{0,number,0} statement|1<{0,number,0} statements}",
    "DbStudioTopComponent_failedCount= · {0,number,0} failed",
    "DbStudioTopComponent_totalMs= · {0,number,0} ms",
    "DbStudioTopComponent_rowsCapped={0,number,0}+ rows",
    "DbStudioTopComponent_rows={0,choice,0#{0,number,0} rows|1#{0,number,0} row|1<{0,number,0} rows}",
    "DbStudioTopComponent_updated={0,number,0} updated",
    "DbStudioTopComponent_readOnly=Read-only",
    "DbStudioTopComponent_rowsAffected={0,number,0} row(s) affected",
    "DbStudioTopComponent_noPendingEdits=No pending edits",
    "DbStudioTopComponent_editsPending={0,choice,0#{0,number,0} edits pending|1#{0,number,0} edit pending|1<{0,number,0} edits pending}",
    "DbStudioTopComponent_apply=Apply…",
    "DbStudioTopComponent_applyTooltip=Preview and run the UPDATE statements for the pending edits",
    "DbStudioTopComponent_revert=Revert",
    "DbStudioTopComponent_revertTooltip=Forget every pending edit",
    "DbStudioTopComponent_editableGridTooltip=Double-click a cell to edit; Apply… previews the UPDATEs first",
    "DbStudioTopComponent_csv=CSV",
    "DbStudioTopComponent_csvTooltip=Export this grid as CSV (UTF-8)",
    "DbStudioTopComponent_json=JSON",
    "DbStudioTopComponent_jsonTooltip=Export this grid as JSON (UTF-8)",
    "DbStudioTopComponent_noRowsToExport=No rows to export",
    "DbStudioTopComponent_exportCsvTitle=Export as CSV",
    "DbStudioTopComponent_exportJsonTitle=Export as JSON",
    "DbStudioTopComponent_exportedTo=Exported {0,number,0} row(s) → {1}",
    "DbStudioTopComponent_exportedRows=Exported {0,number,0} row(s)",
    "DbStudioTopComponent_exportFailedWith=Export failed: {0}",
    "DbStudioTopComponent_exportFailed=Export failed",
    "DbStudioTopComponent_runInFlightRetry=A run is in flight — try again when it finishes",
    "DbStudioTopComponent_applying=Applying {0,number,0} update(s)…",
    "DbStudioTopComponent_statementDidNotExecute=The statement did not execute",
    "DbStudioTopComponent_zeroRowsMatched=0 rows matched — the row may have changed since the grid loaded. Re-run the query and try again.",
    "DbStudioTopComponent_applyStopped=Apply stopped: {0}",
    "DbStudioTopComponent_applyStoppedAfter=Apply stopped after {0,number,0} of {1,number,0} update(s)",
    "DbStudioTopComponent_editsKept={0} — your edits are kept; fix and retry.",
    "DbStudioTopComponent_appliedRefreshed=Applied {0,number,0} update(s) — grid refreshed",
    "DbStudioTopComponent_appliedTo=Applied {0,number,0} update(s) to {1}",
    "DbStudioTopComponent_headerMs=   ·   {0,number,0} ms",
    "DbStudioTopComponent_headerFirstRows=   ·   first {0,number,0} rows only",
    "DbStudioTopComponent_nothingToSave=Nothing to save — the console is empty",
    "DbStudioTopComponent_nameInput=Name:",
    "DbStudioTopComponent_saveQueryTitle=Save Query",
    "DbStudioTopComponent_savedQueryNeedsName=A saved query needs a name",
    "DbStudioTopComponent_saved=Saved \"{0}\"",
    "DbStudioTopComponent_loaded=Loaded \"{0}\"",
    "DbStudioTopComponent_rename=Rename…",
    "DbStudioTopComponent_delete=Delete…",
    "DbStudioTopComponent_newNameInput=New name:",
    "DbStudioTopComponent_renameSavedQueryTitle=Rename Saved Query",
    "DbStudioTopComponent_savedQueryExists=A saved query named \"{0}\" already exists",
    "DbStudioTopComponent_renamedTo=Renamed to \"{0}\"",
    "DbStudioTopComponent_deleteSavedQueryConfirm=Delete saved query \"{0}\"?",
    "DbStudioTopComponent_deleteSavedQueryTitle=Delete Saved Query",
    "DbStudioTopComponent_deleted=Deleted \"{0}\"",
    "DbStudioTopComponent_manageSavedQueriesTitle=Manage Saved Queries",
    "DbStudioTopComponent_noSavedQueries=No saved queries",
    "DbStudioTopComponent_savedQueriesPrompt=Saved queries…",
    "DbStudioTopComponent_envConfigFound=Found database config in .env",
    "DbStudioTopComponent_envOffer=Create a \"{0}\" ({1}) connection? Click to review — nothing is saved until you confirm.",
    "DbStudioTopComponent_addedFromEnv=Added {0} from .env",
    "DbStudioTopComponent_dockerContainerRunning=Database container running in Docker",
    "DbStudioTopComponent_clickToReview= Click to review — nothing is saved until you confirm.",
    "DbStudioTopComponent_addedFromDocker=Added {0} from Docker",
    "DbStudioTopComponent_reloaded=Reloaded {0}",
    "DbStudioTopComponent_reloadedDetail=The file changed outside DB Studio — connections, history and saved queries follow it.",
    "DbStudioTopComponent_loading=Loading…",
    "DbStudioTopComponent_disconnected=Disconnected {0}",
    "DbStudioTopComponent_connectingTo=Connecting to {0}…",
    "DbStudioTopComponent_connectFailed=Connect failed: {0}",
    "DbStudioTopComponent_notConnected=not connected",
    "DbStudioTopComponent_connected=Connected: {0} — {1,number,0} {2}",
    "DbStudioTopComponent_tableNoun={0,choice,0#tables|1#table|1<tables}",
    "DbStudioTopComponent_testing=Testing {0}…",
    "DbStudioTopComponent_reachable=OK: {0} is reachable",
    "DbStudioTopComponent_testFailed=Test failed: {0}",
    "DbStudioTopComponent_testReachable=Test {0}: reachable",
    "DbStudioTopComponent_testFailedFor=Test {0}: failed",
    "DbStudioTopComponent_connectionsRoot=Connections",
    "DbStudioTopComponent_noConnectionsYet=No connections yet — click Add below to create one",
    "DbStudioTopComponent_firstRunHint=Add a connection, then double-click a table to peek at its data",
    "DbStudioTopComponent_noServicesConnections=No connections in the Services window yet",
    "DbStudioTopComponent_empty=(empty)",
    "DbStudioTopComponent_noColumns=(no columns)",
    "DbStudioTopComponent_managedInServices=Managed in the Services window",
    "DbStudioTopComponent_connectThroughNetBeans=Connect through NetBeans (drivers and credentials live in the Services window)",
    "DbStudioTopComponent_runInFlight=A run is in flight",
    "DbStudioTopComponent_explainTooltip=Show the engine's query plan for the console text",
    "DbStudioTopComponent_explainSqlOnly=EXPLAIN applies to SQL engines",
    "DbStudioTopComponent_explainNeedsConnection=Connect first — EXPLAIN needs a live connection",
    "DbStudioTopComponent_explainSelectOnly=EXPLAIN applies to SELECT/WITH statements",
    "DbStudioTopComponent_addedConnection=Added \"{0}\" — Connect opens it",
    "DbStudioTopComponent_updatedConnection=Updated \"{0}\"",
    "DbStudioTopComponent_removeConnectionConfirm=Remove connection \"{0}\"? Its stored password is deleted too.",
    "DbStudioTopComponent_removeConnectionTitle=Remove Connection",
    "DbStudioTopComponent_removedConnection=Removed {0}",
    "DbStudioTopComponent_couldNotRead=Couldn''t read {0} — starting empty",
    "DbStudioTopComponent_backupKept=The unreadable original was kept at {0}.",
    "DbStudioTopComponent_connectionCount={0,choice,0#{0,number,0} connections|1#{0,number,0} connection|1<{0,number,0} connections}",
    "DbStudioTopComponent_cannotSave=DB Studio can't save its connections",
    "DbStudioTopComponent_notPersisted=Changes are not being persisted: {0}",
    "DbStudioTopComponent_explain=Explain…",
    "DbStudioTopComponent_explainErrorTooltip=Ask KVASIR what this database error means (sends the statement and the error — you confirm first)",
    "DbStudioTopComponent_kvasirDidNotRun=KVASIR did not run — needs an API key and your consent.",
    "DbStudioTopComponent_services=Services",
    "DbStudioTopComponent_servicesBadge=Services · {0}",
    "DbStudioTopComponent_servicesBranch=<html><b>Services</b> <font color='#8a8a8a'>(NetBeans Database Explorer)</font></html>",
    // the KVASIR conversation's own title; the disclosure body and question
    // beneath it are prompt text sent to the model, not chrome.
    "DbStudioTopComponent_kvasirTitle={0} error"
})
public final class DbStudioTopComponent extends TopComponent {

    /** The module's worker pool — all engine calls run here, never on the EDT. */
    static final RequestProcessor RP = new RequestProcessor("DB Studio", 3);

    /**
     * Workspace writes ride their own single-throughput lane, NOT
     * {@link #RP} — RP's throughput 3 could interleave two writes, and
     * a close flush must never queue behind a long query (debt #16; the
     * careful parts are documented on the lane class).
     */
    private static final org.nmox.studio.dbstudio.io.SaveLane SAVES =
            new org.nmox.studio.dbstudio.io.SaveLane("DB Studio workspace saves");

    private static final Color OK_GREEN = new Color(0x4E, 0xC9, 0x8B);
    private static final Color FAIL_RED = new Color(0xE2, 0x4B, 0x4A);
    private static final Color ACCENT = new Color(0x1D, 0x9E, 0x75);
    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    // a history row is read by a person, so its clock follows their language
    // (v2.104.0) — resolved per paint, so a live switch is picked up

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
    {
        tree.getAccessibleContext().setAccessibleName(Bundle.DbStudioTopComponent_treeA11y());
    }
    private final JButton editButton = new JButton(Bundle.DbStudioTopComponent_edit());
    private final JButton removeButton = new JButton(Bundle.DbStudioTopComponent_remove());
    private final JButton testButton = new JButton(Bundle.DbStudioTopComponent_test());
    private final JButton connectButton = new JButton(Bundle.DbStudioTopComponent_connect());

    private final JEditorPane console = new JEditorPane();
    private final JButton runButton = new JButton(Bundle.DbStudioTopComponent_run());
    private final JButton explainButton = new JButton(Bundle.DbStudioTopComponent_explainButton());
    private final JButton cancelButton = new JButton(Bundle.DbStudioTopComponent_cancel());
    private final JButton saveQueryButton = new JButton(Bundle.DbStudioTopComponent_saveQuery());
    private final JComboBox<Object> savedCombo = new JComboBox<>();
    /** The combo's trailing action row: opens the Manage dialog (v1.266.0). */
    private static final String MANAGE_SAVED = Bundle.DbStudioTopComponent_manageSaved();
    /** Guards the saved-combo's action listener during programmatic refills. */
    private boolean savedComboRefreshing;
    private final JSpinner limitSpinner = new JSpinner(new SpinnerNumberModel(200, 1, 1_000_000, 100));
    {
        limitSpinner.getAccessibleContext().setAccessibleName(Bundle.DbStudioTopComponent_limitA11y());
    }
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

    private final org.nmox.studio.core.spi.ProjectAim.Listener rackListener;
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
    /** What an empty console currently shows; never runnable (v1.266.0). */
    private String consolePlaceholder = "SELECT \u2026;";

    public DbStudioTopComponent() {
        savedCombo.getAccessibleContext().setAccessibleName(Bundle.DbStudioTopComponent_savedComboA11y());
        historyList.getAccessibleContext().setAccessibleName(Bundle.DbStudioTopComponent_historyA11y());
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

        rackListener = new org.nmox.studio.core.spi.ProjectAim.Listener() {
            @Override
            public void projectChanged() {
                SwingUtilities.invokeLater(DbStudioTopComponent.this::reloadWorkspace);
            }
        };
        attachRackListener();
        attachServicesListener();
        // no workspace read here: the constructor runs during window-system
        // deserialization; componentOpened owns the initial load
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
        JButton addButton = new JButton(Bundle.DbStudioTopComponent_add());
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
        // ledger 75: with the tab sharing a row with a wide neighbor, the
        // one-row toolbar clipped Save… and the saved-queries combo off the
        // right edge. WrapLayout reports the WRAPPED height, so at narrow
        // widths the bar flows onto a second row and every verb stays
        // reachable — no menu consolidation, RUN/EXPLAIN/Cancel keep
        // their one-click place.
        bar.setLayout(new org.nmox.studio.core.util.WrapLayout(
                java.awt.FlowLayout.LEFT, 4, 2));
        runButton.setForeground(ACCENT);
        runButton.setToolTipText(Bundle.DbStudioTopComponent_runTooltip());
        runButton.addActionListener(e -> run());
        bar.add(runButton);
        explainButton.setEnabled(false);
        explainButton.addActionListener(e -> explain());
        bar.add(explainButton);
        bar.addSeparator();
        bar.add(new JLabel(Bundle.DbStudioTopComponent_limitLabel()));
        limitSpinner.setMaximumSize(limitSpinner.getPreferredSize());
        limitSpinner.setToolTipText(Bundle.DbStudioTopComponent_limitTooltip());
        bar.add(limitSpinner);
        bar.addSeparator();
        cancelButton.setEnabled(false);
        cancelButton.setToolTipText(Bundle.DbStudioTopComponent_cancelTooltip());
        cancelButton.addActionListener(e -> cancel());
        bar.add(cancelButton);
        bar.addSeparator();
        saveQueryButton.setToolTipText(Bundle.DbStudioTopComponent_saveQueryTooltip());
        saveQueryButton.addActionListener(e -> saveCurrentQuery());
        bar.add(saveQueryButton);
        savedCombo.setRenderer(new SavedQueryRenderer());
        savedCombo.setToolTipText(Bundle.DbStudioTopComponent_savedComboTooltip());
        savedCombo.addActionListener(e -> savedQueryPicked());
        bar.add(savedCombo);
        bar.addSeparator();
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        bar.add(statusLabel);
        panel.add(bar, BorderLayout.NORTH);

        console.setFont(MONO);
        // The placeholder is a HINT, not content: it vanishes the moment
        // the console takes focus and returns only to a blank console, so
        // it can never ride along into a RUN (v1.266.0 — the DBA persona
        // walk's first query executed the untouched "SELECT \u2026;" as
        // statement 1 and reported a failure the user never wrote).
        console.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                if (ConsoleMimes.shouldClearOnFocus(console.getText())) {
                    console.setText("");
                }
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (ConsoleMimes.shouldRestoreOnBlur(console.getText())) {
                    console.setText(consolePlaceholder);
                }
            }
        });
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
        historyList.setToolTipText(Bundle.DbStudioTopComponent_historyTooltip());
        historyList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int index = historyList.locationToIndex(e.getPoint());
                if (e.getClickCount() == 2 && index >= 0) {
                    console.setText(historyModel.get(index).text());
                    status(Bundle.DbStudioTopComponent_loadedFromHistory(), Color.GRAY);
                }
            }
        });
        // History stays the LAST tab; result tabs are inserted before it per run
        resultsTabs.addTab(Bundle.DbStudioTopComponent_history(), new JScrollPane(historyList));
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
            status(Bundle.DbStudioTopComponent_peekOtherDatabase(spec.database(), info.name()), FAIL_RED);
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
        if (ConsoleMimes.isPlaceholderOrBlank(console.getText())) {
            status(Bundle.DbStudioTopComponent_consoleEmpty(), Color.GRAY);
            return;
        }
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
            status(Bundle.DbStudioTopComponent_selectConnectionFirst(), FAIL_RED);
            return;
        }
        if (ConsoleMimes.isPlaceholderOrBlank(text)) {
            status(Bundle.DbStudioTopComponent_nothingToRun(), FAIL_RED);
            return;
        }
        int limit = (Integer) limitSpinner.getValue();
        running = true;
        refreshActions();
        cancelButton.setEnabled(true);
        status(Bundle.DbStudioTopComponent_running(), Color.GRAY);
        long started = System.currentTimeMillis();
        RP.post(() -> {
            DbBackend backend = backendFor(spec);
            List<QueryResult> results;
            if (backend == null) { // a Services connection that just left the explorer
                results = List.of(new QueryResult(List.of(), List.of(), 0, -1, false, 0,
                        Bundle.DbStudioTopComponent_connectionGone(), text));
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
            status(Bundle.DbStudioTopComponent_cancelRequested(), Color.GRAY);
        }
    }

    /**
     * Drops every result tab, keeping the trailing History tab. Shared
     * by a fresh run and by a workspace re-aim — a result (and anything
     * Explain could disclose from it) belongs to the workspace that
     * produced it.
     */
    private void clearResultTabs() {
        while (resultsTabs.getTabCount() > 1) {
            resultsTabs.removeTabAt(0); // everything but the trailing History tab
        }
    }

    private void showResults(ConnectionSpec spec, List<TabContent> tabs, long totalMs) {
        clearResultTabs();
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
        String summary = Bundle.DbStudioTopComponent_statements(tabs.size())
                + (failed > 0 ? Bundle.DbStudioTopComponent_failedCount(failed) : "")
                + Bundle.DbStudioTopComponent_totalMs(totalMs);
        status(summary, failed > 0 ? FAIL_RED : OK_GREEN);
    }

    private static String tabTitle(int index, QueryResult result) {
        String base = "#" + (index + 1);
        if (result.isError()) {
            return base + " ✗";
        }
        if (result.isResultSet()) {
            return base + " · " + (result.truncated() ? Bundle.DbStudioTopComponent_rowsCapped(result.rowCount()) : Bundle.DbStudioTopComponent_rows(result.rowCount()));
        }
        return base + " · " + Bundle.DbStudioTopComponent_updated(result.updateCount());
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
        JLabel header = new JLabel(PlainText.plain(headerText(result)));
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
                JTable table = org.nmox.studio.core.util.PlainTables
                        .disableHtml(new JTable(new ResultsTableModel(result)));
                table.setFont(MONO);
                table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); // wide results scroll, not squash
                String reason = decision == null ? Bundle.DbStudioTopComponent_readOnly() : decision.reason();
                table.setToolTipText(PlainText.plain(reason));
                panel.add(new JScrollPane(table), BorderLayout.CENTER);
                panel.add(readOnlyStrip(result, reason), BorderLayout.SOUTH);
            }
        } else {
            JTextArea message = new JTextArea(result.isError()
                    ? result.error()
                    : Bundle.DbStudioTopComponent_rowsAffected(result.updateCount()));
            message.getAccessibleContext().setAccessibleName(Bundle.DbStudioTopComponent_statementResultA11y());
            message.setEditable(false);
            message.setFont(MONO);
            message.setLineWrap(true);
            message.setWrapStyleWord(true);
            if (result.isError()) {
                message.setForeground(FAIL_RED);
            }
            panel.add(new JScrollPane(message), BorderLayout.CENTER);
            // KVASIR is a SOFT dependency (ledger 30): no rack in the
            // platform means the lookup misses and no button appears
            if (result.isError() && org.nmox.studio.core.spi.KvasirAsk.find() != null) {
                panel.add(explainStrip(spec, result), BorderLayout.SOUTH);
            }
        }
        panel.revalidate();
        panel.repaint();
    }

    /** The grid plus the edit strip: dirty-cell tint, pending chip, Apply…/Revert, exports. */
    private JComponent editableGrid(JPanel tabPanel, ConnectionSpec spec, TabContent content) {
        EditSession session = content.decision().session();
        JLabel chip = new JLabel(Bundle.DbStudioTopComponent_noPendingEdits());
        chip.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        JButton applyButton = new JButton(Bundle.DbStudioTopComponent_apply());
        applyButton.setEnabled(false);
        applyButton.setToolTipText(Bundle.DbStudioTopComponent_applyTooltip());
        JButton revertButton = new JButton(Bundle.DbStudioTopComponent_revert());
        revertButton.setEnabled(false);
        revertButton.setToolTipText(Bundle.DbStudioTopComponent_revertTooltip());
        EditableResultsModel model = new EditableResultsModel(session, () -> {
            int dirty = session.dirtyCount();
            chip.setText(PlainText.plain(dirty == 0 ? Bundle.DbStudioTopComponent_noPendingEdits()
                    : Bundle.DbStudioTopComponent_editsPending(dirty)));
            chip.setForeground(dirty == 0 ? Color.GRAY : ACCENT);
            applyButton.setEnabled(dirty > 0);
            revertButton.setEnabled(dirty > 0);
        });
        JTable table = new JTable(model);
        table.setFont(MONO);
        table.getAccessibleContext().setAccessibleName(Bundle.DbStudioTopComponent_resultRowsA11y());
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        // editable grid shows external DB cell values — keep markup literal
        table.setDefaultRenderer(String.class,
                org.nmox.studio.core.util.PlainTables.plain(new DirtyCellRenderer(model)));
        table.setToolTipText(Bundle.DbStudioTopComponent_editableGridTooltip());
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
        JLabel why = new JLabel(PlainText.plain(reason));
        why.setForeground(Color.GRAY);
        why.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        strip.add(why);
        strip.add(javax.swing.Box.createHorizontalGlue());
        addExportButtons(strip, result);
        return strip;
    }

    private void addExportButtons(JToolBar strip, QueryResult result) {
        boolean hasRows = result.rowCount() > 0;
        JButton csvButton = new JButton(Bundle.DbStudioTopComponent_csv());
        csvButton.setEnabled(hasRows);
        csvButton.setToolTipText(PlainText.plain(hasRows ? Bundle.DbStudioTopComponent_csvTooltip()
                : Bundle.DbStudioTopComponent_noRowsToExport()));
        csvButton.addActionListener(e -> exportResult(result, true));
        JButton jsonButton = new JButton(Bundle.DbStudioTopComponent_json());
        jsonButton.setEnabled(hasRows);
        jsonButton.setToolTipText(PlainText.plain(hasRows ? Bundle.DbStudioTopComponent_jsonTooltip()
                : Bundle.DbStudioTopComponent_noRowsToExport()));
        jsonButton.addActionListener(e -> exportResult(result, false));
        strip.add(csvButton);
        strip.add(jsonButton);
    }

    /** Save-dialog then off-EDT write; the balloon carries the full path. */
    private void exportResult(QueryResult result, boolean csv) {
        String extension = csv ? ".csv" : ".json";
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(csv ? Bundle.DbStudioTopComponent_exportCsvTitle() : Bundle.DbStudioTopComponent_exportJsonTitle());
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
                    status(Bundle.DbStudioTopComponent_exportedTo(result.rowCount(), target.getName()), OK_GREEN);
                    balloon(Bundle.DbStudioTopComponent_exportedRows(result.rowCount()),
                            target.getAbsolutePath(), true);
                });
            } catch (IOException | RuntimeException ex) {
                SwingUtilities.invokeLater(() -> {
                    status(Bundle.DbStudioTopComponent_exportFailedWith(ex.getMessage()), FAIL_RED);
                    balloon(Bundle.DbStudioTopComponent_exportFailed(), ex.getMessage(), false);
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
            status(Bundle.DbStudioTopComponent_runInFlightRetry(), FAIL_RED);
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
        status(Bundle.DbStudioTopComponent_applying(statements.size()), Color.GRAY);
        RP.post(() -> {
            DbBackend backend = backendFor(spec);
            String failure = null;
            int applied = 0;
            if (backend == null) {
                failure = Bundle.DbStudioTopComponent_connectionGone();
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
                                    ? Bundle.DbStudioTopComponent_statementDidNotExecute() : first.error();
                            break;
                        }
                        // Each UPDATE is PK-scoped to exactly one row; a
                        // count of 0 means the row changed/vanished under
                        // the grid since it loaded. Counting that as
                        // "applied" would report a success the DB never
                        // performed — the edit is silently lost.
                        if (first.updateCount() == 0) {
                            failure = Bundle.DbStudioTopComponent_zeroRowsMatched();
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
                    status(Bundle.DbStudioTopComponent_applyStopped(reason), FAIL_RED);
                    balloon(Bundle.DbStudioTopComponent_applyStoppedAfter(done, statements.size()),
                            Bundle.DbStudioTopComponent_editsKept(reason),
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
                status(Bundle.DbStudioTopComponent_appliedRefreshed(statements.size()),
                        OK_GREEN);
                balloon(Bundle.DbStudioTopComponent_appliedTo(statements.size(), session.table().name()), null, true);
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
        sb.append(Bundle.DbStudioTopComponent_headerMs(result.elapsedMs()));
        if (result.isResultSet() && result.truncated()) {
            sb.append(Bundle.DbStudioTopComponent_headerFirstRows(result.rowCount()));
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
            status(Bundle.DbStudioTopComponent_nothingToSave(), FAIL_RED);
            return;
        }
        NotifyDescriptor.InputLine input =
                new NotifyDescriptor.InputLine(Bundle.DbStudioTopComponent_nameInput(), Bundle.DbStudioTopComponent_saveQueryTitle());
        input.setInputText(WorkspaceEdits.defaultName(text));
        if (DialogDisplayer.getDefault().notify(input) != NotifyDescriptor.OK_OPTION) {
            return;
        }
        String name = input.getInputText().strip();
        if (name.isEmpty()) {
            status(Bundle.DbStudioTopComponent_savedQueryNeedsName(), FAIL_RED);
            return;
        }
        ConnectionSpec spec = activeSpec();
        savedQueries = WorkspaceEdits.withSaved(savedQueries,
                new DbWorkspaceIO.SavedQuery(name, text, spec == null ? "" : engineLabel(spec)));
        saveWorkspace();
        refreshSavedCombo();
        status(Bundle.DbStudioTopComponent_saved(name), OK_GREEN);
    }

    /** Selecting a saved query loads it into the console; the combo snaps back to its label. */
    private void savedQueryPicked() {
        if (savedComboRefreshing) {
            return;
        }
        Object picked = savedCombo.getSelectedItem();
        if (picked instanceof DbWorkspaceIO.SavedQuery query) {
            console.setText(query.text());
            status(Bundle.DbStudioTopComponent_loaded(query.name()), Color.GRAY);
        } else if (MANAGE_SAVED.equals(picked)) {
            SwingUtilities.invokeLater(this::manageSavedQueries);
        }
        if (picked instanceof DbWorkspaceIO.SavedQuery || MANAGE_SAVED.equals(picked)) {
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

    /**
     * The Manage Saved Queries dialog: a list plus Rename/Delete. All
     * list edits go through {@link WorkspaceEdits} (pure, test-pinned);
     * Delete defaults to the SAFE button per the v1.98.0 idiom, and a
     * rename that would collide with another saved query is refused
     * with a status line rather than silently destroying it.
     */
    private void manageSavedQueries() {
        javax.swing.DefaultListModel<DbWorkspaceIO.SavedQuery> model =
                new javax.swing.DefaultListModel<>();
        savedQueries.forEach(model::addElement);
        javax.swing.JList<DbWorkspaceIO.SavedQuery> list = new javax.swing.JList<>(model);
        list.setCellRenderer(new SavedQueryRenderer());
        list.setVisibleRowCount(8);
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }
        JButton rename = new JButton(Bundle.DbStudioTopComponent_rename());
        JButton delete = new JButton(Bundle.DbStudioTopComponent_delete());
        Runnable syncButtons = () -> {
            boolean has = list.getSelectedValue() != null;
            rename.setEnabled(has);
            delete.setEnabled(has);
        };
        list.addListSelectionListener(e -> syncButtons.run());
        syncButtons.run();
        rename.addActionListener(e -> {
            DbWorkspaceIO.SavedQuery q = list.getSelectedValue();
            if (q == null) {
                return;
            }
            NotifyDescriptor.InputLine input =
                    new NotifyDescriptor.InputLine(Bundle.DbStudioTopComponent_newNameInput(), Bundle.DbStudioTopComponent_renameSavedQueryTitle());
            input.setInputText(q.name());
            if (DialogDisplayer.getDefault().notify(input) != NotifyDescriptor.OK_OPTION) {
                return;
            }
            String newName = input.getInputText().strip();
            if (newName.isEmpty() || newName.equals(q.name())) {
                return;
            }
            java.util.List<DbWorkspaceIO.SavedQuery> renamed =
                    WorkspaceEdits.withRenamed(savedQueries, q.name(), newName);
            if (renamed.equals(savedQueries)) {
                status(Bundle.DbStudioTopComponent_savedQueryExists(newName), FAIL_RED);
                return;
            }
            savedQueries = renamed;
            saveWorkspace();
            refreshSavedCombo();
            int index = list.getSelectedIndex();
            model.clear();
            savedQueries.forEach(model::addElement);
            list.setSelectedIndex(Math.min(index, model.size() - 1));
            status(Bundle.DbStudioTopComponent_renamedTo(newName), OK_GREEN);
        });
        delete.addActionListener(e -> {
            DbWorkspaceIO.SavedQuery q = list.getSelectedValue();
            if (q == null) {
                return;
            }
            // full ctor so Enter lands on the SAFE option (v1.98.0 law)
            NotifyDescriptor confirm = new NotifyDescriptor(
                    org.nmox.studio.core.util.PlainDialogs.plain(Bundle.DbStudioTopComponent_deleteSavedQueryConfirm(q.name()), Bundle.DbStudioTopComponent_messageA11y()),
                    Bundle.DbStudioTopComponent_deleteSavedQueryTitle(),
                    NotifyDescriptor.YES_NO_OPTION,
                    NotifyDescriptor.QUESTION_MESSAGE,
                    null,
                    NotifyDescriptor.NO_OPTION);
            if (DialogDisplayer.getDefault().notify(confirm) != NotifyDescriptor.YES_OPTION) {
                return;
            }
            savedQueries = WorkspaceEdits.withoutSaved(savedQueries, q.name());
            saveWorkspace();
            refreshSavedCombo();
            int index = list.getSelectedIndex();
            model.removeElement(q);
            if (!model.isEmpty()) {
                list.setSelectedIndex(Math.min(index, model.size() - 1));
            }
            syncButtons.run();
            status(Bundle.DbStudioTopComponent_deleted(q.name()), Color.GRAY);
        });
        JPanel south = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        south.add(rename);
        south.add(delete);
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        panel.add(south, BorderLayout.SOUTH);
        DialogDescriptor dd = new DialogDescriptor(panel, Bundle.DbStudioTopComponent_manageSavedQueriesTitle(),
                true, new Object[]{DialogDescriptor.CLOSED_OPTION}, DialogDescriptor.CLOSED_OPTION,
                DialogDescriptor.DEFAULT_ALIGN, null, null);
        DialogDisplayer.getDefault().notify(dd);
    }

    private void refreshSavedCombo() {
        savedComboRefreshing = true;
        try {
            savedCombo.removeAllItems();
            savedCombo.addItem(savedQueries.isEmpty()
                    ? Bundle.DbStudioTopComponent_noSavedQueries() : Bundle.DbStudioTopComponent_savedQueriesPrompt());
            for (DbWorkspaceIO.SavedQuery query : savedQueries) {
                savedCombo.addItem(query);
            }
            if (!savedQueries.isEmpty()) {
                // renaming/deleting lives behind one row instead of a
                // context menu a combo popup cannot host (v1.266.0 — the
                // DBA persona walk found saved queries could only ever be
                // recalled; a typo'd name lived forever unless the user
                // hand-edited .nmoxdb.json)
                savedCombo.addItem(MANAGE_SAVED);
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
        // once-per-project guard on the EDT (the set is EDT-confined); the
        // stat + read + parse ride RP — the other half of ledger 54 M5, so
        // a slow/networked .env never touches the paint thread. Claiming
        // the guard before the read keeps the offer idempotent even if two
        // reloads race; a missing .env "wastes" the claim, which is fine —
        // there is nothing to offer for that project anyway.
        if (!envOfferedProjects.add(dir.getAbsolutePath())) {
            return;
        }
        RP.post(() -> {
            File envFile = new File(dir, ".env");
            if (!envFile.isFile()) {
                return;
            }
            String content;
            try {
                content = Files.readString(envFile.toPath(), StandardCharsets.UTF_8);
            } catch (IOException unreadable) {
                return; // no signal, no offer
            }
            EnvConnections.fromEnv(content).ifPresent(suggestion ->
                    SwingUtilities.invokeLater(() -> maybeOfferEnv(suggestion)));
        });
    }

    /** EDT: shows the .env offer if it isn't already a workspace connection. */
    private void maybeOfferEnv(EnvConnections.Suggestion suggestion) {
        if (WorkspaceEdits.alreadyConfigured(suggestion, specs)) {
            return;
        }
        org.openide.awt.NotificationDisplayer.getDefault().notify(
                Bundle.DbStudioTopComponent_envConfigFound(),
                javax.swing.UIManager.getIcon("OptionPane.informationIcon"),
                Bundle.DbStudioTopComponent_envOffer(suggestion.database(), suggestion.engine().displayName()),
                e -> createFromSuggestion(suggestion),
                org.openide.awt.NotificationDisplayer.Priority.LOW);
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
        status(Bundle.DbStudioTopComponent_addedFromEnv(spec.name()), OK_GREEN);
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
            // KEPT (ledger 30): DockerClient is rack UI-cluster surface with
            // no core facade; dbstudio hard-depends on rack for it, and this
            // guard covers stripped test platforms where its init can fail
            return;
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
                    Bundle.DbStudioTopComponent_dockerContainerRunning(),
                    javax.swing.UIManager.getIcon("OptionPane.informationIcon"),
                    org.nmox.studio.dbstudio.io.DockerDbOffers.offerText(offer)
                    + Bundle.DbStudioTopComponent_clickToReview(),
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
        status(Bundle.DbStudioTopComponent_addedFromDocker(spec.name()), OK_GREEN);
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
        // soft dependency by lookup (ledger 30): a null provider means the
        // rack is absent (plain tests) — no manifest events
        org.nmox.studio.core.spi.ProjectAim aim =
                org.nmox.studio.core.spi.ProjectAim.find();
        if (aim != null) {
            aim.addManifestListener(listener);
            manifestListener = listener;
        }
    }

    private void detachManifestListener() {
        if (manifestListener == null) {
            return;
        }
        org.nmox.studio.core.spi.ProjectAim aim =
                org.nmox.studio.core.spi.ProjectAim.find();
        if (aim != null) {
            aim.removeManifestListener(manifestListener);
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
                        // the stat rides the save lane, so it queues behind
                        // any write+stamp pair this report may have raced —
                        // our own save mid-landing never stats as a foreign
                        // version (debt #16)
                        SAVES.classify(() -> {
                            org.nmox.studio.dbstudio.io.ExternalEdits.Stamp stamp =
                                    org.nmox.studio.dbstudio.io.ExternalEdits.Stamp.of(workspaceFile);
                            SwingUtilities.invokeLater(() -> handleExternalStamp(stamp));
                        });
                    });
        } catch (RuntimeException | LinkageError rackUnavailable) {
            // KEPT (ledger 30): FileWatcher is a rack utility with no core
            // facade; dbstudio hard-depends on rack for it, and this guard
            // covers stripped test platforms
            return;
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
                balloon(Bundle.DbStudioTopComponent_reloaded(org.nmox.studio.dbstudio.io.DbWorkspaceIO.FILENAME),
                        Bundle.DbStudioTopComponent_reloadedDetail(), true);
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
        // same lane as the watcher's stat: ordered behind our own writes,
        // so the just-completed save this re-check anticipates has landed
        // WITH its stamp by the time the stat runs (debt #16)
        SAVES.classify(() -> {
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
        consolePlaceholder = placeholder;
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
                setPlaceholder(node, Bundle.DbStudioTopComponent_loading());
                tree.collapsePath(new TreePath(node.getPath()));
            }
            status(Bundle.DbStudioTopComponent_disconnected(spec.name()), Color.GRAY);
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
        status(Bundle.DbStudioTopComponent_connectingTo(spec.name()), Color.GRAY);
        RP.post(() -> {
            // a remote engine can take seconds to answer: a real
            // ProgressHandle in the status line, not just label text
            // (debt #34). No Cancellable — backend.cancel() aborts a
            // running statement, not a connect in flight, so a cancel
            // button here would be a lie.
            org.netbeans.api.progress.ProgressHandle progress =
                    org.netbeans.api.progress.ProgressHandle.createHandle(
                            Bundle.DbStudioTopComponent_connectingTo(spec.name()));
            progress.start();
            DbBackend backend;
            String error;
            List<TableInfo> containers;
            try {
                backend = backendFor(spec);
                if (backend == null) { // a Services connection that just left the explorer
                    error = Bundle.DbStudioTopComponent_connectionGone();
                    containers = List.of();
                } else {
                    error = backend.open();
                    containers = error == null ? backend.listContainers() : List.of();
                }
            } finally {
                progress.finish();
            }
            SwingUtilities.invokeLater(() -> {
                connecting.remove(spec.id());
                if (error != null) {
                    status(Bundle.DbStudioTopComponent_connectFailed(error), FAIL_RED);
                    balloon(Bundle.DbStudioTopComponent_connectFailed(spec.name()), error, false);
                    if (node != null) {
                        setPlaceholder(node, Bundle.DbStudioTopComponent_notConnected());
                    }
                } else {
                    containerCache.put(spec.id(), containers);
                    if (node != null) {
                        fillContainers(node, containers);
                        if (expand) {
                            tree.expandPath(new TreePath(node.getPath()));
                        }
                    }
                    // the engine's own noun, never the internal word
                    // "containers" — beside a Docker-sourced connection
                    // that word reads as a Docker statement (v1.274.0)
                    status(Bundle.DbStudioTopComponent_connected(spec.name(), containers.size(),
                            spec.engine() != null
                                    ? spec.engine().containerNoun(containers.size())
                                    : Bundle.DbStudioTopComponent_tableNoun(containers.size())),
                            OK_GREEN);
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
        status(Bundle.DbStudioTopComponent_testing(spec.name()), Color.GRAY);
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
                        ? Bundle.DbStudioTopComponent_reachable(spec.name())
                        : Bundle.DbStudioTopComponent_testFailed(error), error == null ? OK_GREEN : FAIL_RED);
                balloon(error == null ? Bundle.DbStudioTopComponent_testReachable(spec.name()) : Bundle.DbStudioTopComponent_testFailedFor(spec.name()),
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
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(Bundle.DbStudioTopComponent_connectionsRoot());
        for (ConnectionSpec spec : specs) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(spec);
            node.add(new DefaultMutableTreeNode(Bundle.DbStudioTopComponent_loading()));
            root.add(node);
        }
        if (specs.isEmpty()) {
            // first-run guidance: an empty tree with greyed buttons reads as
            // broken; say what to do instead
            root.add(new DefaultMutableTreeNode(Bundle.DbStudioTopComponent_noConnectionsYet()));
            status(Bundle.DbStudioTopComponent_firstRunHint(), Color.GRAY);
        }
        // the Services branch always trails the workspace connections
        servicesBranchNode = new DefaultMutableTreeNode(ServicesBranch.INSTANCE);
        servicesBranchNode.add(new DefaultMutableTreeNode(Bundle.DbStudioTopComponent_loading()));
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
                    branch.add(new DefaultMutableTreeNode(Bundle.DbStudioTopComponent_noServicesConnections()));
                } else {
                    for (ConnectionSpec spec : synthesized) {
                        DefaultMutableTreeNode node = new DefaultMutableTreeNode(spec);
                        node.add(new DefaultMutableTreeNode(Bundle.DbStudioTopComponent_loading()));
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
            node.add(new DefaultMutableTreeNode(Bundle.DbStudioTopComponent_empty()));
        }
        for (TableInfo container : containers) {
            DefaultMutableTreeNode child = new DefaultMutableTreeNode(container);
            child.add(new DefaultMutableTreeNode(Bundle.DbStudioTopComponent_loading()));
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
                    node.add(new DefaultMutableTreeNode(Bundle.DbStudioTopComponent_noColumns()));
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
        String managedElsewhere = services ? Bundle.DbStudioTopComponent_managedInServices() : null;
        editButton.setToolTipText(PlainText.plain(managedElsewhere));
        removeButton.setToolTipText(PlainText.plain(managedElsewhere));
        testButton.setToolTipText(PlainText.plain(managedElsewhere));
        connectButton.setEnabled(selected);
        DbBackend backend = selected ? backends.get(spec.id()) : null;
        // Services connections never show Disconnect: NetBeans owns the lifecycle
        connectButton.setText(PlainText.plain(!services && backend != null && backend.isOpen()
                ? Bundle.DbStudioTopComponent_disconnect() : Bundle.DbStudioTopComponent_connect()));
        connectButton.setToolTipText(PlainText.plain(services
                ? Bundle.DbStudioTopComponent_connectThroughNetBeans()
                : null));
        // RUN gates on having a target: an always-armed button that silently
        // no-ops reads as broken. The tooltip says why it's off.
        ConnectionSpec active = activeSpec();
        boolean runnable = !running && active != null;
        runButton.setEnabled(runnable);
        runButton.setToolTipText(PlainText.plain(runnable
                ? Bundle.DbStudioTopComponent_runTooltip()
                : running ? Bundle.DbStudioTopComponent_runInFlight() : Bundle.DbStudioTopComponent_selectConnectionFirst()));
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
            explainButton.setToolTipText(Bundle.DbStudioTopComponent_explainTooltip());
        } else if (running) {
            explainButton.setToolTipText(Bundle.DbStudioTopComponent_runInFlight());
        } else if (active == null) {
            explainButton.setToolTipText(Bundle.DbStudioTopComponent_selectConnectionFirst());
        } else if (active.engine() == null
                || active.engine().kind() != DbEngine.Kind.SQL) {
            explainButton.setToolTipText(Bundle.DbStudioTopComponent_explainSqlOnly());
        } else if (!open) {
            explainButton.setToolTipText(Bundle.DbStudioTopComponent_explainNeedsConnection());
        } else {
            explainButton.setToolTipText(Bundle.DbStudioTopComponent_explainSelectOnly());
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
        // the v2.18.0 law: gestures speak — Remove always did, Add and
        // Edit were the silent two (the 2026-08-20 DBA walk's find)
        status(Bundle.DbStudioTopComponent_addedConnection(spec.name()), Color.GRAY);
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
        status(Bundle.DbStudioTopComponent_updatedConnection(updated.name()), Color.GRAY);
    }

    private void removeSelected() {
        ConnectionSpec spec = selectedConnection();
        if (spec == null || isServicesSpec(spec)) {
            return; // Services entries are removed in the Services window
        }
        // Cancel is the default button (the v1.98.0 idiom — Confirmation
        // hard-codes initialValue=OK_OPTION): a reflexive Enter must not
        // delete the connection and its keychain password.
        NotifyDescriptor confirm = new NotifyDescriptor(
                org.nmox.studio.core.util.PlainDialogs.plain(Bundle.DbStudioTopComponent_removeConnectionConfirm(spec.name()), Bundle.DbStudioTopComponent_messageA11y()),
                Bundle.DbStudioTopComponent_removeConnectionTitle(), NotifyDescriptor.OK_CANCEL_OPTION,
                NotifyDescriptor.QUESTION_MESSAGE,
                new Object[]{NotifyDescriptor.OK_OPTION, NotifyDescriptor.CANCEL_OPTION},
                NotifyDescriptor.CANCEL_OPTION);
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
        status(Bundle.DbStudioTopComponent_removedConnection(spec.name()), Color.GRAY);
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
        // soft dependency by lookup (ledger 30): a null provider means the
        // rack is absent (plain tests) and home is the honest fallback
        org.nmox.studio.core.spi.ProjectAim aim =
                org.nmox.studio.core.spi.ProjectAim.find();
        if (aim != null) {
            File dir = aim.projectDir();
            if (dir != null && dir.isDirectory()) {
                return dir;
            }
        }
        return new File(System.getProperty("user.home"));
    }

    /** EDT-confined reload sequence; only {@link #reloadWorkspace} bumps it. */
    private long reloadSeq;

    private void reloadWorkspace() {
        // The FILE READ (and the save-lane drain it must run behind) ride
        // RP, never the EDT (ledger 54 M5; the web3 v1.100.0 idiom): a slow
        // or networked filesystem stalls a worker, not the paint thread.
        // State teardown waits for the loaded workspace so the tab never
        // shows an empty in-between; the sequence makes the newest reload
        // win an overlapping re-aim/external-edit burst — every re-aim
        // routes through here (the rack listener), so a stale read of a
        // previous project's dir is always superseded before it applies.
        final File dir = projectDir();
        final long seq = ++reloadSeq;
        RP.post(() -> {
            // the read must see every queued write — an A→B→A re-aim bounce
            // could otherwise read A's file before A's last save lands; the
            // save was queued on the EDT before this task was posted, so
            // draining here preserves that ordering (bounded ms drain)
            SAVES.flush(5, java.util.concurrent.TimeUnit.SECONDS);
            DbWorkspaceIO.LoadOutcome outcome = DbWorkspaceIO.loadWorkspaceGuarded(dir);
            // stamp the just-read file off-EDT too: Stamp.of stats the file,
            // and the whole point of M5 is that no reload I/O touches paint
            org.nmox.studio.dbstudio.io.ExternalEdits.Stamp ownStamp =
                    org.nmox.studio.dbstudio.io.ExternalEdits.Stamp.of(
                            new File(dir, org.nmox.studio.dbstudio.io.DbWorkspaceIO.FILENAME));
            SwingUtilities.invokeLater(() -> {
                if (seq != reloadSeq) {
                    return; // a newer reload superseded this read
                }
                applyReloadedWorkspace(outcome, ownStamp);
            });
        });
    }

    /** EDT: swaps the studio onto a freshly read workspace. */
    private void applyReloadedWorkspace(DbWorkspaceIO.LoadOutcome outcome,
            org.nmox.studio.dbstudio.io.ExternalEdits.Stamp ownStamp) {
        closeAllBackends();
        containerCache.clear();
        connecting.clear();
        activeSpecId = null;
        specs.clear();
        // Results belong to the workspace that produced them. Leaving
        // them up was cosmetic until v1.174.0 put an Explain button on
        // the error tab: after a re-aim it stayed armed with the
        // PREVIOUS project's SQL and error. Same class as the v1.172.0
        // API Studio fix, found by asking whether the second consumer
        // survived the hazard the first one failed.
        clearResultTabs();
        DbWorkspaceIO.Workspace workspace = outcome.workspace();
        if (outcome.backup() != null) {
            // corrupt file: the IO layer copied it aside BEFORE handing us the
            // empty fallback (the next save can't clobber it) — say so
            try {
                org.openide.awt.NotificationDisplayer.getDefault().notify(
                        Bundle.DbStudioTopComponent_couldNotRead(DbWorkspaceIO.FILENAME),
                        javax.swing.UIManager.getIcon("OptionPane.warningIcon"),
                        Bundle.DbStudioTopComponent_backupKept(outcome.backup().getName()),
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
                : Bundle.DbStudioTopComponent_connectionCount(specs.size()), Color.GRAY);
        // the freshly loaded version is now "ours" — only later foreign
        // writes should trigger the external-reload flow. The stamp was
        // computed off-EDT right after the read (same file, same moment).
        externalEdits.recordOwn(ownStamp);
        offerEnvConnection();
        if (isOpened()) {
            restartWorkspaceWatcher(); // the project dir may have changed
            // hidden tabs never probe (boot spawns zero processes — the
            // v1.38.0 law); componentShowing probes fresh when the hold
            // is empty, so a reload behind a hidden tab still offers on show
            if (isShowing()) {
                offerDockerConnections();
            }
        }
    }

    /** Save-lane-thread confined — only {@link #writeSnapshot} touches it. */
    private boolean saveFailureNotified;

    /**
     * EDT: the connection/history/saved lists are EDT-confined, so the
     * JSON snapshot is taken here and only the disk write rides the
     * save lane — the EDT never touches the disk (debt #16). There is
     * no debounce in DB Studio (every change saves the moment it is
     * made), but the write half gets the same off-EDT care.
     */
    private void saveWorkspace() {
        File file = new File(projectDir(), DbWorkspaceIO.FILENAME);
        String json = DbWorkspaceIO.toJson(new DbWorkspaceIO.Workspace(
                specs, persistedHistory, savedQueries));
        SAVES.save(() -> writeSnapshot(file, json));
    }

    /**
     * Save lane only: the write and its self-stamp are ONE task, so a
     * lane-ordered watcher verdict can never see the write without the
     * stamp.
     */
    private void writeSnapshot(File file, String json) {
        try {
            org.nmox.studio.core.util.AtomicFiles.writeString(file.toPath(), json);
            externalEdits.recordOwn(
                    org.nmox.studio.dbstudio.io.ExternalEdits.Stamp.of(file));
            saveFailureNotified = false;
        } catch (Exception ex) {
            // a failed save never interrupts editing — but never lose work silently
            java.util.logging.Logger.getLogger(DbStudioTopComponent.class.getName())
                    .log(java.util.logging.Level.WARNING, "DB workspace save failed", ex);
            if (!saveFailureNotified) {
                saveFailureNotified = true;
                org.openide.awt.NotificationDisplayer.getDefault().notify(
                        Bundle.DbStudioTopComponent_cannotSave(),
                        javax.swing.UIManager.getIcon("OptionPane.warningIcon"),
                        Bundle.DbStudioTopComponent_notPersisted(ex.getMessage()),
                        null);
            }
        }
    }

    /**
     * The failed-statement strip: one button that hands KVASIR a
     * REDACTED account of the failure. The disclosure is assembled here,
     * in the studio that owns the data, and carries only the SQL, the
     * driver's message and the engine kind — a failed statement produced
     * no rows, so there is nothing else to withhold. The connection and
     * its keychain password never appear.
     */
    private JComponent explainStrip(ConnectionSpec spec, QueryResult result) {
        JPanel strip = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 4));
        JButton explain = new JButton(Bundle.DbStudioTopComponent_explain());
        explain.setToolTipText(Bundle.DbStudioTopComponent_explainErrorTooltip());
        explain.addActionListener(e -> {
            org.nmox.studio.core.spi.KvasirAsk kvasir = org.nmox.studio.core.spi.KvasirAsk.find();
            if (kvasir == null) {
                return;
            }
            // the engine slug also rides the wire body — a raw literal,
            // never a translated one
            String engine = spec == null || spec.engine() == null
                    ? "unknown" : spec.engine().name().toLowerCase(java.util.Locale.ROOT);
            boolean started = kvasir.explain(new org.nmox.studio.core.spi.KvasirAsk.Disclosure(
                    "db.error", Bundle.DbStudioTopComponent_kvasirTitle(engine),
                    org.nmox.studio.dbstudio.engine.SqlErrorDisclosure.what(engine),
                    org.nmox.studio.dbstudio.engine.SqlErrorDisclosure.body(
                            engine, result.statement(), result.error()),
                    "What does this error mean, and how do I fix the statement?"));
            if (!started) {
                status(Bundle.DbStudioTopComponent_kvasirDidNotRun(), Color.GRAY);
            }
        });
        strip.add(explain);
        return strip;
    }

    private void status(String message, Color color) {
        statusLabel.setForeground(color);
        statusLabel.setText(PlainText.plain(message));
        org.openide.awt.StatusDisplayer.getDefault().setStatusText(org.nmox.studio.core.util.PlainStatus.text(message));
    }

    // ---- lifecycle ----

    private void attachRackListener() {
        org.nmox.studio.core.spi.ProjectAim aim =
                org.nmox.studio.core.spi.ProjectAim.find();
        if (aim == null) {
            return; // rack absent (plain tests): no project switches to follow
        }
        aim.addListener(rackListener);
        rackListenerAttached = true;
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

    /** The initial load happened; re-aims arrive via the rack listener. */
    private boolean loadedOnce;

    @Override
    public void componentOpened() {
        if (!rackListenerAttached) {
            attachRackListener();
        }
        attachServicesListener();
        attachManifestListener();
        if (!loadedOnce) {
            // first open after construction: exactly one initial load. The
            // rack listener only reloads on re-aims, so nothing else does it.
            loadedOnce = true;
            reloadWorkspace();
        }
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
        aimFollower.showing(); // v1.235.0: ambient aim selection (ledger 29)
        java.util.List<org.nmox.studio.rack.docker.DockerClient.ContainerInfo> held =
                dockerHold.onShowing();
        if (held.isEmpty()) {
            offerDockerConnections();
        } else {
            showDockerOffers(held); // the probe already ran — don't re-probe
        }
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
    public void componentClosed() {
        aimFollower.closed();
        // a write may sit queued on the save lane (every edit saves the
        // moment it is made) — drain it before the studio is torn down
        // (bounded; see SaveLane.flush)
        SAVES.flush(5, java.util.concurrent.TimeUnit.SECONDS);
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
            org.nmox.studio.core.spi.ProjectAim aim =
                    org.nmox.studio.core.spi.ProjectAim.find();
            if (aim != null) {
                aim.removeListener(rackListener);
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
                                ? Bundle.DbStudioTopComponent_servicesBadge(spec.engine().displayName()) : Bundle.DbStudioTopComponent_services())
                        : spec.engine().displayName();
                setText("<html>" + (connected ? "<b>" : "") + esc(spec.name())
                        + (connected ? "</b>" : "")
                        + " <font color='#8a8a8a'>(" + esc(badge)
                        + ")</font></html>");
            } else if (userObject instanceof ServicesBranch) {
                setText(Bundle.DbStudioTopComponent_servicesBranch());
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
                setToolTipText(PlainText.plain(query.text()));
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
                setText(org.nmox.studio.core.util.Clocks.display(entry.timestamp())
                        + "  [" + entry.engine() + "]  " + firstLine);
                setFont(MONO);
            }
            return this;
        }
    }

    private static String esc(String s) {
        return PlainText.escape(s);
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
