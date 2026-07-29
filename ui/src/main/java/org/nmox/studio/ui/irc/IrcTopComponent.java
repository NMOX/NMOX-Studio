package org.nmox.studio.ui.irc;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
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
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.nmox.studio.ui.irc.engine.IrcClient;
import org.nmox.studio.ui.irc.engine.IrcConfig;
import org.nmox.studio.ui.irc.protocol.Ctcp;
import org.nmox.studio.ui.irc.protocol.IrcMessage;
import org.nmox.studio.ui.irc.protocol.MircFormat;
import org.nmox.studio.ui.irc.protocol.Numerics;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

/**
 * The IRC client window (⌥⌘3, v1.204.0): networks and channels in a
 * tree on the left, a styled transcript in the middle (timestamps,
 * colored nicks, mIRC formatting, {@code /me} actions), the channel's
 * nick list on the right, topic on top, input line + Connect button on
 * the bottom. A real chat client, docked like every other studio tab.
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
 *     touched.</li>
 * <li><b>Connections outlive the window</b> — clients live in a static
 *     registry; closing the tab detaches this window's listeners
 *     (symmetry law) but leaves the chat connected, and a reopen
 *     re-attaches without double-delivery (one bridge per network per
 *     window, plus the engine's equality-guarded add).</li>
 * <li><b>Secrets</b> — NickServ passwords ride the OS keychain via
 *     {@code IrcSecrets}; nothing here echoes one.</li>
 * </ul>
 */
