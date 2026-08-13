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
import javax.swing.JComponent;
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
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import org.nmox.studio.ui.browser.devtools.AngularSnapshotParser;
import org.nmox.studio.ui.browser.devtools.AngularSnapshotParser.NgNode;
import org.nmox.studio.ui.browser.devtools.AngularSnapshotParser.NgTree;
import org.nmox.studio.ui.browser.devtools.ConsoleModel;
import org.nmox.studio.ui.browser.devtools.DevScripts;
import org.nmox.studio.ui.browser.devtools.DomSnapshotParser;
import org.nmox.studio.ui.browser.devtools.DomSnapshotParser.DomNode;
import org.nmox.studio.ui.browser.devtools.NetworkModel;
import org.nmox.studio.ui.browser.devtools.ScriptRunner;
import org.nmox.studio.ui.browser.devtools.StorageSnapshotParser;
import org.nmox.studio.ui.browser.devtools.StyleSummary;
import org.nmox.studio.ui.browser.devtools.SvelteSnapshotParser;
import org.nmox.studio.ui.browser.devtools.VueSnapshotParser;
import org.nmox.studio.ui.browser.devtools.VueSnapshotParser.VueNode;
import org.nmox.studio.ui.browser.devtools.VueSnapshotParser.VueTree;
import org.openide.util.RequestProcessor;

