package org.nmox.studio.ui.browser.fx;

import org.nmox.studio.core.util.PlainTables;
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
import org.nmox.studio.ui.browser.devtools.RuntimeErrors;
import org.nmox.studio.ui.browser.devtools.BrowserErrorDisclosure;
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
    private final JLabel networkDropped = PlainTables.plain(new JLabel());
    private final javax.swing.Timer networkSync;

    // DOM tab
    private final DefaultTreeModel domTree = new DefaultTreeModel(new DefaultMutableTreeNode("(press Refresh)"));
    private final JTextArea domDetails = readOnlyArea("DOM element details");
    private final JLabel domStatus = PlainTables.plain(new JLabel(" "));
    private volatile DomNode lastDomRoot;
    private javax.swing.Timer pickPoll;
    /** The DOM tab's tree — the Motion tab animates its selection. */
    private JTree domTreeView;

    // Motion tab (v2.12.0 — the DHTML keyframe timeline)
    private final JLabel motionStatus = PlainTables.plain(new JLabel(" "));
    private final JTextField motionName = new JTextField("my-motion", 10);
    private final javax.swing.JSpinner motionDuration = new javax.swing.JSpinner(
            new javax.swing.SpinnerNumberModel(1500, 100, 600_000, 100));
    private final javax.swing.JComboBox<String> motionEasing = new javax.swing.JComboBox<>(
            new String[]{"linear", "ease", "ease-in", "ease-out", "ease-in-out"});
    private final javax.swing.JComboBox<String> motionIterations = new javax.swing.JComboBox<>(
            new String[]{"infinite", "1", "2", "3"});
    private TimelineStrip motionStrip;
    private volatile boolean motionPlaying;
    private final org.nmox.studio.ui.browser.devtools.MotionTargetGuard motionGuard =
            new org.nmox.studio.ui.browser.devtools.MotionTargetGuard();

    // Storage tab
    private final DefaultTableModel storageTable = readOnlyTable("Area", "Key", "Value");

    // Vue tab
    private final DefaultTreeModel vueTree = new DefaultTreeModel(new DefaultMutableTreeNode("(press Refresh)"));
    private final DefaultTableModel vueDetails = readOnlyTable("Kind", "Name", "Value");
    private final JLabel vueStatus = new JLabel(" ");

    // Svelte tab
    private final DefaultTreeModel svelteTree = new DefaultTreeModel(new DefaultMutableTreeNode("(press Refresh)"));
    private final JTextArea svelteDetails = readOnlyArea("Svelte component details");
    private final JLabel svelteStatus = new JLabel(" ");

    // Angular tab
    private final DefaultTreeModel ngTree = new DefaultTreeModel(new DefaultMutableTreeNode("(press Refresh)"));
    private final DefaultTableModel ngDetails = readOnlyTable("Kind", "Name", "Value");
    private final JLabel ngStatus = new JLabel(" ");

    private final RuntimeErrors runtimeErrors;

    public DevToolsPanel(ConsoleModel console, NetworkModel network, ScriptRunner runner,
            RuntimeErrors runtimeErrors) {
        super(new BorderLayout());
        this.runtimeErrors = runtimeErrors;
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
        tabs.addTab("Motion", motionTab());
        tabs.addTab("Network", networkTab());
        tabs.addTab("Storage", storageTab());
        tabs.addTab("Vue", vueTab());
        tabs.addTab("Svelte", svelteTab());
        tabs.addTab("Angular", angularTab());
        add(tabs, BorderLayout.CENTER);
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        if (pickPoll != null) {
            pickPoll.stop();
        }
    }

    // ---- Console -------------------------------------------------------

    private JPanel consoleTab() {
        JPanel panel = new JPanel(new BorderLayout());
        JList<ConsoleModel.Entry> list = new JList<>(consoleList);
        list.getAccessibleContext().setAccessibleName("Console output");
        list.setCellRenderer(new ConsoleRenderer());
        panel.add(consoleDropped, BorderLayout.NORTH);
        consoleDropped.setVisible(false);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout(4, 0));
        JTextField repl = new JTextField();
        repl.getAccessibleContext().setAccessibleName("Console input");
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
        // the learning multiplier (v2.39.2): the located error the page
        // just threw, explained — through the OracleAsk seam with its
        // own consent kind; a beginner's broken page becomes a lesson
        JButton explain = new JButton("Explain error…");
        explain.setToolTipText("Ask ORACLE about the page's last runtime error"
                + " — sends the message and, when it resolved to your project,"
                + " a few source lines around the failing line");
        explain.getAccessibleContext().setAccessibleName("Explain the last runtime error");
        explain.addActionListener(e -> explainLastError());
        javax.swing.JPanel east = new javax.swing.JPanel(
                new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 4, 0));
        east.add(explain);
        east.add(clear);
        south.add(repl, BorderLayout.CENTER);
        south.add(east, BorderLayout.EAST);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    /** Explain error… — the OracleAsk flow for the last located error;
     *  refusals are honest status lines (the LCD rule). */
    private void explainLastError() {
        org.nmox.studio.core.spi.OracleAsk oracle = org.nmox.studio.core.spi.OracleAsk.find();
        if (oracle == null) {
            org.openide.awt.StatusDisplayer.getDefault()
                    .setStatusText("ORACLE is not available (rack module absent).");
            return;
        }
        java.util.List<String> consoleErrors = new java.util.ArrayList<>();
        for (ConsoleModel.Entry en : console.entries()) {
            if ("error".equals(en.level())) {
                consoleErrors.add(en.text());
            }
        }
        RuntimeErrors.ExplainTarget target = RuntimeErrors.pickExplainTarget(
                runtimeErrors == null ? java.util.List.of() : runtimeErrors.current(),
                consoleErrors);
        if (target == null) {
            org.openide.awt.StatusDisplayer.getDefault()
                    .setStatusText("No runtime error to explain yet — the console is clean.");
            return;
        }
        String message = target.message();
        java.io.File file = target.file();
        int line = target.line();
        boolean started = oracle.explain(new org.nmox.studio.core.spi.OracleAsk.Disclosure(
                "browser.error",
                "Runtime error — " + (file == null ? "page" : file.getName() + ":" + line),
                BrowserErrorDisclosure.what(file, line),
                BrowserErrorDisclosure.body(message, file, line),
                "Why does this error happen here, and what should I change?"));
        if (!started) {
            org.openide.awt.StatusDisplayer.getDefault()
                    .setStatusText("Explain declined or no API key — nothing was sent.");
        }
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
        // PLAIN-TABLE-EXEMPT: the DOM pane's renderer carries its own
        // html-disable idiom, gated by DevToolsHtmlSafetyTest (v1.208.0)
        JTree tree = new JTree(model);
        tree.getAccessibleContext().setAccessibleName("DOM tree");
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
        table.getAccessibleContext().setAccessibleName("Network requests");
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
        domTreeView = tree;
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
        // result rides window.__nmoxPickResult like every snapshot does.
        // EVERY exit path must stop the timer EXPLICITLY: setSelected(false)
        // fires no ActionListener, so the toggle-off branch below never runs
        // for programmatic disarms — the v1.359.0 review found the success
        // path leaving the poll running forever
        pickPoll = new javax.swing.Timer(300, e -> {
            if (!pick.isSelected()) {
                pickPoll.stop(); // backstop: never poll for a disarmed pick
                return;
            }
            runner.run(DevScripts.PICK_POLL, r -> {
                if (r != null && !r.isBlank()) {
                    SwingUtilities.invokeLater(() -> {
                        pickPoll.stop();
                        pick.setSelected(false);
                        onPicked(tree, r);
                    });
                }
            }, err -> { });
        });
        pickPoll.setRepeats(true);
        pick.addActionListener(e -> {
            if (pick.isSelected()) {
                domStatus.setText("Click an element in the page…");
                runner.run(DevScripts.PICK_ARM, r -> { }, err -> {
                    domStatus.setText("Pick failed: " + err);
                    SwingUtilities.invokeLater(() -> {
                        pickPoll.stop();
                        pick.setSelected(false);
                    });
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
        value.getAccessibleContext().setAccessibleName("Value");
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
        writeBackWith(rulesJson,
                (css, selector) -> org.nmox.studio.ui.browser.devtools.StyleWriteback
                        .apply(css, selector, property, value),
                this::status);
    }

    /**
     * The one refusal ladder every source write rides (v2.12.0 made it
     * a seam): walk the cascade's matched rules backwards, resolve the
     * stylesheet only through vouched channels, refuse compiled output
     * and unsaved buffers, and hand the (css, selector) pair to
     * {@code transform} for the actual rewrite — Edit Style sets one
     * declaration, the Motion pane lands a whole keyframe system.
     */
    private void writeBackWith(String rulesJson,
            java.util.function.BiFunction<String, String,
                    org.nmox.studio.ui.browser.devtools.StyleWriteback.Result> transform,
            java.util.function.Consumer<String> report) {
        java.util.List<Object> rules = org.nmox.studio.ui.browser.devtools.JsonLite.asArray(
                org.nmox.studio.ui.browser.devtools.JsonLite.parse(rulesJson));
        if (rules.isEmpty()) {
            report.accept("Applied in page only — no stylesheet rule matches this element");
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
            // page-side caps are page-controlled (the sibling parsers'
            // rule): a hostile selectorText re-clipped Java-side, and a
            // selector too long to be honest is skipped, not truncated —
            // a truncated selector would silently match the wrong rule
            if (selector.isEmpty() || selector.length() > 1000 || href.length() > 2000) {
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
                        transform.apply(css, selector);
                if (!result.ok()) {
                    firstReason = keep(firstReason, result.reason());
                    continue;
                }
                org.nmox.studio.core.util.AtomicFiles.writeString(cssFile.toPath(), result.css());
                if (fo != null) {
                    fo.refresh();
                }
                report.accept("Saved to " + cssFile.getName() + "  (" + selector + ")");
                return;
            } catch (java.io.IOException ex) {
                firstReason = keep(firstReason, cssFile.getName() + ": " + ex.getMessage());
            }
        }
        report.accept("Applied in page only — "
                + (firstReason != null ? firstReason : "no writable stylesheet rule found"));
    }

    // ---- Motion (v2.12.0): DHTML, reborn as a keyframe timeline ---------

    /**
     * The Motion pane: a timeline strip that authors a real CSS
     * animation for the DOM tab's selected element. Play/scrub preview
     * IN the page (a single {@code style#__nmox_motion} tag plus the
     * element's inline {@code animation}); Apply lands the
     * {@code @keyframes} block and the {@code animation:} shorthand in
     * the SOURCE stylesheet through the same {@link #writeBackWith
     * refusal ladder} as Edit Style — compiled output, inline styles,
     * unserved sheets, and unsaved buffers all refuse with reasons.
     */
    private JPanel motionTab() {
        JPanel panel = new JPanel(new BorderLayout());
        motionName.getAccessibleContext().setAccessibleName("Animation name");
        motionDuration.getAccessibleContext().setAccessibleName("Duration in milliseconds");
        motionEasing.getAccessibleContext().setAccessibleName("Easing function");
        motionIterations.getAccessibleContext().setAccessibleName("Iteration count");

        javax.swing.JComboBox<String> presets = new javax.swing.JComboBox<>(
                org.nmox.studio.ui.browser.devtools.Keyframes.presets().stream()
                        .map(org.nmox.studio.ui.browser.devtools.Keyframes.Spec::name)
                        .toArray(String[]::new));
        presets.getAccessibleContext().setAccessibleName("DHTML preset");
        JButton load = new JButton("Load");
        load.getAccessibleContext().setAccessibleName("Load preset");
        load.addActionListener(e -> {
            String wanted = String.valueOf(presets.getSelectedItem());
            org.nmox.studio.ui.browser.devtools.Keyframes.presets().stream()
                    .filter(spec -> spec.name().equals(wanted)).findFirst()
                    .ifPresent(spec -> {
                        motionName.setText(spec.name());
                        motionDuration.setValue(spec.durationMs());
                        motionEasing.setSelectedItem(spec.easing());
                        motionIterations.setSelectedItem(
                                spec.iterations() <= 0 ? "infinite"
                                        : String.valueOf(spec.iterations()));
                        motionStrip.model().load(spec.frames());
                        motionStrip.refresh();
                        motionStatus.setText("Loaded \u201c" + wanted
                                + "\u201d — select an element in the DOM tab, then Play");
                        motionPreviewIfPlaying();
                    });
        });

        javax.swing.JComboBox<String> trackProp = new javax.swing.JComboBox<>(
                new String[]{"transform", "opacity", "filter", "background-color",
                    "color", "letter-spacing", "border-radius", "width"});
        trackProp.setEditable(true);
        trackProp.getAccessibleContext().setAccessibleName("Track property");
        JButton addTrack = new JButton("Add Track");
        addTrack.getAccessibleContext().setAccessibleName("Add property track");
        addTrack.addActionListener(e -> {
            String prop = String.valueOf(trackProp.getEditor().getItem()).trim();
            if (!prop.isEmpty()) {
                motionStrip.model().addTrack(prop);
                motionStrip.refresh();
                motionStatus.setText("Double-click the " + prop + " track to add keyframes");
            }
        });
        JButton delTrack = new JButton("Remove Track");
        delTrack.getAccessibleContext().setAccessibleName("Remove property track");
        delTrack.addActionListener(e -> {
            String prop = motionStrip.selectedProperty() != null
                    ? motionStrip.selectedProperty()
                    : String.valueOf(trackProp.getEditor().getItem()).trim();
            // every sibling gesture reports on motionStatus — a free-typed
            // name matching no track must refuse out loud, not sit inert
            if (motionStrip.model().removeTrack(prop)) {
                motionStrip.refresh();
                motionPreviewIfPlaying();
                motionStatus.setText("Removed the " + prop + " track");
            } else {
                motionStatus.setText("No track named \"" + prop
                        + "\" — select a track or pick its exact name");
            }
        });

        motionStrip = new TimelineStrip(this::motionScrub, this::motionPreviewIfPlaying,
                this::editStopValue);

        JButton play = new JButton("Play");
        play.getAccessibleContext().setAccessibleName("Play animation preview");
        play.addActionListener(e -> motionPlay());
        JButton stop = new JButton("Stop");
        stop.getAccessibleContext().setAccessibleName("Stop animation preview");
        stop.addActionListener(e -> motionStop());
        JButton apply = new JButton("Apply to Source");
        apply.getAccessibleContext().setAccessibleName("Apply animation to source stylesheet");
        apply.addActionListener(e -> motionApply());

        JPanel barTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        barTop.add(new JLabel("Preset:"));
        barTop.add(presets);
        barTop.add(load);
        barTop.add(new JLabel("Name:"));
        barTop.add(motionName);
        barTop.add(new JLabel("Duration (ms):"));
        barTop.add(motionDuration);
        barTop.add(new JLabel("Easing:"));
        barTop.add(motionEasing);
        barTop.add(new JLabel("Runs:"));
        barTop.add(motionIterations);
        JPanel barTracks = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        barTracks.add(new JLabel("Track:"));
        barTracks.add(trackProp);
        barTracks.add(addTrack);
        barTracks.add(delTrack);
        JPanel north = new JPanel(new java.awt.GridLayout(2, 1));
        north.add(barTop);
        north.add(barTracks);
        panel.add(north, BorderLayout.NORTH);
        panel.add(new JScrollPane(motionStrip), BorderLayout.CENTER);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        south.add(play);
        south.add(stop);
        south.add(apply);
        south.add(motionStatus);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    /** The DOM tab's selection, or a status hint when there is none. */
    private DomNode motionTarget() {
        DomNode node = domTreeView == null ? null : selectedDom(domTreeView);
        if (node == null || node.isPlaceholder()) {
            motionStatus.setText(
                    "Select an element in the DOM tab first (Pick element works too)");
            return null;
        }
        return node;
    }

    /** The spec the pane currently describes. */
    private org.nmox.studio.ui.browser.devtools.Keyframes.Spec motionSpec() {
        String iter = String.valueOf(motionIterations.getSelectedItem());
        return new org.nmox.studio.ui.browser.devtools.Keyframes.Spec(
                motionName.getText().trim(),
                (Integer) motionDuration.getValue(),
                String.valueOf(motionEasing.getSelectedItem()),
                "infinite".equals(iter) ? 0 : Integer.parseInt(iter),
                motionStrip.model().frames());
    }

    private void motionPlay() {
        DomNode node = motionTarget();
        if (node == null) {
            return;
        }
        org.nmox.studio.ui.browser.devtools.Keyframes.Spec spec = motionSpec();
        String bad = org.nmox.studio.ui.browser.devtools.Keyframes.problem(spec);
        if (bad != null) {
            motionStatus.setText("Refused: " + bad);
            return;
        }
        motionPlaying = true;
        java.util.List<Integer> stale = motionGuard.retarget(node.path);
        if (stale != null) {
            // the previously-previewed element keeps its inline
            // animation: property otherwise, and the next same-named
            // keyframes injection resurrects it (the v1.172.0 law: a
            // preview belongs to the element that started it)
            runner.run(DevScripts.applyInlineStyle(stale, "animation", "none"),
                    r -> { }, err -> { });
        }
        runner.run(DevScripts.injectMotionKeyframes(spec.block()), r -> { }, err -> { });
        runner.run(DevScripts.applyInlineStyle(node.path, "animation-delay", "0s"),
                r -> { }, err -> { });
        runner.run(DevScripts.applyInlineStyle(node.path, "animation-play-state", "running"),
                r -> { }, err -> { });
        runner.run(DevScripts.applyInlineStyle(node.path, "animation", spec.animationValue()),
                r -> { }, err -> { });
        motionStatus.setText("Playing: " + spec.animationValue());
    }

    private void motionStop() {
        motionPlaying = false;
        // stop what is PLAYING, not what is selected — the guard
        // remembers the element the preview was applied to
        DomNode node = domTreeView == null ? null : selectedDom(domTreeView);
        java.util.List<Integer> fallback =
                node != null && !node.isPlaceholder() ? node.path : null;
        java.util.List<Integer> target = motionGuard.stopTarget(fallback);
        if (target != null) {
            runner.run(DevScripts.applyInlineStyle(target, "animation", "none"),
                    r -> { }, err -> { });
        }
        runner.run(DevScripts.clearMotionPreview(), r -> { }, err -> { });
        motionStatus.setText(" ");
    }

    /** A timeline edit while the preview runs re-injects it live. */
    private void motionPreviewIfPlaying() {
        if (motionPlaying) {
            motionPlay();
        }
    }

    /**
     * Scrub: hold the page at {@code percent} of the animation — the
     * paused-with-negative-delay trick, so the element really sits at
     * that moment of the real animation, not an approximation.
     */
    private void motionScrub(int percent) {
        DomNode node = domTreeView == null ? null : selectedDom(domTreeView);
        if (node == null || node.isPlaceholder()) {
            return;
        }
        org.nmox.studio.ui.browser.devtools.Keyframes.Spec spec = motionSpec();
        if (org.nmox.studio.ui.browser.devtools.Keyframes.problem(spec) != null) {
            return;
        }
        motionPlaying = false;
        java.util.List<Integer> stale = motionGuard.retarget(node.path);
        if (stale != null) {
            runner.run(DevScripts.applyInlineStyle(stale, "animation", "none"),
                    r -> { }, err -> { });
        }
        double seconds = spec.durationMs() / 1000.0 * percent / 100.0;
        runner.run(DevScripts.injectMotionKeyframes(spec.block()), r -> { }, err -> { });
        runner.run(DevScripts.applyInlineStyle(node.path, "animation",
                spec.name() + " " + spec.durationMs() + "ms " + spec.easing() + " 1"),
                r -> { }, err -> { });
        runner.run(DevScripts.applyInlineStyle(node.path, "animation-play-state", "paused"),
                r -> { }, err -> { });
        runner.run(DevScripts.applyInlineStyle(node.path, "animation-delay",
                String.format(java.util.Locale.ROOT, "-%.3fs", seconds)),
                r -> { }, err -> { });
        // scrubbing pauses the preview — the status must stop claiming
        // "Playing" (review find: a lying status is a small dishonesty)
        motionStatus.setText("Holding at " + percent + "% — press Play to run");
    }

    /** Double-click on a diamond: edit that stop's value. */
    private void editStopValue(String property, Integer percent) {
        String current = motionStrip.model().stops(property).get(percent);
        JTextField field = new JTextField(current == null ? "" : current, 18);
        field.getAccessibleContext().setAccessibleName("Keyframe value");
        org.openide.DialogDescriptor dd = new org.openide.DialogDescriptor(
                field, property + " at " + percent + "%");
        if (org.openide.DialogDisplayer.getDefault().notify(dd)
                == org.openide.DialogDescriptor.OK_OPTION && !field.getText().isBlank()) {
            motionStrip.model().setStop(property, percent, field.getText().trim());
            motionStrip.refresh();
            motionPreviewIfPlaying();
        }
    }

    /**
     * Apply: preview in the page first (the Edit Style law — you see
     * WHAT you asked for even when the write refuses), then land the
     * {@code @keyframes} block AND the {@code animation:} shorthand in
     * the source stylesheet in one atomic write.
     */
    private void motionApply() {
        DomNode node = motionTarget();
        if (node == null) {
            return;
        }
        org.nmox.studio.ui.browser.devtools.Keyframes.Spec spec = motionSpec();
        String bad = org.nmox.studio.ui.browser.devtools.Keyframes.problem(spec);
        if (bad != null) {
            motionStatus.setText("Refused: " + bad);
            return;
        }
        runner.run(DevScripts.injectMotionKeyframes(spec.block()), r -> { }, err -> { });
        runner.run(DevScripts.applyInlineStyle(node.path, "animation", spec.animationValue()),
                r -> { }, err -> { });
        runner.run(DevScripts.matchedRules(node.path),
                json -> RP.post(() -> writeBackWith(json,
                        (css, selector) -> {
                            org.nmox.studio.ui.browser.devtools.Keyframes.Result block =
                                    org.nmox.studio.ui.browser.devtools.Keyframes
                                            .applyBlock(css, spec);
                            if (!block.ok()) {
                                return new org.nmox.studio.ui.browser.devtools
                                        .StyleWriteback.Result(null, block.reason());
                            }
                            return org.nmox.studio.ui.browser.devtools.StyleWriteback
                                    .apply(block.css(), selector, "animation",
                                            spec.animationValue());
                        },
                        text -> SwingUtilities.invokeLater(
                                () -> motionStatus.setText(text)))),
                err -> motionStatus.setText("No page: " + err));
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

    private static JTextArea readOnlyArea(String accessibleName) {
        JTextArea area = new JTextArea();
        area.getAccessibleContext().setAccessibleName(accessibleName);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        return area;
    }
}
