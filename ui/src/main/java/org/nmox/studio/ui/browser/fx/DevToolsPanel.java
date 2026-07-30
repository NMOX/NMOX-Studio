package org.nmox.studio.ui.browser.fx;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import org.nmox.studio.ui.browser.devtools.ConsoleModel;
import org.nmox.studio.ui.browser.devtools.DevScripts;
import org.nmox.studio.ui.browser.devtools.DomSnapshotParser;
import org.nmox.studio.ui.browser.devtools.DomSnapshotParser.DomNode;
import org.nmox.studio.ui.browser.devtools.NetworkModel;
import org.nmox.studio.ui.browser.devtools.ScriptRunner;
import org.nmox.studio.ui.browser.devtools.StorageSnapshotParser;
import org.nmox.studio.ui.browser.devtools.StyleSummary;
import org.nmox.studio.ui.browser.devtools.VueSnapshotParser;
import org.nmox.studio.ui.browser.devtools.VueSnapshotParser.VueNode;
import org.nmox.studio.ui.browser.devtools.VueSnapshotParser.VueTree;
import org.openide.util.RequestProcessor;

/**
 * The Browser's developer-tools pane: Console / DOM / Network /
 * Storage / Vue tabs in a collapsible bottom split. Pure Swing shell —
 * ALL logic lives in the tested devtools cores (models, parsers,
 * scripts); this class only renders them and talks to the page
 * through the {@link ScriptRunner} seam (so it has zero JavaFX
 * imports and never touches the engine directly).
 *
 * <p>Threading: everything here runs on the EDT. Snapshot JSON is
 * parsed on a named RequestProcessor lane, results applied back on
 * the EDT. The models fire change callbacks on the EDT (the bridge
 * marshals) and the lists coalesce re-renders with a short timer so a
 * console storm cannot pin the paint thread.
 */
public final class DevToolsPanel extends JPanel {

    private static final RequestProcessor RP = new RequestProcessor("Browser DevTools", 1);

    private final ConsoleModel console;
    private final NetworkModel network;
    private final ScriptRunner runner;

    // Console tab
    private final javax.swing.DefaultListModel<ConsoleModel.Entry> consoleList = new javax.swing.DefaultListModel<>();
    private final JLabel consoleDropped = new JLabel();
    private final javax.swing.Timer consoleSync;

    // Network tab
    private final DefaultTableModel networkTable = readOnlyTable("Method", "URL", "Status", "OK", "ms", "Size");
    private final JLabel networkDropped = new JLabel();
    private final javax.swing.Timer networkSync;

    // DOM tab
    private final DefaultTreeModel domTree = new DefaultTreeModel(new DefaultMutableTreeNode("(press Refresh)"));
    private final JTextArea domDetails = readOnlyArea();

    // Storage tab
    private final DefaultTableModel storageTable = readOnlyTable("Area", "Key", "Value");

    // Vue tab
    private final DefaultTreeModel vueTree = new DefaultTreeModel(new DefaultMutableTreeNode("(press Refresh)"));
    private final DefaultTableModel vueDetails = readOnlyTable("Kind", "Name", "Value");
    private final JLabel vueStatus = new JLabel(" ");

    public DevToolsPanel(ConsoleModel console, NetworkModel network, ScriptRunner runner) {
        super(new BorderLayout());
        this.console = console;
        this.network = network;
        this.runner = runner;
        consoleSync = coalesced(this::syncConsole);
        networkSync = coalesced(this::syncNetwork);
        console.setListener(consoleSync::restart);
        network.setListener(networkSync::restart);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Console", consoleTab());
        tabs.addTab("DOM", domTab());
        tabs.addTab("Network", networkTab());
        tabs.addTab("Storage", storageTab());
        tabs.addTab("Vue", vueTab());
        add(tabs, BorderLayout.CENTER);
    }

    // ---- Console -------------------------------------------------------

