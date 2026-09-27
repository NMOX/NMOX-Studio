package org.nmox.studio.infra.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JPanel;
import javax.swing.TransferHandler;
import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.InfraGraph.InfraNode;
import org.nmox.studio.infra.model.InfraGraph.Wire;
import org.nmox.studio.infra.model.NodeKind;
import org.nmox.studio.core.util.PlainText;

/**
 * The flow canvas, Node-RED style: a dotted dark grid, rounded nodes
 * in category colors with port nubs on their left/right edges, and
 * horizontal bezier wires. Drag nodes to arrange, drag from an output
 * nub to wire, drag empty space to pan, scroll to zoom, Delete to
 * remove, double-click to configure.
 */
@org.openide.util.NbBundle.Messages({
    "FlowCanvas_lockedBanner=CLOUD OPERATION RUNNING — canvas locked until it finishes",
    "FlowCanvas_emptyInvite=Drag a resource from the palette to begin",
    "FlowCanvas_emptyInviteWire=A wire reads \"serves\" — drag from one resource\u2019s edge to another",
    "FlowCanvas_liveStatus=live",
    "FlowCanvas_nodeTooltip=<html><b>{0}</b> {1}<br>${2}/mo{3}</html>",
    "FlowCanvas_tooltipLive=<br>live: {0}",
    "FlowCanvas_tooltipDesignOnly=<br>design only",
    "FlowCanvas_a11yName=Infrastructure design",
    "# {0} the resource kind, {1} its label",
    "FlowCanvas_a11yNodeName=Infrastructure design: {0} {1}",
    "FlowCanvas_a11yHelp=No resource selected. Arrow keys move between resources, Shift with an arrow moves"
        + " the selected one, Enter opens its properties, W wires it to another resource, Shift+F10 opens its"
        + " menu, Delete removes it.",
    "# {0} and {1} lists of resources, each written as kind and label",
    "FlowCanvas_a11yWires=Serves: {0}. Served by: {1}.",
    "FlowCanvas_a11yNobody=nothing",
    "FlowCanvas_a11yStatus=Status: {0}.",
    "FlowCanvas_wireTo=Wire to…",
    "FlowCanvas_wireTitle=Wire {0} to…",
    "FlowCanvas_wireTargets=Resources {0} can serve",
    "FlowCanvas_wireNone={0} has nothing left to serve in this design — a wire reads \"serves\""
})
public class FlowCanvas extends JPanel {

    /** What the host window wants to know about. */
    public interface Callbacks {
        void nodeDoubleClicked(InfraNode node);

        void nodeContextMenu(InfraNode node, Point screenPoint);

        void selectionChanged(InfraNode node);

        /**
         * A wire drop landed on a real target but the graph refused it —
         * either the rule table forbids the pair or the wire already
         * exists. Default no-op so headless fakes stay small; the
         * designer window narrates the reason on the status line
         * (v1.271.0 — the ghost used to just vanish, indistinguishable
         * from a misdrop).
         */
        default void wireRefused(InfraNode from, InfraNode to, boolean duplicate) {
        }
    }

    public static final int NODE_W = 150;
    public static final int NODE_H = 36;

    private static final Color CANVAS_BG = new Color(0x1B, 0x1B, 0x1F);
    private static final Color GRID_DOT = new Color(0x2E, 0x2E, 0x34);
    private static final Color WIRE = new Color(0x8A, 0x8D, 0x94);
    private static final Color WIRE_SELECTED = new Color(0xE8, 0x74, 0x22);
    private static final Color NUB = new Color(0xD6, 0xD7, 0xDB);
    private static final Color LIVE_DOT = new Color(0x4E, 0xC9, 0x8B);

    private final InfraGraph graph;
    private final Callbacks callbacks;

    private double zoom = 1.0;
    private double panX = 0;
    private double panY = 0;

    private InfraNode selectedNode;
    private Wire selectedWire;
    private InfraNode draggingNode;
    private Point dragOffset;
    private InfraNode wireFrom;
    private Point2D wireGhost;
    private Point panStart;

    /**
     * At most one deferral listener is armed while the canvas is 0×0, so a
     * fit()/selectNode() call before the window has sized this component
     * waits on the real resize EVENT instead of busy-reposting itself onto
     * the EDT forever (the startup-hang storm). Guarded by
     * {@link #deferralArmed}: a second call while one is pending is dropped.
     */
    private boolean deferralArmed;

    /** Test hook: how many times the fit() body has actually executed. */
    int fitBodyRuns;

    /** Test hook: how many times the selectNode() body has actually executed. */
    int selectBodyRuns;

