package org.nmox.studio.ui.irc;

import org.nmox.studio.core.util.PlainText;
import org.nmox.studio.core.util.PlainTables;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.net.URI;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.nmox.studio.core.spi.EmbeddedBrowser;
import org.nmox.studio.ui.irc.engine.IrcClient;
import org.nmox.studio.ui.irc.engine.IrcConfig;
import org.nmox.studio.ui.irc.engine.IrcLogger;
import org.nmox.studio.ui.irc.protocol.Ctcp;
import org.nmox.studio.ui.irc.protocol.IrcMessage;
import org.nmox.studio.ui.irc.protocol.MircFormat;
import org.nmox.studio.ui.irc.protocol.NickPrefix;
import org.nmox.studio.ui.irc.protocol.Numerics;
import org.nmox.studio.ui.irc.protocol.ServerTime;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.NotificationDisplayer;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

/**
 * The IRC client window (⌥⌘3, v1.204.0; grown advanced in v1.205.0):
 * networks and channels in a tree on the left, a styled transcript in
 * the middle (timestamps — server-time-honest when the network sends
 * {@code @time} tags — colored nicks, mIRC formatting, {@code /me}
 * actions, clickable URLs into the in-app Browser), the channel's nick
 * list on the right (away nicks dimmed), topic on top, input line with
 * Tab nick completion and Up/Down history + Connect button on the
 * bottom. Mentions highlight, badge the tree, and post a platform
 * notification when the window is hidden; ⌘F opens a find bar; per-
 * channel daily logs land under {@code ~/.nmox/irc-logs}. A real chat
 * client, docked like every other studio tab.
 *
 * <p><b>House laws it lives by:</b>
 * <ul>
 * <li><b>Zero boot cost</b> — the constructor sets a name; everything
 *     else builds in {@code componentOpened}. <b>No auto-connect,
 *     ever</b>: an outward network flow needs a user gesture, so the
 *     first connection is always the Connect button (or
 *     {@code /connect}).</li>
 * <li><b>EDT contract</b> — the engine ({@link IrcClient}) fires its
 *     callbacks on its own RequestProcessor thread; the {@link Bridge}
 *     adapter marshals every one through
 *     {@code SwingUtilities.invokeLater} before any component is
 *     touched. Log writes ride {@link IrcLogger}'s own lane.</li>
 * <li><b>Connections outlive the window</b> — clients live in a static
 *     registry; closing the tab detaches this window's listeners
 *     (symmetry law) but leaves the chat connected, and a reopen
 *     re-attaches without double-delivery (one bridge per network per
 *     window, plus the engine's equality-guarded add).</li>
 * <li><b>Secrets</b> — NickServ/SASL passwords ride the OS keychain via
 *     {@code IrcSecrets}; nothing here echoes one, and services queries
 *     are never logged.</li>
 * </ul>
 */
@TopComponent.Description(preferredID = "IrcTopComponent",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS)
@TopComponent.Registration(mode = "editor", openAtStartup = true, position = 356)
@ActionID(category = "Window", id = "org.nmox.studio.ui.irc.IrcTopComponent")
@org.openide.awt.ActionReferences({
    @ActionReference(path = "Menu/Window", position = 268),
    @ActionReference(path = "Shortcuts", name = "DA-3")
})
@TopComponent.OpenActionRegistration(displayName = "#CTL_IrcAction",
        preferredID = "IrcTopComponent")
@Messages({
    "CTL_IrcAction=IRC",
    "CTL_IrcTopComponent=IRC",
    "HINT_IrcTopComponent=IRC chat client"
})
public final class IrcTopComponent extends TopComponent {

    /**
     * The live connections, keyed by network name. Static on purpose:
     * a chat client outlives its window — closing the tab must not
     * drop the user off the network. Only {@code quitAndClose} (the
     * Disconnect button or {@code /quit}) ends a session.
     */
    private static final ConcurrentMap<String, IrcClient> SESSIONS = new ConcurrentHashMap<>();

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("HH:mm");

    /** The mIRC 16-color palette, tuned to read on the dark theme. */
    private static final Color[] MIRC_COLORS = {
        new Color(0xEEEEEE), new Color(0x555555), new Color(0x5C6BC0), new Color(0x66BB6A),
        new Color(0xEF5350), new Color(0xA1887F), new Color(0xAB47BC), new Color(0xFFA726),
        new Color(0xFFEE58), new Color(0x9CCC65), new Color(0x26A69A), new Color(0x4DD0E1),
        new Color(0x42A5F5), new Color(0xF06292), new Color(0x9E9E9E), new Color(0xCFD8DC)
    };

    /** Nick colors: a phosphor-friendly subset, picked by nick hash. */
    private static final Color[] NICK_COLORS = {
        new Color(0x66BB6A), new Color(0x4DD0E1), new Color(0xFFA726), new Color(0xAB47BC),
        new Color(0xF06292), new Color(0x9CCC65), new Color(0x42A5F5), new Color(0x26A69A)
    };

    private static final Color STATUS_COLOR = new Color(0x8A9BA8);
    private static final Color STAMP_COLOR = new Color(0x607D8B);
    /** Background wash behind a line that mentioned you. */
    private static final Color HIGHLIGHT_BG = new Color(0x4A3320);
    /** Link blue for detected URLs. */
    private static final Color LINK_COLOR = new Color(0x64B5F6);
    /** Tree badge red for unread mentions. */
    private static final Color MENTION_COLOR = new Color(0xEF5350);
    /** Find-bar highlight-all wash. */
    private static final Color FIND_MATCH = new Color(0x5A5220);

    /** Style attribute carrying a clickable URL on a transcript run. */
    private static final String ATTR_URL = "nmox.irc.url";

    /** A tree row: a network ({@code target == ""}) or one of its targets. */
    private record TargetRef(String network, String target) {
    }

    private boolean built;
    private final Map<String, Bridge> bridges = new HashMap<>();

    // ---- EDT-confined view state ----
    private final Map<String, StyledDocument> docs = new HashMap<>();
    private final Map<String, String> topics = new HashMap<>();
    private final Map<String, Map<String, String>> nickLists = new HashMap<>();
    private final Map<String, Set<String>> pendingNames = new HashMap<>();
    private final Set<String> unread = new HashSet<>();
    /** Targets already ballooned about, until viewed (one balloon per target). */
    private final Set<String> notifiedMentions = new HashSet<>();
    /** key → unread MENTION count (distinct from the plain-unread bold). */
    private final Map<String, Integer> mentions = new HashMap<>();

    /** WeeChat's smart filter: who spoke where, recently (pure core). */
    private final SmartFilter smartFilter = new SmartFilter();
    private boolean smartFilterOn = IrcConfig.getDefault().smartFilterEnabled();
    /** Custom /filter regexes (v2.10.0, pure core) — loaded from the
     *  config at construction; a saved form that no longer compiles is
     *  skipped there, never a broken client. Applied at the transcript
     *  append decision only: the engine-side log tap keeps everything. */
    private final TextFilters textFilters = new TextFilters();
    {
        IrcConfig.getDefault().textFilters().forEach(textFilters::addFromStringForm);
    }

    /** The dispatch switch's own labels; an alias may not shadow one. */
    private static final Set<String> BUILTIN_COMMANDS = Set.of(
            "connect", "join", "j", "part", "msg", "query", "me", "nick", "topic",
            "whois", "list", "ignore", "unignore", "away", "back", "log", "notice",
            "ctcp", "quit", "raw", "help", "filter", "alias", "kick", "mode",
            "invite", "op", "deop", "voice", "devoice", "ban", "unban", "clear",
            "close", "cycle", "lastlog");
    /** network → lowercased nicks currently marked away (away-notify + 301). */
    private final Map<String, Set<String>> awayNicks = new HashMap<>();
    /** per-target input recall (never persisted). */
    private final Map<String, InputHistory> histories = new HashMap<>();
    /** network → the collector armed by an in-flight {@code /list}. */
    private final Map<String, ChannelListCollector> listCollectors = new HashMap<>();
    /** network → WHOIS assembler (card rendered on 318). */
    private final Map<String, WhoisCollector> whoisCollectors = new HashMap<>();

    private final NickCompleter completer = new NickCompleter();
    private final IrcLogger logger = IrcLogger.getDefault();

    private DefaultMutableTreeNode rootNode;
    private DefaultTreeModel treeModel;
    private JTree tree;
    private final Map<String, DefaultMutableTreeNode> networkNodes = new HashMap<>();
    private final Map<String, DefaultMutableTreeNode> targetNodes = new HashMap<>();

    private JTextPane transcript;
    private JList<String> nickList;
    private final DefaultListModel<String> nickModel = new DefaultListModel<>();
    private JLabel topicLabel;
    private JTextField input;
    private JButton connectButton;

    // ---- find bar (⌘F) ----
    private JPanel findBar;
    private JTextField findField;
    private JLabel findCount;
    private List<Integer> findMatches = List.of();
    private int lastFindPos = -1;

    private String activeKey;

    public IrcTopComponent() {
        // zero boot cost: name and tooltip only — see componentOpened
        setName(Bundle.CTL_IrcTopComponent());
        setToolTipText(Bundle.HINT_IrcTopComponent());
    }

    // ------------------------------------------------------------------ UI