    private JPanel consoleTab() {
        JPanel panel = new JPanel(new BorderLayout());
        JList<ConsoleModel.Entry> list = new JList<>(consoleList);
        list.setCellRenderer(new ConsoleRenderer());
        panel.add(consoleDropped, BorderLayout.NORTH);
        consoleDropped.setVisible(false);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout(4, 0));
        JTextField repl = new JTextField();
        repl.putClientProperty("JTextField.placeholderText", "Run JavaScript in the page…");
        repl.addActionListener(e -> {
            String expr = repl.getText();
            if (expr == null || expr.isBlank()) {
                return;
            }
            repl.setText("");
            console.add("log", "> " + expr, System.currentTimeMillis());
            runner.run(DevScripts.evalScript(expr),
                    result -> {
                        if (result.startsWith(DevScripts.EVAL_ERROR_MARKER)) {
                            console.add("error", result.substring(DevScripts.EVAL_ERROR_MARKER.length()),
                                    System.currentTimeMillis());
                        } else {
                            console.add("result", result, System.currentTimeMillis());
                        }
                    },
                    error -> console.add("error", error, System.currentTimeMillis()));
        });
        JButton clear = new JButton("Clear");
        clear.addActionListener(e -> console.clear());
        south.add(repl, BorderLayout.CENTER);
        south.add(clear, BorderLayout.EAST);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private void syncConsole() {
        consoleList.clear();
        for (ConsoleModel.Entry e : console.entries()) {
            consoleList.addElement(e);
        }
        long dropped = console.droppedCount();
        consoleDropped.setText("  " + dropped + " older entries dropped (cap " + ConsoleModel.CAP + ")");
        consoleDropped.setVisible(dropped > 0);
    }