    public FlowCanvas(InfraGraph graph, Callbacks callbacks) {
        this.graph = graph;
        this.callbacks = callbacks;
        setBackground(CANVAS_BG);
        setFocusable(true);
        graph.addListener(new InfraGraph.Listener() {
            @Override
            public void graphChanged() {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    dropStaleSelection();
                    repaint();
                });
            }

            @Override
            public void nodeStatusChanged(InfraNode node) {
                javax.swing.SwingUtilities.invokeLater(FlowCanvas.this::repaint);
            }
        });
        Mouse mouse = new Mouse();
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    deleteSelection();
                }
            }
        });
        setTransferHandler(new PaletteDrop());
        setToolTipText("");
        installKeyboard();
    }

    // ---- the keyboard's canvas (3.4, question 3) ----

    /**
     * Everything the mouse does here, from the keyboard: arrows move the
     * selection to the nearest resource that way, Shift+arrows move the
     * selected resource on the grid, Enter opens its properties (the
     * double-click), W wires it to a resource it can serve (the drag, through
     * the same {@code graph.connect} and the same refusal), Shift+F10 and the
     * menu key open its menu, Delete removes it. Tab keeps its meaning — the
     * next control — so the canvas never traps the keyboard.
     */
    private void installKeyboard() {
        key("LEFT", () -> moveSelection(-1, 0));
        key("RIGHT", () -> moveSelection(1, 0));
        key("UP", () -> moveSelection(0, -1));
        key("DOWN", () -> moveSelection(0, 1));
        key("shift LEFT", () -> nudgeSelected(-10, 0));
        key("shift RIGHT", () -> nudgeSelected(10, 0));
        key("shift UP", () -> nudgeSelected(0, -10));
        key("shift DOWN", () -> nudgeSelected(0, 10));
        key("ENTER", () -> {
            if (selectedNode != null) {
                callbacks.nodeDoubleClicked(selectedNode);
            }
        });
        key("W", () -> wireFrom(selectedNode));
        org.nmox.studio.core.util.KeyboardAccess.onMenuKey(this, () -> {
            if (selectedNode != null) {
                callbacks.nodeContextMenu(selectedNode, screenPointOf(selectedNode));
            }
        });
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                repaint();
            }
        });
    }

    private void key(String stroke, Runnable run) {
        javax.swing.KeyStroke ks = javax.swing.KeyStroke.getKeyStroke(stroke);
        String name = "nmox-canvas-" + stroke;
        getInputMap(WHEN_FOCUSED).put(ks, name);
        getActionMap().put(name, new javax.swing.AbstractAction(name) {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                run.run();
            }
        });
    }

    /**
     * Arrow keys: the nearest resource in that direction from the selected
     * one, the axis distance counting half as much as the sideways drift, so
     * Right means "to the right", not "anywhere closer". With nothing
     * selected, the first resource in reading order.
     */
    void moveSelection(int dx, int dy) {
        java.util.List<InfraNode> nodes = graph.getNodes();
        if (nodes.isEmpty()) {
            return;
        }
        if (selectedNode == null || !nodes.contains(selectedNode)) {
            InfraNode first = nodes.stream()
                    .min(java.util.Comparator.<InfraNode>comparingInt(n -> n.y).thenComparingInt(n -> n.x))
                    .orElseThrow();
            select(first, null);
            ensureVisible(first);
            return;
        }
        InfraNode from = selectedNode;
        InfraNode best = null;
        double bestScore = Double.MAX_VALUE;
        for (InfraNode n : nodes) {
            if (n == from) {
                continue;
            }
            int along = dx != 0 ? (n.x - from.x) * dx : (n.y - from.y) * dy;
            int across = Math.abs(dx != 0 ? n.y - from.y : n.x - from.x);
            if (along <= 0) {
                continue;
            }
            double score = along + 2.0 * across;
            if (score < bestScore) {
                bestScore = score;
                best = n;
            }
        }
        if (best != null) {
            select(best, null);
            ensureVisible(best);
        }
    }

    /** Shift+arrows: the selected resource one grid step that way, saved as a drag is. */
    void nudgeSelected(int dx, int dy) {
        if (selectedNode == null) {
            return;
        }
        selectedNode.x += dx;
        selectedNode.y += dy;
        graph.touch();
        ensureVisible(selectedNode);
        repaint();
    }

    /** Pans just enough to show {@code node}; a canvas with no size yet is left alone. */
    private void ensureVisible(InfraNode node) {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        Point tl = toScreen(node.x, node.y);
        Point br = toScreen(node.x + NODE_W, node.y + NODE_H);
        int margin = 20;
        if (tl.x < margin) {
            panX += margin - tl.x;
        } else if (br.x > getWidth() - margin) {
            panX -= br.x - (getWidth() - margin);
        }
        if (tl.y < margin) {
            panY += margin - tl.y;
        } else if (br.y > getHeight() - margin) {
            panY -= br.y - (getHeight() - margin);
        }
    }

    /** Where a keyboard-opened node menu appears: the node's centre, on screen when the canvas shows. */
    private Point screenPointOf(InfraNode node) {
        Point p = toScreen(node.x + NODE_W / 2.0, node.y + NODE_H / 2.0);
        if (isShowing()) {
            javax.swing.SwingUtilities.convertPointToScreen(p, this);
        }
        return p;
    }

    /**
     * Chooses the resource a keyboard wire goes to, among the legal ones;
     * null cancels. Tests replace it; the product asks in a small dialog.
     */
    java.util.function.BiFunction<InfraNode, java.util.List<InfraNode>, InfraNode> wireChooser =
            FlowCanvas::chooseWireTarget;

    /** The node menu's and the W key's label for {@link #wireFrom}. */
    public static String wireToLabel() {
        return Bundle.FlowCanvas_wireTo();
    }

    /**
     * Wires {@code from} to a resource it can serve, chosen from a list of
     * exactly the legal, not-yet-wired targets (the drag's rule table, so a
     * keyboard cannot make a wire the mouse could not). The wire is made by
     * the same {@code graph.connect} the drag calls, and a refusal speaks
     * through the same {@link Callbacks#wireRefused}. Refused while a cloud
     * operation holds the canvas, as the drag is (53b), and out loud.
     */
    public void wireFrom(InfraNode from) {
        if (from == null || !graph.getNodes().contains(from)) {
            return;
        }
        if (refusedWhileLocked()) {
            return;
        }
        java.util.List<InfraNode> targets = new java.util.ArrayList<>();
        for (InfraNode n : graph.getNodes()) {
            if (graph.canConnect(from, n) && !wired(from, n)) {
                targets.add(n);
            }
        }
        if (targets.isEmpty()) {
            org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                    org.nmox.studio.core.util.PlainStatus.text(Bundle.FlowCanvas_wireNone(from.label)));
            return;
        }
        InfraNode to = wireChooser.apply(from, targets);
        if (to == null) {
            return;
        }
        boolean duplicate = wired(from, to);
        if (!graph.connect(from, to)) {
            callbacks.wireRefused(from, to, duplicate);
        }
        repaint();
    }

    private boolean wired(InfraNode from, InfraNode to) {
        return graph.getWires().stream().anyMatch(w -> w.fromId().equals(from.id) && w.toId().equals(to.id));
    }

    /** The product's chooser: a list of the legal targets in a dialog, the first one selected. */
    private static InfraNode chooseWireTarget(InfraNode from, java.util.List<InfraNode> targets) {
        javax.swing.DefaultListModel<String> names = new javax.swing.DefaultListModel<>();
        for (InfraNode n : targets) {
            names.addElement(n.kind.getDisplayName() + " " + n.label);
        }
        javax.swing.JList<String> list = new javax.swing.JList<>(names);
        list.setCellRenderer(org.nmox.studio.core.util.PlainTables.plain(new javax.swing.DefaultListCellRenderer()));
        list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.setVisibleRowCount(Math.min(10, targets.size()));
        list.getAccessibleContext().setAccessibleName(Bundle.FlowCanvas_wireTargets(from.label));
        org.openide.DialogDescriptor dd = new org.openide.DialogDescriptor(
                new javax.swing.JScrollPane(list), Bundle.FlowCanvas_wireTitle(from.label));
        Object answer = org.openide.DialogDisplayer.getDefault().notify(dd);
        int i = list.getSelectedIndex();
        return org.openide.DialogDescriptor.OK_OPTION.equals(answer) && i >= 0 ? targets.get(i) : null;
    }

    // ---- what a screen reader hears ----

    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleCanvas();
        }
        return accessibleContext;
    }

    /**
     * The canvas read aloud: its name is the selected resource (or the
     * design itself), its description that resource's wires and status, or,
     * with nothing selected, the keys that work here. Selection changes are
     * announced as name changes, the event a screen reader speaks.
     */
    protected final class AccessibleCanvas extends AccessibleJPanel {

        @Override
        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.CANVAS;
        }

        @Override
        public String getAccessibleName() {
            InfraNode n = selectedNode;
            return n == null ? Bundle.FlowCanvas_a11yName()
                    : Bundle.FlowCanvas_a11yNodeName(n.kind.getDisplayName(), n.label);
        }

        @Override
        public String getAccessibleDescription() {
            InfraNode n = selectedNode;
            if (n == null) {
                return Bundle.FlowCanvas_a11yHelp();
            }
            String wires = Bundle.FlowCanvas_a11yWires(names(graph.getWires().stream()
                            .filter(w -> w.fromId().equals(n.id)).map(w -> graph.node(w.toId()))),
                    names(graph.getWires().stream()
                            .filter(w -> w.toId().equals(n.id)).map(w -> graph.node(w.fromId()))));
            return n.status == null || n.status.isBlank() ? wires
                    : wires + " " + Bundle.FlowCanvas_a11yStatus(n.status);
        }

        private String names(java.util.stream.Stream<InfraNode> nodes) {
            String joined = nodes.filter(java.util.Objects::nonNull)
                    .map(x -> x.kind.getDisplayName() + " " + x.label)
                    .collect(java.util.stream.Collectors.joining(", "));
            return joined.isEmpty() ? Bundle.FlowCanvas_a11yNobody() : joined;
        }
    }

    // ---- coordinate transforms ----

    private Point2D toWorld(Point screen) {
        return new Point2D.Double((screen.x - panX) / zoom, (screen.y - panY) / zoom);
    }

    private Point toScreen(double wx, double wy) {
        return new Point((int) (wx * zoom + panX), (int) (wy * zoom + panY));
    }

    // ---- selection & editing ----

    public InfraNode getSelectedNode() {
        return selectedNode;
    }

    private void select(InfraNode node, Wire wire) {
        String oldName = accessibleContext == null ? null : accessibleContext.getAccessibleName();
        selectedNode = node;
        selectedWire = wire;
        callbacks.selectionChanged(node);
        if (accessibleContext != null) {
            // what a screen reader speaks on a selection change (3.4)
            accessibleContext.firePropertyChange(
                    javax.accessibility.AccessibleContext.ACCESSIBLE_NAME_PROPERTY,
                    oldName, accessibleContext.getAccessibleName());
        }
        repaint();
    }

    /**
     * Selects a node and pans it into the center of the viewport - the
     * hook Quick Search uses to jump to a node by name. Zoom is left
     * alone so repeated jumps stay at a steady scale.
     */
    public void selectNode(InfraNode node) {
        if (node == null) {
            return;
        }
        if (deferUntilSized(() -> selectNode(node))) {
            return;
        }
        selectBodyRuns++;
        double cx = node.x + NODE_W / 2.0;
        double cy = node.y + NODE_H / 2.0;
        panX = getWidth() / 2.0 - cx * zoom;
        panY = getHeight() / 2.0 - cy * zoom;
        select(node, null);
    }

    /**
     * Structural-edit lock (ledger 53b): while a live cloud operation runs
     * (deploy / destroy / sync), deleting a node mid-plan would orphan a
     * created-and-billed resource with no id recorded. The designer locks
     * the canvas for the operation's duration — delete, wire, and
     * palette-drop are refused (property edits stay live: they are not
     * structural and cannot orphan anything). Painted as a banner so the
     * state is unmistakable. EDT-confined like every other canvas field:
     * runExclusive arms it before posting and clears it in a marshalled
     * finally, and every reader is a mouse/key/paint handler.
     */
    private boolean locked;

    public void setLocked(boolean locked) {
        this.locked = locked;
        repaint();
    }

    public boolean isLocked() {
        return locked;
    }

    /**
     * Forgets a selection the graph no longer holds. A reload (a re-aim, an
     * external edit, a resolved merge) replaces every node, and node ids
     * repeat across designs, so a remembered node let W wire and Delete
     * remove a same-id node of the NEXT design with nothing highlighted —
     * a result acting on a workspace that did not produce it (the 3.4
     * review).
     */
    void dropStaleSelection() {
        boolean nodeGone = selectedNode != null && !graph.getNodes().contains(selectedNode);
        // by identity: a Wire is a record, and a reloaded design's wire
        // between same-id nodes would compare equal to the forgotten one
        boolean wireGone = selectedWire != null
                && graph.getWires().stream().noneMatch(w -> w == selectedWire);
        if (nodeGone || wireGone) {
            select(null, null);
        }
    }

    /**
     * True, having said so on the status line, when a cloud operation holds
     * the canvas (53b). Every structural gesture outside the canvas's own
     * mouse handlers asks here — the palette's double-click and Enter, the
     * node menu's Remove and Wire to…, the keyboard's Delete — so none of
     * them can add or remove a resource mid-plan, and none refuses silently.
     */
    public boolean refusedWhileLocked() {
        if (!locked) {
            return false;
        }
        org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                org.nmox.studio.core.util.PlainStatus.text(Bundle.FlowCanvas_lockedBanner()));
        return true;
    }

    /** Removes {@code node} from the design, unless a cloud operation holds the canvas. */
    public void removeNode(InfraNode node) {
        if (node == null || refusedWhileLocked()) {
            return;
        }
        graph.removeNode(node);
        if (node == selectedNode) {
            select(null, null);
        }
    }

    private void deleteSelection() {
        if (refusedWhileLocked()) {
            return; // a cloud op is running — structural edits are refused
        }
        dropStaleSelection(); // a key can arrive before the reload's repaint
        if (selectedNode != null) {
            graph.removeNode(selectedNode);
            select(null, null);
        } else if (selectedWire != null) {
            graph.disconnect(selectedWire);
            select(null, null);
        }
    }

    /**
     * When this canvas has no size yet (called before the window system has
     * laid it out — e.g. an open-at-startup tab that is not the selected
     * one), arm a ONE-SHOT resize listener that runs {@code body} the first
     * time a real size arrives, and return true so the caller stops. At most
     * one such listener is armed at a time ({@link #deferralArmed}); repeat
     * calls while unsized are dropped rather than each posting a fresh retry.
     *
     * <p>This replaces the former {@code invokeLater(this::fit)} busy-repost,
     * which spun the EDT at full speed forever when the canvas was never
     * sized — starving the main window's first paint (the startup-hang
     * storm). We now wait on the resize EVENT, not on a spin.
     *
     * @return true if the call was deferred (caller must return); false if
     *         the canvas is already sized and the caller should proceed.
     */
    private boolean deferUntilSized(Runnable body) {
        if (getWidth() > 0 && getHeight() > 0) {
            return false;
        }
        if (deferralArmed) {
            // A listener is already pending; when it fires it re-runs the
            // real entry point, which re-checks size. Don't stack another.
            return true;
        }
        deferralArmed = true;
        addComponentListener(new ComponentAdapter() {
            /** Per-listener latch: this instance runs its body at most once. */
            private boolean fired;

            @Override
            public void componentResized(ComponentEvent e) {
                // Wait for the FIRST real size, then run the body exactly once.
                // AWT can deliver a resize event to an already-captured
                // listener more than once (a listener removed mid-dispatch is
                // still called for an event snapshotted before removal), so we
                // latch on `fired` — remove + disarm + latch all flip before
                // body.run(), keeping the body strictly one-shot even under
                // re-delivery or a re-entrant relayout.
                if (fired || getWidth() <= 0 || getHeight() <= 0) {
                    return;
                }
                fired = true;
                deferralArmed = false;
                removeComponentListener(this);
                body.run();
            }
        });
        return true;
    }

    /** Centers the view on the design. */
    public void fit() {
        // called on load before the window system has sized this canvas:
        // dividing by width 0 slammed zoom to the floor ("way out").
        // Wait for the first real resize event, then fit against the real
        // viewport (see deferUntilSized — this used to busy-repost the EDT).
        if (deferUntilSized(this::fit)) {
            return;
        }
        fitBodyRuns++;
        var nodes = graph.getNodes();
        if (nodes.isEmpty()) {
            zoom = 1.0;
            panX = panY = 0;
        } else {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            for (InfraNode n : nodes) {
                minX = Math.min(minX, n.x);
                minY = Math.min(minY, n.y);
                maxX = Math.max(maxX, n.x + NODE_W);
                maxY = Math.max(maxY, n.y + NODE_H + 18);
            }
            double contentW = maxX - minX + 120;
            double contentH = maxY - minY + 120;
            double zx = getWidth() / Math.max(200.0, contentW);
            double zy = getHeight() / Math.max(160.0, contentH);
            // contain, never magnify: small designs stay at natural size
            zoom = Math.max(0.3, Math.min(1.0, Math.min(zx, zy)));
            // center the content in the viewport, both axes
            panX = (getWidth() - (maxX - minX) * zoom) / 2 - minX * zoom;
            panY = (getHeight() - (maxY - minY) * zoom) / 2 - minY * zoom;
        }
        repaint();
    }

    /** Current zoom factor (1.0 = natural size). */
    public double getZoom() {
        return zoom;
    }

    /** Step zoom around the viewport center - the toolbar +/- buttons. */
    public void zoomIn() {
        zoomAroundCenter(1.25);
    }

    public void zoomOut() {
        zoomAroundCenter(1 / 1.25);
    }

    private void zoomAroundCenter(double factor) {
        double newZoom = Math.max(0.3, Math.min(2.5, zoom * factor));
        double cx = getWidth() / 2.0, cy = getHeight() / 2.0;
        panX = cx - (cx - panX) * (newZoom / zoom);
        panY = cy - (cy - panY) * (newZoom / zoom);
        zoom = newZoom;
        repaint();
    }

    // ---- hit testing ----

    private InfraNode nodeAt(Point2D world) {
        var nodes = graph.getNodes();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            InfraNode n = nodes.get(i);
            if (world.getX() >= n.x && world.getX() <= n.x + NODE_W
                    && world.getY() >= n.y && world.getY() <= n.y + NODE_H) {
                return n;
            }
        }
        return null;
    }

    /** Topmost node whose output nub contains the point, inside or out. */
    private InfraNode outputNubOwnerAt(Point2D world) {
        var nodes = graph.getNodes();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            if (onOutputNub(nodes.get(i), world)) {
                return nodes.get(i);
            }
        }
        return null;
    }

    /**
     * ONE tolerance for both nub hit zones (v1.275.0 review): the press
     * side (output nub, right edge) and the drop side (input nub, left
     * edge) must accept the same halo around their dot, or a future
     * tune of one silently diverges the two ends of the same gesture.
     */
    private boolean nearNub(double edgeX, InfraNode node, Point2D world) {
        return Math.abs(world.getX() - edgeX) < 9 / zoom + 4
                && Math.abs(world.getY() - (node.y + NODE_H / 2.0)) < 12;
    }

    /** Topmost node whose input nub (left edge) contains the point. */
    private InfraNode inputNubOwnerAt(Point2D world) {
        var nodes = graph.getNodes();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            InfraNode n = nodes.get(i);
            if (nearNub(n.x, n, world)) {
                return n;
            }
        }
        return null;
    }

    private boolean onOutputNub(InfraNode node, Point2D world) {
        return node != null && nearNub(node.x + NODE_W, node, world);
    }

    private Wire wireAt(Point2D world) {
        for (Wire wire : graph.getWires()) {
            InfraNode from = graph.node(wire.fromId());
            InfraNode to = graph.node(wire.toId());
            if (from == null || to == null) {
                continue;
            }
            CubicCurve2D curve = wireCurve(from, to);
            // sample the curve; close enough beats exact
            for (double t = 0; t <= 1.0; t += 0.05) {
                Point2D pt = pointOn(curve, t);
                if (pt.distance(world) < 6 / zoom + 3) {
                    return wire;
                }
            }
        }
        return null;
    }

    private static Point2D pointOn(CubicCurve2D c, double t) {
        double mt = 1 - t;
        double x = mt * mt * mt * c.getX1() + 3 * mt * mt * t * c.getCtrlX1()
                + 3 * mt * t * t * c.getCtrlX2() + t * t * t * c.getX2();
        double y = mt * mt * mt * c.getY1() + 3 * mt * mt * t * c.getCtrlY1()
                + 3 * mt * t * t * c.getCtrlY2() + t * t * t * c.getY2();
        return new Point2D.Double(x, y);
    }

    private static CubicCurve2D wireCurve(InfraNode from, InfraNode to) {
        double x1 = from.x + NODE_W, y1 = from.y + NODE_H / 2.0;
        double x2 = to.x, y2 = to.y + NODE_H / 2.0;
        double dx = Math.max(40, Math.abs(x2 - x1) / 2);
        return new CubicCurve2D.Double(x1, y1, x1 + dx, y1, x2 - dx, y2, x2, y2);
    }

    // ---- painting ----

    @Override
    protected void paintComponent(Graphics gr) {
        super.paintComponent(gr);
        Graphics2D g = (Graphics2D) gr.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        paintGrid(g);
        g.translate(panX, panY);
        g.scale(zoom, zoom);

        for (Wire wire : graph.getWires()) {
            InfraNode from = graph.node(wire.fromId());
            InfraNode to = graph.node(wire.toId());
            if (from == null || to == null) {
                continue;
            }
            boolean selected = wire.equals(selectedWire);
            g.setColor(selected ? WIRE_SELECTED : WIRE);
            g.setStroke(new BasicStroke(selected ? 3.4f : 2.6f,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(wireCurve(from, to));
        }

        if (wireFrom != null && wireGhost != null) {
            g.setColor(new Color(0xE8, 0x74, 0x22, 180));
            g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    0, new float[]{8, 6}, 0));
            double x1 = wireFrom.x + NODE_W, y1 = wireFrom.y + NODE_H / 2.0;
            double dx = Math.max(40, Math.abs(wireGhost.getX() - x1) / 2);
            g.draw(new CubicCurve2D.Double(x1, y1, x1 + dx, y1,
                    wireGhost.getX() - dx, wireGhost.getY(), wireGhost.getX(), wireGhost.getY()));
        }

        for (InfraNode node : graph.getNodes()) {
            paintNode(g, node);
        }
        if (graph.getNodes().isEmpty()) {
            // THE CANVAS SAYS WHERE RESOURCES COME FROM (v2.122.0). The rack
            // has silkscreened this since v1.0 and the coherence pass made it
            // reachable again; its sibling canvas, which a first-time user
            // meets from the same tab strip, said nothing at all — an empty
            // dark rectangle with a DEPLOY button above it. Same idea, two
            // doors, one of them silent. It retires the moment a node lands.
            paintInvitation(g);
        }
        if (locked) {
            // unmistakable state: a cloud op is running, edits are refused
            Graphics2D banner = (Graphics2D) gr.create();
            banner.setColor(new Color(0xC6, 0x2B, 0x2B, 200));
            banner.fillRect(0, 0, getWidth(), 26);
            banner.setColor(Color.WHITE);
            banner.setFont(banner.getFont().deriveFont(Font.BOLD, 12f));
            banner.drawString(Bundle.FlowCanvas_lockedBanner(), 12, 18);
            banner.dispose();
        }
        if (hasFocus()) {
            // where the keyboard is (3.4): a ring while the canvas holds focus
            Graphics2D ring = (Graphics2D) gr.create();
            ring.setColor(org.nmox.studio.core.util.KeyboardAccess.focusColor());
            ring.setStroke(new BasicStroke(2f));
            ring.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
            ring.dispose();
        }
        g.dispose();
    }

    /**
     * The empty canvas's invitation, centred in whatever room there is.
     * Two lines because the palette answers "where do resources come from"
     * and the wire answers the question the walk of v1.271.0 found people
     * asking next — what a connection between two of them even means.
     */
    private void paintInvitation(Graphics2D g) {
        Graphics2D hint = (Graphics2D) g.create();
        hint.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        hint.setColor(new Color(0xFF, 0xFF, 0xFF, 60));
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 13f));
        String first = Bundle.FlowCanvas_emptyInvite();
        String second = Bundle.FlowCanvas_emptyInviteWire();
        FontMetrics fm = hint.getFontMetrics();
        int cx = getWidth() / 2;
        int cy = getHeight() / 2;
        hint.drawString(first, cx - fm.stringWidth(first) / 2, cy - 6);
        hint.setColor(new Color(0xFF, 0xFF, 0xFF, 40));
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
        FontMetrics fm2 = hint.getFontMetrics();
        hint.drawString(second, cx - fm2.stringWidth(second) / 2, cy + 14);
        hint.dispose();
    }

    /** True when the canvas is showing its invitation rather than a graph. */
    boolean isInviting() {
        return graph.getNodes().isEmpty();
    }

    private void paintGrid(Graphics2D g) {
        double step = 20 * zoom;
        if (step < 6) {
            return;
        }
        g.setColor(GRID_DOT);
        double startX = panX % step;
        double startY = panY % step;
        for (double x = startX; x < getWidth(); x += step) {
            for (double y = startY; y < getHeight(); y += step) {
                g.fillRect((int) x, (int) y, 1, 1);
            }
        }
    }

    private void paintNode(Graphics2D g, InfraNode node) {
        Color base = node.kind.getCategory().color;
        boolean selected = node == selectedNode;

        RoundRectangle2D body = new RoundRectangle2D.Double(node.x, node.y, NODE_W, NODE_H, 10, 10);
        // soft drop shadow
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new RoundRectangle2D.Double(node.x + 2, node.y + 3, NODE_W, NODE_H, 10, 10));
        g.setColor(base);
        g.fill(body);
        // darker left icon-band, Node-RED style
        g.setColor(base.darker());
        g.fill(new RoundRectangle2D.Double(node.x, node.y, 28, NODE_H, 10, 10));
        g.fillRect(node.x + 14, node.y, 14, NODE_H);
        g.setColor(new Color(255, 255, 255, 200));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.drawString(glyphFor(node.kind), node.x + 8, node.y + NODE_H / 2 + 5);

        g.setColor(selected ? WIRE_SELECTED : new Color(0, 0, 0, 140));
        g.setStroke(new BasicStroke(selected ? 2.4f : 1.2f));
        g.draw(body);

        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        FontMetrics fm = g.getFontMetrics();
        String label = node.label;
        while (label.length() > 1 && fm.stringWidth(label) > NODE_W - 40) {
            label = label.substring(0, label.length() - 1);
        }
        g.drawString(label, node.x + 34, node.y + NODE_H / 2 + 4);

        // port nubs: input left (when anything can wire in), output right
        g.setColor(NUB);
        g.fill(new RoundRectangle2D.Double(node.x - 5, node.y + NODE_H / 2.0 - 5, 10, 10, 3, 3));
        if (!node.kind.wiresInto().isEmpty()) {
            g.fill(new RoundRectangle2D.Double(node.x + NODE_W - 5, node.y + NODE_H / 2.0 - 5, 10, 10, 3, 3));
        }
        g.setColor(new Color(0, 0, 0, 120));
        g.draw(new RoundRectangle2D.Double(node.x - 5, node.y + NODE_H / 2.0 - 5, 10, 10, 3, 3));

        // status: live dot + text under the node
        if (node.doId != null || !node.status.isEmpty()) {
            g.setColor(node.doId != null ? LIVE_DOT : new Color(0xE8, 0xC4, 0x4A));
            g.fill(new Ellipse2D.Double(node.x + 4, node.y + NODE_H + 5, 7, 7));
            g.setColor(new Color(0x9A, 0x9D, 0xA4));
            g.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 10));
            String status = node.doId != null && node.status.isEmpty()
                    ? Bundle.FlowCanvas_liveStatus() : node.status;
            g.drawString(status, node.x + 15, node.y + NODE_H + 12);
        }
    }

    private static String glyphFor(NodeKind kind) {
        return switch (kind.getCategory()) {
            case COMPUTE -> "▣";
            case NETWORKING -> "⇄";
            case STORAGE -> "▤";
            case DATABASES -> "◫";
            case OPS -> "✚";
            case HETZNER -> "▦";
            case CLOUDFLARE -> "☁";
        };
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        InfraNode node = nodeAt(toWorld(e.getPoint()));
        if (node == null) {
            return null;
        }
        return Bundle.FlowCanvas_nodeTooltip(
                PlainText.escape(node.kind.getDisplayName()),
                PlainText.escape(node.label),
                org.nmox.studio.core.util.Numbers.display(node.monthlyUsd(), 2),
                node.doId != null
                        ? Bundle.FlowCanvas_tooltipLive(PlainText.escape(node.doId))
                        : Bundle.FlowCanvas_tooltipDesignOnly());
    }

    // ---- interaction ----

    private final class Mouse extends MouseAdapter {

        @Override
        public void mousePressed(MouseEvent e) {
            requestFocusInWindow();
            Point2D world = toWorld(e.getPoint());
            InfraNode node = nodeAt(world);
            if (e.isPopupTrigger()) {
                if (node != null) {
                    callbacks.nodeContextMenu(node, e.getLocationOnScreen());
                }
                return;
            }
            // the output nub is painted CENTERED on the node's right edge,
            // so half of the visible dot lies OUTSIDE the node rectangle —
            // gating the nub test on nodeAt() made the dot's center and
            // outer half pan the canvas instead of starting a wire
            // (v1.271.0, found by pressing the dot four times in a row)
            InfraNode nubOwner = outputNubOwnerAt(world);
            if (nubOwner != null) {
                wireFrom = nubOwner;
                wireGhost = world;
            } else if (node != null) {
                draggingNode = node;
                dragOffset = new Point((int) (world.getX() - node.x), (int) (world.getY() - node.y));
                select(node, null);
            } else {
                Wire wire = wireAt(world);
                if (wire != null) {
                    select(null, wire);
                } else {
                    select(null, null);
                    panStart = e.getPoint();
                }
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            Point2D world = toWorld(e.getPoint());
            if (wireFrom != null) {
                wireGhost = world;
                repaint();
            } else if (draggingNode != null) {
                draggingNode.x = (int) Math.round((world.getX() - dragOffset.x) / 10) * 10;
                draggingNode.y = (int) Math.round((world.getY() - dragOffset.y) / 10) * 10;
                repaint();
            } else if (panStart != null) {
                panX += e.getX() - panStart.x;
                panY += e.getY() - panStart.y;
                panStart = e.getPoint();
                repaint();
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.isPopupTrigger()) {
                InfraNode node = nodeAt(toWorld(e.getPoint()));
                if (node != null) {
                    callbacks.nodeContextMenu(node, e.getLocationOnScreen());
                }
            }
            if (wireFrom != null) {
                Point2D drop = toWorld(e.getPoint());
                // the input nub straddles the LEFT edge the same way the
                // output nub straddles the right — a drop on its outer
                // half must still count as hitting the node
                InfraNode target = nodeAt(drop);
                if (target == null) {
                    target = inputNubOwnerAt(drop);
                }
                if (target != null && !locked && target != wireFrom) { // 53b: no wiring mid-op
                    final InfraNode to = target;
                    boolean duplicate = graph.getWires().stream().anyMatch(w
                            -> w.fromId().equals(wireFrom.id) && w.toId().equals(to.id));
                    if (!graph.connect(wireFrom, target)) {
                        callbacks.wireRefused(wireFrom, target, duplicate);
                    }
                }
                wireFrom = null;
                wireGhost = null;
                repaint();
            }
            if (draggingNode != null) {
                graph.touch();
                draggingNode = null;
            }
            panStart = null;
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
                InfraNode node = nodeAt(toWorld(e.getPoint()));
                if (node != null) {
                    callbacks.nodeDoubleClicked(node);
                }
            }
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            double factor = e.getWheelRotation() < 0 ? 1.1 : 1 / 1.1;
            double newZoom = Math.max(0.35, Math.min(2.5, zoom * factor));
            // zoom around the cursor
            Point2D before = toWorld(e.getPoint());
            zoom = newZoom;
            Point after = toScreen(before.getX(), before.getY());
            panX += e.getX() - after.x;
            panY += e.getY() - after.y;
            repaint();
        }
    }

    /** Drop target for the palette: a NodeKind name lands as a new node. */
    private final class PaletteDrop extends TransferHandler {

        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.stringFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (locked) {
                return false; // 53b: no palette drops mid-op
            }
            try {
                String name = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                NodeKind kind = NodeKind.valueOf(name);
                Point drop = support.isDrop() ? support.getDropLocation().getDropPoint()
                        : new Point(getWidth() / 2, getHeight() / 2);
                Point2D world = toWorld(drop);
                InfraNode node = graph.addNode(kind,
                        (int) Math.round(world.getX() / 10) * 10,
                        (int) Math.round(world.getY() / 10) * 10);
                select(node, null);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }
}