    @Override
    protected void componentOpened() {
        // Deliberately empty since v1.211.0: this tab now opens by DEFAULT so a
        // newcomer discovers that the IDE has a chat client, which means
        // componentOpened fires during startup. Building the whole UI there
        // would put Swing construction on the EDT before the window paints —
        // the zero-work-at-boot law (v1.38.0). It all moved to
        // componentShowing. There is still NO auto-connect: opening the tab
        // costs nothing and talks to nobody until you press Connect.
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
        aimFollower.showing();
        if (!built) {
            built = true;
            buildUi();
            IrcConfig config = IrcConfig.getDefault();
            config.ensureDefaults();
            logger.setEnabled(config.isLoggingEnabled());
            for (IrcConfig.Network n : config.networks()) {
                ensureNetworkNode(n.name());
            }
            selectNetwork(config.lastSelected());
        }
        // re-attach to whatever is still connected (reopen case): one
        // bridge per network per window, so no double-delivery
        for (Map.Entry<String, IrcClient> e : SESSIONS.entrySet()) {
            // a live session with NO bridge means the window was CLOSED
            // and is reopening (bridges survive tab switches — only
            // componentClosed clears them; the connect path attaches its
            // own bridge immediately). Messages kept arriving engine-side
            // (IrcLogTap, v1.322.0) but this view missed them — say so
            // instead of showing a silently gap-less scrollback (v1.344.0,
            // found by the live Libera walk of shipped 1.343.0)
            boolean reattach = !bridges.containsKey(e.getKey());
            attachBridge(e.getKey(), e.getValue());
            ensureNetworkNode(e.getKey());
            for (String chan : e.getValue().joinedChannels()) {
                ensureTargetNode(e.getKey(), chan);
            }
            if (reattach) {
                appendGapMarkers(e.getKey(), e.getValue().joinedChannels());
            }
        }
        refreshConnectButton();
    }

    @Override
    protected void componentClosed() {
        aimFollower.closed();
        // listener symmetry: this window's ears detach; the connections
        // stay up — a chat client outlives its window
        for (Map.Entry<String, Bridge> e : bridges.entrySet()) {
            IrcClient client = SESSIONS.get(e.getKey());
            if (client != null) {
                client.removeListener(e.getValue());
            }
        }
        bridges.clear();
    }

