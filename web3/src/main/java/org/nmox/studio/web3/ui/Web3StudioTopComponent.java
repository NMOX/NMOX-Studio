package org.nmox.studio.web3.ui;

import org.nmox.studio.core.util.PlainText;
import org.nmox.studio.core.util.PlainTables;
import org.nmox.studio.core.util.Threads;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.nmox.studio.core.process.ProcessSupport;
import org.nmox.studio.core.process.ToolLocator;
import org.nmox.studio.web3.engine.AbiCodec;
import org.nmox.studio.web3.engine.ArtifactScanner;
import org.nmox.studio.web3.engine.ContractSizeCheck;
import org.nmox.studio.web3.engine.DisplayValues;
import org.nmox.studio.web3.engine.EventMatcher;
import org.nmox.studio.web3.engine.GasReportParser;
import org.nmox.studio.web3.engine.InteractSession;
import org.nmox.studio.web3.engine.JsonRpcClient;
import org.nmox.studio.web3.engine.ErcStandards;
import org.nmox.studio.web3.engine.TokenAmounts;
import org.nmox.studio.web3.engine.TxInspection;
import org.nmox.studio.web3.engine.ReceiptWaiter;
import org.nmox.studio.web3.engine.WatchFeed;
import org.nmox.studio.web3.engine.WatchRows;
import org.nmox.studio.web3.io.RpcSecrets;
import org.nmox.studio.web3.io.Web3WorkspaceIO;
import org.nmox.studio.web3.model.AbiEntry;
import org.nmox.studio.web3.model.AbiParam;
import org.nmox.studio.web3.model.ContractArtifact;
import org.nmox.studio.web3.model.DeploymentRecord;
import org.nmox.studio.web3.model.Network;
import org.nmox.studio.web3.search.Web3SearchProvider;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.windows.TopComponent;

/**
 * Contract Studio: the EVM smart-contract tab. Networks, scanned
 * artifacts (Foundry {@code out/}, Hardhat {@code artifacts/}), and the
 * deployment address book on the left; Interact / Watch / Oversight on
 * the right; a compile log strip below.
 *
 * <p><b>The security boundary, enforced here:</b> the IDE never
 * touches a private key. Deploys and state-changing sends go through
 * {@code eth_sendTransaction} with the node's OWN unlocked accounts —
 * exactly what anvil and hardhat devnets provide. When
 * {@code eth_accounts} comes back empty (a remote RPC), every write
 * control is disabled with {@link InteractSession#READ_ONLY_REASON the
 * honest reason} shown, not hidden. Secret RPC URLs live in the OS
 * keyring via {@link RpcSecrets}; {@code .nmoxweb3.json} never carries
 * one, and no full endpoint URL is ever logged or displayed.
 *
 * <p>Threading (the DB Studio contract): every RPC and process call
 * happens on the module's {@link RequestProcessor} or the Watch pane's
 * daemon scheduler; results are marshalled back with
 * {@code SwingUtilities.invokeLater}. Sends and deploys are serialized
 * by the {@code running} gate so receipts never interleave.
 */
@TopComponent.Description(preferredID = "Web3StudioTopComponent",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS)
@TopComponent.Registration(mode = "editor", openAtStartup = true, position = 275)
@ActionID(category = "Window", id = "org.nmox.studio.web3.ui.Web3StudioTopComponent")
@org.openide.awt.ActionReferences({
    @ActionReference(path = "Menu/Window", position = 259),
    // Cmd+Alt (DA-) — the studio row lives in the one digit family no
    // shipped module claims. The old chord opened a platform window
    // instead of this one: ⇧⌘6 was the platform's Tasks window. Keymaps-profile
    // registrations beat Shortcuts-folder ones, so a layer-only audit
    // misses these; WindowShortcutsTest pins the reserved list.
    @ActionReference(path = "Shortcuts", name = "DA-6")
})
@TopComponent.OpenActionRegistration(displayName = "#CTL_Web3StudioAction",
        preferredID = "Web3StudioTopComponent")
@Messages({
    "CTL_Web3StudioAction=Contract Studio",
    "CTL_Web3StudioTopComponent=Contract Studio",
    "HINT_Web3StudioTopComponent=Smart contracts: compile, deploy, interact, watch, oversee (EVM)"
})
public final class Web3StudioTopComponent extends TopComponent {

    /** The module's worker pool — every RPC/process call runs here, never on the EDT. */
    static final RequestProcessor RP = new RequestProcessor("Contract Studio", 3);

    /**
     * Workspace writes ride their own single-throughput lane, NOT
     * {@link #RP} — RP's throughput 3 could interleave two writes, and
     * a close flush must never queue behind a slow RPC (debt #16; the
     * careful parts are documented on the lane class).
     */
    private static final org.nmox.studio.web3.engine.SaveLane SAVES =
            new org.nmox.studio.web3.engine.SaveLane("Contract Studio workspace saves");

    /** Always first in the combo, never persisted — the devnet ANVIL provides. */
    static final Network LOCAL_ANVIL =
            new Network("Local (anvil)", 31337, false, "http://127.0.0.1:8545");

    static final String NOT_CONNECTED =
            "not connected — start a local chain (ANVIL in the rack)";
    static final String FORGE_HINT =
            "forge not found — install Foundry: curl -L https://foundry.paradigm.xyz | bash";
    static final String NO_PROJECT_HINT =
            "No project aimed — aim the rack at a Foundry or Hardhat project first";

    private static final Color OK_GREEN = new Color(0x4E, 0xC9, 0x8B);
    private static final Color FAIL_RED = new Color(0xE2, 0x4B, 0x4A);
    private static final Color AMBER = new Color(0xC9, 0x93, 0x2B);
    private static final Color ACCENT = new Color(0x1D, 0x9E, 0x75);
    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    /** How many missed blocks one Watch tick will backfill at most. */
    private static final int CATCHUP_CAP = 50;

    // ---- state ------------------------------------------------------------

    /** User-added networks (persisted); LOCAL_ANVIL rides in front of them. */
    private final List<Network> networks = new ArrayList<>();
    /** The address book, newest first — mirrors .nmoxweb3.json. */
    private final List<DeploymentRecord> deployments = new ArrayList<>();
    private List<ContractArtifact> artifacts = List.of();
    /** Imported-ABI contracts (v2.45.0) — survive rescans by design. */
    private List<org.nmox.studio.web3.model.ImportedContract> importedContracts = List.of();
    private List<ContractArtifact> importedArtifacts = List.of();
    /** Read by the Watch poller thread. */
    private volatile EventMatcher eventMatcher = EventMatcher.empty();
    /** Read by the Watch poller thread. */
    private volatile JsonRpcClient client;
    /** Read by the Watch poller thread; recomputed on the EDT. */
    private volatile List<String> watchAddresses = List.of();
    private List<String> accounts = List.of();
    private boolean connected;
    /** Read by the Watch poller thread for the live chip text. */
    private volatile long liveChainId;
    /** Bumps on every network switch so stale connect results get dropped. */
    private int connectSeq;
    /** True while a send/deploy waits for its receipt; serializes writes. */
    private boolean running;
    private boolean compiling;
    private boolean gasRunning;
    private InteractSession session;

    // ---- UI ------------------------------------------------------------------

    private final JComboBox<Network> networkCombo = new JComboBox<>();
    private boolean networkComboRefreshing;
    private final JLabel chipLabel = new JLabel(PlainText.plain(NOT_CONNECTED));
    private final JButton compileButton = new JButton("Compile");
    private final JButton rescanButton = new JButton("Rescan");
    private final JLabel statusLabel = new JLabel(" ");

    private final JTree tree = new JTree();
    {
        tree.getAccessibleContext().setAccessibleName("Contract artifacts and networks");
    }
    private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("");
    private final DefaultMutableTreeNode networksNode =
            new DefaultMutableTreeNode(Branch.NETWORKS);
    private final DefaultMutableTreeNode contractsNode =
            new DefaultMutableTreeNode(Branch.CONTRACTS);
    private final DefaultMutableTreeNode deploymentsNode =
            new DefaultMutableTreeNode(Branch.DEPLOYMENTS);

    private final JTabbedPane tabs = new JTabbedPane();
    private final JPanel interactPanel = new JPanel(new BorderLayout());
    private final JComboBox<String> fromCombo = new JComboBox<>();

    private final WatchFeed feed = new WatchFeed();
    private final WatchModel watchModel = new WatchModel();
    private final JButton watchButton = new JButton("START");
    private final JComboBox<Object> watchFilterCombo = new JComboBox<>();
    private boolean watchFilterRefreshing;
    private ScheduledExecutorService watchExec;
    /** Poller-thread-only cursor state; reset on the EDT before the poller starts. */
    private long lastWatchedBlock = -1;
    private long logsFromBlock = Long.MAX_VALUE;

    /**
     * Bumped on every {@link #stopWatch}. {@code shutdownNow} interrupts
     * but does not JOIN a tick blocked in an RPC (up to the 10s timeout);
     * a quick STOP→START or a network switch could otherwise leave the
     * dying tick writing the two cursor fields the new poller now owns.
     * A tick captures the generation at entry and abandons its cursor
     * writes if it changed. AtomicLong (not a volatile ++): the
     * increment must be atomic for SpotBugs' VO_VOLATILE_INCREMENT law
     * even though stopWatch is EDT-confined.
     */
    private final java.util.concurrent.atomic.AtomicLong watchGeneration =
            new java.util.concurrent.atomic.AtomicLong();

    private final SizeModel sizeModel = new SizeModel();
    private final GasModel gasModel = new GasModel();
    private final DeploymentsModel deploymentsModel = new DeploymentsModel();
    private final JButton gasButton = new JButton("Run gas report");

    private final JTextArea logArea = new JTextArea(5, 40);

    private final org.nmox.studio.core.spi.ProjectAim.Listener rackListener;
    private boolean rackListenerAttached;
    /** An artifact walk is owed but the tab is hidden; served on componentShowing. */
    boolean rescanPending;
    /** Save-lane-thread confined — only {@link #writeSnapshot} touches it. */
    private boolean saveFailureNotified;

    /** Auto-connects the chip when the rack serves a matching chain; null when rack absent. */
    private org.nmox.studio.web3.engine.ChainAutoConnect chainAutoConnect;
    /** Polls artifacts + workspace file while the tab is open; null when closed/no project. */
    private org.nmox.studio.web3.engine.ArtifactPulse pulse;
    /** Distinguishes our own .nmoxweb3.json writes from foreign edits. */
    private final org.nmox.studio.core.util.SelfWriteTracker selfWrites =
            new org.nmox.studio.core.util.SelfWriteTracker();

    public Web3StudioTopComponent() {
        networkCombo.getAccessibleContext().setAccessibleName("Network");
        fromCombo.getAccessibleContext().setAccessibleName("From account");
        watchFilterCombo.getAccessibleContext().setAccessibleName("Watch filter");
        logArea.getAccessibleContext().setAccessibleName("Event log");
        setName(Bundle.CTL_Web3StudioTopComponent());
        setToolTipText(Bundle.HINT_Web3StudioTopComponent());
        setLayout(new BorderLayout());

        add(buildToolbar(), BorderLayout.NORTH);

        tabs.addTab("Interact", buildInteractTab());
        tabs.addTab("Watch", buildWatchTab());
        tabs.addTab("Oversight", buildOversightTab());

        logArea.setEditable(false);
        logArea.setFont(MONO);
        logArea.setLineWrap(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Log"));
        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabs, logScroll);
        rightSplit.setResizeWeight(1.0);
        rightSplit.setDividerLocation(430);

        JSplitPane center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildTreePanel(), rightSplit);
        center.setDividerLocation(260);
        add(center, BorderLayout.CENTER);