@TopComponent.Description(preferredID = "IrcTopComponent",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS)
@TopComponent.Registration(mode = "editor", openAtStartup = false, position = 356)
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

    private String activeKey;

    public IrcTopComponent() {
        // zero boot cost: name and tooltip only — see componentOpened
        setName(Bundle.CTL_IrcTopComponent());
        setToolTipText(Bundle.HINT_IrcTopComponent());
    }

    // ------------------------------------------------------------------ UI

    @Override
    protected void componentOpened() {
        if (!built) {
            built = true;
            buildUi();
            IrcConfig config = IrcConfig.getDefault();
            config.ensureDefaults();
            for (IrcConfig.Network n : config.networks()) {
                ensureNetworkNode(n.name());
            }
            selectNetwork(config.lastSelected());
        }
        // re-attach to whatever is still connected (reopen case): one
        // bridge per network per window, so no double-delivery
        for (Map.Entry<String, IrcClient> e : SESSIONS.entrySet()) {
            attachBridge(e.getKey(), e.getValue());
            ensureNetworkNode(e.getKey());
            for (String chan : e.getValue().joinedChannels()) {
                ensureTargetNode(e.getKey(), chan);
            }
        }
        refreshConnectButton();
    }

    @Override
    protected void componentClosed() {
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
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new UnreadBoldRenderer());
        tree.addTreeSelectionListener(e -> onTreeSelection());

        transcript = new JTextPane();
        transcript.setEditable(false);
        transcript.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

        nickList = new JList<>(nickModel);
        nickList.setPrototypeCellValue("@a-rather-long-nickname");

        topicLabel = new JLabel(" ");
        topicLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        input = new JTextField();
        input.addActionListener(e -> onInput());
        connectButton = new JButton("Connect");
        connectButton.addActionListener(e -> onConnectButton());

        JPanel bottom = new JPanel(new BorderLayout(4, 0));
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(connectButton, BorderLayout.EAST);

        JPanel center = new JPanel(new BorderLayout());
        center.add(topicLabel, BorderLayout.NORTH);
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
    }

    /** Bolds tree rows with unread activity (the simple unread marker). */
    private final class UnreadBoldRenderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree t, Object value,
                boolean sel, boolean expanded, boolean leaf, int row, boolean focus) {
            Component c = super.getTreeCellRendererComponent(t, value, sel, expanded, leaf, row, focus);
            Object user = value instanceof DefaultMutableTreeNode n ? n.getUserObject() : null;
            boolean bold = false;
            if (user instanceof TargetRef ref) {
                setText(ref.target().isEmpty() ? ref.network() : ref.target());
                bold = unread.contains(key(ref.network(), ref.target()));
            }
            c.setFont(c.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN));
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
        transcript.setDocument(docForKey(activeKey));
        transcript.setCaretPosition(transcript.getDocument().getLength());
        topicLabel.setText(topics.getOrDefault(activeKey, " "));
        rebuildNickModel();
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
                .comparingInt((String s) -> prefixRank(s))
                .thenComparing(s -> stripPrefix(s).toLowerCase(Locale.ROOT)));
        for (String n : sorted) {
            nickModel.addElement(n);
        }
    }

    private static int prefixRank(String display) {
        if (display.isEmpty()) {
            return 3;
        }
        return switch (display.charAt(0)) {
            case '~', '&', '@' -> 0;   // ops (owner/admin fold in with ops)
            case '%' -> 1;             // half-op
            case '+' -> 2;             // voice
            default -> 3;
        };
    }

    private static String stripPrefix(String display) {
        int i = 0;
        while (i < display.length() && "~&@%+".indexOf(display.charAt(i)) >= 0) {
            i++;
        }
        return display.substring(i);
    }

    // ------------------------------------------------------ transcript

    private StyledDocument docForKey(String k) {
        return docs.computeIfAbsent(k, x -> new DefaultStyledDocument());
    }

    private void append(String k, List<Object[]> runs) {
        StyledDocument doc = docForKey(k);
        try {
            for (Object[] run : runs) {
                doc.insertString(doc.getLength(), (String) run[0], (SimpleAttributeSet) run[1]);
            }
            doc.insertString(doc.getLength(), "\n", null);
        } catch (BadLocationException ex) {
            // appending at getLength() cannot be out of bounds
        }
        if (k.equals(activeKey)) {
            transcript.setCaretPosition(doc.getLength());
        } else {
            unread.add(k);
            tree.repaint();
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

    private List<Object[]> stampedRuns() {
        List<Object[]> runs = new ArrayList<>(6);
        runs.add(new Object[] {"[" + STAMP.format(LocalTime.now()) + "] ",
            attrs(STAMP_COLOR, false, false, false)});
        return runs;
    }

    /** A chat line: {@code [HH:mm] <nick> text} or {@code [HH:mm] * nick text}. */
    private void appendChat(String k, String nick, String body, boolean action) {
        List<Object[]> runs = stampedRuns();
        if (action) {
            runs.add(new Object[] {"* " + nick + " ", attrs(nickColor(nick), false, true, false)});
        } else {
            runs.add(new Object[] {"<" + nick + "> ", attrs(nickColor(nick), true, false, false)});
        }
        for (MircFormat.Span span : MircFormat.parse(body)) {
            if (span.text().isEmpty()) {
                continue;
            }
            SimpleAttributeSet a = attrs(mircColor(span.foreground()),
                    span.bold(), span.italic() || action, span.underline());
            Color bg = mircColor(span.background());
            if (bg != null) {
                StyleConstants.setBackground(a, bg);
            }
            runs.add(new Object[] {span.text(), a});
        }
        append(k, runs);
    }

    /** A grey status line ({@code [HH:mm] text}). */
    private void appendStatus(String k, String text) {
        List<Object[]> runs = stampedRuns();
        runs.add(new Object[] {MircFormat.stripToText(text), attrs(STATUS_COLOR, false, true, false)});
        append(k, runs);
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
                String k = isChannel(target)
                        ? key(network, target)
                        : key(network, sender); // a query lands under the sender
                if (!isChannel(target)) {
                    ensureTargetNode(network, sender);
                } else {
                    ensureTargetNode(network, target);
                }
                appendChat(k, sender, body, action);
            }
            case "NOTICE" -> {
                String sender = msg.nick() == null ? network : msg.nick();
                String target = msg.param(0);
                String body = msg.trailing() == null ? "" : msg.trailing();
                String k = isChannel(target) ? key(network, target) : statusKey;
                appendStatus(k, "-" + sender + "- " + body);
            }
            case "JOIN" -> {
                String chan = msg.trailing() != null ? msg.trailing() : msg.param(0);
                String who = msg.nick() == null ? "?" : msg.nick();
                String k = key(network, chan);
                if (who.equalsIgnoreCase(me)) {
                    ensureTargetNode(network, chan);
                    selectTarget(network, chan);
                    appendStatus(k, "You joined " + chan);
                } else {
                    nickLists.computeIfAbsent(k, x -> new HashMap<>())
                            .put(who.toLowerCase(Locale.ROOT), who);
                    if (k.equals(activeKey)) {
                        rebuildNickModel();
                    }
                    appendStatus(k, "→ " + who + " joined");
                }
            }
            case "PART" -> {
                String chan = msg.param(0);
                String who = msg.nick() == null ? "?" : msg.nick();
                String k = key(network, chan);
                removeNick(k, who);
                appendStatus(k, who.equalsIgnoreCase(me)
                        ? "You left " + chan
                        : "← " + who + " left");
            }
            case "KICK" -> {
                String chan = msg.param(0);
                String victim = msg.param(1);
                String k = key(network, chan);
                removeNick(k, victim);
                appendStatus(k, victim + " was kicked by "
                        + (msg.nick() == null ? "?" : msg.nick()));
            }
            case "QUIT" -> {
                String who = msg.nick() == null ? "?" : msg.nick();
                String lowerWho = who.toLowerCase(Locale.ROOT);
                for (Map.Entry<String, Map<String, String>> e : nickLists.entrySet()) {
                    if (e.getKey().startsWith(network + '\u0000')
                            && e.getValue().remove(lowerWho) != null) {
                        appendStatus(e.getKey(), "← " + who + " quit");
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
                for (Map.Entry<String, Map<String, String>> e : nickLists.entrySet()) {
                    if (!e.getKey().startsWith(network + '\u0000')) {
                        continue;
                    }
                    String display = e.getValue().remove(lowerWho);
                    if (display != null) {
                        String prefix = display.substring(0, display.length() - who.length());
                        e.getValue().put(now.toLowerCase(Locale.ROOT), prefix + now);
                        appendStatus(e.getKey(), who + " is now known as " + now);
                        if (e.getKey().equals(activeKey)) {
                            rebuildNickModel();
                        }
                    }
                }
            }
            case "TOPIC" -> {
                String chan = msg.param(0);
                String k = key(network, chan);
                String topicText = msg.trailing() == null ? "" : msg.trailing();
                topics.put(k, MircFormat.stripToText(topicText));
                if (k.equals(activeKey)) {
                    topicLabel.setText(topics.get(k));
                }
                appendStatus(k, (msg.nick() == null ? "?" : msg.nick())
                        + " set the topic: " + topicText);
            }
            default ->
                handleNumeric(network, statusKey, msg);
        }
    }

    private void handleNumeric(String network, String statusKey, IrcMessage msg) {
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
                        pending.add(name);
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
                        nicks.put(stripPrefix(display).toLowerCase(Locale.ROOT), display);
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
                    topicLabel.setText(topics.get(k));
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
                // PING/PONG/MODE and friends: no rendering needed
            }
        }
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
            appendStatus(key(activeNetwork(), ""), "Not connected — press Connect or use /connect");
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
        IrcConfig.Network saved = IrcConfig.getDefault().network(network);
        if (saved == null) {
            appendStatus(key(network, ""), "No saved network named " + network);
            return;
        }
        IrcClient client = SESSIONS.get(network);
        if (client == null || client.state() == IrcClient.State.CLOSED) {
            client = new IrcClient(new IrcClient.Profile(saved.name(), saved.host(),
                    saved.port(), saved.tls(), saved.nick()));
            SESSIONS.put(network, client);
        }
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
        if (!raw.startsWith("/")) {
            sayToActive(raw, false);
            return;
        }
        int sp = raw.indexOf(' ');
        String cmd = (sp < 0 ? raw.substring(1) : raw.substring(1, sp)).toLowerCase(Locale.ROOT);
        String args = sp < 0 ? "" : raw.substring(sp + 1).trim();
        switch (cmd) {
            case "connect" -> commandConnect(args);
            case "join" -> {
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
                        appendChat(key(activeNetwork(), to), c.currentNick(), text, false);
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
            default ->
                appendStatus(key(activeNetwork(), ""), "Unknown command: /" + cmd
                        + " (try /connect /join /part /msg /query /me /nick /topic /whois /quit /raw)");
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
                appendStatus(key(activeNetwork(), ""), "Not a port: " + parts[1]);
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
            appendStatus(key(activeNetwork(), ""), "Pick a channel or query first (or /join one)");
            return;
        }
        IrcClient c = liveClient();
        if (c == null) {
            return;
        }
        c.privmsg(target, action ? Ctcp.action(text) : text);
        appendChat(key(activeNetwork(), target), c.currentNick(), text, action);
    }
}