    private void buildUi() {
        setLayout(new BorderLayout());

        rootNode = new DefaultMutableTreeNode("irc");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        // WeeChat's hotlist jump: Ctrl+J hops to the next mention, else
        // the next unread buffer, sweeping the tree top to bottom
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                javax.swing.KeyStroke.getKeyStroke("control J"), "irc-jump-activity");
        getActionMap().put("irc-jump-activity", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                jumpToActivity();
            }
        });
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new UnreadBoldRenderer());
        tree.addTreeSelectionListener(e -> onTreeSelection());
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeTreePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeTreePopup(e);
            }
        });

        transcript = new JTextPane();
        transcript.setEditable(false);
        transcript.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        transcript.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String url = urlAt(e);
                if (url != null) {
                    openUrl(url);
                }
            }
        });
        transcript.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                transcript.setCursor(urlAt(e) != null
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
            }
        });

        nickList = new JList<>(nickModel);
        nickList.setPrototypeCellValue("@a-rather-long-nickname");
        nickList.setCellRenderer(new AwayAwareNickRenderer());

        topicLabel = new JLabel(" ");
        // a channel topic is arbitrary server/op text (stripToText removes
        // mIRC colors, NOT <html>) — keep it literal so a topic can't make
        // the IDE fetch a URL at paint time (the v1.208.0 class, v1.307.0)
        org.nmox.studio.core.util.PlainTables.plain(topicLabel);
        topicLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        input = new JTextField();
        input.addActionListener(e -> onInput());
        installInputKeys();
        connectButton = new JButton("Connect");
        connectButton.addActionListener(e -> onConnectButton());

        JPanel bottom = new JPanel(new BorderLayout(4, 0));
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(connectButton, BorderLayout.EAST);

        buildFindBar();
        JPanel north = new JPanel(new BorderLayout());
        north.add(topicLabel, BorderLayout.NORTH);
        north.add(findBar, BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout());
        center.add(north, BorderLayout.NORTH);
        center.add(new JScrollPane(transcript), BorderLayout.CENTER);

        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setMinimumSize(new Dimension(140, 0));
        JScrollPane nickScroll = new JScrollPane(nickList);
        nickScroll.setMinimumSize(new Dimension(120, 0));

        JSplitPane right = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, center, nickScroll);
        right.setResizeWeight(1.0);
        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, right);
        main.setResizeWeight(0.0);
        main.setDividerLocation(180);

        add(main, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        // ⌘F anywhere in the tab toggles the find bar
        KeyStroke find = KeyStroke.getKeyStroke(KeyEvent.VK_F,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(find, "irc-find");
        getActionMap().put("irc-find", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                toggleFindBar();
            }
        });
    }

    /**
     * Bolds tree rows with unread activity; rows with unread MENTIONS
     * additionally go red with a count — "someone said my name" must
     * read differently from "someone said something".
     */
    private final class UnreadBoldRenderer extends DefaultTreeCellRenderer {

        UnreadBoldRenderer() {
            // channel/network labels can carry server-chosen text; disable
            // HTML at CONSTRUCTION (before any setText) so a name like
            // <html><img src=http://evil> can't make the IDE fetch the URL
            // at paint time — the v1.208.0 class. Setting it after super's
            // setText is too late: BasicHTML installs the view on text change.
            org.nmox.studio.core.util.PlainTables.plain(this);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree t, Object value,
                boolean sel, boolean expanded, boolean leaf, int row, boolean focus) {
            Component c = super.getTreeCellRendererComponent(t, value, sel, expanded, leaf, row, focus);
            Object user = value instanceof DefaultMutableTreeNode n ? n.getUserObject() : null;
            boolean bold = false;
            if (user instanceof TargetRef ref) {
                String k = key(ref.network(), ref.target());
                String label = ref.target().isEmpty() ? ref.network() : ref.target();
                int mentionCount = mentions.getOrDefault(k, 0);
                if (mentionCount > 0) {
                    setText(label + " (" + mentionCount + ")");
                    if (!sel) {
                        setForeground(MENTION_COLOR);
                    }
                    bold = true;
                } else {
                    setText(label);
                    bold = unread.contains(k);
                }
            }
            c.setFont(c.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN));
            return c;
        }
    }

    /** Away nicks render dim italic (away-notify keeps the set fresh). */
    private final class AwayAwareNickRenderer extends DefaultListCellRenderer {

        AwayAwareNickRenderer() {
            // nicks come straight from the server; disable HTML at
            // CONSTRUCTION (before any setText) so a nick like
            // <html><img src=http://evil> can't make the IDE fetch the URL
            // at paint time (the v1.208.0 class). After super's setText is
            // too late: BasicHTML installs the view on the text change.
            org.nmox.studio.core.util.PlainTables.plain(this);
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            Component c = super.getListCellRendererComponent(list, value, index,
                    isSelected, cellHasFocus);
            String display = String.valueOf(value);
            Set<String> away = awayNicks.get(activeNetwork());
            boolean isAway = away != null
                    && away.contains(NickPrefix.strip(display).toLowerCase(Locale.ROOT));
            if (isAway) {
                c.setFont(c.getFont().deriveFont(Font.ITALIC));
                if (!isSelected) {
                    c.setForeground(STATUS_COLOR);
                }
            }
            return c;
        }
    }

    // ------------------------------------------------------ tree plumbing

    private static String key(String network, String target) {
        return network + '\u0000' + target.toLowerCase(Locale.ROOT);
    }

    private DefaultMutableTreeNode ensureNetworkNode(String network) {
        DefaultMutableTreeNode node = networkNodes.get(network);
        if (node == null) {
            node = new DefaultMutableTreeNode(new TargetRef(network, ""));
            networkNodes.put(network, node);
            treeModel.insertNodeInto(node, rootNode, rootNode.getChildCount());
            tree.expandPath(new TreePath(rootNode.getPath()));
        }
        return node;
    }

    private DefaultMutableTreeNode ensureTargetNode(String network, String target) {
        String k = key(network, target);
        DefaultMutableTreeNode node = targetNodes.get(k);
        if (node == null) {
            DefaultMutableTreeNode parent = ensureNetworkNode(network);
            node = new DefaultMutableTreeNode(new TargetRef(network, target));
            targetNodes.put(k, node);
            treeModel.insertNodeInto(node, parent, parent.getChildCount());
            tree.expandPath(new TreePath(parent.getPath()));
        }
        return node;
    }

    private void selectNetwork(String network) {
        DefaultMutableTreeNode node = networkNodes.get(network);
        if (node != null) {
            tree.setSelectionPath(new TreePath(node.getPath()));
        }
    }

    private void selectTarget(String network, String target) {
        DefaultMutableTreeNode node = ensureTargetNode(network, target);
        tree.setSelectionPath(new TreePath(node.getPath()));
    }

    private TargetRef selectedRef() {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return null;
        }
        Object user = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        return user instanceof TargetRef ref ? ref : null;
    }

    private void onTreeSelection() {
        TargetRef ref = selectedRef();
        if (ref == null) {
            return;
        }
        activeKey = key(ref.network(), ref.target());
        unread.remove(activeKey);
        notifiedMentions.remove(activeKey);
        mentions.remove(activeKey); // looking at it clears the badge
        transcript.setDocument(docForKey(activeKey));
        transcript.setCaretPosition(transcript.getDocument().getLength());
        topicLabel.setText(PlainText.plain(topics.getOrDefault(activeKey, " ")));
        rebuildNickModel();
        completer.reset();
        IrcConfig.getDefault().setLastSelected(ref.network());
        refreshConnectButton();
        tree.repaint();
    }

    private void rebuildNickModel() {
        nickModel.clear();
        Map<String, String> nicks = nickLists.get(activeKey);
        if (nicks == null) {
            return;
        }
        List<String> sorted = new ArrayList<>(nicks.values());
        sorted.sort(Comparator
                .comparingInt((String s) -> NickPrefix.rank(s))
                .thenComparing(s -> NickPrefix.strip(s).toLowerCase(Locale.ROOT)));
        for (String n : sorted) {
            nickModel.addElement(n);
        }
    }

    // ------------------------------------------------------ tree popup

    private void maybeTreePopup(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path != null) {
            tree.setSelectionPath(path);
        }
        TargetRef ref = selectedRef();
        JPopupMenu menu = new JPopupMenu();
        JMenuItem add = new JMenuItem("Add Network…");
        add.addActionListener(a -> {
            String name = NetworkEditorDialog.show(IrcConfig.getDefault(), null);
            if (name != null) {
                ensureNetworkNode(name);
                selectNetwork(name);
            }
        });
        menu.add(add);
        if (ref != null) {
            String network = ref.network();
            JMenuItem edit = new JMenuItem("Edit Network…");
            edit.addActionListener(a -> {
                IrcConfig config = IrcConfig.getDefault();
                IrcConfig.Network existing = config.network(network);
                if (existing != null) {
                    NetworkEditorDialog.show(config, existing);
                }
            });
            menu.add(edit);
            JMenuItem delete = new JMenuItem("Delete Network…");
            delete.addActionListener(a -> deleteNetwork(network));
            menu.add(delete);
        }
        menu.show(tree, e.getX(), e.getY());
    }

    private void deleteNetwork(String network) {
        if (!NetworkEditorDialog.confirmAndDelete(IrcConfig.getDefault(), network)) {
            return;
        }
        IrcClient client = SESSIONS.remove(network);
        if (client != null) {
            client.quitAndClose("network removed");
        }
        DefaultMutableTreeNode node = networkNodes.remove(network);
        if (node != null) {
            treeModel.removeNodeFromParent(node);
        }
        targetNodes.keySet().removeIf(k -> k.startsWith(network + '\u0000'));
        refreshConnectButton();
    }

    // ------------------------------------------------------ transcript

    private StyledDocument docForKey(String k) {
        return docs.computeIfAbsent(k, x -> new DefaultStyledDocument());
    }

    /** Test seam: the transcript document for a network/target pair. */
    StyledDocument docForKeyTest(String network, String target) {
        return docForKey(key(network, target));
    }

    /**
     * Transcript retention ceiling per tab, in characters. The engine
     * caps a LINE at 8k, but a connection deliberately outlives the
     * window — an overnight busy channel (or a hostile server) would
     * otherwise grow an EDT-owned StyledDocument without bound, the one
     * read the v1.104–v1.124 bounded-read sweeps didn't reach. When the
     * cap is passed, whole lines fall off the head, mirroring the
     * FlightRecorder's rotation shape. Package-private for the cap test.
     */
    static final int TRANSCRIPT_CAP_CHARS = 1_000_000;

    /**
     * Ceilings on server-driven view state (one family with the
     * transcript cap): a hostile server must not be able to mint
     * unbounded query tabs or nick-set entries with zero user gestures.
     * The nick cap is generous — real channels run to tens of thousands.
     */
    static final int QUERY_TAB_CAP = 100;
    static final int NICK_SET_CAP = 50_000;

    private int queryTabCount(String network) {
        int n = 0;
        for (String k : targetNodes.keySet()) {
            String prefix = network + '\u0000';
            if (k.startsWith(prefix) && !isChannel(k.substring(prefix.length()))
                    && k.length() > prefix.length()) {
                n++;
            }
        }
        return n;
    }

    /** Adds to a server-driven set unless the ceiling is reached (drop, not grow). */
    private static void capAdd(java.util.Set<String> set, String item) {
        if (set.size() < NICK_SET_CAP) {
            set.add(item);
        }
    }

    /** Puts into a server-driven map unless the ceiling is reached (drop, not grow). */
    private static void capPut(Map<String, String> map, String k, String v) {
        if (map.size() < NICK_SET_CAP || map.containsKey(k)) {
            map.put(k, v);
        }
    }

    /**
     * The reattach gap line (v1.344.0). While the window was closed the
     * engine kept receiving — and logging — this network's traffic, but
     * the scrollback did not; a transcript that silently omits messages
     * it could have shown misleads. One dim line per restored transcript
     * says so and points at the record. Package-private constant + seam
     * so the marker is pinnable headless (the class's UI paths are
     * source-gated, not driven, in tests).
     */
    static final String GAP_MARKER =
            "— view was closed; the full record is in ~/.nmox/irc-logs —";

    /**
     * Appends {@link #GAP_MARKER} to the network's status transcript and
     * each given channel's transcript. Writes the documents DIRECTLY
     * (not via {@code append}): a marker is bookkeeping, not a message —
     * it must not bold the tree, count as unread, or ring a mention.
     */
    void appendGapMarkers(String network, java.util.Collection<String> channels) {
        SimpleAttributeSet dim = attrs(new Color(0x88, 0x88, 0x88), false, true, false);
        java.util.List<String> keys = new java.util.ArrayList<>();
        keys.add(key(network, ""));
        for (String chan : channels) {
            keys.add(key(network, chan));
        }
        for (String k : keys) {
            StyledDocument doc = docForKey(k);
            try {
                doc.insertString(doc.getLength(), GAP_MARKER + "\n", dim);
                trimTranscript(doc);
            } catch (BadLocationException ex) {
                // appending at getLength() cannot be out of bounds
            }
        }
    }

    private void append(String k, List<Object[]> runs, boolean mention) {
        StyledDocument doc = docForKey(k);
        try {
            for (Object[] run : runs) {
                doc.insertString(doc.getLength(), (String) run[0], (SimpleAttributeSet) run[1]);
            }
            doc.insertString(doc.getLength(), "\n", null);
            trimTranscript(doc);
        } catch (BadLocationException ex) {
            // appending at getLength() cannot be out of bounds
        }
        if (k.equals(activeKey)) {
            transcript.setCaretPosition(doc.getLength());
        } else {
            unread.add(k);
            if (mention) {
                mentions.merge(k, 1, Integer::sum);
            }
            tree.repaint();
        }
    }

    /** Drops whole head lines until the document is back under the cap. */
    static void trimTranscript(StyledDocument doc) throws BadLocationException {
        while (doc.getLength() > TRANSCRIPT_CAP_CHARS) {
            String head = doc.getText(0, Math.min(16_384, doc.getLength()));
            int nl = head.indexOf('\n');
            // a single line larger than the probe window (engine caps
            // lines at 8k, so this is unreachable in practice) still
            // makes progress by dropping the whole probe
            doc.remove(0, nl >= 0 ? nl + 1 : head.length());
        }
    }

    private static SimpleAttributeSet attrs(Color fg, boolean bold, boolean italic, boolean underline) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        if (fg != null) {
            StyleConstants.setForeground(a, fg);
        }
        StyleConstants.setBold(a, bold);
        StyleConstants.setItalic(a, italic);
        StyleConstants.setUnderline(a, underline);
        return a;
    }

    private static Color nickColor(String nick) {
        return NICK_COLORS[Math.floorMod(nick.toLowerCase(Locale.ROOT).hashCode(), NICK_COLORS.length)];
    }

    private static Color mircColor(int index) {
        return index >= 0 && index < MIRC_COLORS.length ? MIRC_COLORS[index] : null;
    }

    /** The transcript timestamp: the server's {@code @time} tag when present, else now. */
    private static String stampOf(IrcMessage msg) {
        return ServerTime.localTime(msg.tags().get("time"))
                .map(STAMP::format)
                .orElseGet(() -> STAMP.format(LocalTime.now()));
    }

    private static String stampNow() {
        return STAMP.format(LocalTime.now());
    }

    private List<Object[]> stampedRuns(String stamp) {
        List<Object[]> runs = new ArrayList<>(6);
        runs.add(new Object[] {"[" + stamp + "] ", attrs(STAMP_COLOR, false, false, false)});
        return runs;
    }

    /** A chat line: {@code [HH:mm] <nick> text} or {@code [HH:mm] * nick text}. */
    private void appendChat(String k, String nick, String body, boolean action) {
        appendChat(k, nick, body, action, stampNow(), false);
    }

    /**
     * A chat line with an explicit timestamp and highlight flag. URLs
     * inside the body become underlined link-colored runs carrying
     * {@link #ATTR_URL}; a highlighted (mention) line gets a warm
     * background wash and, off-screen, a tree badge.
     */
    private void appendChat(String k, String nick, String body, boolean action,
            String stamp, boolean highlight) {
        // a nick is rendered verbatim (never parsed as format codes), so a
        // nick carrying mIRC color bytes or C0 controls would paint
        // garbage glyphs into the transcript — strip to plain text first
        nick = MircFormat.stripToText(nick);
        List<Object[]> runs = stampedRuns(stamp);
        SimpleAttributeSet nickAttrs = action
                ? attrs(nickColor(nick), false, true, false)
                : attrs(nickColor(nick), true, false, false);
        if (highlight) {
            StyleConstants.setBackground(nickAttrs, HIGHLIGHT_BG);
        }
        runs.add(new Object[] {action ? "* " + nick + " " : "<" + nick + "> ", nickAttrs});
        for (MircFormat.Span span : MircFormat.parse(body)) {
            if (span.text().isEmpty()) {
                continue;
            }
            appendSpanRuns(runs, span, action, highlight);
        }
        append(k, runs, highlight);
    }

    /** One mIRC span, split further around any URLs it contains. */
    private void appendSpanRuns(List<Object[]> runs, MircFormat.Span span,
            boolean action, boolean highlight) {
        String text = span.text();
        List<UrlDetector.Range> urls = UrlDetector.find(text);
        int at = 0;
        for (UrlDetector.Range r : urls) {
            if (r.start() > at) {
                runs.add(new Object[] {text.substring(at, r.start()),
                    spanAttrs(span, action, highlight)});
            }
            String url = r.of(text);
            SimpleAttributeSet a = attrs(LINK_COLOR, span.bold(),
                    span.italic() || action, true);
            if (highlight) {
                StyleConstants.setBackground(a, HIGHLIGHT_BG);
            }
            a.addAttribute(ATTR_URL, url);
            runs.add(new Object[] {url, a});
            at = r.end();
        }
        if (at < text.length()) {
            runs.add(new Object[] {text.substring(at), spanAttrs(span, action, highlight)});
        }
    }

    private SimpleAttributeSet spanAttrs(MircFormat.Span span, boolean action, boolean highlight) {
        SimpleAttributeSet a = attrs(mircColor(span.foreground()),
                span.bold(), span.italic() || action, span.underline());
        Color bg = mircColor(span.background());
        if (bg != null) {
            StyleConstants.setBackground(a, bg);
        } else if (highlight) {
            StyleConstants.setBackground(a, HIGHLIGHT_BG);
        }
        return a;
    }

    /** A grey status line ({@code [HH:mm] text}). */
    private void appendStatus(String k, String text) {
        appendStatus(k, text, stampNow());
    }

    private void appendStatus(String k, String text, String stamp) {
        List<Object[]> runs = stampedRuns(stamp);
        runs.add(new Object[] {MircFormat.stripToText(text), attrs(STATUS_COLOR, false, true, false)});
        append(k, runs, false);
    }

    // ------------------------------------------------------ URLs

    /** The URL under the mouse, or null. */
    private String urlAt(MouseEvent e) {
        int pos = transcript.viewToModel2D(e.getPoint());
        if (pos < 0) {
            return null;
        }
        StyledDocument doc = (StyledDocument) transcript.getDocument();
        Object url = doc.getCharacterElement(pos).getAttributes().getAttribute(ATTR_URL);
        return url instanceof String s ? s : null;
    }

    /**
     * Opens a clicked link: the in-app Browser via the
     * {@link EmbeddedBrowser} seam when available, the system browser
     * otherwise — a click on a link must never be dead (the BrowserDevice
     * idiom).
     */
    private void openUrl(String url) {
        try {
            EmbeddedBrowser embedded = EmbeddedBrowser.find();
            if (embedded != null && embedded.open(url)) {
                return;
            }
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ex) {
            appendStatus(activeKey != null ? activeKey : key(activeNetwork(), ""),
                    "Could not open " + url);
        }
    }

    // ------------------------------------------------------ find bar (⌘F)

    private void buildFindBar() {
        findBar = new JPanel(new BorderLayout(6, 0));
        findBar.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        findField = new JTextField();
        findCount = new JLabel(" ");
        findBar.add(new JLabel("Find:"), BorderLayout.WEST);
        findBar.add(findField, BorderLayout.CENTER);
        findBar.add(findCount, BorderLayout.EAST);
        findBar.setVisible(false);
        findField.addActionListener(e -> findNext());
        findField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                refreshFind();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                refreshFind();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                refreshFind();
            }
        });
        findField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    closeFindBar();
                    e.consume();
                }
            }
        });
    }

    private void toggleFindBar() {
        if (findBar.isVisible()) {
            closeFindBar();
        } else {
            findBar.setVisible(true);
            findField.requestFocusInWindow();
            refreshFind();
        }
    }

    private void closeFindBar() {
        findBar.setVisible(false);
        transcript.getHighlighter().removeAllHighlights();
        findMatches = List.of();
        lastFindPos = -1;
        findCount.setText(" ");
        input.requestFocusInWindow();
    }

    /** Recomputes matches + highlight-all for the current query. */
    private void refreshFind() {
        transcript.getHighlighter().removeAllHighlights();
        lastFindPos = -1;
        String query = findField.getText();
        String text = transcriptText();
        findMatches = IrcSearch.matches(text, query);
        if (query.isEmpty()) {
            findCount.setText(" ");
            return;
        }
        DefaultHighlighter.DefaultHighlightPainter painter
                = new DefaultHighlighter.DefaultHighlightPainter(FIND_MATCH);
        for (int m : findMatches) {
            try {
                transcript.getHighlighter().addHighlight(m, m + query.length(), painter);
            } catch (BadLocationException ex) {
                // stale offset after concurrent append: skip
            }
        }
        boolean capped = findMatches.size() >= IrcSearch.MAX_MATCHES;
        findCount.setText(PlainText.plain(capped ? findMatches.size() + "+ matches" : org.nmox.studio.core.util.Plural.of(findMatches.size(), "match", "matches")));
        findNext();
    }

    /** Enter cycles: jump the caret to the next match, wrapping. */
    private void findNext() {
        int next = IrcSearch.next(findMatches, lastFindPos);
        if (next < 0) {
            return;
        }
        lastFindPos = next;
        int end = Math.min(next + findField.getText().length(),
                transcript.getDocument().getLength());
        transcript.setCaretPosition(end);
        int idx = findMatches.indexOf(next) + 1;
        boolean capped = findMatches.size() >= IrcSearch.MAX_MATCHES;
        findCount.setText(PlainText.plain(idx + " of " + findMatches.size() + (capped ? "+" : "")));
    }

    private String transcriptText() {
        try {
            return transcript.getDocument().getText(0, transcript.getDocument().getLength());
        } catch (BadLocationException ex) {
            return "";
        }
    }

    // ------------------------------------------------------ input keys

    /**
     * Tab completes/cycles nicks, Up/Down walk this target's history,
     * Ctrl+U clears the line (the readline chord); any other key resets
     * the completion cycle (the WeeChat contract).
     */
    private void installInputKeys() {
        input.setFocusTraversalKeysEnabled(false); // Tab is ours now
        // Ctrl+U clears the line — the readline/terminal "kill line"
        // chord IRC users already have in their fingers.
        //
        // Escape is NOT the clear key, and the v1.205.0 live gauntlet is
        // why: the platform consumes ESC above this component in a docked
        // TopComponent — neither a KeyListener nor a WHEN_FOCUSED key
        // binding ever sees it (both were tried against the assembled
        // app). The Escape case in the listener below stays as a no-cost
        // fallback for window modes that DO deliver it, but nothing
        // advertises Escape, because a documented key that does nothing
        // is worse than no key at all.
        input.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "nmox-irc-clear");
        input.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK),
                "nmox-irc-clear");
        // Ctrl+J must ride the input field's OWN map: the v2.2.0 walk
        // proved the window-level binding never fires in a docked
        // TopComponent (the v1.205.0 Escape lesson, same layer) — the
        // field is where the user's fingers already are
        input.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_J, InputEvent.CTRL_DOWN_MASK),
                "irc-jump-activity");
        input.getActionMap().put("irc-jump-activity", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                jumpToActivity();
            }
        });
        input.getActionMap().put("nmox-irc-clear", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                clearInput();
            }
        });
        input.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_TAB -> {
                        NickCompleter.Result r = completer.complete(
                                input.getText(), input.getCaretPosition(), activeNickNames());
                        if (r != null) {
                            input.setText(r.text());
                            input.setCaretPosition(r.caret());
                        }
                        e.consume();
                    }
                    case KeyEvent.VK_UP -> {
                        String prev = historyFor(activeHistoryKey()).up(input.getText());
                        if (prev != null) {
                            input.setText(prev);
                        }
                        completer.reset();
                        e.consume();
                    }
                    case KeyEvent.VK_DOWN -> {
                        String next = historyFor(activeHistoryKey()).down();
                        if (next != null) {
                            input.setText(next);
                        }
                        completer.reset();
                        e.consume();
                    }
                    case KeyEvent.VK_ESCAPE -> {
                        // kept for window modes that DO deliver Escape to
                        // listeners; the InputMap binding above is what
                        // actually fires in the docked case
                        clearInput();
                        e.consume();
                    }
                    default ->
                        completer.reset();
                }
            }
        });
    }

    /**
     * Abandons the half-typed line: the text, the completion cycle, and
     * the history cursor all go back to their resting state. One method
     * so every clear path stays identical.
     */
    private void clearInput() {
        input.setText("");
        completer.reset();
        historyFor(activeHistoryKey()).resetCursor();
    }

    /** The active channel's bare nicks (prefixes stripped), list order. */
    private List<String> activeNickNames() {
        List<String> out = new ArrayList<>();
        Map<String, String> nicks = nickLists.get(activeKey);
        if (nicks != null) {
            List<String> sorted = new ArrayList<>(nicks.values());
            sorted.sort(Comparator.comparing(s -> NickPrefix.strip(s).toLowerCase(Locale.ROOT)));
            for (String display : sorted) {
                out.add(NickPrefix.strip(display));
            }
        }
        return out;
    }

    private String activeHistoryKey() {
        return activeKey == null ? key(activeNetwork(), "") : activeKey;
    }

    /**
     * Where COMMAND feedback lands: the transcript the user typed into,
     * network status only when nothing is selected (the v2.34.4 law —
     * spoken in the wrong room reads as silence; the v2.36.2 sweep
     * found six more handlers answering into network status while the
     * user watched a channel, the worst being "Not connected", which
     * made every command typed in a channel while disconnected answer
     * invisibly). Server-initiated events keep their own routing.
     */
    private String feedbackKey() {
        return activeKey != null ? activeKey : key(activeNetwork(), "");
    }

    private InputHistory historyFor(String k) {
        return histories.computeIfAbsent(k, x -> new InputHistory());
    }

    // ------------------------------------------------------ engine bridge

    /**
     * The engine→EDT adapter, one per network per window. Every engine
     * callback arrives on the IRC RequestProcessor thread and is
     * marshalled through {@code SwingUtilities.invokeLater} — the ONLY
     * road into the components (the engine's documented contract).
     */
    private final class Bridge implements IrcClient.Listener {

        private final String network;

        Bridge(String network) {
            this.network = network;
        }

        @Override
        public void connected() {
            SwingUtilities.invokeLater(() -> {
                appendStatus(key(network, ""), "Connected to server, registering…");
                refreshConnectButton();
            });
        }

        @Override
        public void registered(String nick) {
            SwingUtilities.invokeLater(() -> {
                appendStatus(key(network, ""), "Registered as " + nick);
                refreshConnectButton();
            });
        }

        @Override
        public void lineReceived(IrcMessage message) {
            SwingUtilities.invokeLater(() -> handleLine(network, message));
        }

        @Override
        public void disconnected(String reason) {
            SwingUtilities.invokeLater(() -> {
                appendStatus(key(network, ""), "Disconnected: " + reason);
                refreshConnectButton();
            });
        }
    }

    private void attachBridge(String network, IrcClient client) {
        Bridge bridge = bridges.get(network);
        if (bridge == null) {
            bridge = new Bridge(network);
            bridges.put(network, bridge);
        }
        client.addListener(bridge); // equality-guarded in the engine too
    }

    // ------------------------------------------------------ message routing

    /** EDT. Routes one parsed line into transcripts, nick lists, and the tree. */
    private void handleLine(String network, IrcMessage msg) {
        String statusKey = key(network, "");
        IrcClient client = SESSIONS.get(network);
        String me = client == null ? "" : client.currentNick();
        String stamp = stampOf(msg);
        switch (msg.command()) {
            case "PRIVMSG" -> {
                String sender = msg.nick() == null ? "?" : msg.nick();
                String target = msg.param(0);
                String body = msg.trailing() == null ? "" : msg.trailing();
                boolean action = false;
                Ctcp ctcp = Ctcp.extract(body);
                if (ctcp != null) {
                    if (Ctcp.ACTION.equals(ctcp.command())) {
                        action = true;
                        body = ctcp.argument();
                    } else {
                        appendStatus(statusKey, "CTCP " + ctcp.command() + " from " + sender);
                        return;
                    }
                }
                boolean fromSelf = sender.equalsIgnoreCase(me);
                String targetName;
                if (isChannel(target)) {
                    targetName = target;
                } else if (fromSelf) {
                    // echo-message: our own line echoed back files under
                    // the PEER's query, not under our own nick
                    targetName = target;
                } else {
                    targetName = sender;
                }
                String k = key(network, targetName);
                if (isChannel(targetName)) {
                    // speech is what makes later presence lines signal
                    smartFilter.spoke(network, targetName, sender);
                }
                // custom /filter regexes: a line the user explicitly
                // filtered produces NO signal — no transcript line, no
                // unread, no mention, no query tab; the engine-side
                // IrcLogTap upstream keeps the full record. The check
                // sits BELOW smartFilter.spoke so hiding a line never
                // corrupts the presence bookkeeping.
                if (textFilters.hides(targetName, "<" + sender + "> " + body)) {
                    break;
                }
                // A server minting sender nicks could otherwise create
                // unbounded query tabs (tree nodes + documents) with zero
                // user gestures; past the cap, overflow speech lands in
                // the network status tab with the sender named, so
                // nothing is silently dropped.
                if (!isChannel(targetName)
                        && targetNodes.get(k) == null
                        && queryTabCount(network) >= QUERY_TAB_CAP) {
                    appendStatus(key(network, ""),
                            "(query overflow) <" + MircFormat.stripToText(sender) + "> " + body,
                            stamp);
                    break; // (logged by IrcLogTap)
                }
                ensureTargetNode(network, targetName);
                boolean highlight = !fromSelf && Highlights.matches(me,
                        IrcConfig.getDefault().highlightKeywords(), body);
                appendChat(k, sender, body, action, stamp, highlight);
                if (highlight) {
                    notifyMention(network, targetName, sender, body);
                }
            }
            case "NOTICE" -> {
                String sender = msg.nick() == null ? network : msg.nick();
                String target = msg.param(0);
                String body = msg.trailing() == null ? "" : msg.trailing();
                String k = isChannel(target) ? key(network, target) : statusKey;
                // channel notices honor the custom /filter regexes too
                // (the classic spam vector); the log tap keeps the line
                if (isChannel(target)
                        && textFilters.hides(target, "-" + sender + "- " + body)) {
                    break;
                }
                appendStatus(k, "-" + sender + "- " + body, stamp);
            }
            case "JOIN" -> {
                String chan = msg.trailing() != null ? msg.trailing() : msg.param(0);
                String who = msg.nick() == null ? "?" : msg.nick();
                String k = key(network, chan);
                if (who.equalsIgnoreCase(me)) {
                    ensureTargetNode(network, chan);
                    selectTarget(network, chan);
                    appendStatus(k, "You joined " + chan, stamp);
                } else {
                    capPut(nickLists.computeIfAbsent(k, x -> new HashMap<>()),
                            who.toLowerCase(Locale.ROOT), who);
                    if (k.equals(activeKey)) {
                        rebuildNickModel();
                    }
                    if (showPresence(network, chan, who)) {
                        appendStatus(k, "→ " + who + " joined", stamp);
                    }
                }
            }
            case "PART" -> {
                String chan = msg.param(0);
                String who = msg.nick() == null ? "?" : msg.nick();
                String k = key(network, chan);
                removeNick(k, who);
                if (who.equalsIgnoreCase(me)) {
                    appendStatus(k, "You left " + chan, stamp);
                } else if (showPresence(network, chan, who)) {
                    appendStatus(k, "← " + who + " left", stamp);
                }
            }
            case "KICK" -> {
                String chan = msg.param(0);
                String victim = msg.param(1);
                String k = key(network, chan);
                removeNick(k, victim);
                appendStatus(k, victim + " was kicked by "
                        + (msg.nick() == null ? "?" : msg.nick()), stamp);
            }
            case "QUIT" -> {
                String who = msg.nick() == null ? "?" : msg.nick();
                String lowerWho = who.toLowerCase(Locale.ROOT);
                for (Map.Entry<String, Map<String, String>> e : nickLists.entrySet()) {
                    if (e.getKey().startsWith(network + '\u0000')
                            && e.getValue().remove(lowerWho) != null) {
                        if (showPresence(network, targetOfKey(e.getKey()), who)) {
                            appendStatus(e.getKey(), "← " + who + " quit", stamp);
                        }
                        if (e.getKey().equals(activeKey)) {
                            rebuildNickModel();
                        }
                    }
                }
            }
            case "NICK" -> {
                String who = msg.nick() == null ? "?" : msg.nick();
                String now = msg.trailing() != null ? msg.trailing() : msg.param(0);
                String lowerWho = who.toLowerCase(Locale.ROOT);
                smartFilter.rename(network, who, now);
                for (Map.Entry<String, Map<String, String>> e : nickLists.entrySet()) {
                    if (!e.getKey().startsWith(network + '\u0000')) {
                        continue;
                    }
                    String display = e.getValue().remove(lowerWho);
                    if (display != null) {
                        String prefix = display.substring(0, display.length() - who.length());
                        e.getValue().put(now.toLowerCase(Locale.ROOT), prefix + now);
                        if (who.equalsIgnoreCase(me) || now.equalsIgnoreCase(me)
                                || showPresence(network, targetOfKey(e.getKey()), who)) {
                            appendStatus(e.getKey(), who + " is now known as " + now, stamp);
                        }
                        if (e.getKey().equals(activeKey)) {
                            rebuildNickModel();
                        }
                    }
                }
            }
            case "AWAY" -> {
                // away-notify: a tagged-cap server tells us who stepped out
                String who = msg.nick() == null ? "?" : msg.nick();
                Set<String> away = awayNicks.computeIfAbsent(network, x -> new HashSet<>());
                if (msg.trailing() == null || msg.trailing().isEmpty()) {
                    away.remove(who.toLowerCase(Locale.ROOT));
                } else {
                    capAdd(away, who.toLowerCase(Locale.ROOT));
                }
                nickList.repaint();
            }
            case "TOPIC" -> {
                String chan = msg.param(0);
                String k = key(network, chan);
                String topicText = msg.trailing() == null ? "" : msg.trailing();
                topics.put(k, MircFormat.stripToText(topicText));
                if (k.equals(activeKey)) {
                    topicLabel.setText(PlainText.plain(topics.get(k)));
                }
                appendStatus(k, (msg.nick() == null ? "?" : msg.nick())
                        + " set the topic: " + topicText, stamp);
            }
            default ->
                handleNumeric(network, statusKey, msg);
        }
    }

    /** The target half of a view key (for log paths). */
    private static String targetOfKey(String k) {
        int sep = k.indexOf('\u0000');
        return sep < 0 ? k : k.substring(sep + 1);
    }

    /**
     * A mention while the IRC tab is hidden becomes a platform
     * notification (the UpdateCheck idiom); clicking it opens and
     * fronts this window on the right channel.
     */
    private void notifyMention(String network, String target, String sender, String body) {
        if (isShowing()) {
            return;
        }
        // one balloon per target until the user views it — a flood of
        // mention lines must not become a balloon per line
        if (!notifiedMentions.add(key(network, target))) {
            return;
        }
        String title = MircFormat.stripToText(sender)
                + " mentioned you in " + MircFormat.stripToText(target);
        NotificationDisplayer.getDefault().notify(title,
                UIManager.getIcon("OptionPane.informationIcon"),
                MircFormat.stripToText(body),
                e -> SwingUtilities.invokeLater(() -> {
                    open();
                    requestActive();
                    selectTarget(network, target);
                }));
    }

    private void handleNumeric(String network, String statusKey, IrcMessage msg) {
        // LIST replies feed the armed collector (the /list browser)
        ChannelListCollector listCollector = listCollectors.get(network);
        if (listCollector != null
                && ("321".equals(msg.command()) || "322".equals(msg.command())
                || "323".equals(msg.command()))) {
            if (listCollector.accept(msg)) {
                listCollectors.remove(network);
                IrcClient client = SESSIONS.get(network);
                ChannelListDialog.show(network, listCollector.rows(),
                        listCollector.totalSeen(),
                        chan -> {
                            if (client != null) {
                                client.join(chan);
                            }
                        });
            }
            return;
        }
        // WHOIS numerics assemble into one card, rendered on 318
        if (isWhoisNumeric(msg.command())) {
            WhoisCollector wc = whoisCollectors.computeIfAbsent(network,
                    x -> new WhoisCollector());
            WhoisCollector.WhoisInfo info = wc.accept(msg);
            if (info != null) {
                String k = activeKey != null && activeKey.startsWith(network + '\u0000')
                        ? activeKey : statusKey;
                for (String line : WhoisCollector.cardLines(info)) {
                    appendStatus(k, line);
                }
            }
            if (wc.collecting() || info != null) {
                return; // suppress the raw scatter while the card assembles
            }
        }
        // 301: someone we messaged (or whois'd) is away
        if ("301".equals(msg.command())) {
            String k = activeKey != null && activeKey.startsWith(network + '\u0000')
                    ? activeKey : statusKey;
            appendStatus(k, msg.param(1) + " is away: "
                    + (msg.trailing() == null ? "" : msg.trailing()));
            Set<String> away = awayNicks.computeIfAbsent(network, x -> new HashSet<>());
            capAdd(away, msg.param(1).toLowerCase(Locale.ROOT));
            nickList.repaint();
            return;
        }
        switch (Numerics.classify(msg.command())) {
            case WELCOME ->
                appendStatus(statusKey, msg.trailing() == null ? "Welcome" : msg.trailing());
            case NAMES -> {
                // params: me, symbol, channel; trailing: "@op +voiced plain"
                String chan = msg.param(2);
                String k = key(network, chan);
                Set<String> pending = pendingNames.computeIfAbsent(k, x -> new HashSet<>());
                String namesBlob = msg.trailing() == null ? "" : msg.trailing();
                for (String name : namesBlob.split(" ")) {
                    if (!name.isEmpty()) {
                        capAdd(pending, name);
                    }
                }
            }
            case NAMES_END -> {
                String chan = msg.param(1);
                String k = key(network, chan);
                Set<String> pending = pendingNames.remove(k);
                if (pending != null) {
                    Map<String, String> nicks = new HashMap<>();
                    for (String display : pending) {
                        // multi-prefix: strip the WHOLE stacked run (@+nick)
                        nicks.put(NickPrefix.strip(display).toLowerCase(Locale.ROOT), display);
                    }
                    nickLists.put(k, nicks);
                    if (k.equals(activeKey)) {
                        rebuildNickModel();
                    }
                }
            }
            case TOPIC -> {
                String chan = msg.param(1);
                String k = key(network, chan);
                String topicText = msg.trailing() == null ? "" : msg.trailing();
                topics.put(k, MircFormat.stripToText(topicText));
                if (k.equals(activeKey)) {
                    topicLabel.setText(PlainText.plain(topics.get(k)));
                }
            }
            case TOPIC_META -> {
                // who-set-it + when: transcript noise; skip
            }
            case NICK_IN_USE ->
                appendStatus(statusKey, "Nickname in use — trying an alternate");
            case MOTD, WHOIS, ERROR, OTHER ->
                appendStatus(statusKey, tailOf(msg));
            case NOT_NUMERIC -> {
                // PING/PONG/MODE/CAP/AUTHENTICATE and friends: no rendering
            }
        }
    }

    private static boolean isWhoisNumeric(String command) {
        return switch (command) {
            case "311", "312", "317", "318", "319", "330" -> true;
            default -> false;
        };
    }

    /** Numeric params minus our own nick (param 0), joined for status display. */
    private static String tailOf(IrcMessage msg) {
        List<String> params = msg.params();
        if (params.size() <= 1) {
            return msg.command();
        }
        return String.join(" ", params.subList(1, params.size()));
    }

    private void removeNick(String k, String nick) {
        Map<String, String> nicks = nickLists.get(k);
        if (nicks != null) {
            nicks.remove(nick.toLowerCase(Locale.ROOT));
            if (k.equals(activeKey)) {
                rebuildNickModel();
            }
        }
    }

    private static boolean isChannel(String target) {
        return !target.isEmpty() && (target.charAt(0) == '#' || target.charAt(0) == '&');
    }

    // ------------------------------------------------------ user input

    private String activeNetwork() {
        TargetRef ref = selectedRef();
        if (ref != null) {
            return ref.network();
        }
        return IrcConfig.getDefault().lastSelected();
    }

    private String activeTarget() {
        TargetRef ref = selectedRef();
        return ref == null ? "" : ref.target();
    }

    private IrcClient liveClient() {
        IrcClient client = SESSIONS.get(activeNetwork());
        if (client == null || client.state() == IrcClient.State.CLOSED) {
            appendStatus(feedbackKey(), "Not connected — press Connect or use /connect");
            return null;
        }
        return client;
    }

    private void onConnectButton() {
        String network = activeNetwork();
        IrcClient client = SESSIONS.get(network);
        if (client != null && client.state() != IrcClient.State.CLOSED) {
            client.quitAndClose("NMOX Studio");
        } else {
            connectNetwork(network);
        }
        refreshConnectButton();
    }

    /** The one road to a new connection — always a user gesture. */
    private void connectNetwork(String network) {
        IrcConfig config = IrcConfig.getDefault();
        IrcConfig.Network saved = config.network(network);
        if (saved == null) {
            appendStatus(key(network, ""), "No saved network named " + network);
            return;
        }
        IrcClient client = SESSIONS.get(network);
        if (client == null || client.state() == IrcClient.State.CLOSED) {
            client = new IrcClient(new IrcClient.Profile(saved.name(), saved.host(),
                    saved.port(), saved.tls(), saved.nick(), null, null,
                    saved.saslAccount()));
            // logging is a CLIENT-lifetime concern (v1.322.0, ledger 66):
            // the tap listener lives as long as the connection does, so an
            // enabled log keeps recording while the window is closed — the
            // Bridge below renders only, it no longer logs inbound traffic
            client.addListener(new org.nmox.studio.ui.irc.engine.IrcLogTap(
                    saved.name(), logger));
            SESSIONS.put(network, client);
        }
        client.setIgnoredNicks(config.ignoredNicks(network));
        attachBridge(network, client);
        ensureNetworkNode(network);
        appendStatus(key(network, ""), "Connecting to " + saved.host() + ":" + saved.port()
                + (saved.tls() ? " (TLS)…" : "…"));
        client.connect();
        for (String chan : saved.autojoin()) {
            client.join(chan);
        }
        refreshConnectButton();
    }

    private void refreshConnectButton() {
        if (connectButton == null) {
            return;
        }
        IrcClient client = SESSIONS.get(activeNetwork());
        boolean live = client != null && client.state() != IrcClient.State.CLOSED;
        connectButton.setText(live ? "Disconnect" : "Connect");
    }

    private void onInput() {
        String raw = input.getText();
        if (raw == null || raw.isBlank()) {
            return;
        }
        input.setText("");
        completer.reset();
        historyFor(activeHistoryKey()).add(raw);
        if (!raw.startsWith("/")) {
            sayToActive(raw, false);
            return;
        }
        int sp = raw.indexOf(' ');
        String cmd = (sp < 0 ? raw.substring(1) : raw.substring(1, sp)).toLowerCase(Locale.ROOT);
        String args = sp < 0 ? "" : raw.substring(sp + 1).trim();
        // /alias expansion, ONE level: the expansion is re-parsed but
        // never re-expanded, so an alias cannot loop; a name matching a
        // built-in never expands (aliases may not shadow the switch)
        String expansion = IrcConfig.getDefault().aliases().get(cmd);
        if (expansion != null && !BUILTIN_COMMANDS.contains(cmd)) {
            String rewritten = "/" + expansion + (args.isEmpty() ? "" : " " + args);
            int sp0 = rewritten.indexOf(' ');
            cmd = (sp0 < 0 ? rewritten.substring(1) : rewritten.substring(1, sp0))
                    .toLowerCase(Locale.ROOT);
            args = sp0 < 0 ? "" : rewritten.substring(sp0 + 1).trim();
        }
        switch (cmd) {
            case "connect" -> commandConnect(args);
            case "join", "j" -> {
                IrcClient c = liveClient();
                if (c != null && !args.isEmpty()) {
                    c.join(args.split(" ")[0]);
                }
            }
            case "part" -> {
                IrcClient c = liveClient();
                if (c != null) {
                    String chan = args.isEmpty() ? activeTarget() : args.split(" ")[0];
                    if (isChannel(chan)) {
                        c.part(chan);
                    }
                }
            }
            case "msg" -> {
                int sp2 = args.indexOf(' ');
                if (sp2 > 0) {
                    String to = args.substring(0, sp2);
                    String text = args.substring(sp2 + 1);
                    IrcClient c = liveClient();
                    if (c != null) {
                        c.privmsg(to, text);
                        ensureTargetNode(activeNetwork(), to);
                        if (!c.capEnabled("echo-message")) {
                            // same /filter verdict as the send path (v2.10.2)
                            if (!textFilters.hides(to, "<" + c.currentNick() + "> " + text)) {
                                appendChat(key(activeNetwork(), to), c.currentNick(), text, false);
                            }
                            logger.chat(activeNetwork(), to, c.currentNick(), text);
                        }
                    }
                }
            }
            case "query" -> {
                if (!args.isEmpty()) {
                    selectTarget(activeNetwork(), args.split(" ")[0]);
                }
            }
            case "me" -> sayToActive(args, true);
            case "nick" -> {
                IrcClient c = liveClient();
                if (c != null && !args.isEmpty()) {
                    c.nick(args.split(" ")[0]);
                }
            }
            case "topic" -> {
                IrcClient c = liveClient();
                String chan = activeTarget();
                if (c != null && isChannel(chan)) {
                    c.topic(chan, args.isEmpty() ? null : args);
                }
            }
            case "whois" -> {
                IrcClient c = liveClient();
                if (c != null && !args.isEmpty()) {
                    c.whois(args.split(" ")[0]);
                }
            }
            case "list" -> commandList(args);
            case "ignore" -> commandIgnore(args);
            case "unignore" -> commandUnignore(args);
            case "away" -> {
                IrcClient c = liveClient();
                if (c != null) {
                    c.sendRaw(args.isEmpty() ? "AWAY" : "AWAY :" + args);
                    appendStatus(feedbackKey(), args.isEmpty()
                            ? "You are no longer marked away"
                            : "You are now marked away: " + args);
                }
            }
            case "log" -> commandLog(args);
            case "notice" -> {
                int sp2 = args.indexOf(' ');
                IrcClient c = liveClient();
                if (c != null && sp2 > 0) {
                    String to = args.substring(0, sp2);
                    String text = args.substring(sp2 + 1);
                    c.notice(to, text);
                    appendStatus(feedbackKey(), "-" + c.currentNick()
                            + " → " + to + "- " + text);
                }
            }
            case "ctcp" -> {
                String[] parts = args.split(" ", 3);
                IrcClient c = liveClient();
                if (c != null && parts.length >= 2) {
                    String verb = parts[1].toUpperCase(Locale.ROOT);
                    String arg = parts.length > 2 ? parts[2] : "";
                    c.privmsg(parts[0], Ctcp.wrap(verb, arg));
                    appendStatus(feedbackKey(),
                            "CTCP " + verb + " sent to " + parts[0]);
                }
            }
            case "quit" -> {
                IrcClient c = SESSIONS.get(activeNetwork());
                if (c != null) {
                    c.quitAndClose(args.isEmpty() ? "NMOX Studio" : args);
                }
            }
            case "raw" -> {
                IrcClient c = liveClient();
                if (c != null && !args.isEmpty()) {
                    c.sendRaw(args);
                }
            }
            case "filter" -> commandFilter(args);
            case "lastlog" -> commandLastlog(args);
            case "alias" -> commandAlias(args);
            case "kick" -> {
                IrcClient c = liveClient();
                String chan = activeTarget();
                if (c != null && isChannel(chan) && !args.isEmpty()) {
                    String[] parts = args.split(" ", 2);
                    c.sendRaw("KICK " + chan + " " + parts[0]
                            + (parts.length > 1 ? " :" + parts[1] : ""));
                }
            }
            case "mode" -> {
                IrcClient c = liveClient();
                if (c != null && !args.isEmpty()) {
                    // WeeChat's shorthand: flags with no target apply to
                    // the channel you are looking at
                    boolean bare = args.startsWith("+") || args.startsWith("-");
                    c.sendRaw("MODE " + (bare ? activeTarget() + " " : "") + args);
                }
            }
            case "invite" -> {
                IrcClient c = liveClient();
                if (c != null && !args.isEmpty()) {
                    String[] parts = args.split(" ");
                    String chan = parts.length > 1 ? parts[1] : activeTarget();
                    if (isChannel(chan)) {
                        c.sendRaw("INVITE " + parts[0] + " " + chan);
                    }
                }
            }
            case "op", "deop", "voice", "devoice" -> commandOpish(cmd, args);
            case "ban", "unban" -> {
                IrcClient c = liveClient();
                String chan = activeTarget();
                if (c != null && isChannel(chan) && !args.isEmpty()) {
                    c.sendRaw(OpModes.mode(chan, cmd.equals("ban"), 'b',
                            List.of(OpModes.banMask(args.split(" ")[0]))));
                }
            }
            case "clear" -> {
                StyledDocument doc = docForKey(activeKey);
                try {
                    doc.remove(0, doc.getLength());
                } catch (BadLocationException ex) {
                    // removing the whole document cannot be out of bounds
                }
            }
            case "cycle" -> {
                IrcClient c = liveClient();
                String chan = activeTarget();
                if (c != null && isChannel(chan)) {
                    c.part(chan);
                    c.join(chan);
                }
            }
            case "back" -> {
                IrcClient c = liveClient();
                if (c != null) {
                    c.sendRaw("AWAY");
                    appendStatus(feedbackKey(), "You are no longer marked away");
                }
            }
            case "close" -> commandClose();
            case "help" -> commandHelp();
            default ->
                appendStatus(feedbackKey(), "Unknown command: /" + cmd
                        + " — /help lists everything");
        }
    }

    /** {@code /list [pattern]}: arm a collector, ask, browse on 323. */
    private void commandList(String args) {
        IrcClient c = liveClient();
        if (c == null) {
            return;
        }
        listCollectors.put(activeNetwork(), new ChannelListCollector());
        c.sendRaw(args.isEmpty() ? "LIST" : "LIST " + args);
        appendStatus(feedbackKey(), "Fetching channel list…");
    }

    /** {@code /ignore} lists; {@code /ignore nick} adds + applies live. */
    private void commandIgnore(String args) {
        IrcConfig config = IrcConfig.getDefault();
        String network = activeNetwork();
        String statusKey = key(network, "");
        if (args.isEmpty()) {
            List<String> ignored = config.ignoredNicks(network);
            appendStatus(statusKey, ignored.isEmpty()
                    ? "Nobody is ignored on " + network
                    : "Ignored on " + network + ": " + String.join(", ", ignored));
            return;
        }
        String nick = args.split(" ")[0];
        config.addIgnored(network, nick);
        applyIgnores(network);
        appendStatus(statusKey, "Ignoring " + nick
                + " — messages are dropped silently (/unignore " + nick + " to undo)");
    }

    private void commandUnignore(String args) {
        if (args.isEmpty()) {
            return;
        }
        IrcConfig config = IrcConfig.getDefault();
        String network = activeNetwork();
        String nick = args.split(" ")[0];
        config.removeIgnored(network, nick);
        applyIgnores(network);
        appendStatus(key(network, ""), "No longer ignoring " + nick);
    }

    private void applyIgnores(String network) {
        IrcClient client = SESSIONS.get(network);
        if (client != null) {
            client.setIgnoredNicks(IrcConfig.getDefault().ignoredNicks(network));
        }
    }

    /** {@code /log [on|off]}: session toggle, persisted as the global default. */
    private void commandLog(String args) {
        String statusKey = key(activeNetwork(), "");
        switch (args.toLowerCase(Locale.ROOT)) {
            case "on" -> {
                logger.setEnabled(true);
                IrcConfig.getDefault().setLoggingEnabled(true);
                appendStatus(statusKey, "Logging ON → " + logger.root());
            }
            case "off" -> {
                logger.setEnabled(false);
                IrcConfig.getDefault().setLoggingEnabled(false);
                appendStatus(statusKey, "Logging OFF");
            }
            default ->
                appendStatus(statusKey, "Logging is " + (logger.isEnabled() ? "ON" : "OFF")
                        + " — files under " + logger.root()
                        + " (services queries are never logged)");
        }
    }

    /** The smart filter's verdict for one presence line (self is the caller's call). */
    private boolean showPresence(String network, String channel, String nick) {
        return !smartFilterOn || !isChannel(channel)
                || smartFilter.shouldShow(network, channel, nick);
    }

    /** Ctrl+J: hop to the next buffer with a mention, else plain unread. */
    private void jumpToActivity() {
        List<String> order = new ArrayList<>();
        java.util.Enumeration<?> en =
                ((DefaultMutableTreeNode) treeModel.getRoot()).preorderEnumeration();
        while (en.hasMoreElements()) {
            Object user = ((DefaultMutableTreeNode) en.nextElement()).getUserObject();
            if (user instanceof TargetRef ref) {
                order.add(key(ref.network(), ref.target()));
            }
        }
        Hotlist.pick(order, mentions, unread).ifPresent(k -> {
            int sep = k.indexOf('\u0000');
            selectTarget(k.substring(0, sep), k.substring(sep + 1));
        });
    }

    /**
     * {@code /filter} — the smart toggle plus WeeChat's custom regex
     * filters (v2.10.0): {@code add <name> <#channel|*> <regex>},
     * {@code del <name>}, {@code enable|disable <name>}, {@code list};
     * {@code [smart] on|off} keeps its v2.2.0 meaning and bare
     * {@code /filter} reports everything. Custom filters persist in the
     * config and hide matching chat lines with NO signal — the
     * engine-side log always keeps the full record.
     */
    private void commandFilter(String args) {
        // feedback lands WHERE THE COMMAND WAS TYPED (v2.34.4, the
        // Libera walk: a malformed /filter add answered into the
        // network-status transcript while the user watched a channel —
        // spoken, but in the wrong room reads as silence; /lastlog has
        // always answered in place). Network status stays the fallback
        // when nothing is selected.
        String statusKey = feedbackKey();
        IrcConfig config = IrcConfig.getDefault();
        String[] parts = args.trim().split("\\s+", 2);
        String sub = parts[0].toLowerCase(Locale.ROOT);
        String rest = parts.length > 1 ? parts[1].trim() : "";
        switch (sub) {
            case "add" -> {
                String[] a = rest.split("\\s+", 3);
                if (a.length < 3) {
                    appendStatus(statusKey, "Usage: /filter add <name> <#channel|*> <regex>");
                    return;
                }
                String problem = textFilters.add(a[0], a[1], a[2], true);
                if (problem != null) {
                    appendStatus(statusKey, "Filter refused: " + problem);
                    return;
                }
                TextFilters.Filter f = textFilters.list().get(textFilters.list().size() - 1);
                config.saveTextFilter(f.name(), f.stringForm());
                appendStatus(statusKey, "Filter '" + f.name() + "' hides lines matching /"
                        + f.regex() + "/ in " + ("*".equals(f.scope()) ? "every channel" : f.scope())
                        + " (logs keep everything; /filter del " + f.name() + " removes it)");
            }
            case "del" -> {
                if (textFilters.remove(rest)) {
                    config.removeTextFilter(rest);
                    appendStatus(statusKey, "Filter removed: " + rest.toLowerCase(Locale.ROOT));
                } else {
                    appendStatus(statusKey, "No filter named '" + rest + "' (/filter list)");
                }
            }
            case "enable", "disable" -> {
                boolean on = sub.equals("enable");
                if (textFilters.setEnabled(rest, on)) {
                    textFilters.list().stream()
                            .filter(f -> f.name().equalsIgnoreCase(rest))
                            .findFirst()
                            .ifPresent(f -> config.saveTextFilter(f.name(), f.stringForm()));
                    appendStatus(statusKey, "Filter '" + rest.toLowerCase(Locale.ROOT)
                            + "' " + (on ? "enabled" : "disabled"));
                } else {
                    appendStatus(statusKey, "No filter named '" + rest + "' (/filter list)");
                }
            }
            case "list" -> {
                var all = textFilters.list();
                if (all.isEmpty()) {
                    appendStatus(statusKey, "No custom filters: /filter add <name> <#channel|*> <regex>");
                } else {
                    for (TextFilters.Filter f : all) {
                        appendStatus(statusKey, (f.enabled() ? "[on]  " : "[off] ")
                                + f.name() + "  " + f.scope() + "  /" + f.regex() + "/");
                    }
                }
            }
            default -> {
                String a = args.trim().toLowerCase(Locale.ROOT).replace("smart", "").trim();
                if (a.equals("on") || a.equals("off")) {
                    smartFilterOn = a.equals("on");
                    IrcConfig.getDefault().setSmartFilterEnabled(smartFilterOn);
                }
                appendStatus(statusKey, "Smart join/part/quit filter is "
                        + (smartFilterOn ? "ON" : "OFF") + " (/filter smart on|off). Joins, parts,"
                        + " quits, and renames from nicks silent for 5 minutes are hidden;"
                        + " kicks and your own lines always show, and logs keep everything."
                        + " Custom filters: " + textFilters.list().size()
                        + " (/filter add|del|enable|disable|list).");
            }
        }
    }

    /**
     * {@code /lastlog <text> [count]} — WeeChat's scrollback search: the
     * last matching lines of the ACTIVE transcript (case-insensitive
     * substring, default 20) printed as a dim block. The block writes
     * the document DIRECTLY like the gap marker (v1.344.0): a search
     * result is bookkeeping, not a message — no unread, no mention.
     */
    private void commandLastlog(String args) {
        String[] a = args.trim().split("\\s+");
        if (a.length == 0 || a[0].isEmpty()) {
            appendStatus(feedbackKey(), "Usage: /lastlog <text> [count]");
            return;
        }
        int limit = 20;
        String pattern = args.trim();
        if (a.length > 1) {
            try {
                limit = Integer.parseInt(a[a.length - 1]);
                pattern = args.trim().substring(0, args.trim().length()
                        - a[a.length - 1].length()).trim();
            } catch (NumberFormatException notACount) {
                // the whole argument string is the pattern
            }
        }
        StyledDocument doc = docForKey(activeKey);
        String scrollback;
        try {
            scrollback = doc.getText(0, doc.getLength());
        } catch (BadLocationException ex) {
            return; // reading [0, length) cannot be out of bounds
        }
        java.util.List<String> hits = TextFilters.lastlog(scrollback, pattern, limit);
        SimpleAttributeSet dim = attrs(new Color(0x88, 0x88, 0x88), false, true, false);
        try {
            doc.insertString(doc.getLength(),
                    "— lastlog: " + hits.size() + " match" + (hits.size() == 1 ? "" : "es")
                    + " for \"" + pattern + "\" —\n", dim);
            for (String hit : hits) {
                doc.insertString(doc.getLength(), "  " + hit + "\n", dim);
            }
            trimTranscript(doc);
        } catch (BadLocationException ex) {
            // appending at getLength() cannot be out of bounds
        }
        transcript.setCaretPosition(doc.getLength());
    }

    /** {@code /alias} lists; {@code /alias name cmd} defines; {@code /alias -name} removes. */
    private void commandAlias(String args) {
        IrcConfig config = IrcConfig.getDefault();
        String statusKey = key(activeNetwork(), "");
        if (args.isEmpty()) {
            var all = config.aliases();
            if (all.isEmpty()) {
                appendStatus(statusKey, "No aliases yet: /alias name command defines one");
            } else {
                all.forEach((n, e) -> appendStatus(statusKey, "/" + n + " = /" + e));
            }
            return;
        }
        if (args.startsWith("-")) {
            String name = args.substring(1).trim().toLowerCase(Locale.ROOT);
            config.removeAlias(name);
            appendStatus(statusKey, "Alias removed: /" + name);
            return;
        }
        int sp2 = args.indexOf(' ');
        if (sp2 < 0) {
            appendStatus(statusKey,
                    "Usage: /alias name command, /alias -name removes, /alias lists");
            return;
        }
        String name = args.substring(0, sp2).toLowerCase(Locale.ROOT).replaceFirst("^/", "");
        String body = args.substring(sp2 + 1).trim().replaceFirst("^/", "");
        if (!name.matches("[a-z0-9]+")) {
            appendStatus(statusKey, "Alias names are letters and digits only");
            return;
        }
        if (BUILTIN_COMMANDS.contains(name)) {
            appendStatus(statusKey, "/" + name + " is built in; an alias may not shadow it");
            return;
        }
        config.saveAlias(name, body);
        appendStatus(statusKey, "/" + name + " = /" + body);
    }

    /** {@code /op nick...} and friends: one batched MODE line via OpModes. */
    private void commandOpish(String cmd, String args) {
        IrcClient c = liveClient();
        String chan = activeTarget();
        if (c == null || !isChannel(chan) || args.isEmpty()) {
            return;
        }
        boolean grant = !cmd.startsWith("de");
        char flag = cmd.endsWith("voice") ? 'v' : 'o';
        c.sendRaw(OpModes.mode(chan, grant, flag, List.of(args.trim().split("\\s+"))));
    }

    /** {@code /close}: part a channel, drop the tab, land on network status. */
    private void commandClose() {
        TargetRef ref = selectedRef();
        if (ref == null || ref.target().isEmpty()) {
            appendStatus(feedbackKey(), "/close closes a channel or query tab");
            return;
        }
        String network = ref.network();
        String target = ref.target();
        IrcClient c = SESSIONS.get(network);
        if (isChannel(target) && c != null) {
            c.part(target);
        }
        String k = key(network, target);
        DefaultMutableTreeNode node = targetNodes.remove(k);
        if (node != null) {
            treeModel.removeNodeFromParent(node);
        }
        docs.remove(k);
        nickLists.remove(k);
        topics.remove(k);
        unread.remove(k);
        mentions.remove(k);
        selectTarget(network, "");
    }

    private void commandHelp() {
        String statusKey = key(activeNetwork(), "");
        String[] lines = {
            "Commands:",
            "  /connect [host [port]] — connect the selected (or an ad-hoc) network",
            "  /join #chan — join a channel      /part [#chan] — leave one",
            "  /msg nick text — private message  /query nick — open a query tab",
            "  /me does something — action       /notice target text — send a NOTICE",
            "  /nick newnick — change nickname   /topic [text] — show or set the topic",
            "  /whois nick — who is that (card)  /ctcp nick VERSION|PING [arg]",
            "  /list [pattern] — browse channels (double-click to join)",
            "  /ignore [nick] — list or add      /unignore nick — remove",
            "  /away [message] — mark away       /back — clear away",
            "  /kick nick [reason] · /mode [target] flags · /invite nick [#chan]",
            "  /op /deop /voice /devoice nick… — one batched MODE line",
            "  /ban /unban nick-or-mask — a bare nick bans as nick!*@*",
            "  /filter smart on|off — hide join/part/quit noise from silent nicks",
            "  /filter add name #chan|* regex — hide matching lines (del/enable/disable/list)",
            "  /lastlog text [count] — the last matching scrollback lines, in place",
            "  /alias name command… — your own commands (/alias lists, -name removes)",
            "  /clear — empty this transcript    /close — close this tab",
            "  /cycle — part and rejoin          /log [on|off] — logging (~/.nmox/irc-logs)",
            "  /raw LINE — send a raw IRC line   /quit [message] — disconnect for good",
            "Tab completes nicks · Up/Down recall input · Ctrl+U clears the line",
            "Ctrl+J jumps to the next mention, then the next unread buffer",
            "⌘F finds in the transcript (⌘F again closes)"
        };
        for (String line : lines) {
            appendStatus(statusKey, line);
        }
    }

    /** {@code /connect} with no args dials the selected network; with args, an ad-hoc host. */
    private void commandConnect(String args) {
        if (args.isEmpty()) {
            connectNetwork(activeNetwork());
            return;
        }
        String[] parts = args.split(" ");
        String host = parts[0];
        int port = 6697;
        if (parts.length > 1) {
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ex) {
                appendStatus(feedbackKey(), "Not a port: " + parts[1]);
                return;
            }
        }
        IrcConfig config = IrcConfig.getDefault();
        IrcConfig.Network def = config.network(config.lastSelected());
        String nick = def == null ? "nmox-user" : def.nick();
        config.save(new IrcConfig.Network(host, host, port, port == 6697, nick, List.of()));
        ensureNetworkNode(host);
        selectNetwork(host);
        connectNetwork(host);
    }

    /** Sends plain text (or a {@code /me} action) to the selected channel/query. */
    private void sayToActive(String text, boolean action) {
        String target = activeTarget();
        if (target.isEmpty()) {
            appendStatus(feedbackKey(), "Pick a channel or query first (or /join one)");
            return;
        }
        IrcClient c = liveClient();
        if (c == null) {
            return;
        }
        c.privmsg(target, action ? Ctcp.action(text) : text);
        // echo-message: when the cap is active the server echoes our own
        // line back and THAT renders — a local echo would double it.
        // The custom /filter verdict applies HERE too (v2.10.2): whether
        // your own matching line hides must not depend on a server
        // capability you cannot see; the log keeps it either way.
        if (!c.capEnabled("echo-message")) {
            if (!textFilters.hides(target, "<" + c.currentNick() + "> " + text)) {
                appendChat(key(activeNetwork(), target), c.currentNick(), text, action);
            }
            if (action) {
                logger.action(activeNetwork(), target, c.currentNick(), text);
            } else {
                logger.chat(activeNetwork(), target, c.currentNick(), text);
            }
        }
    }
}