/**
 * The Browser's developer-tools pane: Console / DOM / Network /
 * Storage / Vue / Svelte tabs in a collapsible bottom split. Pure Swing shell —
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
    private final JLabel domStatus = new JLabel(" ");
    private volatile DomNode lastDomRoot;

    // Storage tab
    private final DefaultTableModel storageTable = readOnlyTable("Area", "Key", "Value");

    // Vue tab
    private final DefaultTreeModel vueTree = new DefaultTreeModel(new DefaultMutableTreeNode("(press Refresh)"));
    private final DefaultTableModel vueDetails = readOnlyTable("Kind", "Name", "Value");
    private final JLabel vueStatus = new JLabel(" ");

    // Svelte tab
    private final DefaultTreeModel svelteTree = new DefaultTreeModel(new DefaultMutableTreeNode("(press Refresh)"));
    private final JTextArea svelteDetails = readOnlyArea();
    private final JLabel svelteStatus = new JLabel(" ");

    // Angular tab
    private final DefaultTreeModel ngTree = new DefaultTreeModel(new DefaultMutableTreeNode("(press Refresh)"));
    private final DefaultTableModel ngDetails = readOnlyTable("Kind", "Name", "Value");
    private final JLabel ngStatus = new JLabel(" ");

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
        tabs.addTab("Svelte", svelteTab());
        tabs.addTab("Angular", angularTab());
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
            // expression form first (host-compiled — a CSP without
            // 'unsafe-eval' killed the old window.eval path, v1.276.0);
            // runtime errors come back as the in-page marker, so an
            // error CALLBACK here means the text didn't compile as an
            // expression — retry it as a statement body before giving up
            java.util.function.Consumer<String> render = result -> {
                if (result.startsWith(DevScripts.EVAL_ERROR_MARKER)) {
                    console.add("error", result.substring(DevScripts.EVAL_ERROR_MARKER.length()),
                            System.currentTimeMillis());
                } else {
                    console.add("result", result, System.currentTimeMillis());
                }
            };
            runner.run(DevScripts.evalScript(expr), render,
                    error -> runner.run(DevScripts.statementScript(expr), render,
                            error2 -> console.add("error", error2, System.currentTimeMillis())));
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

    /**
     * Every string in these panes comes from the inspected page: element
     * tag names, Vue component names, Svelte source paths, request URLs,
     * localStorage keys and values. Swing's default renderers are
     * {@link JLabel}s, and a JLabel whose text starts with
     * {@code <html>} RENDERS it — so a page naming a component
     * {@code <html><img src="http://evil/beacon">} would make the IDE's
     * own JVM fetch that URL (a silent outbound beacon, and a reach at
     * local files via {@code file:}). Disabling the HTML view makes
     * every such string display as the literal text it is, by
     * construction rather than by luck of the format string.
     *
     * <p>Applied to the tree/table/list renderers of every DevTools pane;
     * {@code DevToolsHtmlSafetyTest} fails the build if a pane is added
     * without it.
     */
    static void disableHtmlRendering(JComponent renderer) {
        renderer.putClientProperty("html.disable", Boolean.TRUE);
    }

    /** A JTree whose labels never interpret page text as HTML. */
    private static JTree safeTree(javax.swing.tree.TreeModel model) {
        JTree tree = new JTree(model);
        if (tree.getCellRenderer() instanceof JComponent c) {
            disableHtmlRendering(c);
        }
        return tree;
    }

    /** A JTable whose cells never interpret page text as HTML. */
    private static JTable safeTable(javax.swing.table.TableModel model) {
        // PLAIN-TABLE-EXEMPT: this pane predates core.util.PlainTables and
        // carries its own disableHtmlRendering + DevToolsHtmlSafetyTest gate
        // (v1.206.0). The safety is identical (html.disable on the renderer).
        JTable table = new JTable(model);
        DefaultTableCellRenderer plain = new DefaultTableCellRenderer();
        disableHtmlRendering(plain);
        table.setDefaultRenderer(Object.class, plain);
        return table;
    }

    private static final class ConsoleRenderer extends DefaultListCellRenderer {

        ConsoleRenderer() {
            // safe today only because the row format leads with "[%tT]";
            // pin it by construction instead (see disableHtmlRendering)
            disableHtmlRendering(this);
        }

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
        JTree tree = safeTree(domTree);
        tree.setRootVisible(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshDom());
        javax.swing.JToggleButton pick = new javax.swing.JToggleButton("Pick element");
        JButton openSource = new JButton("Open Source");
        JButton editStyle = new JButton("Edit Style…");
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        bar.add(refresh);
        bar.add(pick);
        bar.add(openSource);
        bar.add(editStyle);
        bar.add(domStatus);
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
        // inspect-to-source (v1.357.0): a 300ms poll while pick is armed —
        // the bridge is one-way (page JS can't call into Swing), so the
        // result rides window.__nmoxPickResult like every snapshot does
        javax.swing.Timer pickPoll = new javax.swing.Timer(300, e -> runner.run(DevScripts.PICK_POLL, r -> {
            if (r != null && !r.isBlank()) {
                SwingUtilities.invokeLater(() -> {
                    pick.setSelected(false);
                    onPicked(tree, r);
                });
            }
        }, err -> { }));
        pickPoll.setRepeats(true);
        pick.addActionListener(e -> {
            if (pick.isSelected()) {
                domStatus.setText("Click an element in the page…");
                runner.run(DevScripts.PICK_ARM, r -> { }, err -> {
                    domStatus.setText("Pick failed: " + err);
                    SwingUtilities.invokeLater(() -> pick.setSelected(false));
                });
                pickPoll.start();
            } else {
                pickPoll.stop();
                domStatus.setText(" ");
                runner.run(DevScripts.PICK_CANCEL, r -> { }, err -> { });
            }
        });
        openSource.addActionListener(e -> openSource(selectedDom(tree)));
        editStyle.addActionListener(e -> editStyle(selectedDom(tree)));
        tree.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent me) {
                if (me.getClickCount() == 2) {
                    openSource(selectedDom(tree));
                }
            }
        });
        return panel;
    }

    /**
     * Style write-back (v1.358.0): a small property/value dialog; on
     * OK the tweak is (1) applied INLINE in the page for instant
     * feedback, then (2) written into the source stylesheet — the rule
     * chosen by asking the PAGE which selectors matched the element
     * (cascade order, last match wins), the file resolved only through
     * vouched channels, and every impossible case refused on the
     * status label with its reason.
     */
    private void editStyle(DomNode node) {
        if (node == null || node.isPlaceholder()) {
            domStatus.setText("Select an element first");
            return;
        }
        javax.swing.JComboBox<String> prop = new javax.swing.JComboBox<>(
                StyleSummary.KEYS.toArray(String[]::new));
        prop.setEditable(true);
        JTextField value = new JTextField(18);
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        form.add(new JLabel("Property:"));
        form.add(prop);
        form.add(new JLabel("Value:"));
        form.add(value);
        org.openide.DialogDescriptor dd = new org.openide.DialogDescriptor(
                form, "Edit Style on <" + node.tag + ">");
        if (org.openide.DialogDisplayer.getDefault().notify(dd)
                != org.openide.DialogDescriptor.OK_OPTION) {
            return;
        }
        String property = String.valueOf(prop.getEditor().getItem()).trim();
        String newValue = value.getText().trim();
        if (property.isEmpty() || newValue.isEmpty()) {
            domStatus.setText("Property and value are both required");
            return;
        }
        // live preview first — the page shows the tweak even when the
        // source write refuses (the user sees WHAT they asked for, the
        // status says WHY it isn't saved)
        runner.run(DevScripts.applyInlineStyle(node.path, property, newValue), r -> { }, err -> { });
        runner.run(DevScripts.matchedRules(node.path),
                json -> RP.post(() -> writeBack(json, property, newValue)),
                err -> domStatus.setText("No page: " + err));
    }

    /** RP-side: pick the last cascade-matching rule with a writable source. */
    private void writeBack(String rulesJson, String property, String value) {
        java.util.List<Object> rules = org.nmox.studio.ui.browser.devtools.JsonLite.asArray(
                org.nmox.studio.ui.browser.devtools.JsonLite.parse(rulesJson));
        if (rules.isEmpty()) {
            status("Applied in page only — no stylesheet rule matches this element");
            return;
        }
        org.nmox.studio.core.spi.LiveServings servings = org.nmox.studio.core.spi.LiveServings.find();
        java.util.List<org.nmox.studio.core.spi.LiveServings.Serving> snapshot =
                servings == null ? java.util.List.of() : servings.snapshot();
        String firstReason = null;
        // cascade order: the LAST matching rule wins, so walk backwards
        for (int i = rules.size() - 1; i >= 0; i--) {
            if (!(rules.get(i) instanceof java.util.Map)) {
                continue;
            }
            java.util.Map<String, Object> rule = org.nmox.studio.ui.browser.devtools.JsonLite.asObject(rules.get(i));
            String href = org.nmox.studio.ui.browser.devtools.JsonLite.str(rule, "h", "");
            String selector = org.nmox.studio.ui.browser.devtools.JsonLite.str(rule, "s", "");
            if (selector.isEmpty()) {
                continue;
            }
            if (href.isEmpty()) {
                firstReason = keep(firstReason, "rule lives in an inline <style>, not a stylesheet file");
                continue;
            }
            org.nmox.studio.ui.browser.devtools.PageSourceResolver.Resolved resolved =
                    org.nmox.studio.ui.browser.devtools.PageSourceResolver.resolve(href, snapshot);
            if (resolved == null) {
                firstReason = keep(firstReason, "stylesheet " + href + " is not served from a project here");
                continue;
            }
            java.io.File cssFile = resolved.file();
            // a compiled sibling means the .css is BUILD OUTPUT — writing
            // there is silently lost on the next compile (v1.230.0's
            // recompile-on-save would even do the losing immediately)
            String base = cssFile.getName().replaceFirst("[.]css$", "");
            java.io.File dir = cssFile.getParentFile();
            if (new java.io.File(dir, base + ".scss").isFile()
                    || new java.io.File(dir, base + ".less").isFile()
                    || new java.io.File(dir, base + ".sass").isFile()) {
                firstReason = keep(firstReason, cssFile.getName()
                        + " is compiled output — edit the preprocessor source instead");
                continue;
            }
            try {
                org.openide.filesystems.FileObject fo = org.openide.filesystems.FileUtil
                        .toFileObject(org.openide.filesystems.FileUtil.normalizeFile(cssFile));
                if (fo != null) {
                    org.openide.loaders.DataObject dobj = org.openide.loaders.DataObject.find(fo);
                    if (dobj.isModified()) {
                        firstReason = keep(firstReason, cssFile.getName()
                                + " has unsaved editor changes — save it first");
                        continue;
                    }
                }
                String css = java.nio.file.Files.readString(cssFile.toPath());
                org.nmox.studio.ui.browser.devtools.StyleWriteback.Result result =
                        org.nmox.studio.ui.browser.devtools.StyleWriteback.apply(
                                css, selector, property, value);
                if (!result.ok()) {
                    firstReason = keep(firstReason, result.reason());
                    continue;
                }
                org.nmox.studio.core.util.AtomicFiles.writeString(cssFile.toPath(), result.css());
                if (fo != null) {
                    fo.refresh();
                }
                status("Saved to " + cssFile.getName() + "  (" + selector + ")");
                return;
            } catch (java.io.IOException ex) {
                firstReason = keep(firstReason, cssFile.getName() + ": " + ex.getMessage());
            }
        }
        status("Applied in page only — "
                + (firstReason != null ? firstReason : "no writable stylesheet rule found"));
    }

    private static String keep(String existing, String candidate) {
        return existing != null ? existing : candidate;
    }

    /**
     * A page click while pick was armed: refresh the snapshot (the
     * page may have changed since the tree was built), then select
     * the picked node so highlight + details + Open Source all aim at
     * it. Timer already stopped by the caller.
     */
    private void onPicked(JTree tree, String pathJson) {
        List<Integer> path = new java.util.ArrayList<>();
        for (Object o : org.nmox.studio.ui.browser.devtools.JsonLite.asArray(
                org.nmox.studio.ui.browser.devtools.JsonLite.parse(pathJson))) {
            if (o instanceof Double d) {
                path.add((int) (double) d);
            }
        }
        runner.run(DevScripts.DOM_SNAPSHOT, json -> RP.post(() -> {
            DomNode root = DomSnapshotParser.parse(json);
            DefaultMutableTreeNode swingRoot = toSwing(root);
            SwingUtilities.invokeLater(() -> {
                domTree.setRoot(swingRoot);
                lastDomRoot = root;
                DefaultMutableTreeNode hit = findByPath(swingRoot, path);
                if (hit != null) {
                    javax.swing.tree.TreePath tp = new javax.swing.tree.TreePath(hit.getPath());
                    tree.setSelectionPath(tp);
                    tree.scrollPathToVisible(tp);
                    domStatus.setText(" ");
                } else {
                    domStatus.setText("Picked element not in the snapshot (page changed?)");
                }
            });
        }), err -> domStatus.setText("Pick failed: " + err));
    }

    private static DefaultMutableTreeNode findByPath(DefaultMutableTreeNode swingRoot, List<Integer> path) {
        java.util.Enumeration<?> all = swingRoot.depthFirstEnumeration();
        while (all.hasMoreElements()) {
            Object o = all.nextElement();
            if (o instanceof DefaultMutableTreeNode n
                    && n.getUserObject() instanceof DomNode dn
                    && !dn.isPlaceholder() && dn.path.equals(path)) {
                return n;
            }
        }
        return null;
    }

    /**
     * Inspect-to-source: the selected DOM node opens the HTML file
     * that produced it, at the line. Honest refusals on the status
     * label — a remote page has no local source, and an element the
     * source doesn't contain (script-generated) must not jump
     * somewhere wrong. File IO rides the RP; the editor opens on the
     * EDT.
     */
    private void openSource(DomNode node) {
        if (node == null || node.isPlaceholder()) {
            domStatus.setText("Select an element first");
            return;
        }
        DomNode root = lastDomRoot;
        runner.run(DevScripts.PAGE_URL, url -> RP.post(() -> {
            org.nmox.studio.core.spi.LiveServings servings =
                    org.nmox.studio.core.spi.LiveServings.find();
            java.util.List<org.nmox.studio.core.spi.LiveServings.Serving> snapshot =
                    servings == null ? java.util.List.of() : servings.snapshot();
            org.nmox.studio.ui.browser.devtools.PageSourceResolver.Resolved resolved =
                    org.nmox.studio.ui.browser.devtools.PageSourceResolver.resolve(url, snapshot);
            if (resolved == null) {
                status("No local source for " + url + " (not served from a project here)");
                return;
            }
            String html;
            try {
                html = java.nio.file.Files.readString(resolved.file().toPath());
            } catch (java.io.IOException ex) {
                status("Cannot read " + resolved.file().getName() + ": " + ex.getMessage());
                return;
            }
            int line = org.nmox.studio.ui.browser.devtools.HtmlSourceLocator.lineOf(html, node, root);
            if (line < 0) {
                status("<" + node.tag + "> not found in " + resolved.file().getName()
                        + " — likely script-generated");
                return;
            }
            openAt(resolved.file(), line);
        }), err -> domStatus.setText("No page URL: " + err));
    }

    private void status(String text) {
        SwingUtilities.invokeLater(() -> domStatus.setText(text));
    }

    /** Opens the file in the editor at the 1-based line (EDT). */
    private void openAt(java.io.File file, int line) {
        SwingUtilities.invokeLater(() -> {
            try {
                org.openide.filesystems.FileObject fo =
                        org.openide.filesystems.FileUtil.toFileObject(
                                org.openide.filesystems.FileUtil.normalizeFile(file));
                if (fo == null) {
                    domStatus.setText("File vanished: " + file.getName());
                    return;
                }
                org.openide.loaders.DataObject dobj = org.openide.loaders.DataObject.find(fo);
                org.openide.cookies.LineCookie lc = dobj.getLookup().lookup(org.openide.cookies.LineCookie.class);
                if (lc != null) {
                    org.openide.text.Line l = lc.getLineSet().getOriginal(Math.max(0, line - 1));
                    l.show(org.openide.text.Line.ShowOpenType.OPEN,
                            org.openide.text.Line.ShowVisibilityType.FOCUS);
                    domStatus.setText(file.getName() + ":" + line);
                } else {
                    org.openide.cookies.OpenCookie oc = dobj.getLookup().lookup(org.openide.cookies.OpenCookie.class);
                    if (oc != null) {
                        oc.open();
                        domStatus.setText(file.getName());
                    }
                }
            } catch (org.openide.loaders.DataObjectNotFoundException | IndexOutOfBoundsException ex) {
                domStatus.setText("Cannot open " + file.getName() + ": " + ex.getMessage());
            }
        });
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
            // the check a designer runs by hand in a contrast calculator,
            // done where the colors already are (v1.227.0); silent when the
            // background is transparent — the real backdrop is an ancestor's
            org.nmox.studio.ui.browser.devtools.WcagContrast.Verdict contrast =
                    org.nmox.studio.ui.browser.devtools.WcagContrast.of(
                            style.get("color"), style.get("background-color"));
            if (contrast != null) {
                sb.append("\nContrast: ").append(contrast.summary()).append('\n');
            }
        }
        return sb.toString();
    }

    private void refreshDom() {
        runner.run(DevScripts.DOM_SNAPSHOT, json -> RP.post(() -> {
            DomNode root = DomSnapshotParser.parse(json);
            DefaultMutableTreeNode swingRoot = toSwing(root);
            SwingUtilities.invokeLater(() -> {
                domTree.setRoot(swingRoot);
                lastDomRoot = root;
            });
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
        JTable table = safeTable(networkTable);
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
        JTable table = safeTable(storageTable);
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
        JTree tree = safeTree(vueTree);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshVue());
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        bar.add(refresh);
        bar.add(vueStatus);
        panel.add(bar, BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(tree), new JScrollPane(safeTable(vueDetails)));
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
            if (!parsed.productionOnly.isEmpty()) {
                // Vue IS here, but a production build hides its component
                // tree from every inspector — say that instead of "no Vue",
                // which sends a developer hunting a bug that isn't theirs.
                // Keep it SHORT: this row is a FlowLayout, so a label wider
                // than the panel wraps to a second row that the fixed row
                // height hides — an over-long status reads as no status at
                // all (found live in the v1.206.0 gauntlet). The full
                // explanation rides the tooltip.
                vueStatus.setText("Vue " + parsed.productionOnly
                        + " — production build, no component tree");
                vueStatus.setToolTipText("A production Vue build exposes neither "
                        + "app._instance nor __vueParentComponent, so no inspector "
                        + "can walk its components — the official Vue DevTools is "
                        + "limited the same way. Run a development build to inspect.");
            } else {
                vueStatus.setText("No Vue detected — Vue 2 and 3 supported");
                vueStatus.setToolTipText("Angular has its own tab; React is not inspected.");
            }
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

    // ---- Svelte --------------------------------------------------------

    private JPanel svelteTab() {
        JPanel panel = new JPanel(new BorderLayout());
        JTree tree = safeTree(svelteTree);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshSvelte());
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        bar.add(refresh);
        bar.add(svelteStatus);
        panel.add(bar, BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(tree), new JScrollPane(svelteDetails));
        split.setResizeWeight(0.5);
        panel.add(split, BorderLayout.CENTER);
        tree.addTreeSelectionListener(e -> {
            Object last = tree.getLastSelectedPathComponent();
            if (!(last instanceof DefaultMutableTreeNode n)) {
                return;
            }
            if (n.getUserObject() instanceof SvelteSnapshotParser.Loc loc) {
                Object parent = ((DefaultMutableTreeNode) n.getParent()).getUserObject();
                String file = parent instanceof SvelteSnapshotParser.SvelteFile sf
                        ? sf.file : "";
                svelteDetails.setText(file + "\nline " + loc.line + ":" + loc.column);
                if (!loc.path.isEmpty()) {
                    runner.run(DevScripts.highlight(loc.path), r -> { }, err -> { });
                }
            } else if (n.getUserObject() instanceof SvelteSnapshotParser.SvelteFile sf) {
                svelteDetails.setText(sf.file + "\n" + sf.count + " element"
                        + (sf.count == 1 ? "" : "s"));
            }
        });
        return panel;
    }

    private void refreshSvelte() {
        runner.run(DevScripts.SVELTE_SNAPSHOT, json -> RP.post(() -> {
            SvelteSnapshotParser.SvelteTree parsed = SvelteSnapshotParser.parse(json);
            SwingUtilities.invokeLater(() -> applySvelte(parsed));
        }), err -> svelteStatus.setText("(no page: " + err + ")"));
    }

    private void applySvelte(SvelteSnapshotParser.SvelteTree parsed) {
        svelteDetails.setText("");
        if (parsed.empty()) {
            svelteTree.setRoot(new DefaultMutableTreeNode("(no Svelte)"));
            // Keep the status SHORT (a FlowLayout label wider than the
            // panel wraps to a hidden second row — the v1.206.0 Vue-tab
            // lesson); the honest limits ride the tooltip.
            svelteStatus.setText("No Svelte detected (dev builds only)");
            svelteStatus.setToolTipText("Svelte compiles components away — no "
                    + "component instances, props, or state exist at runtime. "
                    + "A DEV build (vite dev) plants __svelte_meta source "
                    + "locations on rendered elements, which is what this pane "
                    + "shows; a production build offers nothing to inspect.");
            return;
        }
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Svelte sources");
        for (SvelteSnapshotParser.SvelteFile f : parsed.files) {
            DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(f);
            for (SvelteSnapshotParser.Loc loc : f.locs) {
                fileNode.add(new DefaultMutableTreeNode(loc));
            }
            root.add(fileNode);
        }
        svelteTree.setRoot(root);
        svelteStatus.setText("Svelte — " + parsed.total + " element"
                + (parsed.total == 1 ? "" : "s") + " from " + parsed.files.size()
                + " file" + (parsed.files.size() == 1 ? "" : "s"));
        svelteStatus.setToolTipText("Source mapping from dev-mode __svelte_meta: "
                + "which .svelte file and line rendered each element. Svelte "
                + "compiles components away, so file/line mapping is all a "
                + "runtime inspector can offer — select a line to highlight "
                + "its element in the page.");
    }

    // ---- Angular -------------------------------------------------------

    private JPanel angularTab() {
        JPanel panel = new JPanel(new BorderLayout());
        JTree tree = safeTree(ngTree);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshAngular());
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        bar.add(refresh);
        bar.add(ngStatus);
        panel.add(bar, BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(tree), new JScrollPane(safeTable(ngDetails)));
        split.setResizeWeight(0.5);
        panel.add(split, BorderLayout.CENTER);
        tree.addTreeSelectionListener(e -> {
            Object last = tree.getLastSelectedPathComponent();
            if (last instanceof DefaultMutableTreeNode n && n.getUserObject() instanceof NgNode ng) {
                ngDetails.setRowCount(0);
                for (Map.Entry<String, String> s : ng.state.entrySet()) {
                    ngDetails.addRow(new Object[]{"state", s.getKey(), s.getValue()});
                }
                for (String d : ng.directives) {
                    ngDetails.addRow(new Object[]{"directive", d, ""});
                }
                if (!ng.domPath.isEmpty()) {
                    runner.run(DevScripts.highlight(ng.domPath), r -> { }, err -> { });
                }
            }
        });
        return panel;
    }

    private void refreshAngular() {
        runner.run(DevScripts.ANGULAR_SNAPSHOT, json -> RP.post(() -> {
            NgTree parsed = AngularSnapshotParser.parse(json);
            SwingUtilities.invokeLater(() -> applyAngular(parsed));
        }), err -> ngStatus.setText("(no page: " + err + ")"));
    }

    private void applyAngular(NgTree parsed) {
        ngDetails.setRowCount(0);
        if (parsed.empty()) {
            ngTree.setRoot(new DefaultMutableTreeNode("(no components)"));
            // Keep the status SHORT (a FlowLayout label wider than the
            // panel wraps to a hidden second row — the v1.206.0 Vue-tab
            // lesson); the honest limits ride the tooltip.
            if (!parsed.productionOnly.isEmpty()) {
                ngStatus.setText("Angular " + parsed.productionOnly
                        + " — production build, no component tree");
                ngStatus.setToolTipText("The page carries ng-version, so Angular IS "
                        + "here — but a production build strips window.ng, the debug "
                        + "API every inspector needs (the official Angular DevTools "
                        + "is limited the same way). Run a dev build (ng serve) to "
                        + "inspect components.");
            } else {
                ngStatus.setText("No Angular detected (dev builds only)");
                ngStatus.setToolTipText("Detection looks for the ng-version marker "
                        + "and window.ng.getComponent, which Angular exposes in dev "
                        + "builds (ng serve). Vue and Svelte have their own tabs; "
                        + "React is not inspected.");
            }
            return;
        }
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(
                "Angular " + (parsed.version.isEmpty() ? "app" : parsed.version));
        int[] count = {0};
        for (NgNode r : parsed.roots) {
            root.add(toSwing(r, count));
        }
        ngTree.setRoot(root);
        ngStatus.setText("Angular " + parsed.version + " — " + count[0] + " component"
                + (count[0] == 1 ? "" : "s"));
        ngStatus.setToolTipText("Component instances from window.ng.getComponent "
                + "(dev builds). Select a component to see its fields and host "
                + "directives, and to highlight its host element in the page.");
    }

    private static DefaultMutableTreeNode toSwing(NgNode node, int[] count) {
        count[0]++;
        DefaultMutableTreeNode swing = new DefaultMutableTreeNode(node);
        for (NgNode child : node.children) {
            swing.add(toSwing(child, count));
        }
        return swing;
    }

    // ---- shared --------------------------------------------------------

    /**
     * EDT. Stops the coalescing timers — called when the Browser tab
     * closes so an invisible panel stops rebuilding its models
     * (v1.208.0 review; the panel is discarded with the tab).
     */
    void stopTimers() {
        consoleSync.stop();
        networkSync.stop();
    }

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
