package org.nmox.studio.web3.ui;

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
    @ActionReference(path = "Shortcuts", name = "DS-6")
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
    private final JLabel chipLabel = new JLabel(NOT_CONNECTED);
    private final JButton compileButton = new JButton("Compile");
    private final JButton rescanButton = new JButton("Rescan");
    private final JLabel statusLabel = new JLabel(" ");

    private final JTree tree = new JTree();
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

    private final SizeModel sizeModel = new SizeModel();
    private final GasModel gasModel = new GasModel();
    private final DeploymentsModel deploymentsModel = new DeploymentsModel();
    private final JButton gasButton = new JButton("Run gas report");

    private final JTextArea logArea = new JTextArea(5, 40);

    private final org.nmox.studio.rack.model.Rack.Listener rackListener;
    private boolean rackListenerAttached;
    private boolean saveFailureNotified;

    /** Auto-connects the chip when the rack serves a matching chain; null when rack absent. */
    private org.nmox.studio.web3.engine.ChainAutoConnect chainAutoConnect;
    /** Polls artifacts + workspace file while the tab is open; null when closed/no project. */
    private org.nmox.studio.web3.engine.ArtifactPulse pulse;
    /** Distinguishes our own .nmoxweb3.json writes from foreign edits. */
    private final org.nmox.studio.web3.engine.SelfWriteTracker selfWrites =
            new org.nmox.studio.web3.engine.SelfWriteTracker();

    public Web3StudioTopComponent() {
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

        rackListener = new org.nmox.studio.rack.model.Rack.Listener() {
            @Override
            public void projectChanged() {
                SwingUtilities.invokeLater(Web3StudioTopComponent.this::reloadWorkspace);
            }
        };
        attachRackListener();
        reloadWorkspace();
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
        } else if (artifacts.isEmpty()) {
            contractsNode.add(new DefaultMutableTreeNode(
                    "No artifacts found — Compile (forge build) or Rescan"));
        } else {
            for (ContractArtifact artifact : artifacts) {
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
            openInteractFor(InteractSession.deploying(artifact, hasAccounts()));
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
        JLabel hint = new JLabel(text);
        hint.setForeground(Color.GRAY);
        hint.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        interactPanel.add(hint, BorderLayout.NORTH);
        interactPanel.revalidate();
        interactPanel.repaint();
    }

    private void openInteractFor(InteractSession newSession) {
        session = newSession;
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
            JLabel source = new JLabel(s.artifact().sourcePath());
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
            argFields.add(field);
            addLabeledRow(form, row++, paramLabel(param), field);
        }
        if (s.constructorHint() != null) {
            JLabel hint = new JLabel(s.constructorHint());
            hint.setForeground(Color.GRAY);
            addFormRow(form, row++, hint);
        }

        JTextField valueField = null;
        if (s.constructorPayable()) {
            valueField = new JTextField(10);
            valueField.setFont(MONO);
            valueField.setToolTipText("ETH to send with the deployment, like 0.5");
            addLabeledRow(form, row++, "Value (ETH):", valueField);
        }

        addLabeledRow(form, row++, "From:", fromCombo);

        JButton deployButton = new JButton("Deploy");
        deployButton.setForeground(ACCENT);
        deployButton.setEnabled(reason == null && connected);
        deployButton.setToolTipText(reason != null ? reason
                : connected ? "eth_sendTransaction with the node's unlocked account"
                        : NOT_CONNECTED);
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

        JLabel title = new JLabel(s.artifact().name() + " @ "
                + DisplayValues.shortAddress(s.address()));
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
        JLabel name = new JLabel((read ? "ƒ " : "✎ ") + function.name());
        name.setFont(MONO.deriveFont(Font.BOLD));
        name.setToolTipText(function.signature() + " · " + function.stateMutability());
        rowPanel.add(name);

        List<JTextField> argFields = new ArrayList<>();
        for (AbiParam param : function.inputs()) {
            JLabel label = new JLabel(paramLabel(param));
            label.setForeground(Color.GRAY);
            rowPanel.add(label);
            JTextField field = new JTextField(10);
            field.setFont(MONO);
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
            rowPanel.add(valueField);
        }

        JButton action = new JButton(read ? "CALL" : "SEND");
        JLabel result = new JLabel(" ");
        if (read) {
            action.setToolTipText("eth_call — free, read-only");
            action.addActionListener(e -> call(s, function, argFields, result));
        } else {
            action.setEnabled(sendReason == null && connected);
            action.setToolTipText(sendReason != null ? sendReason
                    : connected ? "eth_sendTransaction with the node's unlocked account"
                            : NOT_CONNECTED);
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
            data = s.callData(function, fieldTexts(argFields));
            valueHex = InteractSession.valueWeiHex(
                    valueField == null ? null : valueField.getText());
        } catch (IllegalArgumentException refusal) {
            setResult(result, refusal.getMessage(), FAIL_RED);
            status(refusal.getMessage(), FAIL_RED);
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
        for (ContractArtifact artifact : artifacts) {
            if (artifact.name().equals(name)) {
                return artifact;
            }
        }
        return null;
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
        panel.add(bar, BorderLayout.NORTH);

        JTable table = new JTable(watchModel);
        table.setFont(MONO);
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
            Thread t = new Thread(r, "Contract Studio watch");
            t.setDaemon(true);
            return t;
        });
        watchExec.scheduleWithFixedDelay(this::watchTick, 0, 2, TimeUnit.SECONDS);
        watchButton.setText("STOP");
        Network network = selectedNetwork();
        status("Watching " + (network == null ? "the chain" : network.name())
                + " — polling every 2 s", Color.GRAY);
    }

    private void stopWatch() {
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
        try {
            long current = c.blockNumber();
            if (lastWatchedBlock < 0) {
                lastWatchedBlock = current - 1;
                logsFromBlock = current; // the watch-start block
            }
            long from = Math.max(lastWatchedBlock + 1, current - CATCHUP_CAP + 1);
            for (long n = from; n <= current; n++) {
                JsonRpcClient.Block block = c.getBlockByNumber(String.valueOf(n), false);
                if (block != null) {
                    feed.addBlock(block.number(), block.txCount(), block.gasUsed(),
                            block.gasLimit(), block.hash());
                }
            }
            List<String> addresses = watchAddresses;
            EventMatcher matcher = eventMatcher;
            if (current >= logsFromBlock && !addresses.isEmpty()) {
                for (String address : addresses) {
                    feedLogs(c, matcher, address, logsFromBlock, current);
                }
                logsFromBlock = current + 1;
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
        sizeTable.setFont(MONO);
        sizeTable.setDefaultRenderer(Object.class, new SizeCellRenderer());
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
        JTable gasTable = new JTable(gasModel);
        gasTable.setFont(MONO);
        gasPanel.add(new JScrollPane(gasTable), BorderLayout.CENTER);
        gasPanel.setBorder(BorderFactory.createTitledBorder("Gas report"));
        panel.add(gasPanel);

        JTable deployTable = new JTable(deploymentsModel);
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
        deployTable.setComponentPopupMenu(popup);
        deployTable.setToolTipText("Double-click to open in Interact; right-click to copy the address");
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
        status("Scanning artifacts…", Color.GRAY);
        RP.post(() -> {
            List<ContractArtifact> found = ArtifactScanner.scan(dir.toPath());
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
        RP.post(() -> {
            List<ContractArtifact> found = ArtifactScanner.scan(dir.toPath());
            EventMatcher matcher = EventMatcher.build(found);
            SwingUtilities.invokeLater(() -> {
                if (found.equals(artifacts)) {
                    return; // nothing actually changed — fire no UI updates
                }
                applyArtifacts(found, matcher);
            });
        });
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
    }

    private void refreshFromCombo() {
        fromCombo.removeAllItems();
        for (String account : accounts) {
            fromCombo.addItem(account);
        }
        fromCombo.setEnabled(!accounts.isEmpty());
        fromCombo.setToolTipText(accounts.isEmpty()
                ? InteractSession.READ_ONLY_REASON
                : "The node's unlocked accounts (eth_accounts) — it signs, the IDE never can");
    }

    /** Re-arms or disables the write surface after an accounts refresh. */
    private void refreshSessionAccounts() {
        if (session != null) {
            session = session.withAccounts(hasAccounts());
            rebuildInteract();
        }
    }

    private void chip(String text, Color color) {
        chipLabel.setForeground(color);
        chipLabel.setText(text);
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

    // ---- persistence (.nmoxweb3.json, the RackService idiom) ---------------------

    private File projectDirOrNull() {
        try {
            File dir = org.nmox.studio.rack.service.RackService.getDefault()
                    .getRack().getProjectDir();
            if (dir != null && dir.isDirectory()) {
                return dir;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // rack unavailable (tests, stripped platform)
        }
        return null;
    }

    private File workspaceDir() {
        File dir = projectDirOrNull();
        return dir != null ? dir : new File(System.getProperty("user.home"));
    }

    private void reloadWorkspace() {
        Network previous = selectedNetwork();
        stopWatch();
        session = null;
        networks.clear();
        deployments.clear();
        Web3WorkspaceIO.Workspace workspace = Web3WorkspaceIO.load(workspaceDir());
        networks.addAll(workspace.networks());
        deployments.addAll(workspace.deployments());
        selfWrites.noteSync(new File(workspaceDir(), Web3WorkspaceIO.FILENAME));
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

    /** Writes networks AND deployments together — adding one never clobbers the other. */
    private void saveWorkspace() {
        try {
            Web3WorkspaceIO.save(workspaceDir(),
                    new Web3WorkspaceIO.Workspace(networks, deployments));
            selfWrites.noteSync(new File(workspaceDir(), Web3WorkspaceIO.FILENAME));
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
        try {
            if (chainAutoConnect == null) {
                chainAutoConnect = new org.nmox.studio.web3.engine.ChainAutoConnect(
                        org.nmox.studio.rack.service.ServingRegistry.getDefault(),
                        new ChainSeam());
            }
            chainAutoConnect.attach();
            // a chain that started while the tab was closed still connects;
            // the snapshot read belongs off the EDT
            org.nmox.studio.web3.engine.ChainAutoConnect poker = chainAutoConnect;
            RP.post(poker::refresh);
        } catch (RuntimeException | LinkageError ignored) {
            // rack unavailable (tests, stripped platform): manual combo still works
        }
        restartPulseIfOpen();
    }

    @Override
    public void componentClosed() {
        stopWatch();
        stopPulse();
        if (chainAutoConnect != null) {
            try {
                chainAutoConnect.detach();
            } catch (RuntimeException | LinkageError ignored) {
                // already unavailable — nothing to detach from
            }
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
            SwingUtilities.invokeLater(() -> {
                if (!selfWrites.isForeign(mtime, size)) {
                    return; // our save raced the tick; the EDT re-check settles it
                }
                // networks and deployments persist the moment they change —
                // there is no dirty in-memory state to clobber, so reload silently
                reloadWorkspace();
                balloon("Reloaded " + Web3WorkspaceIO.FILENAME,
                        "Picked up changes made outside the studio", true);
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
        label.setText(text);
        label.setToolTipText(text);
    }

    private static String messageOf(Exception e) {
        return e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName() : e.getMessage();
    }

    private void status(String message, Color color) {
        statusLabel.setForeground(color);
        statusLabel.setText(message);
        org.openide.awt.StatusDisplayer.getDefault().setStatusText(message);
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
        form.add(new JLabel(label), l);
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