        rackListener = new org.nmox.studio.core.spi.ProjectAim.Listener() {
            @Override
            public void projectChanged() {
                SwingUtilities.invokeLater(Web3StudioTopComponent.this::reloadWorkspace);
            }
        };
        attachRackListener();
        // no workspace read here: the constructor runs during window-system
        // deserialization; componentOpened owns the initial load
    }

    // ---- toolbar -----------------------------------------------------------

    private JToolBar buildToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        networkCombo.setRenderer(new NetworkRenderer());
        networkCombo.setToolTipText("The network every call, send, and watch targets");
        networkCombo.setMaximumSize(new java.awt.Dimension(240, 60));
        networkCombo.addActionListener(e -> {
            if (!networkComboRefreshing) {
                networkSelected();
            }
        });
        bar.add(networkCombo);
        JButton addNetworkButton = new JButton("Add Network…");
        addNetworkButton.setToolTipText("Add an RPC endpoint — secret URLs go to the OS keychain");
        addNetworkButton.addActionListener(e -> addNetwork());
        bar.add(addNetworkButton);
        JButton removeNetworkButton = new JButton("Remove Network\u2026");
        removeNetworkButton.setToolTipText(
                "Removes the selected network \u2014 a secret RPC URL's keychain entry is deleted too");
        removeNetworkButton.addActionListener(e -> removeSelectedNetwork());
        bar.add(removeNetworkButton);
        bar.addSeparator();
        JButton importAbiButton = new JButton("Import ABI\u2026");
        importAbiButton.setToolTipText("Interact with any deployed contract — paste "
                + "its ABI; attach by address; no build needed");
        importAbiButton.getAccessibleContext().setAccessibleName("Import an ABI");
        importAbiButton.addActionListener(e -> importAbi());
        bar.add(importAbiButton);
        JButton removeImportedButton = new JButton("Remove Imported\u2026");
        removeImportedButton.setToolTipText(
                "Forget an imported ABI \u2014 the chain is untouched");
        removeImportedButton.getAccessibleContext().setAccessibleName(
                "Remove an imported ABI");
        removeImportedButton.addActionListener(e -> removeImported());
        bar.add(removeImportedButton);
        bar.addSeparator();
        chipLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        chipLabel.setForeground(Color.GRAY);
        bar.add(chipLabel);
        bar.add(Box.createHorizontalGlue());
        compileButton.setForeground(ACCENT);
        compileButton.addActionListener(e -> compile());
        bar.add(compileButton);
        rescanButton.setToolTipText("Re-scan out/ and artifacts/ for compiled contracts");
        rescanButton.addActionListener(e -> rescan());
        bar.add(rescanButton);
        bar.addSeparator();
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        bar.add(statusLabel);
        return bar;
    }

    // ---- left: the tree -------------------------------------------------------

    private JComponent buildTreePanel() {
        rootNode.add(networksNode);
        rootNode.add(contractsNode);
        rootNode.add(deploymentsNode);
        tree.setModel(new DefaultTreeModel(rootNode));
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new Web3TreeRenderer());
        tree.addTreeSelectionListener(e -> onTreeSelect());
        return new JScrollPane(tree);
    }

    private void rebuildNetworksBranch() {
        networksNode.removeAllChildren();
        networksNode.add(new DefaultMutableTreeNode(LOCAL_ANVIL));
        for (Network network : networks) {
            networksNode.add(new DefaultMutableTreeNode(network));
        }
        ((DefaultTreeModel) tree.getModel()).nodeStructureChanged(networksNode);
    }

    private void rebuildContractsBranch() {
        contractsNode.removeAllChildren();
        if (projectDirOrNull() == null) {
            contractsNode.add(new DefaultMutableTreeNode(NO_PROJECT_HINT));
        } else if (allArtifacts().isEmpty()) {
            contractsNode.add(new DefaultMutableTreeNode(
                    "No artifacts found — Compile (forge build), Rescan, "
                    + "or Import ABI…"));
        } else {
            for (ContractArtifact artifact : allArtifacts()) {
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(artifact);
                artifact.constructor().ifPresent(c -> node.add(new DefaultMutableTreeNode(c)));
                for (AbiEntry function : artifact.functions()) {
                    node.add(new DefaultMutableTreeNode(function));
                }
                for (AbiEntry event : artifact.events()) {
                    node.add(new DefaultMutableTreeNode(event));
                }
                contractsNode.add(node);
            }
        }
        ((DefaultTreeModel) tree.getModel()).nodeStructureChanged(contractsNode);
        tree.expandPath(new TreePath(contractsNode.getPath()));
    }

    private void rebuildDeploymentsBranch() {
        deploymentsNode.removeAllChildren();
        if (deployments.isEmpty()) {
            deploymentsNode.add(new DefaultMutableTreeNode(
                    "No deployments yet — deploy a contract from Interact"));
        } else {
            for (DeploymentRecord record : deployments) {
                deploymentsNode.add(new DefaultMutableTreeNode(record));
            }
        }
        ((DefaultTreeModel) tree.getModel()).nodeStructureChanged(deploymentsNode);
    }

    private void onTreeSelect() {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObject = node.getUserObject();
        if (userObject instanceof AbiEntry
                && node.getParent() instanceof DefaultMutableTreeNode parent
                && parent.getUserObject() instanceof ContractArtifact artifact) {
            userObject = artifact; // a function/event row means its contract
        }
        if (userObject instanceof Network network) {
            networkCombo.setSelectedItem(network);
        } else if (userObject instanceof ContractArtifact artifact) {
            // an import that carries its deployed address opens ATTACHED —
            // the whole point of importing by address is skipping the
            // attach dance (v2.46.0, the walk note)
            String importedAddress = importedAddressFor(artifact.name());
            openInteractFor(importedAddress != null
                    ? InteractSession.attached(artifact, importedAddress, hasAccounts())
                    : InteractSession.deploying(artifact, hasAccounts()));
        } else if (userObject instanceof DeploymentRecord record) {
            attachDeployment(record);
        }
    }

    // ---- Interact -------------------------------------------------------------

    private JComponent buildInteractTab() {
        showInteractHint("Select a contract or a deployment in the tree.");
        return interactPanel;
    }

    private void showInteractHint(String text) {
        interactPanel.removeAll();
        JLabel hint = new JLabel(PlainText.plain(text));
        hint.setForeground(Color.GRAY);
        hint.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        interactPanel.add(hint, BorderLayout.NORTH);
        interactPanel.revalidate();
        interactPanel.repaint();
    }

    private void openInteractFor(InteractSession newSession) {
        session = newSession;
        tokenDecimals = null; // the next strip read re-learns it (v1.172.0)
        rebuildInteract();
        tabs.setSelectedIndex(0);
    }

    private void rebuildInteract() {
        InteractSession s = session;
        if (s == null) {
            showInteractHint("Select a contract or a deployment in the tree.");
            return;
        }
        interactPanel.removeAll();
        interactPanel.add(s.attached() ? buildFunctionList(s) : buildDeployForm(s),
                BorderLayout.CENTER);
        if (s.attached()) {
            JComponent strip = buildTokenStrip(s);
            if (strip != null) {
                interactPanel.add(strip, BorderLayout.NORTH);
            }
        }
        interactPanel.revalidate();
        interactPanel.repaint();
    }

    /** The constructor/deploy form for an un-attached artifact. */
    private JComponent buildDeployForm(InteractSession s) {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        int row = 0;

        JLabel title = new JLabel("Deploy " + s.artifact().name());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        addFormRow(form, row++, title);
        if (!s.artifact().sourcePath().isEmpty()) {
            JLabel source = new JLabel(PlainText.plain(s.artifact().sourcePath()));
            source.setForeground(Color.GRAY);
            addFormRow(form, row++, source);
        }

        String reason = s.deployDisabledReason();
        if (reason != null) {
            JLabel why = new JLabel("<html>" + esc(reason) + "</html>");
            why.setForeground(FAIL_RED);
            addFormRow(form, row++, why);
        }

        List<JTextField> argFields = new ArrayList<>();
        for (AbiParam param : s.constructorParams()) {
            JTextField field = new JTextField(24);
            field.setFont(MONO);
            field.getAccessibleContext().setAccessibleName(paramLabel(param));
            argFields.add(field);
            addLabeledRow(form, row++, paramLabel(param), field);
        }
        if (s.constructorHint() != null) {
            JLabel hint = new JLabel(PlainText.plain(s.constructorHint()));
            hint.setForeground(Color.GRAY);
            addFormRow(form, row++, hint);
        }

        JTextField valueField = null;
        if (s.constructorPayable()) {
            valueField = new JTextField(10);
            valueField.setFont(MONO);
            valueField.getAccessibleContext().setAccessibleName("Value in ETH");
            valueField.setToolTipText("ETH to send with the deployment, like 0.5");
            addLabeledRow(form, row++, "Value (ETH):", valueField);
        }

        addLabeledRow(form, row++, "From:", fromCombo);

        JButton deployButton = new JButton("Deploy");
        deployButton.setForeground(ACCENT);
        deployButton.setEnabled(reason == null && connected);
        deployButton.setToolTipText(PlainText.plain(reason != null ? reason
                : connected ? "eth_sendTransaction with the node's unlocked account"
                        : NOT_CONNECTED));
        JButton attachButton = new JButton("Attach to address…");
        attachButton.setToolTipText("Interact with an already-deployed instance");
        JLabel result = new JLabel(" ");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(deployButton);
        buttons.add(attachButton);
        addFormRow(form, row++, buttons);
        addFormRow(form, row, result);

        JTextField valueRef = valueField;
        deployButton.addActionListener(e -> deploy(s, argFields, valueRef, result));
        attachButton.addActionListener(e -> attachToAddress(s));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(form, BorderLayout.NORTH);
        return new JScrollPane(wrapper);
    }

    /** The function list for an attached instance. */
    private JComponent buildFunctionList(InteractSession s) {
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel title = new JLabel(PlainText.plain(s.artifact().name() + " @ "
                + DisplayValues.shortAddress(s.address())));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        list.add(leftAligned(title));

        String sendReason = s.sendDisabledReason();
        if (sendReason != null) {
            JLabel why = new JLabel("<html>" + esc(sendReason) + "</html>");
            why.setForeground(AMBER);
            list.add(leftAligned(why));
        } else if (!s.writeFunctions().isEmpty()) {
            JPanel fromRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            fromRow.add(new JLabel("From:"));
            fromRow.add(fromCombo); // single-parented: only one form shows at a time
            list.add(leftAligned(fromRow));
        }
        list.add(Box.createVerticalStrut(8));

        for (AbiEntry function : s.readFunctions()) {
            list.add(leftAligned(functionRow(s, function, null)));
        }
        for (AbiEntry function : s.writeFunctions()) {
            list.add(leftAligned(functionRow(s, function, sendReason)));
        }
        if (s.artifact().functions().isEmpty()) {
            JLabel none = new JLabel("This ABI declares no functions.");
            none.setForeground(Color.GRAY);
            list.add(leftAligned(none));
        }

        if (!s.artifact().events().isEmpty()) {
            list.add(Box.createVerticalStrut(8));
            JLabel eventsHeader = new JLabel("Events (decoded live in the Watch tab):");
            eventsHeader.setForeground(Color.GRAY);
            list.add(leftAligned(eventsHeader));
            for (AbiEntry event : s.artifact().events()) {
                JLabel eventLabel = new JLabel("  " + event.signature());
                eventLabel.setForeground(Color.GRAY);
                eventLabel.setFont(MONO);
                list.add(leftAligned(eventLabel));
            }
        }

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(list, BorderLayout.NORTH);
        return new JScrollPane(wrapper);
    }

    /** One function's row: marker + name, arg fields, CALL/SEND, result label. */
    private JPanel functionRow(InteractSession s, AbiEntry function, String sendReason) {
        boolean read = function.readOnly();
        JPanel rowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JLabel name = new JLabel(PlainText.plain((read ? "ƒ " : "✎ ") + function.name()));
        name.setFont(MONO.deriveFont(Font.BOLD));
        name.setToolTipText(PlainText.plain(function.signature() + " · " + function.stateMutability()));
        rowPanel.add(name);

        List<JTextField> argFields = new ArrayList<>();
        for (AbiParam param : function.inputs()) {
            JLabel label = new JLabel(PlainText.plain(paramLabel(param)));
            label.setForeground(Color.GRAY);
            rowPanel.add(label);
            JTextField field = new JTextField(10);
            field.setFont(MONO);
            field.getAccessibleContext().setAccessibleName(label.getText());
            argFields.add(field);
            rowPanel.add(field);
        }

        JTextField valueField = null;
        if (!read && "payable".equals(function.stateMutability())) {
            JLabel valueLabel = new JLabel("value (ETH):");
            valueLabel.setForeground(Color.GRAY);
            rowPanel.add(valueLabel);
            valueField = new JTextField(6);
            valueField.setFont(MONO);
            valueField.getAccessibleContext().setAccessibleName("Value in ETH");
            rowPanel.add(valueField);
        }

        JButton action = new JButton(read ? "CALL" : "SEND");
        JLabel result = new JLabel(" ");
        if (read) {
            action.setToolTipText("eth_call — free, read-only");
            action.addActionListener(e -> call(s, function, argFields, result));
        } else {
            action.setEnabled(sendReason == null && connected);
            action.setToolTipText(PlainText.plain(sendReason != null ? sendReason
                    : connected ? "eth_sendTransaction with the node's unlocked account"
                            : NOT_CONNECTED));
            JTextField valueRef = valueField;
            action.addActionListener(e -> send(s, function, argFields, valueRef, result));
        }
        rowPanel.add(action);
        rowPanel.add(result);
        return rowPanel;
    }

    private static String paramLabel(AbiParam param) {
        String name = param.name() == null || param.name().isBlank()
                ? "arg" : param.name();
        return name + " (" + param.type() + "):";
    }

    // ---- deploy / call / send -----------------------------------------------

    /**
     * EDT, before any non-local broadcast. A devnet on loopback stays
     * frictionless (the tutorial's ANVIL loop is untouched), but a
     * remote endpoint with unlocked accounts — a self-hosted geth
     * {@code --dev}, a mainnet fork holding value-equivalent state —
     * accepts a real, irreversible transaction on one stray click.
     * The v1.98.0 dialog-safety idiom: the full NotifyDescriptor
     * constructor with NO as the initialValue, so Enter/Space land on
     * the safe button ({@code Confirmation} hard-codes OK as default).
     */
    private boolean confirmRemoteBroadcast(String verb) {
        JsonRpcClient c = client;
        if (c == null || c.isLoopbackEndpoint()) {
            return true;
        }
        Network network = selectedNetwork();
        String where = network == null ? "a remote endpoint" : "\"" + network.name() + "\"";
        NotifyDescriptor d = new NotifyDescriptor(
                org.nmox.studio.core.util.PlainDialogs.plain(verb + " will broadcast a REAL transaction to " + where
                + " — a non-local endpoint. There is no undo.", "Message"),
                "Broadcast transaction?",
                NotifyDescriptor.YES_NO_OPTION, NotifyDescriptor.WARNING_MESSAGE,
                new Object[]{NotifyDescriptor.YES_OPTION, NotifyDescriptor.NO_OPTION},
                NotifyDescriptor.NO_OPTION);
        return DialogDisplayer.getDefault().notify(d) == NotifyDescriptor.YES_OPTION;
    }

    private void deploy(InteractSession s, List<JTextField> argFields,
            JTextField valueField, JLabel result) {
        if (running) {
            status("A transaction is in flight — wait for its receipt", FAIL_RED);
            return;
        }
        JsonRpcClient c = client;
        Network network = selectedNetwork();
        if (c == null || !connected || network == null) {
            status(NOT_CONNECTED, FAIL_RED);
            return;
        }
        String from = (String) fromCombo.getSelectedItem();
        if (from == null || from.isBlank()) {
            status(InteractSession.READ_ONLY_REASON, FAIL_RED);
            return;
        }
        String data;
        String valueHex;
        try {
            data = s.deployData(fieldTexts(argFields));
            valueHex = InteractSession.valueWeiHex(
                    valueField == null ? null : valueField.getText());
        } catch (IllegalArgumentException | IllegalStateException refusal) {
            setResult(result, refusal.getMessage(), FAIL_RED);
            status(refusal.getMessage(), FAIL_RED);
            return;
        }
        if (!confirmRemoteBroadcast("Deploying " + s.artifact().name())) {
            status("Deploy cancelled", Color.GRAY);
            return;
        }
        running = true;
        setResult(result, "Deploying…", Color.GRAY);
        status("Deploying " + s.artifact().name() + "…", Color.GRAY);
        RP.post(() -> {
            try {
                String txHash = c.sendTransaction(from, null, data, valueHex);
                ReceiptOutcome outcome = awaitReceipt(c, txHash);
                SwingUtilities.invokeLater(() ->
                        deployFinished(s, network, txHash, outcome, result));
            } catch (JsonRpcClient.RpcException rpc) {
                String reason = revertReason(rpc, s);
                SwingUtilities.invokeLater(() -> {
                    running = false;
                    setResult(result, reason, FAIL_RED);
                    status(reason, FAIL_RED);
                });
            } catch (IOException | RuntimeException failed) {
                failTransaction(result, failed);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                failTransaction(result, interrupted);
            }
        });
    }

    private void deployFinished(InteractSession s, Network network, String txHash,
            ReceiptOutcome outcome, JLabel result) {
        running = false;
        JsonRpcClient.Receipt receipt = outcome.receipt();
        if (outcome.decision().state() == ReceiptWaiter.State.SUCCESS
                && receipt != null && !receipt.contractAddress().isBlank()) {
            String address = receipt.contractAddress();
            DeploymentRecord record = new DeploymentRecord(s.artifact().name(),
                    address, network.name(), txHash, receipt.blockNumber(),
                    System.currentTimeMillis());
            deployments.add(0, record);
            saveWorkspace();
            rebuildDeploymentsBranch();
            deploymentsModel.refresh();
            refreshWatchFilter();
            updateWatchAddresses();
            publishSearch();
            String headline = s.artifact().name() + " deployed at "
                    + DisplayValues.shortAddress(address)
                    + " (block " + receipt.blockNumber() + ")";
            status(headline, OK_GREEN);
            balloon(headline, address, true);
            openInteractFor(s.attachedTo(address));
        } else {
            setResult(result, outcome.decision().message(), FAIL_RED);
            status(outcome.decision().message(), FAIL_RED);
        }
    }

    private void call(InteractSession s, AbiEntry function,
            List<JTextField> argFields, JLabel result) {
        JsonRpcClient c = client;
        if (c == null || !connected) {
            setResult(result, NOT_CONNECTED, FAIL_RED);
            return;
        }
        String data;
        try {
            data = s.callData(function, fieldTexts(argFields));
        } catch (IllegalArgumentException refusal) {
            setResult(result, refusal.getMessage(), FAIL_RED);
            status(refusal.getMessage(), FAIL_RED);
            return;
        }
        String to = s.address();
        setResult(result, "calling…", Color.GRAY);
        RP.post(() -> {
            try {
                String returned = c.ethCall(to, data);
                List<String> decoded = AbiCodec.decodeReturn(function, returned);
                String text = decoded.isEmpty()
                        ? "OK (no return value)" : String.join(", ", decoded);
                SwingUtilities.invokeLater(() -> setResult(result, "→ " + text, OK_GREEN));
            } catch (JsonRpcClient.RpcException rpc) {
                String reason = revertReason(rpc, s);
                SwingUtilities.invokeLater(() -> setResult(result, reason, FAIL_RED));
            } catch (IOException | RuntimeException failed) {
                String message = messageOf(failed);
                SwingUtilities.invokeLater(() -> setResult(result, message, FAIL_RED));
            }
        });
    }

    /**
     * Amount fields speak human (v2.46.0): on an ERC-20's canonical
     * write functions, the trailing uint256 accepts "1.5" and converts
     * through the strip's decimals() — raw integers pass through
     * untouched, and a decimal form with unknown decimals is refused
     * with the reason (TokenAmounts.interpretAmount, the money law).
     * Every other function's arguments are handed on verbatim.
     */
    private List<String> tokenAwareArgs(InteractSession s, AbiEntry function,
            List<String> args) {
        if (ErcStandards.detect(s.artifact().abi()) != ErcStandards.Standard.ERC20) {
            return args;
        }
        String signature = function.signature();
        if (!signature.equals("transfer(address,uint256)")
                && !signature.equals("approve(address,uint256)")
                && !signature.equals("transferFrom(address,address,uint256)")) {
            return args;
        }
        List<String> out = new ArrayList<>(args);
        int last = out.size() - 1;
        out.set(last, org.nmox.studio.web3.engine.TokenAmounts.interpretAmount(
                out.get(last), tokenDecimals));
        return out;
    }

    private void send(InteractSession s, AbiEntry function, List<JTextField> argFields,
            JTextField valueField, JLabel result) {
        if (running) {
            status("A transaction is in flight — wait for its receipt", FAIL_RED);
            return;
        }
        JsonRpcClient c = client;
        if (c == null || !connected) {
            setResult(result, NOT_CONNECTED, FAIL_RED);
            return;
        }
        String from = (String) fromCombo.getSelectedItem();
        if (from == null || from.isBlank()) {
            setResult(result, InteractSession.READ_ONLY_REASON, FAIL_RED);
            return;
        }
        String data;
        String valueHex;
        try {
            data = s.callData(function, tokenAwareArgs(s, function, fieldTexts(argFields)));
            valueHex = InteractSession.valueWeiHex(
                    valueField == null ? null : valueField.getText());
        } catch (IllegalArgumentException refusal) {
            setResult(result, refusal.getMessage(), FAIL_RED);
            status(refusal.getMessage(), FAIL_RED);
            return;
        }
        if (!confirmRemoteBroadcast("SEND " + function.name() + "()")) {
            status("Send cancelled", Color.GRAY);
            return;
        }
        running = true;
        setResult(result, "sending…", Color.GRAY);
        status("Sending " + function.name() + "()…", Color.GRAY);
        RP.post(() -> {
            try {
                String txHash = c.sendTransaction(from, s.address(), data, valueHex);
                ReceiptOutcome outcome = awaitReceipt(c, txHash);
                boolean ok = outcome.decision().state() == ReceiptWaiter.State.SUCCESS;
                SwingUtilities.invokeLater(() -> {
                    running = false;
                    setResult(result, outcome.decision().message(),
                            ok ? OK_GREEN : FAIL_RED);
                    status(function.name() + "(): " + outcome.decision().message(),
                            ok ? OK_GREEN : FAIL_RED);
                });
            } catch (JsonRpcClient.RpcException rpc) {
                String reason = revertReason(rpc, s);
                SwingUtilities.invokeLater(() -> {
                    running = false;
                    setResult(result, reason, FAIL_RED);
                    status(reason, FAIL_RED);
                });
            } catch (IOException | RuntimeException failed) {
                failTransaction(result, failed);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                failTransaction(result, interrupted);
            }
        });
    }

    /** Off-EDT: marshals a non-revert transaction failure back to the labels. */
    private void failTransaction(JLabel result, Exception failed) {
        String message = messageOf(failed);
        SwingUtilities.invokeLater(() -> {
            running = false;
            setResult(result, message, FAIL_RED);
            status(message, FAIL_RED);
        });
    }

    /** The revert reason, using the artifact's custom-error entries when data is present. */
    private static String revertReason(JsonRpcClient.RpcException rpc, InteractSession s) {
        if (!rpc.data().isEmpty()) {
            return AbiCodec.decodeRevert(rpc.data(), s.artifact().errors());
        }
        return messageOf(rpc);
    }

    /** The UI loop over the pure {@link ReceiptWaiter} decision model. */
    private static ReceiptOutcome awaitReceipt(JsonRpcClient c, String txHash)
            throws IOException, InterruptedException {
        ReceiptWaiter waiter = new ReceiptWaiter();
        while (true) {
            JsonRpcClient.Receipt receipt = c.getTransactionReceipt(txHash);
            ReceiptWaiter.Decision decision = waiter.onReceipt(receipt);
            if (decision.terminal()) {
                return new ReceiptOutcome(decision, receipt);
            }
            Thread.sleep(decision.delayMillis());
        }
    }

    private record ReceiptOutcome(ReceiptWaiter.Decision decision,
            JsonRpcClient.Receipt receipt) {
    }

    private void attachToAddress(InteractSession s) {
        NotifyDescriptor.InputLine input = new NotifyDescriptor.InputLine(
                "Contract address (0x…):", "Attach " + s.artifact().name() + " to Address");
        while (true) {
            if (DialogDisplayer.getDefault().notify(input) != NotifyDescriptor.OK_OPTION) {
                return;
            }
            String text = input.getInputText().trim();
            if (DisplayValues.isAddress(text)) {
                openInteractFor(s.attachedTo(text));
                status("Attached " + s.artifact().name() + " to "
                        + DisplayValues.shortAddress(text), OK_GREEN);
                return;
            }
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "That isn't an address — expected 0x followed by 40 hex digits.",
                    NotifyDescriptor.WARNING_MESSAGE));
        }
    }

    private void attachDeployment(DeploymentRecord record) {
        ContractArtifact artifact = artifactByName(record.contractName());
        if (artifact == null) {
            showInteractHint("No artifact named " + record.contractName()
                    + " in this project — Compile or Rescan first, then select the "
                    + "deployment again.");
            tabs.setSelectedIndex(0);
            return;
        }
        openInteractFor(InteractSession.attached(artifact, record.address(), hasAccounts()));
        Network current = selectedNetwork();
        if (current != null && !current.name().equals(record.networkName())) {
            status("Note: this deployment was recorded on " + record.networkName()
                    + " — calls go to " + current.name(), AMBER);
        }
    }

    private ContractArtifact artifactByName(String name) {
        for (ContractArtifact artifact : allArtifacts()) {
            if (artifact.name().equals(name)) {
                return artifact;
            }
        }
        return null;
    }

    /** The saved address of an imported contract, or null. */
    private String importedAddressFor(String name) {
        for (org.nmox.studio.web3.model.ImportedContract record : importedContracts) {
            if (record.name().equals(name) && !record.address().isEmpty()) {
                return record.address();
            }
        }
        return null;
    }

    /** The attached token's decimals(), read by the strip — null until
     *  known; consulted by the amount-field interpreter (v2.46.0). */
    private Integer tokenDecimals;

    /** Built artifacts + imported ABIs — every consumer sees both. */
    private List<ContractArtifact> allArtifacts() {
        if (importedArtifacts.isEmpty()) {
            return artifacts;
        }
        List<ContractArtifact> all = new ArrayList<>(artifacts);
        all.addAll(importedArtifacts);
        return all;
    }

    // ---- Watch ------------------------------------------------------------------

    private JComponent buildWatchTab() {
        JPanel panel = new JPanel(new BorderLayout());
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        watchButton.setForeground(ACCENT);
        watchButton.setToolTipText("Poll the chain every 2 s: new blocks plus decoded "
                + "events of your deployed contracts");
        watchButton.addActionListener(e -> toggleWatch());
        bar.add(watchButton);
        bar.addSeparator();
        bar.add(new JLabel(" Contract: "));
        watchFilterCombo.setRenderer(new WatchFilterRenderer());
        watchFilterCombo.setToolTipText("Whose events to fetch — blocks always show");
        watchFilterCombo.addActionListener(e -> {
            if (!watchFilterRefreshing) {
                updateWatchAddresses();
            }
        });
        bar.add(watchFilterCombo);
        bar.addSeparator();
        JButton inspectButton = new JButton("Inspect tx\u2026");
        inspectButton.setToolTipText("Decode a transaction by hash against your "
                + "artifacts' ABIs \u2014 read-only");
        inspectButton.getAccessibleContext().setAccessibleName(
                "Inspect a transaction by hash");
        inspectButton.addActionListener(e -> inspectTransaction());
        bar.add(inspectButton);
        JButton historyButton = new JButton("History\u2026");
        historyButton.setToolTipText("Fetch and decode past events for an address "
                + "over a bounded block range \u2014 exportable as CSV");
        historyButton.getAccessibleContext().setAccessibleName(
                "Query event history");
        historyButton.addActionListener(e -> eventHistory());
        bar.add(historyButton);
        panel.add(bar, BorderLayout.NORTH);

        JTable table = org.nmox.studio.core.util.PlainTables.disableHtml(new JTable(watchModel));
        table.setFont(MONO);
        table.getAccessibleContext().setAccessibleName("Watched blocks and events");
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getColumnModel().getColumn(0).setPreferredWidth(70);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(520);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        refreshWatchFilter();
        return panel;
    }

    private void toggleWatch() {
        if (watchExec != null) {
            stopWatch();
            status("Watch stopped", Color.GRAY);
            return;
        }
        if (client == null || !connected) {
            status(NOT_CONNECTED, FAIL_RED);
            return;
        }
        feed.clear();
        watchModel.refresh();
        lastWatchedBlock = -1;
        logsFromBlock = Long.MAX_VALUE;
        updateWatchAddresses();
        watchExec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = Threads.daemon(r, "Contract Studio watch");
            return t;
        });
        watchExec.scheduleWithFixedDelay(this::watchTick, 0, 2, TimeUnit.SECONDS);
        watchButton.setText("STOP");
        Network network = selectedNetwork();
        status("Watching " + (network == null ? "the chain" : network.name())
                + " — polling every 2 s", Color.GRAY);
    }

    private void stopWatch() {
        watchGeneration.incrementAndGet(); // any in-flight tick loses cursor ownership
        if (watchExec != null) {
            watchExec.shutdownNow();
            watchExec = null;
        }
        watchButton.setText("START");
    }

    /**
     * One poll, on the watch daemon thread: new blocks (deduped by the
     * feed) and, for the watched addresses, logs since the last polled
     * block, decoded against the scanned events. Errors gray the chip;
     * they never raise a dialog.
     */
    private void watchTick() {
        JsonRpcClient c = client;
        if (c == null) {
            return;
        }
        final long gen = watchGeneration.get();
        try {
            long current = c.blockNumber();
            boolean firstTick = lastWatchedBlock < 0;
            // both lanes clamped to the cap window (the log clamp is the
            // v1.100.0 fix: a failing getLogs never advanced its cursor,
            // so retries widened the range — and the response — unboundedly)
            var plan = org.nmox.studio.web3.engine.WatchCursor.plan(
                    lastWatchedBlock, logsFromBlock, current, CATCHUP_CAP);
            for (long n = plan.blockFrom(); n <= plan.blockTo(); n++) {
                JsonRpcClient.Block block = c.getBlockByNumber(String.valueOf(n), false);
                if (block != null) {
                    feed.addBlock(block.number(), block.txCount(), block.gasUsed(),
                            block.gasLimit(), block.hash());
                }
            }
            List<String> addresses = watchAddresses;
            EventMatcher matcher = eventMatcher;
            boolean consumedLogs = plan.hasLogs() && !addresses.isEmpty();
            if (consumedLogs) {
                for (String address : addresses) {
                    feedLogs(c, matcher, address, plan.logFrom(), plan.logTo());
                }
            }
            if (gen != watchGeneration.get()) {
                return; // the watch was re-armed mid-tick — the new
                        // poller owns the cursors; a dying tick must
                        // not tear or resurrect them
            }
            if (consumedLogs) {
                logsFromBlock = plan.logTo() + 1;
            } else if (firstTick) {
                logsFromBlock = current; // the watch-start block
            }
            lastWatchedBlock = current;
            SwingUtilities.invokeLater(() -> {
                watchModel.refresh();
                chip("chain " + liveChainId + " · block " + current, OK_GREEN);
            });
        } catch (IOException | RuntimeException pollFailed) {
            SwingUtilities.invokeLater(() -> chip(NOT_CONNECTED, Color.GRAY));
        }
    }

    /** Fetches and decodes one address's logs for the block range; skips unknown topics. */
    private void feedLogs(JsonRpcClient c, EventMatcher matcher, String address,
            long fromBlock, long toBlock) throws IOException {
        for (JsonRpcClient.LogEntry log
                : c.getLogs(address, String.valueOf(fromBlock), String.valueOf(toBlock))) {
            if (log.topics().isEmpty()) {
                continue;
            }
            EventMatcher.Match match = matcher.match(log.topics().get(0));
            if (match == null) {
                continue; // someone else's event shape — normal, skip
            }
            Map<String, String> decoded;
            try {
                decoded = matcher.decodedDisplay(match, log.topics(), log.data());
            } catch (RuntimeException malformed) {
                decoded = Map.of("note", "decode failed: " + malformed.getMessage());
            }
            feed.addEvent(log.blockNumber(), match.contractName(),
                    match.event().name(), decoded);
        }
    }

    /** Recomputes (on the EDT) which addresses the poller fetches logs for. */
    private void updateWatchAddresses() {
        Network network = selectedNetwork();
        String networkName = network == null ? "" : network.name();
        Object filter = watchFilterCombo.getSelectedItem();
        Set<String> addresses = new LinkedHashSet<>();
        for (DeploymentRecord record : deployments) {
            if (!record.networkName().equals(networkName)) {
                continue;
            }
            if (filter instanceof DeploymentRecord wanted
                    && !wanted.address().equals(record.address())) {
                continue;
            }
            addresses.add(record.address());
        }
        watchAddresses = List.copyOf(addresses);
    }

    private void refreshWatchFilter() {
        watchFilterRefreshing = true;
        try {
            Object selected = watchFilterCombo.getSelectedItem();
            watchFilterCombo.removeAllItems();
            watchFilterCombo.addItem("All deployed contracts");
            for (DeploymentRecord record : deployments) {
                watchFilterCombo.addItem(record);
            }
            watchFilterCombo.setSelectedItem(
                    selected instanceof DeploymentRecord ? selected : "All deployed contracts");
            watchFilterCombo.setMaximumSize(watchFilterCombo.getPreferredSize());
        } finally {
            watchFilterRefreshing = false;
        }
        updateWatchAddresses();
    }

    // ---- Oversight ---------------------------------------------------------------

    private JComponent buildOversightTab() {
        JPanel panel = new JPanel(new java.awt.GridLayout(3, 1));

        JTable sizeTable = new JTable(sizeModel);
        sizeTable.getAccessibleContext().setAccessibleName("Contract sizes");
        sizeTable.setFont(MONO);
        sizeTable.setDefaultRenderer(Object.class,
                org.nmox.studio.core.util.PlainTables.plain(new SizeCellRenderer()));
        sizeTable.getColumnModel().getColumn(2).setCellRenderer(new HeadroomBarRenderer());
        JScrollPane sizeScroll = new JScrollPane(sizeTable);
        sizeScroll.setBorder(BorderFactory.createTitledBorder(
                "Contract sizes — EIP-170 caps deployed bytecode at 24,576 bytes"));
        panel.add(sizeScroll);

        JPanel gasPanel = new JPanel(new BorderLayout());
        JToolBar gasBar = new JToolBar();
        gasBar.setFloatable(false);
        gasButton.setToolTipText("forge test --gas-report, parsed into the table");
        gasButton.addActionListener(e -> runGasReport());
        gasBar.add(gasButton);
        gasPanel.add(gasBar, BorderLayout.NORTH);
        JTable gasTable = org.nmox.studio.core.util.PlainTables.disableHtml(new JTable(gasModel));
        gasTable.getAccessibleContext().setAccessibleName("Gas report");
        gasTable.setFont(MONO);
        gasPanel.add(new JScrollPane(gasTable), BorderLayout.CENTER);
        gasPanel.setBorder(BorderFactory.createTitledBorder("Gas report"));
        panel.add(gasPanel);

        JTable deployTable = org.nmox.studio.core.util.PlainTables.disableHtml(new JTable(deploymentsModel));
        deployTable.getAccessibleContext().setAccessibleName("Deployment address book");
        deployTable.setFont(MONO);
        deployTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = deployTable.rowAtPoint(e.getPoint());
                    if (row >= 0 && row < deployments.size()) {
                        attachDeployment(deployments.get(row));
                    }
                }
            }
        });
        JPopupMenu popup = new JPopupMenu();
        javax.swing.JMenuItem copy = new javax.swing.JMenuItem("Copy address");
        copy.addActionListener(e -> {
            int row = deployTable.getSelectedRow();
            if (row >= 0 && row < deployments.size()) {
                String address = deployments.get(row).address();
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new java.awt.datatransfer.StringSelection(address), null);
                status("Copied " + address, OK_GREEN);
            }
        });
        popup.add(copy);
        javax.swing.JMenuItem forget = new javax.swing.JMenuItem("Forget deployment");
        forget.addActionListener(e -> {
            int row = deployTable.getSelectedRow();
            if (row < 0 || row >= deployments.size()) {
                return;
            }
            DeploymentRecord record = deployments.get(row);
            // the address book is BOOKKEEPING — forgetting a row touches
            // nothing on any chain, and the message says so; safe default
            // per the v1.98.0 law (v1.269.0 — the organize sweep's fourth
            // surface: deployments accumulated forever with no gesture)
            NotifyDescriptor confirm = new NotifyDescriptor(
                    org.nmox.studio.core.util.PlainDialogs.plain("Forget " + record.contractName() + " at "
                            + record.address() + "? Only this address-book row"
                            + " is removed \u2014 the contract on chain is"
                            + " untouched.", "Message"),
                    "Forget Deployment",
                    NotifyDescriptor.YES_NO_OPTION,
                    NotifyDescriptor.QUESTION_MESSAGE,
                    null,
                    NotifyDescriptor.NO_OPTION);
            if (DialogDisplayer.getDefault().notify(confirm)
                    != NotifyDescriptor.YES_OPTION) {
                return;
            }
            deployments.remove(row);
            saveWorkspace();
            deploymentsModel.fireTableDataChanged();
            rebuildDeploymentsBranch();
            status("Forgot " + record.contractName(), Color.GRAY);
        });
        popup.add(forget);
        // the menu's verbs read getSelectedRow(); make the CLICKED row
        // the selected row before the menu opens (v1.270.0 arc review)
        org.nmox.studio.core.util.Popups.selectOnTrigger(deployTable);
        deployTable.setComponentPopupMenu(popup);
        deployTable.setToolTipText("Double-click to open in Interact; right-click to copy the address or forget the row");
        JScrollPane deployScroll = new JScrollPane(deployTable);
        deployScroll.setBorder(BorderFactory.createTitledBorder("Deployments (address book)"));
        panel.add(deployScroll);

        return panel;
    }

    private void runGasReport() {
        File dir = projectDirOrNull();
        if (dir == null) {
            status(NO_PROJECT_HINT, FAIL_RED);
            return;
        }
        if (!trustedToRunProjectCode(dir)) {
            return;
        }
        if (gasRunning) {
            return;
        }
        gasRunning = true;
        gasButton.setEnabled(false);
        appendLog("$ forge test --gas-report\n");
        status("Running forge test --gas-report…", Color.GRAY);
        RP.post(() -> {
            try {
                StringBuilder output = new StringBuilder();
                int exit = streamProcess(
                        List.of(forgeCommand(), "test", "--gas-report"), dir, output);
                List<GasReportParser.FunctionGas> rows =
                        GasReportParser.parseGasReport(output.toString());
                SwingUtilities.invokeLater(() -> {
                    gasRunning = false;
                    gasButton.setEnabled(true);
                    gasModel.set(rows);
                    if (rows.isEmpty()) {
                        status(exit == 0
                                ? "No gas table in the output — does the project have tests?"
                                : "forge test failed (exit " + exit + ") — see the log",
                                FAIL_RED);
                    } else {
                        status(rows.size() + " function row"
                                + (rows.size() == 1 ? "" : "s")
                                + (exit == 0 ? "" : " · forge exit " + exit),
                                exit == 0 ? OK_GREEN : AMBER);
                    }
                });
            } catch (IOException notFound) {
                SwingUtilities.invokeLater(() -> {
                    gasRunning = false;
                    gasButton.setEnabled(true);
                    status(FORGE_HINT, FAIL_RED);
                    balloon("forge not found", FORGE_HINT, false);
                });
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                SwingUtilities.invokeLater(() -> {
                    gasRunning = false;
                    gasButton.setEnabled(true);
                    status("Gas report interrupted", FAIL_RED);
                });
            }
        });
    }

    // ---- Compile / Rescan ------------------------------------------------------

    private void compile() {
        File dir = projectDirOrNull();
        if (dir == null) {
            status(NO_PROJECT_HINT, FAIL_RED);
            return;
        }
        if (!trustedToRunProjectCode(dir)) {
            return;
        }
        if (compiling) {
            return;
        }
        compiling = true;
        compileButton.setEnabled(false);
        appendLog("$ forge build\n");
        status("forge build…", Color.GRAY);
        RP.post(() -> {
            try {
                StringBuilder ignored = new StringBuilder(0);
                int exit = streamProcess(List.of(forgeCommand(), "build"), dir, ignored);
                SwingUtilities.invokeLater(() -> {
                    compiling = false;
                    compileButton.setEnabled(true);
                    if (exit == 0) {
                        status("forge build OK", OK_GREEN);
                        rescan();
                    } else {
                        status("forge build failed (exit " + exit + ") — see the log",
                                FAIL_RED);
                    }
                });
            } catch (IOException notFound) {
                SwingUtilities.invokeLater(() -> {
                    compiling = false;
                    compileButton.setEnabled(true);
                    appendLog(FORGE_HINT + "\n");
                    status(FORGE_HINT, FAIL_RED);
                    balloon("forge not found", FORGE_HINT, false);
                });
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                SwingUtilities.invokeLater(() -> {
                    compiling = false;
                    compileButton.setEnabled(true);
                    status("Compile interrupted", FAIL_RED);
                });
            }
        });
    }

    /**
     * Off-EDT: runs the command in {@code dir}, streaming each output
     * line to the log strip (and into {@code collector}); returns the
     * exit code. UTF-8, stderr merged — a compiler's diagnostics belong
     * in the same stream.
     */
    /**
     * Workspace Trust for the forge buttons (v1.224.0 spawn-site
     * sweep): {@code forge build} honors the repo's own foundry.toml
     * and {@code forge test} executes the repo's test contracts —
     * with Foundry's {@code ffi} cheatcode enabled those tests can run
     * ARBITRARY HOST COMMANDS, so this is the same inward-execution
     * flow every other project-code spawn gates. web3 carries no rack
     * dependency (v1.46.0), so the gate rides the {@link
     * org.nmox.studio.core.spi.TrustGate} facade; a null lookup means
     * the rack module is absent (no trust service exists to consult —
     * a degenerate install this product never ships).
     */
    private boolean trustedToRunProjectCode(File dir) {
        org.nmox.studio.core.spi.TrustGate gate =
                org.nmox.studio.core.spi.TrustGate.find();
        if (gate != null && !gate.requestTrust(dir)) {
            status("Not run — workspace not trusted", FAIL_RED);
            return false;
        }
        return true;
    }

    private int streamProcess(List<String> command, File dir, StringBuilder collector)
            throws IOException, InterruptedException {
        ProcessBuilder builder = ProcessSupport.builder(command)
                .directory(dir)
                .redirectErrorStream(true);
        Process process = builder.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                collector.append(line).append('\n');
                String shown = line;
                SwingUtilities.invokeLater(() -> appendLog(shown + "\n"));
            }
        }
        return process.waitFor();
    }

    /**
     * Foundry's installer puts forge in ~/.foundry/bin, which the IDE's
     * augmented PATH doesn't cover — fall back to it explicitly before
     * letting the OS report "not found".
     */
    private static String forgeCommand() {
        if (!"forge".equals(ToolLocator.resolve("forge"))) {
            return "forge"; // resolvable on the augmented PATH
        }
        File foundry = new File(System.getProperty("user.home"), ".foundry/bin/forge");
        return foundry.canExecute() ? foundry.getAbsolutePath() : "forge";
    }

    private void rescan() {
        File dir = projectDirOrNull();
        if (dir == null) {
            artifacts = List.of();
            eventMatcher = EventMatcher.empty();
            rebuildContractsBranch();
            sizeModel.refresh();
            publishSearch();
            status(NO_PROJECT_HINT, Color.GRAY);
            return;
        }
        // Hidden tabs take a note instead of walking: open-at-startup means
        // this runs during boot behind the selected tab, and the scan is a
        // Files.walk over out/ + artifacts/ nobody is looking at. The walk
        // waits for componentShowing (the DB Studio Docker-offer idiom) —
        // the window system fires it at startup for the tab that IS selected.
        if (!isShowing()) {
            rescanPending = true;
            return;
        }
        status("Scanning artifacts…", Color.GRAY);
        RP.post(() -> {
            List<ContractArtifact> found = scanWithProgress(dir);
            EventMatcher matcher = EventMatcher.build(found);
            SwingUtilities.invokeLater(() -> {
                applyArtifacts(found, matcher);
                status(found.size() + (found.size() == 1
                        ? " contract artifact" : " contract artifacts"), Color.GRAY);
            });
        });
    }

    /**
     * A build finished somewhere (rack lane, terminal, CI) — the pulse
     * saw artifact JSON move. Rescan quietly: the tree updating IS the
     * feedback, so no balloon and no status churn; identical scan
     * results apply nothing (the storm-law equality guard).
     */
    private void autoRescan() {
        File dir = projectDirOrNull();
        if (dir == null) {
            return;
        }
        if (!isShowing()) {
            // same visibility economy as rescan(): a build storm behind a
            // hidden tab becomes one deferred walk on next show, not N walks
            rescanPending = true;
            return;
        }
        RP.post(() -> {
            List<ContractArtifact> found = scanWithProgress(dir);
            EventMatcher matcher = EventMatcher.build(found);
            SwingUtilities.invokeLater(() -> {
                if (found.equals(artifacts)) {
                    return; // nothing actually changed — fire no UI updates
                }
                applyArtifacts(found, matcher);
            });
        });
    }

    /**
     * Off-EDT: the artifact walk under a finally-guarded ProgressHandle —
     * the last sliver of ledger 34, closed v1.48.0. Indeterminate and with
     * no Cancellable: {@code ArtifactScanner.scan} is one uninterruptible
     * {@code Files.walk} with no seam to abort mid-walk, so a cancel
     * button would be a lie (the v1.44.0 DB-connect / cloud-sync idiom).
     * The walk is usually fast; the handle exists so a huge out/ tree
     * still shows honest activity in the status line instead of silence.
     */
    private static List<ContractArtifact> scanWithProgress(File dir) {
        org.netbeans.api.progress.ProgressHandle progress =
                org.netbeans.api.progress.ProgressHandle.createHandle(
                        "Scanning contract artifacts…");
        progress.start();
        try {
            return ArtifactScanner.scan(dir.toPath());
        } finally {
            progress.finish();
        }
    }

    /** EDT: installs a scan result into the tree, models, and search index. */
    private void applyArtifacts(List<ContractArtifact> found, EventMatcher matcher) {
        artifacts = found;
        eventMatcher = matcher;
        rebuildContractsBranch();
        sizeModel.refresh();
        publishSearch();
        if (session != null
                && artifactByName(session.artifact().name()) == null) {
            session = null; // the artifact left the build output
            rebuildInteract();
        }
    }

    // ---- networks + connection chip ------------------------------------------

    private Network selectedNetwork() {
        return (Network) networkCombo.getSelectedItem();
    }

    private boolean hasAccounts() {
        return !accounts.isEmpty();
    }

    private void refreshNetworkCombo(Network select) {
        networkComboRefreshing = true;
        try {
            networkCombo.removeAllItems();
            networkCombo.addItem(LOCAL_ANVIL);
            for (Network network : networks) {
                networkCombo.addItem(network);
            }
            networkCombo.setSelectedItem(select == null ? LOCAL_ANVIL : select);
        } finally {
            networkComboRefreshing = false;
        }
        networkSelected();
    }

    /**
     * The combo changed: drop the old client, stop the watch, and probe
     * the new endpoint (chainId + blockNumber + accounts) off-EDT. The
     * chip answers honestly; the FROM combo and every write gate follow
     * eth_accounts.
     */
    private void networkSelected() {
        Network network = selectedNetwork();
        if (network == null) {
            return;
        }
        stopWatch();
        int seq = ++connectSeq;
        client = null;
        connected = false;
        accounts = List.of();
        updateWatchAddresses();
        chip("connecting…", Color.GRAY);
        RP.post(() -> {
            String url = urlFor(network);
            if (url == null) {
                SwingUtilities.invokeLater(() -> {
                    if (seq == connectSeq) {
                        chip("no RPC URL stored for " + network.name()
                                + " — remove and re-add the network", FAIL_RED);
                    }
                });
                return;
            }
            JsonRpcClient probe = new JsonRpcClient(url);
            try {
                long chainId = probe.chainId();
                long block = probe.blockNumber();
                List<String> unlocked;
                try {
                    unlocked = probe.accounts();
                } catch (IOException | RuntimeException noAccounts) {
                    unlocked = List.of(); // some gateways refuse eth_accounts: read-only
                }
                List<String> unlockedFinal = unlocked;
                SwingUtilities.invokeLater(() ->
                        connectionUp(seq, network, probe, chainId, block, unlockedFinal));
            } catch (IOException | RuntimeException down) {
                SwingUtilities.invokeLater(() -> {
                    if (seq == connectSeq) {
                        client = probe; // kept so manual actions get honest errors
                        connected = false;
                        chip(NOT_CONNECTED, Color.GRAY);
                        refreshFromCombo();
                        refreshSessionAccounts();
                    }
                });
            }
        });
    }

    private void connectionUp(int seq, Network network, JsonRpcClient probe,
            long chainId, long block, List<String> unlocked) {
        if (seq != connectSeq) {
            return; // the user has already switched again
        }
        client = probe;
        connected = true;
        liveChainId = chainId;
        accounts = unlocked;
        if (network.chainId() > 0 && chainId != network.chainId()) {
            chip("chain " + chainId + " (expected " + network.chainId()
                    + ") · block " + block, AMBER);
        } else {
            chip("chain " + chainId + " · block " + block, OK_GREEN);
        }
        refreshFromCombo();
        refreshSessionAccounts();
        updateWatchAddresses();
        // the fee strip (v2.47.0): one extra read, appended when it
        // lands — a node without eth_gasPrice just keeps the plain chip
        RP.post(() -> {
            try {
                java.math.BigInteger price = probe.gasPrice();
                SwingUtilities.invokeLater(() -> {
                    if (seq == connectSeq && connected) {
                        chip(chipLabel.getText() + " · gas "
                                + org.nmox.studio.web3.engine.Units.formatWei(price),
                                chipLabel.getForeground());
                    }
                });
            } catch (Exception unsupported) {
                // honest absence — the chip already tells the truth
            }
        });
    }

    private void refreshFromCombo() {
        fromCombo.removeAllItems();
        for (String account : accounts) {
            fromCombo.addItem(account);
        }
        fromCombo.setEnabled(!accounts.isEmpty());
        fromCombo.setToolTipText(PlainText.plain(accounts.isEmpty()
                ? InteractSession.READ_ONLY_REASON
                : "The node's unlocked accounts (eth_accounts) — it signs, the IDE never can"));
    }

    /** Re-arms or disables the write surface after an accounts refresh. */
    private void refreshSessionAccounts() {
        if (session != null) {
            // a network switch may put a DIFFERENT token behind the same
            // address — drop the cached decimals so a decimal-form amount
            // REFUSES until the new chain answers, instead of converting
            // with the old chain's scale (v2.47.1 review; the money law's
            // correct failure mode is a refusal, never a mis-scale)
            tokenDecimals = null;
            session = session.withAccounts(hasAccounts());
            rebuildInteract();
        }
    }

    private void chip(String text, Color color) {
        chipLabel.setForeground(color);
        chipLabel.setText(PlainText.plain(text));
    }

    /**
     * Off-EDT (the keyring may block): the endpoint URL for a network —
     * plain from the record, or from the OS keyring for secret ones.
     * Null when a secret network has nothing stored.
     */
    private static String urlFor(Network network) {
        if (!network.secretUrl()) {
            return network.plainUrl();
        }
        char[] stored = RpcSecrets.read(network.name());
        if (stored == null) {
            return null;
        }
        try {
            return new String(stored);
        } finally {
            Arrays.fill(stored, '\0');
        }
    }

    private void addNetwork() {
        Set<String> taken = new HashSet<>();
        taken.add(LOCAL_ANVIL.name().toLowerCase(Locale.ROOT));
        for (Network network : networks) {
            taken.add(network.name().toLowerCase(Locale.ROOT));
        }
        NetworkDialog.Result result = NetworkDialog.show(taken);
        if (result == null) {
            return;
        }
        networks.add(result.network());
        if (result.network().secretUrl() && result.secretUrl() != null) {
            char[] url = result.secretUrl();
            String name = result.network().name();
            RP.post(() -> {
                RpcSecrets.save(name, url);
                Arrays.fill(url, '\0');
            });
        }
        saveWorkspace();
        rebuildNetworksBranch();
        refreshNetworkCombo(result.network());
        status("Added network " + result.network().name(), OK_GREEN);
    }

    /**
     * Removes the combo's selected network (v1.269.0 — the organize
     * sweep's fourth surface: Add Network\u2026 existed since v1.205-era
     * with no inverse, so a typo'd RPC URL lived in the combo forever
     * and a SECRET network's keychain entry outlived any way to drop
     * it). LOCAL_ANVIL is built in and refuses; the confirm carries the
     * safe default and names the keychain consequence, the DB Studio
     * remove-connection wording. Selection falls back to LOCAL_ANVIL.
     */
    private void removeSelectedNetwork() {
        Object selected = networkCombo.getSelectedItem();
        if (!(selected instanceof Network network) || LOCAL_ANVIL.equals(network)) {
            status("The local Anvil network is built in \u2014 select an added network to remove", Color.GRAY);
            return;
        }
        NotifyDescriptor confirm = new NotifyDescriptor(
                org.nmox.studio.core.util.PlainDialogs.plain("Remove network \"" + network.name() + "\"?"
                        + (network.secretUrl()
                                ? " Its keychain RPC URL is deleted too."
                                : ""), "Message"),
                "Remove Network",
                NotifyDescriptor.YES_NO_OPTION,
                NotifyDescriptor.QUESTION_MESSAGE,
                null,
                NotifyDescriptor.NO_OPTION);
        if (DialogDisplayer.getDefault().notify(confirm) != NotifyDescriptor.YES_OPTION) {
            return;
        }
        networks.remove(network);
        if (network.secretUrl()) {
            String name = network.name();
            RP.post(() -> RpcSecrets.delete(name));
        }
        saveWorkspace();
        rebuildNetworksBranch();
        refreshNetworkCombo(LOCAL_ANVIL);
        status("Removed network " + network.name(), Color.GRAY);
    }

    // ---- persistence (.nmoxweb3.json, the RackService idiom) ---------------------

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

    private File workspaceDir() {
        File dir = projectDirOrNull();
        return dir != null ? dir : new File(System.getProperty("user.home"));
    }

    private void reloadWorkspace() {
        // the read below must see every queued write — an A→B→A re-aim
        // bounce could otherwise read A's file before A's last save lands
        // (bounded ms drain; see SaveLane.flush)
        SAVES.flush(5, java.util.concurrent.TimeUnit.SECONDS);
        // the FILE READ rides RP, not the EDT (the apiclient
        // onProjectReaimed idiom): a slow or networked filesystem must
        // stall a worker, never the paint thread. State teardown waits
        // for the loaded workspace so the tab never shows an empty
        // in-between; the sequence makes the newest reload win an
        // overlapping re-aim/pulse burst.
        final File dir = workspaceDir();
        final long seq = ++reloadSeq;
        RP.post(() -> {
            Web3WorkspaceIO.LoadOutcome outcome = Web3WorkspaceIO.loadGuarded(dir);
            SwingUtilities.invokeLater(() -> {
                if (seq != reloadSeq) {
                    return; // a newer reload superseded this read
                }
                applyReloadedWorkspace(dir, outcome);
            });
        });
    }

    /** EDT-confined: the reload seam; only {@link #reloadWorkspace} bumps it. */
    private long reloadSeq;

    /** EDT: swaps the studio onto a freshly read workspace. */
    private void applyReloadedWorkspace(File dir, Web3WorkspaceIO.LoadOutcome outcome) {
        Network previous = selectedNetwork();
        stopWatch();
        session = null;
        networks.clear();
        deployments.clear();
        Web3WorkspaceIO.Workspace workspace = outcome.workspace();
        if (outcome.backup() != null) {
            // corrupt file: the IO layer copied it aside BEFORE handing us the
            // empty fallback — the address book survives in the .bak
            try {
                org.openide.awt.NotificationDisplayer.getDefault().notify(
                        "Couldn't read " + Web3WorkspaceIO.FILENAME + " — starting empty",
                        javax.swing.UIManager.getIcon("OptionPane.warningIcon"),
                        "The unreadable original was kept at " + outcome.backup().getName() + ".",
                        null);
            } catch (RuntimeException | LinkageError ignored) {
                // notifications unavailable (tests, stripped platform)
            }
        }
        networks.addAll(workspace.networks());
        deployments.addAll(workspace.deployments());
        applyImported(workspace.imported());
        selfWrites.noteSync(new File(dir, Web3WorkspaceIO.FILENAME));
        rebuildNetworksBranch();
        rebuildDeploymentsBranch();
        deploymentsModel.refresh();
        refreshWatchFilter();
        rebuildInteract();
        publishSearch();
        // keep the user's network if the reloaded list still has it —
        // an external edit that adds a deployment must not yank the combo
        Network keep = null;
        if (previous != null) {
            for (Network network : networks) {
                if (network.name().equals(previous.name())) {
                    keep = network;
                }
            }
        }
        refreshNetworkCombo(keep);
        rescan();
        restartPulseIfOpen();
    }

    /**
     * Rebuilds the imported-artifact list from the workspace records. A
     * record whose stored ABI no longer parses (a hand-edit) is skipped
     * with a log line and kept in the FILE untouched — the studio never
     * destroys what it cannot read (the .bak law's little sibling).
     */
    private void applyImported(List<org.nmox.studio.web3.model.ImportedContract> records) {
        importedContracts = new ArrayList<>(records);
        List<ContractArtifact> parsed = new ArrayList<>();
        for (org.nmox.studio.web3.model.ImportedContract record : records) {
            try {
                parsed.add(org.nmox.studio.web3.engine.ArtifactScanner.fromImported(record));
            } catch (RuntimeException malformed) {
                java.util.logging.Logger.getLogger(Web3StudioTopComponent.class.getName())
                        .log(java.util.logging.Level.WARNING,
                                "Imported ABI \"{0}\" no longer parses ({1}) — skipping",
                                new Object[]{record.name(), malformed.getMessage()});
            }
        }
        importedArtifacts = parsed;
        rebuildContractsBranch();
    }

    /** Import ABI… — any deployed contract becomes interactable (v2.45.0). */
    private void importAbi() {
        JTextField nameField = new JTextField(24);
        nameField.getAccessibleContext().setAccessibleName("Contract name");
        JTextField addressField = new JTextField(44);
        addressField.getAccessibleContext().setAccessibleName(
                "Deployed address, optional");
        javax.swing.JTextArea abiArea = new javax.swing.JTextArea(12, 48);
        abiArea.setFont(MONO);
        abiArea.setLineWrap(true);
        abiArea.getAccessibleContext().setAccessibleName("ABI JSON array");
        JPanel form = new JPanel(new BorderLayout(0, 6));
        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridy = 0; gc.anchor = GridBagConstraints.WEST;
        gc.insets = new java.awt.Insets(2, 2, 2, 6);
        top.add(new JLabel("Name:"), gc);
        gc.gridx = 1;
        top.add(nameField, gc);
        gc.gridx = 0; gc.gridy = 1;
        top.add(new JLabel("Address:"), gc);
        gc.gridx = 1;
        top.add(addressField, gc);
        form.add(top, BorderLayout.NORTH);
        form.add(new JScrollPane(abiArea), BorderLayout.CENTER);
        JLabel hint = new JLabel("Paste the ABI JSON array — from a block explorer's "
                + "Contract tab, an artifact's abi field, or a teammate.");
        hint.setForeground(Color.GRAY);
        form.add(hint, BorderLayout.SOUTH);
        NotifyDescriptor descriptor = new NotifyDescriptor(form, "Import ABI",
                NotifyDescriptor.OK_CANCEL_OPTION, NotifyDescriptor.PLAIN_MESSAGE,
                null, NotifyDescriptor.OK_OPTION);
        if (DialogDisplayer.getDefault().notify(descriptor) != NotifyDescriptor.OK_OPTION) {
            return;
        }
        String name = nameField.getText().trim();
        String address = addressField.getText().trim();
        String abiJson = abiArea.getText().trim();
        if (name.isEmpty()) {
            status("Import needs a name", FAIL_RED);
            return;
        }
        if (artifactByName(name) != null) {
            status("\"" + name + "\" already exists — imported names can't "
                    + "shadow an artifact", FAIL_RED);
            return;
        }
        if (!address.isEmpty() && !address.matches("0x[0-9a-fA-F]{40}")) {
            status("Not an address — expected 0x + 40 hex characters", FAIL_RED);
            return;
        }
        if (importedContracts.size() >= Web3WorkspaceIO.IMPORTED_CAP) {
            status("Import cap reached (" + Web3WorkspaceIO.IMPORTED_CAP
                    + ") — remove one first", FAIL_RED);
            return;
        }
        org.nmox.studio.web3.model.ImportedContract record;
        ContractArtifact artifact;
        try {
            record = new org.nmox.studio.web3.model.ImportedContract(
                    name, abiJson, address);
            artifact = org.nmox.studio.web3.engine.ArtifactScanner.fromImported(record);
        } catch (RuntimeException bad) {
            status("ABI didn't parse: " + bad.getMessage(), FAIL_RED);
            return;
        }
        if (artifact.abi().isEmpty()) {
            status("That ABI has no functions or events — nothing to interact with",
                    FAIL_RED);
            return;
        }
        List<org.nmox.studio.web3.model.ImportedContract> grown =
                new ArrayList<>(importedContracts);
        grown.add(record);
        applyImported(grown);
        saveWorkspace();
        publishSearch();
        status("Imported \"" + name + "\" — " + artifact.functions().size()
                + " functions, " + artifact.events().size() + " events", ACCENT);
        if (!address.isEmpty()) {
            openInteractFor(InteractSession.attached(artifact, address, hasAccounts()));
        }
    }

    /** The inverse gesture (the organize law): imports can be removed. */
    private void removeImported() {
        if (importedContracts.isEmpty()) {
            status("No imported ABIs to remove", FAIL_RED);
            return;
        }
        javax.swing.JComboBox<String> which = new javax.swing.JComboBox<>(
                importedContracts.stream()
                        .map(org.nmox.studio.web3.model.ImportedContract::name)
                        .toArray(String[]::new));
        which.getAccessibleContext().setAccessibleName("Imported contract to remove");
        NotifyDescriptor descriptor = new NotifyDescriptor(which,
                "Remove imported ABI", NotifyDescriptor.OK_CANCEL_OPTION,
                NotifyDescriptor.PLAIN_MESSAGE, null, NotifyDescriptor.CANCEL_OPTION);
        if (DialogDisplayer.getDefault().notify(descriptor) != NotifyDescriptor.OK_OPTION) {
            return;
        }
        String name = (String) which.getSelectedItem();
        List<org.nmox.studio.web3.model.ImportedContract> shrunk =
                new ArrayList<>(importedContracts);
        shrunk.removeIf(c -> c.name().equals(name));
        applyImported(shrunk);
        saveWorkspace();
        publishSearch();
        if (session != null && session.artifact().name().equals(name)) {
            session = null;
            rebuildInteract();
        }
        status("Removed imported \"" + name + "\" — the chain is untouched", ACCENT);
    }

    /**
     * Writes networks AND deployments together — adding one never
     * clobbers the other. EDT: both lists are EDT-confined, so the JSON
     * snapshot is taken here (secret URLs already stripped by toJson)
     * and only the disk write rides the save lane (debt #16).
     */
    private void saveWorkspace() {
        File file = new File(workspaceDir(), Web3WorkspaceIO.FILENAME);
        String json = Web3WorkspaceIO.toJson(
                new Web3WorkspaceIO.Workspace(networks, deployments, importedContracts));
        SAVES.save(() -> writeSnapshot(file, json));
    }

    /**
     * Save lane only: the write and its self-stamp are ONE task, so a
     * lane-ordered pulse verdict can never see the write without the
     * stamp.
     */
    private void writeSnapshot(File file, String json) {
        try {
            org.nmox.studio.core.util.AtomicFiles.writeString(file.toPath(), json);
            selfWrites.noteSync(file);
            saveFailureNotified = false;
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(Web3StudioTopComponent.class.getName())
                    .log(java.util.logging.Level.WARNING, "Web3 workspace save failed", ex);
            if (!saveFailureNotified) {
                saveFailureNotified = true;
                org.openide.awt.NotificationDisplayer.getDefault().notify(
                        "Contract Studio can't save its workspace",
                        javax.swing.UIManager.getIcon("OptionPane.warningIcon"),
                        "Changes are not being persisted: " + ex.getMessage(),
                        null);
            }
        }
    }

    // ---- Quick Search entry points -------------------------------------------------

    private void publishSearch() {
        Web3SearchProvider.publish(List.copyOf(artifacts), List.copyOf(deployments));
    }

    /** Selects the artifact under Contracts, opening Interact on it. Quick Search's door. */
    public void selectContract(String contractName) {
        for (int i = 0; i < contractsNode.getChildCount(); i++) {
            DefaultMutableTreeNode child =
                    (DefaultMutableTreeNode) contractsNode.getChildAt(i);
            if (child.getUserObject() instanceof ContractArtifact artifact
                    && artifact.name().equals(contractName)) {
                TreePath path = new TreePath(child.getPath());
                tree.setSelectionPath(path);
                tree.scrollPathToVisible(path);
                return;
            }
        }
    }

    /** Selects the deployment in the tree (attaching Interact to it). Quick Search's door. */
    public void selectDeployment(DeploymentRecord record) {
        for (int i = 0; i < deploymentsNode.getChildCount(); i++) {
            DefaultMutableTreeNode child =
                    (DefaultMutableTreeNode) deploymentsNode.getChildAt(i);
            if (child.getUserObject() instanceof DeploymentRecord candidate
                    && candidate.address().equals(record.address())
                    && candidate.networkName().equals(record.networkName())) {
                TreePath path = new TreePath(child.getPath());
                tree.setSelectionPath(path);
                tree.scrollPathToVisible(path);
                return;
            }
        }
        attachDeployment(record); // not in the tree (stale index): attach directly
    }

    // ---- lifecycle -----------------------------------------------------------------

    private void attachRackListener() {
        org.nmox.studio.core.spi.ProjectAim aim =
                org.nmox.studio.core.spi.ProjectAim.find();
        if (aim == null) {
            return; // rack absent (plain tests): no project switches to follow
        }
        aim.addListener(rackListener);
        rackListenerAttached = true;
    }

    @Override
    protected void componentShowing() {
        if (rescanPending) {
            rescanPending = false;
            rescan();
        }
    }

    /** The initial load happened; re-aims arrive via the rack listener. */
    private boolean loadedOnce;

    @Override
    public void componentOpened() {
        if (!rackListenerAttached) {
            attachRackListener();
        }
        if (!loadedOnce) {
            // first open after construction: exactly one initial load, and it
            // must precede the chain auto-connect (which reads the networks).
            // rescan() inside stays showing-gated — hidden tabs still defer.
            loadedOnce = true;
            reloadWorkspace();
        }
        if (chainAutoConnect == null) {
            org.nmox.studio.core.spi.LiveServings servings =
                    org.nmox.studio.core.spi.LiveServings.find();
            if (servings != null) {
                chainAutoConnect = new org.nmox.studio.web3.engine.ChainAutoConnect(
                        servings, new ChainSeam());
            }
            // null: rack absent (plain tests, stripped platform) — the
            // manual network combo still works
        }
        if (chainAutoConnect != null) {
            chainAutoConnect.attach();
            // a chain that started while the tab was closed still connects;
            // the snapshot read belongs off the EDT
            org.nmox.studio.web3.engine.ChainAutoConnect poker = chainAutoConnect;
            RP.post(poker::refresh);
        }
        restartPulseIfOpen();
    }

    @Override
    public void componentClosed() {
        // a write may sit queued on the save lane (every edit saves the
        // moment it is made) — drain it before the studio is torn down
        // (bounded; see SaveLane.flush)
        SAVES.flush(5, java.util.concurrent.TimeUnit.SECONDS);
        stopWatch();
        stopPulse();
        if (chainAutoConnect != null) {
            chainAutoConnect.detach();
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

    /**
     * (Re)aims the pulse at the current project — from componentOpened
     * and from every workspace reload (the project may have switched).
     * No project or a closed tab means no pulse at all.
     */
    private void restartPulseIfOpen() {
        stopPulse();
        if (!isOpened()) {
            return;
        }
        File dir = projectDirOrNull();
        if (dir == null) {
            return;
        }
        pulse = new org.nmox.studio.web3.engine.ArtifactPulse(dir,
                new File(dir, Web3WorkspaceIO.FILENAME), new PulseSink());
        pulse.start(org.nmox.studio.web3.engine.ArtifactPulse.DEFAULT_INTERVAL_MS);
    }

    private void stopPulse() {
        if (pulse != null) {
            pulse.stop();
            pulse = null;
        }
    }

    /** What the auto-connector drives — the exact paths the combo uses. */
    private final class ChainSeam implements org.nmox.studio.web3.engine.ChainAutoConnect.Chain {

        @Override
        public String selectedUrl() {
            Network network = selectedNetwork();
            return network == null || network.secretUrl() ? null : network.plainUrl();
        }

        @Override
        public boolean connected() {
            return connected;
        }

        @Override
        public void connect() {
            networkSelected(); // the same connect/re-poll the combo re-select did
        }

        @Override
        public void disconnect() {
            // the existing not-connected state, no dialogs: chip greys,
            // write gates follow; a running Watch fails-and-greys on its own
            connected = false;
            accounts = List.of();
            chip(NOT_CONNECTED, Color.GRAY);
            refreshFromCombo();
            refreshSessionAccounts();
            updateWatchAddresses();
        }
    }

    /** Pulse-thread callbacks marshalled onto the EDT. */
    private final class PulseSink implements org.nmox.studio.web3.engine.ArtifactPulse.Sink {

        @Override
        public void artifactsChanged() {
            SwingUtilities.invokeLater(Web3StudioTopComponent.this::autoRescan);
        }

        @Override
        public void workspaceChanged(long mtime, long size) {
            if (!selfWrites.isForeign(mtime, size)) {
                return; // our own save — the tracker already knows this stamp
            }
            // the authoritative re-check rides the save lane: it queues
            // behind any write+stamp pair the tick may have raced, so our
            // own save mid-landing never counts as foreign (debt #16)
            SAVES.classify(() -> {
                if (!selfWrites.isForeign(mtime, size)) {
                    return; // our own save, caught mid-landing by the tick
                }
                SwingUtilities.invokeLater(() -> {
                    if (!selfWrites.isForeign(mtime, size)) {
                        return; // our save landed between verdict and dispatch
                    }
                    // networks and deployments persist the moment they change —
                    // there is no dirty in-memory state to clobber, so reload silently
                    reloadWorkspace();
                    balloon("Reloaded " + Web3WorkspaceIO.FILENAME,
                            "Picked up changes made outside the studio", true);
                });
            });
        }
    }

    // ---- small helpers ------------------------------------------------------------

    private static List<String> fieldTexts(List<JTextField> fields) {
        List<String> texts = new ArrayList<>(fields.size());
        for (JTextField field : fields) {
            texts.add(field.getText());
        }
        return texts;
    }

    private static void setResult(JLabel label, String text, Color color) {
        label.setForeground(color);
        label.setText(PlainText.plain(text));
        label.setToolTipText(PlainText.plain(text));
    }

    private static String messageOf(Exception e) {
        return e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName() : e.getMessage();
    }

    // ---- The token strip (v2.44.0) ---------------------------------------

    /**
     * A one-line token face for an attached artifact that implements a
     * token standard — or null for ordinary contracts. ERC-20 metadata
     * (name/symbol/decimals/totalSupply) is read via {@code eth_call}
     * off the EDT; the optional readers the ABI doesn't carry are shown
     * as honest absents, never invented. The async apply is guarded by
     * session identity (the v1.172.0 law: a result belongs to the
     * session that asked).
     */
    private JComponent buildTokenStrip(InteractSession s) {
        ErcStandards.Standard standard = ErcStandards.detect(s.artifact().abi());
        if (standard == null) {
            return null;
        }
        JLabel strip = new JLabel("\u2b21 " + standard.label()
                + (standard == ErcStandards.Standard.ERC20
                        ? " — reading token metadata\u2026" : " token"));
        strip.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        strip.setForeground(ACCENT);
        strip.getAccessibleContext().setAccessibleName(
                standard.label() + " token summary");
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(strip, BorderLayout.CENTER);
        if (standard == ErcStandards.Standard.ERC721) {
            JButton ownerButton = new JButton("Owner of\u2026");
            ownerButton.setToolTipText("eth_call ownerOf(tokenId) \u2014 read-only; "
                    + "shows tokenURI too when the ABI carries it");
            ownerButton.getAccessibleContext().setAccessibleName(
                    "Look up a token's owner");
            ownerButton.addActionListener(e -> lookupTokenOwner(s));
            JPanel east721 = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
            east721.add(ownerButton);
            panel.add(east721, BorderLayout.EAST);
            readNftMetadata(s, strip);
        }
        if (standard == ErcStandards.Standard.ERC20) {
            JButton balanceButton = new JButton("Balance of\u2026");
            balanceButton.setToolTipText(
                    "eth_call balanceOf(address) \u2014 read-only");
            balanceButton.getAccessibleContext().setAccessibleName(
                    "Look up a token balance");
            balanceButton.addActionListener(e -> lookupTokenBalance(s));
            JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
            east.add(balanceButton);
            panel.add(east, BorderLayout.EAST);
            readTokenMetadata(s, strip);
        }
        return panel;
    }

    private record TokenMetadata(String name, String symbol,
            Integer decimals, java.math.BigInteger totalSupply) {
    }

    private void readNftMetadata(InteractSession s, JLabel strip) {
        JsonRpcClient c = client;
        if (c == null || s.address() == null) {
            strip.setText("\u2b21 ERC-721 token \u2014 connect to a network to "
                    + "read its name and symbol");
            return;
        }
        RP.post(() -> {
            String name = callOptionalReader(c, s, "name");
            String symbol = callOptionalReader(c, s, "symbol");
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (session != s) {
                    return;
                }
                StringBuilder b = new StringBuilder("\u2b21 ERC-721");
                if (name != null) {
                    b.append("  \u201c").append(name).append('\u201d');
                }
                if (symbol != null) {
                    b.append(" (").append(symbol).append(')');
                }
                if (name == null && symbol == null) {
                    b.append("  \u00b7 name/symbol not in ABI");
                }
                strip.setText(PlainText.plain(b.toString()));
                strip.setToolTipText(PlainText.plain(b.toString()));
            });
        });
    }

    private void lookupTokenOwner(InteractSession s) {
        NotifyDescriptor.InputLine ask = new NotifyDescriptor.InputLine(
                "Token id:", "Owner of \u2014 read-only eth_call");
        if (DialogDisplayer.getDefault().notify(ask) != NotifyDescriptor.OK_OPTION) {
            return;
        }
        String tokenId = ask.getInputText().trim();
        if (!tokenId.matches("[0-9]+")) {
            status("Not a token id \u2014 expected a whole number", FAIL_RED);
            return;
        }
        JsonRpcClient c = client;
        if (c == null) {
            status("Connect to a network first", FAIL_RED);
            return;
        }
        RP.post(() -> {
            try {
                org.nmox.studio.web3.model.AbiEntry ownerOf = s.artifact().abi().stream()
                        .filter(e2 -> "ownerOf".equals(e2.name())
                                && e2.inputs().size() == 1)
                        .findFirst().orElseThrow();
                String owner = AbiCodec.decodeReturn(ownerOf,
                        c.ethCall(s.address(),
                                AbiCodec.encodeCall(ownerOf, List.of(tokenId)))).get(0);
                String line = "Owner of #" + tokenId + ": " + owner;
                org.nmox.studio.web3.model.AbiEntry tokenUri = s.artifact().abi().stream()
                        .filter(e2 -> "tokenURI".equals(e2.name())
                                && e2.inputs().size() == 1)
                        .findFirst().orElse(null);
                if (tokenUri != null) {
                    try {
                        line += "  \u00b7 tokenURI: " + AbiCodec.decodeReturn(tokenUri,
                                c.ethCall(s.address(), AbiCodec.encodeCall(
                                        tokenUri, List.of(tokenId)))).get(0);
                    } catch (Exception absent) {
                        // a burned/absent token's URI reverts — the owner line stands
                    }
                }
                String shown = line;
                javax.swing.SwingUtilities.invokeLater(() -> status(shown, ACCENT));
            } catch (Exception failure) {
                javax.swing.SwingUtilities.invokeLater(() -> status(
                        "Owner lookup failed: " + failure.getMessage(), FAIL_RED));
            }
        });
    }

    private void readTokenMetadata(InteractSession s, JLabel strip) {
        JsonRpcClient c = client;
        if (c == null || s.address() == null) {
            strip.setText("\u2b21 ERC-20 \u2014 connect to a network to read "
                    + "the token's name, symbol and supply");
            return;
        }
        RP.post(() -> {
            String name = callOptionalReader(c, s, "name");
            String symbol = callOptionalReader(c, s, "symbol");
            String decimalsText = callOptionalReader(c, s, "decimals");
            String supplyText = callOptionalReader(c, s, "totalSupply");
            Integer decimals = null;
            try {
                decimals = decimalsText == null ? null : Integer.valueOf(decimalsText);
            } catch (NumberFormatException ignore) {
            }
            java.math.BigInteger supply = null;
            try {
                supply = supplyText == null ? null : new java.math.BigInteger(supplyText);
            } catch (NumberFormatException ignore) {
            }
            TokenMetadata meta = new TokenMetadata(name, symbol, decimals, supply);
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (session != s) {
                    return; // re-aim/re-attach while reading (v1.172.0)
                }
                tokenDecimals = meta.decimals();
                String line = tokenStripText(meta);
                strip.setText(PlainText.plain(line));
                // a narrow pane ellipsizes the label — the full line
                // survives as the tooltip (the v1.282.0 truncation law)
                strip.setToolTipText(PlainText.plain(line));
            });
        });
    }

    /** Pure line assembly so the honest-absent rules are testable. */
    static String tokenStripText(Object metadataObject) {
        TokenMetadata m = (TokenMetadata) metadataObject;
        StringBuilder b = new StringBuilder("\u2b21 ERC-20");
        if (m.name() != null || m.symbol() != null) {
            b.append("  \u201c").append(m.name() == null ? "?" : m.name())
                    .append("\u201d");
            if (m.symbol() != null) {
                b.append(" (").append(m.symbol()).append(')');
            }
        } else {
            b.append("  \u00b7 name/symbol not in ABI");
        }
        if (m.decimals() != null) {
            b.append("  \u00b7 ").append(m.decimals()).append(" decimals");
        }
        if (m.totalSupply() != null) {
            if (m.decimals() != null) {
                b.append("  \u00b7 supply ").append(TokenAmounts.toHuman(
                        m.totalSupply(), m.decimals()));
                if (m.symbol() != null) {
                    b.append(' ').append(m.symbol());
                }
            } else {
                b.append("  \u00b7 supply ").append(m.totalSupply())
                        .append(" (raw \u2014 decimals not in ABI)");
            }
        }
        return b.toString();
    }

    /** eth_calls a zero-arg optional reader; null when absent or failing. */
    private String callOptionalReader(JsonRpcClient c, InteractSession s, String name) {
        org.nmox.studio.web3.model.AbiEntry reader =
                ErcStandards.metadataReader(s.artifact().abi(), name);
        if (reader == null) {
            return null;
        }
        try {
            String hex = c.ethCall(s.address(), AbiCodec.encodeCall(reader, List.of()));
            List<String> out = AbiCodec.decodeReturn(reader, hex);
            return out.isEmpty() ? null : out.get(0);
        } catch (Exception unreadable) {
            return null;
        }
    }

    private void lookupTokenBalance(InteractSession s) {
        NotifyDescriptor.InputLine ask = new NotifyDescriptor.InputLine(
                "Address:", "Token balance \u2014 read-only eth_call");
        if (DialogDisplayer.getDefault().notify(ask) != NotifyDescriptor.OK_OPTION) {
            return;
        }
        String address = ask.getInputText().trim();
        if (!address.matches("0x[0-9a-fA-F]{40}")) {
            status("Not an address \u2014 expected 0x + 40 hex characters", FAIL_RED);
            return;
        }
        JsonRpcClient c = client;
        if (c == null) {
            status("Connect to a network first", FAIL_RED);
            return;
        }
        RP.post(() -> {
            try {
                org.nmox.studio.web3.model.AbiEntry balanceOf =
                        s.artifact().abi().stream()
                                .filter(e2 -> "balanceOf".equals(e2.name())
                                        && e2.inputs().size() == 1)
                                .findFirst().orElseThrow();
                String hex = c.ethCall(s.address(),
                        AbiCodec.encodeCall(balanceOf, List.of(address)));
                String raw = AbiCodec.decodeReturn(balanceOf, hex).get(0);
                String decimalsText = callOptionalReader(c, s, "decimals");
                String line;
                if (decimalsText != null) {
                    line = "Balance of " + address + ": " + TokenAmounts.toHuman(
                            new java.math.BigInteger(raw),
                            Integer.parseInt(decimalsText));
                } else {
                    line = "Balance of " + address + ": " + raw + " (raw)";
                }
                javax.swing.SwingUtilities.invokeLater(() -> status(line, ACCENT));
            } catch (Exception failure) {
                javax.swing.SwingUtilities.invokeLater(() ->
                        status("Balance lookup failed: " + failure.getMessage(),
                                FAIL_RED));
            }
        });
    }

    // ---- The transaction inspector (v2.44.0) -----------------------------

    /** History… (v2.47.0): decoded past events over a BOUNDED range. */
    private void eventHistory() {
        JsonRpcClient c = client;
        if (c == null || !connected) {
            status(NOT_CONNECTED, FAIL_RED);
            return;
        }
        JTextField addressField = new JTextField(44);
        if (session != null && session.address() != null) {
            addressField.setText(session.address());
        }
        addressField.getAccessibleContext().setAccessibleName("Contract address");
        JTextField fromField = new JTextField(10);
        fromField.getAccessibleContext().setAccessibleName("From block");
        JTextField toField = new JTextField(10);
        toField.getAccessibleContext().setAccessibleName("To block, or latest");
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridy = 0; gc.anchor = GridBagConstraints.WEST;
        gc.insets = new java.awt.Insets(2, 2, 2, 6);
        form.add(new JLabel("Address:"), gc);
        gc.gridx = 1;
        form.add(addressField, gc);
        gc.gridx = 0; gc.gridy = 1;
        form.add(new JLabel("Blocks:"), gc);
        gc.gridx = 1;
        JPanel rangeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        rangeRow.add(fromField);
        rangeRow.add(new JLabel(" to "));
        rangeRow.add(toField);
        rangeRow.add(new JLabel(" (blank = last 1000, cap "
                + org.nmox.studio.web3.engine.EventHistory.SPAN_CAP + ")"));
        form.add(rangeRow, gc);
        NotifyDescriptor descriptor = new NotifyDescriptor(form, "Event history",
                NotifyDescriptor.OK_CANCEL_OPTION, NotifyDescriptor.PLAIN_MESSAGE,
                null, NotifyDescriptor.OK_OPTION);
        if (DialogDisplayer.getDefault().notify(descriptor) != NotifyDescriptor.OK_OPTION) {
            return;
        }
        String address = addressField.getText().trim();
        if (!address.matches("0x[0-9a-fA-F]{40}")) {
            status("Not an address \u2014 expected 0x + 40 hex characters", FAIL_RED);
            return;
        }
        String fromText = fromField.getText();
        String toText = toField.getText();
        List<ContractArtifact> known = allArtifacts();
        status("Fetching history\u2026", Color.GRAY);
        RP.post(() -> {
            try {
                long latest = c.blockNumber();
                org.nmox.studio.web3.engine.EventHistory.Range range =
                        org.nmox.studio.web3.engine.EventHistory.range(
                                fromText, toText, latest);
                List<JsonRpcClient.LogEntry> logs = c.getLogs(address,
                        String.valueOf(range.from()), String.valueOf(range.to()));
                List<org.nmox.studio.web3.engine.EventHistory.Row> rows =
                        org.nmox.studio.web3.engine.EventHistory.rows(logs, known);
                SwingUtilities.invokeLater(() -> showHistory(address, range, rows));
            } catch (IllegalArgumentException refusal) {
                SwingUtilities.invokeLater(() ->
                        status(refusal.getMessage(), FAIL_RED));
            } catch (Exception failure) {
                SwingUtilities.invokeLater(() ->
                        status("History failed: " + failure.getMessage(), FAIL_RED));
            }
        });
    }

    private void showHistory(String address,
            org.nmox.studio.web3.engine.EventHistory.Range range,
            List<org.nmox.studio.web3.engine.EventHistory.Row> rows) {
        StringBuilder text = new StringBuilder();
        for (org.nmox.studio.web3.engine.EventHistory.Row row : rows) {
            text.append(String.format("%-10d %-14s %s%n    %s%n",
                    row.block(), row.event(), row.txHash(), row.details()));
        }
        if (rows.isEmpty()) {
            text.append("No events for ").append(address)
                    .append(" in blocks ").append(range.from())
                    .append("\u2013").append(range.to());
        }
        javax.swing.JTextArea area = new javax.swing.JTextArea(
                text.toString(), 16, 80);
        area.setEditable(false);
        area.setFont(MONO);
        area.getAccessibleContext().setAccessibleName("Event history");
        javax.swing.JButton save = new javax.swing.JButton("Save CSV\u2026");
        save.getAccessibleContext().setAccessibleName("Save history as CSV");
        save.addActionListener(e -> {
            java.io.File file = new org.openide.filesystems.FileChooserBuilder(
                    Web3StudioTopComponent.class)
                    .setTitle("Save event history")
                    .showSaveDialog();
            if (file == null) {
                return;
            }
            java.io.File target = file.getName().contains(".") ? file
                    : new java.io.File(file.getParentFile(), file.getName() + ".csv");
            try {
                org.nmox.studio.core.util.AtomicFiles.writeString(target.toPath(),
                        org.nmox.studio.web3.engine.EventHistory.toCsv(rows));
                status("Saved " + rows.size() + " events to "
                        + target.getName(), ACCENT);
            } catch (Exception failure) {
                status("Save failed: " + failure.getMessage(), FAIL_RED);
            }
        });
        javax.swing.JDialog dialog = new javax.swing.JDialog(
                (java.awt.Frame) null,
                "Events \u2014 " + address + " \u00b7 blocks "
                + range.from() + "\u2013" + range.to() + " \u00b7 "
                + rows.size() + " found", false);
        dialog.setLayout(new BorderLayout(0, 4));
        dialog.add(new JScrollPane(area), BorderLayout.CENTER);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        south.add(save);
        dialog.add(south, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void inspectTransaction() {
        NotifyDescriptor.InputLine ask = new NotifyDescriptor.InputLine(
                "Transaction hash:", "Inspect transaction");
        if (DialogDisplayer.getDefault().notify(ask) != NotifyDescriptor.OK_OPTION) {
            return;
        }
        String hash = ask.getInputText().trim();
        if (!hash.matches("0x[0-9a-fA-F]{64}")) {
            status("Not a transaction hash \u2014 expected 0x + 64 hex characters",
                    FAIL_RED);
            return;
        }
        JsonRpcClient c = client;
        if (c == null) {
            status("Connect to a network first", FAIL_RED);
            return;
        }
        List<ContractArtifact> known = allArtifacts();
        RP.post(() -> {
            try {
                org.json.JSONObject tx = c.getTransactionRaw(hash);
                if (tx == null) {
                    javax.swing.SwingUtilities.invokeLater(() -> status(
                            "Transaction not found on this network", FAIL_RED));
                    return;
                }
                org.json.JSONObject receipt = c.getTransactionReceiptRaw(hash);
                TxInspection.Report report = TxInspection.assemble(tx, receipt, known);
                javax.swing.SwingUtilities.invokeLater(() -> showInspection(hash, report));
            } catch (Exception failure) {
                javax.swing.SwingUtilities.invokeLater(() ->
                        status("Inspect failed: " + failure.getMessage(), FAIL_RED));
            }
        });
    }

    private void showInspection(String hash, TxInspection.Report report) {
        javax.swing.JTextArea area = new javax.swing.JTextArea(
                String.join("\n", report.lines()), 14, 78);
        area.setEditable(false);
        area.setFont(MONO);
        area.getAccessibleContext().setAccessibleName("Transaction inspection");
        javax.swing.JDialog dialog = new javax.swing.JDialog(
                (java.awt.Frame) null, "Transaction " + hash, false);
        dialog.add(new JScrollPane(area));
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void status(String message, Color color) {
        statusLabel.setForeground(color);
        statusLabel.setText(PlainText.plain(message));
        org.openide.awt.StatusDisplayer.getDefault().setStatusText(org.nmox.studio.core.util.PlainStatus.text(message));
    }

    /** Async outcomes land as balloons too — the DB Studio feedback idiom. */
    private static void balloon(String title, String detail, boolean ok) {
        javax.swing.Icon icon = javax.swing.UIManager.getIcon(
                ok ? "OptionPane.informationIcon" : "OptionPane.errorIcon");
        org.openide.awt.NotificationDisplayer.getDefault().notify(
                title, icon, detail == null ? "" : detail, null,
                ok ? org.openide.awt.NotificationDisplayer.Priority.LOW
                        : org.openide.awt.NotificationDisplayer.Priority.NORMAL);
    }

    /** Appends to the log strip, keeping roughly the last 100k characters. */
    private void appendLog(String text) {
        logArea.append(text);
        int over = logArea.getDocument().getLength() - 100_000;
        if (over > 0) {
            logArea.replaceRange("", 0, over);
        }
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private static JComponent leftAligned(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        return component;
    }

    private static void addFormRow(JPanel form, int row, JComponent component) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(3, 0, 3, 0);
        form.add(component, c);
    }

    private static void addLabeledRow(JPanel form, int row, String label,
            JComponent field) {
        GridBagConstraints l = new GridBagConstraints();
        l.gridx = 0;
        l.gridy = row;
        l.anchor = GridBagConstraints.EAST;
        l.insets = new Insets(3, 0, 3, 8);
        form.add(new JLabel(PlainText.plain(label)), l);
        GridBagConstraints f = new GridBagConstraints();
        f.gridx = 1;
        f.gridy = row;
        f.anchor = GridBagConstraints.WEST;
        f.insets = new Insets(3, 0, 3, 0);
        form.add(field, f);
    }

    private static String esc(String s) {
        return s == null ? ""
                : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** The three fixed branches of the tree. */
    private enum Branch {
        NETWORKS("Networks"), CONTRACTS("Contracts"), DEPLOYMENTS("Deployments");

        final String label;

        Branch(String label) {
            this.label = label;
        }
    }

    // ---- renderers ----------------------------------------------------------------

    /** Networks bold-branch tree: contracts carry their size verdict color. */
    private final class Web3TreeRenderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree t, Object value, boolean sel,
                boolean expanded, boolean leaf, int row, boolean focus) {
            super.getTreeCellRendererComponent(t, value, sel, expanded, leaf, row, focus);
            Object userObject = value instanceof DefaultMutableTreeNode node
                    ? node.getUserObject() : null;
            if (userObject instanceof Branch branch) {
                setText("<html><b>" + branch.label + "</b></html>");
            } else if (userObject instanceof Network network) {
                boolean active = network.equals(selectedNetwork());
                setText("<html>" + (active ? "<b>" : "") + esc(network.name())
                        + (active ? "</b>" : "")
                        + " <font color='#8a8a8a'>(chain " + network.chainId()
                        + (network.secretUrl() ? " · keyring" : "") + ")</font></html>");
            } else if (userObject instanceof ContractArtifact artifact) {
                ContractSizeCheck.Verdict verdict = ContractSizeCheck.check(artifact);
                String dot = verdict.over() ? "#E24B4A"
                        : verdict.pct() >= 80.0 ? "#C9932B" : "#4EC98B";
                setText(String.format(Locale.ROOT,
                        "<html><font color='%s'>●</font> %s <font color='#8a8a8a'>(%.1f%%)</font></html>",
                        dot, esc(artifact.name()), verdict.pct()));
            } else if (userObject instanceof AbiEntry entry) {
                switch (entry.kind()) {
                    case FUNCTION -> setText((entry.readOnly() ? "ƒ " : "✎ ")
                            + entry.signature());
                    case EVENT -> setText("event " + entry.signature());
                    case CONSTRUCTOR -> setText("constructor" + entry.signature());
                    default -> setText("error " + entry.signature());
                }
            } else if (userObject instanceof DeploymentRecord record) {
                setText("<html>" + esc(record.contractName()) + " @ "
                        + esc(DisplayValues.shortAddress(record.address()))
                        + " <font color='#8a8a8a'>(" + esc(record.networkName())
                        + ")</font></html>");
            } else if (userObject instanceof String placeholder) {
                setText("<html><i><font color='#8a8a8a'>" + esc(placeholder)
                        + "</font></i></html>");
            }
            return this;
        }
    }

    /** Network combo entries: name plus a gray chain badge. */
    private static final class NetworkRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Network network) {
                setText("<html>" + esc(network.name())
                        + " <font color='#8a8a8a'>(chain " + network.chainId()
                        + ")</font></html>");
            }
            return this;
        }
    }

    /** Watch filter entries: "All deployed contracts" or one deployment. */
    private static final class WatchFilterRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof DeploymentRecord record) {
                setText(record.contractName() + " @ "
                        + DisplayValues.shortAddress(record.address()));
            }
            return this;
        }
    }

    // ---- table models (dumb holders; the wording lives in tested pure classes) ----

    /** The Watch table: a snapshot of the feed, refreshed on the EDT. */
    private final class WatchModel extends AbstractTableModel {

        private List<WatchRows.Cells> cells = List.of();

        void refresh() {
            List<WatchRows.Cells> fresh = new ArrayList<>();
            for (WatchFeed.Row row : feed.rows()) {
                fresh.add(WatchRows.cells(row));
            }
            cells = fresh;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return cells.size();
        }

        @Override
        public int getColumnCount() {
            return WatchRows.columns().size();
        }

        @Override
        public String getColumnName(int column) {
            return WatchRows.columns().get(column);
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            WatchRows.Cells row = cells.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.block();
                case 1 -> row.what();
                default -> row.details();
            };
        }
    }

    /** The Oversight size table over {@link ContractSizeCheck} verdicts. */
    private final class SizeModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Contract", "Bytes", "Of limit", "Verdict"};

        private List<ContractSizeCheck.Verdict> verdicts = List.of();

        void refresh() {
            List<ContractSizeCheck.Verdict> fresh = new ArrayList<>();
            for (ContractArtifact artifact : artifacts) {
                fresh.add(ContractSizeCheck.check(artifact));
            }
            verdicts = fresh;
            fireTableDataChanged();
        }

        ContractSizeCheck.Verdict verdictAt(int row) {
            return verdicts.get(row);
        }

        @Override
        public int getRowCount() {
            return verdicts.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ContractSizeCheck.Verdict verdict = verdicts.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> verdict.contractName();
                case 1 -> String.format(Locale.ROOT, "%,d", verdict.sizeBytes());
                case 2 -> verdict; // the headroom bar renders this
                default -> verdict.message();
            };
        }
    }

    /** The gas-report table over {@link GasReportParser} rows. */
    private static final class GasModel extends AbstractTableModel {

        private static final String[] COLUMNS =
                {"Contract", "Function", "Min", "Avg", "Median", "Max", "Calls"};

        private List<GasReportParser.FunctionGas> rows = List.of();

        void set(List<GasReportParser.FunctionGas> fresh) {
            rows = List.copyOf(fresh);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            GasReportParser.FunctionGas row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.contract();
                case 1 -> row.function();
                case 2 -> String.format(Locale.ROOT, "%,d", row.min());
                case 3 -> String.format(Locale.ROOT, "%,d", row.avg());
                case 4 -> String.format(Locale.ROOT, "%,d", row.median());
                case 5 -> String.format(Locale.ROOT, "%,d", row.max());
                default -> String.valueOf(row.calls());
            };
        }
    }

    /** The address book table; ages via {@link DisplayValues#age}. */
    private final class DeploymentsModel extends AbstractTableModel {

        private static final String[] COLUMNS =
                {"Contract", "Address", "Network", "Block", "Age"};

        void refresh() {
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return deployments.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            DeploymentRecord record = deployments.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> record.contractName();
                case 1 -> record.address();
                case 2 -> record.networkName();
                case 3 -> String.valueOf(record.blockNumber());
                default -> DisplayValues.age(record.timestampMillis(),
                        System.currentTimeMillis());
            };
        }
    }

    /** Size rows over the EIP-170 limit read red across all their text cells. */
    private final class SizeCellRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus,
                    row, column);
            int modelRow = table.convertRowIndexToModel(row);
            boolean over = modelRow < sizeModel.getRowCount()
                    && sizeModel.verdictAt(modelRow).over();
            if (!isSelected) {
                setForeground(over ? FAIL_RED : table.getForeground());
            }
            return this;
        }
    }

    /** The headroom bar: percent of the EIP-170 limit, red when over. */
    private static final class HeadroomBarRenderer implements TableCellRenderer {

        private final JProgressBar bar = new JProgressBar(0, 100);

        HeadroomBarRenderer() {
            bar.setStringPainted(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            if (value instanceof ContractSizeCheck.Verdict verdict) {
                bar.setValue((int) Math.min(100, Math.round(verdict.pct())));
                bar.setString(String.format(Locale.ROOT, "%.1f%%", verdict.pct()));
                bar.setForeground(verdict.over() ? FAIL_RED
                        : verdict.pct() >= 80.0 ? AMBER : OK_GREEN);
            }
            return bar;
        }
    }
}