    private static final class ConsoleRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            if (value instanceof ConsoleModel.Entry e) {
                setText(String.format("[%tT] %-6s %s", e.atMillis(), e.level(),
                        e.text().replace('\n', ' ')));
                if (!selected) {
                    switch (e.level()) {
                        case "error": setForeground(new Color(205, 92, 92)); break;
                        case "warn": setForeground(new Color(205, 150, 60)); break;
                        case "debug": setForeground(Color.GRAY); break;
                        case "result": setForeground(new Color(90, 160, 90)); break;
                        default: break;
                    }
                }
                setFont(new Font(Font.MONOSPACED, Font.PLAIN, getFont().getSize()));
            }
            return this;
        }
    }

    // ---- DOM -----------------------------------------------------------

    private JPanel domTab() {
        JPanel panel = new JPanel(new BorderLayout());
        JTree tree = new JTree(domTree);
        tree.setRootVisible(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshDom());
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        bar.add(refresh);
        bar.add(new JLabel("Select a node to highlight it in the page"));
        panel.add(bar, BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(tree), new JScrollPane(domDetails));
        split.setResizeWeight(0.6);
        panel.add(split, BorderLayout.CENTER);
        tree.addTreeSelectionListener(e -> {
            DomNode node = selectedDom(tree);
            if (node == null || node.isPlaceholder()) {
                return;
            }
            runner.run(DevScripts.highlight(node.path), r -> { }, err -> { });
            runner.run(DevScripts.computedStyle(node.path),
                    css -> domDetails.setText(domDetailsText(node, css)),
                    err -> domDetails.setText(domDetailsText(node, "{}")));
        });
        return panel;
    }

    private static DomNode selectedDom(JTree tree) {
        Object last = tree.getLastSelectedPathComponent();
        if (last instanceof DefaultMutableTreeNode n && n.getUserObject() instanceof DomNode dn) {
            return dn;
        }
        return null;
    }

    private static String domDetailsText(DomNode node, String styleJson) {
        StringBuilder sb = new StringBuilder();
        sb.append('<').append(node.tag).append(">\n");
        if (!node.id.isEmpty()) {
            sb.append("id: ").append(node.id).append('\n');
        }
        if (!node.classes.isEmpty()) {
            sb.append("class: ").append(node.classes).append('\n');
        }
        for (String a : node.attrs) {
            sb.append(a).append('\n');
        }
        Map<String, String> style = StyleSummary.parse(styleJson);
        if (!style.isEmpty()) {
            sb.append("\nComputed style:\n");
            for (Map.Entry<String, String> e : style.entrySet()) {
                sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
            }
        }
        return sb.toString();
    }

    private void refreshDom() {
        runner.run(DevScripts.DOM_SNAPSHOT, json -> RP.post(() -> {
            DomNode root = DomSnapshotParser.parse(json);
            DefaultMutableTreeNode swingRoot = toSwing(root);
            SwingUtilities.invokeLater(() -> domTree.setRoot(swingRoot));
        }), err -> domTree.setRoot(new DefaultMutableTreeNode("(no page: " + err + ")")));
    }

    private static DefaultMutableTreeNode toSwing(DomNode node) {
        DefaultMutableTreeNode swing = new DefaultMutableTreeNode(node);
        for (DomNode child : node.children) {
            swing.add(toSwing(child));
        }
        return swing;
    }

    // ---- Network -------------------------------------------------------

    private JPanel networkTab() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton clear = new JButton("Clear");
        clear.addActionListener(e -> network.clear());
        bar.add(clear);
        bar.add(new JLabel("Requests made after DevTools injection (fetch/XHR); bodies not captured (v1)"));
        bar.add(networkDropped);
        networkDropped.setVisible(false);
        panel.add(bar, BorderLayout.NORTH);
        JTable table = new JTable(networkTable);
        table.getColumnModel().getColumn(1).setPreferredWidth(420);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private void syncNetwork() {
        networkTable.setRowCount(0);
        for (NetworkModel.Entry e : network.entries()) {
            networkTable.addRow(new Object[]{e.method(), e.url(),
                e.status() == 0 ? "—" : String.valueOf(e.status()),
                e.ok() ? "ok" : "failed", String.valueOf(e.durationMillis()),
                e.sizeBytes() < 0 ? "?" : String.valueOf(e.sizeBytes())});
        }
        long dropped = network.droppedCount();
        networkDropped.setText(dropped + " older dropped (cap " + NetworkModel.CAP + ")");
        networkDropped.setVisible(dropped > 0);
    }

    // ---- Storage -------------------------------------------------------

    private JPanel storageTab() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshStorage());
        bar.add(refresh);
        bar.add(new JLabel("localStorage · sessionStorage · cookies — read-only (v1)"));
        panel.add(bar, BorderLayout.NORTH);
        JTable table = new JTable(storageTable);
        table.getColumnModel().getColumn(2).setPreferredWidth(420);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private void refreshStorage() {
        runner.run(DevScripts.STORAGE_SNAPSHOT, json -> RP.post(() -> {
            List<StorageSnapshotParser.Row> rows = StorageSnapshotParser.parse(json);
            SwingUtilities.invokeLater(() -> {
                storageTable.setRowCount(0);
                for (StorageSnapshotParser.Row r : rows) {
                    storageTable.addRow(new Object[]{r.area(), r.key(), r.value()});
                }
            });
        }), err -> storageTable.setRowCount(0));
    }

    // ---- Vue -----------------------------------------------------------

    private JPanel vueTab() {
        JPanel panel = new JPanel(new BorderLayout());
        JTree tree = new JTree(vueTree);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshVue());
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        bar.add(refresh);
        bar.add(vueStatus);
        panel.add(bar, BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(tree), new JScrollPane(new JTable(vueDetails)));
        split.setResizeWeight(0.5);
        panel.add(split, BorderLayout.CENTER);
        tree.addTreeSelectionListener(e -> {
            Object last = tree.getLastSelectedPathComponent();
            if (last instanceof DefaultMutableTreeNode n && n.getUserObject() instanceof VueNode vn) {
                vueDetails.setRowCount(0);
                for (Map.Entry<String, String> p : vn.props.entrySet()) {
                    vueDetails.addRow(new Object[]{"prop", p.getKey(), p.getValue()});
                }
                for (Map.Entry<String, String> s : vn.state.entrySet()) {
                    vueDetails.addRow(new Object[]{"state", s.getKey(), s.getValue()});
                }
                if (!vn.domPath.isEmpty()) {
                    runner.run(DevScripts.highlight(vn.domPath), r -> { }, err -> { });
                }
            }
        });
        return panel;
    }

    private void refreshVue() {
        runner.run(DevScripts.VUE_SNAPSHOT, json -> RP.post(() -> {
            VueTree parsed = VueSnapshotParser.parse(json);
            SwingUtilities.invokeLater(() -> applyVue(parsed));
        }), err -> vueStatus.setText("(no page: " + err + ")"));
    }

    private void applyVue(VueTree parsed) {
        vueDetails.setRowCount(0);
        if (parsed.empty()) {
            vueTree.setRoot(new DefaultMutableTreeNode("(no components)"));
            vueStatus.setText("No Vue detected on this page — Vue 2 and 3 are supported; "
                    + "React/Angular are not inspected (v1)");
            return;
        }
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Vue " + parsed.version + " app");
        int[] count = {0};
        for (VueNode r : parsed.roots) {
            root.add(toSwing(r, count));
        }
        vueTree.setRoot(root);
        vueStatus.setText("Vue " + parsed.version + " — " + count[0] + " component"
                + (count[0] == 1 ? "" : "s"));
    }

    private static DefaultMutableTreeNode toSwing(VueNode node, int[] count) {
        count[0]++;
        DefaultMutableTreeNode swing = new DefaultMutableTreeNode(node);
        for (VueNode child : node.children) {
            swing.add(toSwing(child, count));
        }
        return swing;
    }

    // ---- shared --------------------------------------------------------

    private static javax.swing.Timer coalesced(Runnable body) {
        javax.swing.Timer t = new javax.swing.Timer(80, e -> body.run());
        t.setRepeats(false);
        return t;
    }

    private static DefaultTableModel readOnlyTable(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static JTextArea readOnlyArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        return area;
    }
}
