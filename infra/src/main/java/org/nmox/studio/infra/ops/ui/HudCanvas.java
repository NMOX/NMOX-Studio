package org.nmox.studio.infra.ops.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import org.nmox.studio.infra.ops.OpsEvent;
import org.nmox.studio.infra.ops.OpsHub;
import org.nmox.studio.infra.ops.model.HostStatus;
import org.nmox.studio.infra.ops.model.OpsEdge;
import org.nmox.studio.infra.ops.model.OpsKind;
import org.nmox.studio.infra.ops.model.OpsNode;
import org.nmox.studio.infra.ops.model.Telemetry;
import org.nmox.studio.infra.ops.model.Topology;
import org.openide.util.NbBundle.Messages;

/**
 * The DevOps HUD: the topology painted as a living picture. Nodes glide
 * to where the layout puts them for the current view, gauges follow the
 * readings a probe brought back, a host still becoming usable sweeps its
 * ring, and a pulse runs along a link only when something real crossed
 * it — a probe answered, a deploy step ended. Nothing moves when nothing
 * happened: the frame timer runs only while a spring is unsettled, a
 * sweep is due or a pulse is alive, and under reduced motion every
 * spring snaps.
 *
 * <p>Operable without a mouse: Tab focuses the canvas, arrows move the
 * selection through the view's nodes in layout order, Enter drills into
 * a host, Escape climbs out, and the accessible name reads the selection
 * so a screen reader hears what the eye sees. A right-click (or the
 * context-menu key) opens the inspector's actions for the node under it.
 *
 * <p>A {@link HudCamera} stands between the layout's world and the
 * pixels: the wheel zooms about the cursor, a drag on empty space pans,
 * {@code +}/{@code -} zoom, {@code 0} fits, Shift+arrows pan, and a
 * double-click on empty space fits. Positions and radii follow the
 * camera; text never does — a label is the same size at every zoom, and
 * a node too small to carry its readings simply stops printing them, so
 * zooming out is a level of abstraction and not a squint. Every place a
 * node's position is read (paint, hit test, the context-menu key, the
 * accessible bounds a screen reader is pointed at) goes through
 * {@link #sx}, {@link #sy} and {@link #sr}.
 */
@Messages({
    "HudCanvas_accessibleName=Ops Hub topology",
    "# {0} - how many hosts",
    "# {1} - how many services",
    "HudCanvas_accessibleDescription=Fleet: hosts {0}, services {1}. Arrows move, Enter opens a host, Escape returns. Plus and minus zoom, zero fits, Shift with an arrow pans.",
    "# {0} - the zoom as a whole percentage",
    "HudCanvas_zoomReadout={0}% — 0 fits",
    "# {0} - the node's label",
    "# {1} - its kind",
    "# {2} - its status",
    "HudCanvas_accessibleSelected=Selected {0}, {1}, {2}",
    "HudCanvas_banner=SIMULATION — nothing here is real",
    "HudCanvas_emptyTitle=No servers yet",
    "HudCanvas_emptyHint=New Server… asks a provider for one. Add Host… reaches a server you already have. Simulation shows the loop without a bill.",
    "HudCanvas_kindWorkstation=this machine",
    "HudCanvas_kindHost=host",
    "HudCanvas_kindService=service",
    "HudCanvas_statusRequested=requested",
    "HudCanvas_statusProvisioning=provisioning",
    "HudCanvas_statusBooting=booting",
    "HudCanvas_statusConfiguring=configuring",
    "HudCanvas_statusReady=ready",
    "HudCanvas_statusUnreachable=unreachable",
    "HudCanvas_statusDestroying=destroying",
    "HudCanvas_statusGone=gone",
    "HudCanvas_simBadge=SIM",
    "HudCanvas_viewFleet=Fleet",
    "HudCanvas_viewServices=Workloads",
    "HudCanvas_legend=observed ——  inferred – –  declared · · ·"
})
public final class HudCanvas extends JPanel {

    /** A node's animated place. */
    private static final class Glide {
        final Motion.Spring x;
        final Motion.Spring y;
        final Motion.Spring r;
        final Motion.Spring load = new Motion.Spring(0);
        final Motion.Spring mem = new Motion.Spring(0);

        Glide(double x0, double y0, double r0) {
            x = new Motion.Spring(x0);
            y = new Motion.Spring(y0);
            r = new Motion.Spring(r0);
        }

        boolean settled() {
            return x.settled() && y.settled() && r.settled() && load.settled() && mem.settled();
        }

        void step(double dt) {
            x.step(dt, Motion.LAYOUT_K);
            y.step(dt, Motion.LAYOUT_K);
            r.step(dt, Motion.LAYOUT_K);
            load.step(dt, Motion.GAUGE_K);
            mem.step(dt, Motion.GAUGE_K);
        }

        void snap() {
            x.snap();
            y.snap();
            r.snap();
            load.snap();
            mem.snap();
        }
    }

    /** A dot travelling an edge because something real crossed it. */
    private record Pulse(String from, String to, long startedAt, long duration, Color color) {
    }

    private static final int FRAME_MS = 16;
    private static final long PULSE_MS = 900;
    /** One Shift+arrow, in pixels. */
    private static final int PAN_STEP = 80;

    private final OpsHub hub;
    private final Topology topology;
    private final Map<String, Glide> glides = new HashMap<>();
    private final List<Pulse> pulses = new ArrayList<>();
    private final Timer frames = new Timer(FRAME_MS, this::frame);
    private HudView view = HudView.FLEET;
    private Map<String, HudLayout.Place> places = Map.of();
    private String selected;
    private String hovered;
    private long lastFrame;
    private final HudCamera camera = new HudCamera();
    /** Where a drag on empty space began (screen), or null when no pan is under way. */
    private java.awt.Point panFrom;
    private boolean panned;
    private Runnable onCamera = () -> { };
    private Consumer<String> onSelect = id -> { };
    private Consumer<HudView> onView = v -> { };
    private java.util.function.BiConsumer<String, Point2D> onContextMenu = (id, at) -> { };
    private final Topology.Listener topologyListener = new Topology.Listener() {
        @Override
        public void structureChanged() {
            relayout();
        }

        @Override
        public void nodeChanged(OpsNode node) {
            onNodeChanged(node);
        }
    };
    private final OpsHub.Listener hubListener = new OpsHub.Listener() {
        @Override
        public void event(OpsEvent event) {
            onEvent(event);
        }

        @Override
        public void stateChanged() {
            repaint();
        }
    };
    private javax.accessibility.AccessibleContext accessible;

    public HudCanvas(OpsHub hub) {
        this.hub = hub;
        this.topology = hub.topology();
        setBackground(HudStyle.GROUND);
        setOpaque(true);
        setFocusable(true);
        setPreferredSize(new Dimension(760, 480));
        frames.setCoalesce(true);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                String id = nodeAt(e.getX(), e.getY());
                if (e.isPopupTrigger()) {
                    popup(id, e);
                    return;
                }
                if (id != null) {
                    select(id);
                }
                if (e.getClickCount() == 2) {
                    if (id != null) {
                        drillInto(id);
                    } else {
                        fit();
                    }
                }
                // empty space: this press may become a pan, and a pan must not cost the user their selection —
                // so the deselect waits for the release and happens only if nothing was dragged
                panFrom = id == null && javax.swing.SwingUtilities.isLeftMouseButton(e) ? e.getPoint() : null;
                panned = false;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (panFrom != null) {
                    panFrom = null;
                    setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.DEFAULT_CURSOR));
                    if (!panned && e.getClickCount() < 2) {
                        select(null);
                    }
                }
                if (e.isPopupTrigger()) {
                    popup(nodeAt(e.getX(), e.getY()), e);
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (panFrom == null) {
                    return;
                }
                int dx = e.getX() - panFrom.x;
                int dy = e.getY() - panFrom.y;
                if (dx == 0 && dy == 0) {
                    return;
                }
                panFrom = e.getPoint();
                panned = true;
                setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));
                camera.panBy(dx, dy, false);
                cameraMoved();
            }

            @Override
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                double notches = e.getPreciseWheelRotation();
                if (notches == 0) {
                    return;
                }
                // a wheel is the user's hand: it moves the picture now, never on a spring
                if (camera.zoomAbout(e.getX(), e.getY(), Math.pow(HudCamera.WHEEL_STEP, -notches), false)) {
                    cameraMoved();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                String id = nodeAt(e.getX(), e.getY());
                if (!java.util.Objects.equals(id, hovered)) {
                    hovered = id;
                    setCursor(java.awt.Cursor.getPredefinedCursor(
                            id == null ? java.awt.Cursor.DEFAULT_CURSOR : java.awt.Cursor.HAND_CURSOR));
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hovered != null) {
                    hovered = null;
                    repaint();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        bindKeys();
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                relayout();
            }
        });
    }

    private void bindKeys() {
        bind("RIGHT", () -> move(1));
        bind("DOWN", () -> move(1));
        bind("LEFT", () -> move(-1));
        bind("UP", () -> move(-1));
        bind("ENTER", () -> {
            if (selected != null) {
                drillInto(selected);
            }
        });
        bind("ESCAPE", () -> setView(view.up()));
        bind("CONTEXT_MENU", () -> {
            if (selected != null) {
                Glide g = glides.get(selected);
                onContextMenu.accept(selected, g == null ? new Point2D.Double(10, 10)
                        : new Point2D.Double(sx(g), sy(g)));
            }
        });
        bind("shift F10", () -> getActionMap().get("CONTEXT_MENU").actionPerformed(null));
        // the camera: typed characters, so the key that PRINTS + zooms in on every layout
        bind(KeyStroke.getKeyStroke('+'), "zoom-in", this::zoomIn);
        bind(KeyStroke.getKeyStroke('='), "zoom-in", this::zoomIn);
        bind(KeyStroke.getKeyStroke('-'), "zoom-out", this::zoomOut);
        bind(KeyStroke.getKeyStroke('0'), "fit", this::fit);
        bind(KeyStroke.getKeyStroke("HOME"), "fit", this::fit);
        bind(KeyStroke.getKeyStroke("shift LEFT"), "pan-left", () -> panBy(PAN_STEP, 0));
        bind(KeyStroke.getKeyStroke("shift RIGHT"), "pan-right", () -> panBy(-PAN_STEP, 0));
        bind(KeyStroke.getKeyStroke("shift UP"), "pan-up", () -> panBy(0, PAN_STEP));
        bind(KeyStroke.getKeyStroke("shift DOWN"), "pan-down", () -> panBy(0, -PAN_STEP));
    }

    private void bind(String stroke, Runnable r) {
        bind(KeyStroke.getKeyStroke(stroke), stroke, r);
    }

    private void bind(KeyStroke stroke, String name, Runnable r) {
        getInputMap(WHEN_FOCUSED).put(stroke, name);
        bindAction(name, r);
    }

    private void bindAction(String stroke, Runnable r) {
        getActionMap().put(stroke, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        });
    }

    // ---- lifecycle ----

    @Override
    public void addNotify() {
        super.addNotify();
        topology.addListener(topologyListener);
        hub.addListener(hubListener);
        relayout();
    }

    @Override
    public void removeNotify() {
        topology.removeListener(topologyListener);
        hub.removeListener(hubListener);
        frames.stop();
        super.removeNotify();
    }

    public void onSelect(Consumer<String> listener) {
        this.onSelect = listener;
    }

    public void onViewChanged(Consumer<HudView> listener) {
        this.onView = listener;
    }

    public void onContextMenu(java.util.function.BiConsumer<String, Point2D> listener) {
        this.onContextMenu = listener;
    }

    /** Told whenever the camera moves or changes mode — the window's Fit button and zoom readout follow it. */
    public void onCameraChanged(Runnable listener) {
        this.onCamera = listener;
    }

    public HudView view() {
        return view;
    }

    /** The camera, for reading: its zoom, whether the user has moved it. */
    public HudCamera camera() {
        return camera;
    }

    // ---- the camera's doors ----

    /** Frames the whole view again and hands the camera back to the layout. */
    public void fit() {
        camera.fit(HudLayout.bounds(places), getWidth(), getHeight(), !ReducedMotion.reduced());
        cameraMoved();
    }

    public void zoomIn() {
        zoomAtCentre(HudCamera.KEY_STEP);
    }

    public void zoomOut() {
        zoomAtCentre(1 / HudCamera.KEY_STEP);
    }

    private void zoomAtCentre(double factor) {
        // about the selection when there is one on screen (that is what the user is looking at), else the middle
        double cx = getWidth() / 2.0;
        double cy = getHeight() / 2.0;
        Glide g = selected == null ? null : glides.get(selected);
        if (g != null && g.r.value() > 0 && sx(g) >= 0 && sx(g) <= getWidth() && sy(g) >= 0 && sy(g) <= getHeight()) {
            cx = sx(g);
            cy = sy(g);
        }
        if (camera.zoomAbout(cx, cy, factor, !ReducedMotion.reduced())) {
            cameraMoved();
        }
    }

    private void panBy(int dx, int dy) {
        camera.panBy(dx, dy, !ReducedMotion.reduced());
        cameraMoved();
    }

    /** After any camera move: keep the content reachable, tell the window, animate or repaint. */
    private void cameraMoved() {
        if (camera.manual()) {
            camera.clamp(HudLayout.bounds(places), getWidth(), getHeight());
        }
        onCamera.run();
        ensureFrames();
        repaint();
    }

    /** A glide's centre and radius on screen: the one place world becomes pixels. */
    private double sx(Glide g) {
        return camera.sx(g.x.value());
    }

    private double sy(Glide g) {
        return camera.sy(g.y.value());
    }

    private double sr(Glide g) {
        return g.r.value() * camera.zoom();
    }

    public String selected() {
        return selected;
    }

    public void setView(HudView v) {
        if (v.equals(view)) {
            return;
        }
        view = v;
        onView.accept(v);
        // another view is another picture: frame it, whatever the user did to the last one
        camera.release();
        relayout();
    }

    public void select(String id) {
        if (java.util.Objects.equals(id, selected)) {
            return;
        }
        String old = accessibleSelection();
        selected = id;
        onSelect.accept(id);
        if (accessible != null) {
            accessible.firePropertyChange(javax.accessibility.AccessibleContext.ACCESSIBLE_NAME_PROPERTY,
                    old, accessibleSelection());
        }
        repaint();
    }

    private void drillInto(String id) {
        OpsNode n = topology.node(id);
        if (n != null && n.kind == OpsKind.HOST) {
            setView(HudView.host(id));
        } else if (n != null && n.kind == OpsKind.SERVICE && n.hostId != null) {
            setView(HudView.host(n.hostId));
            select(id);
        }
    }

    private void move(int delta) {
        List<String> order = HudLayout.visible(places);
        if (order.isEmpty()) {
            return;
        }
        int at = selected == null ? -1 : order.indexOf(selected);
        int next = at < 0 ? (delta > 0 ? 0 : order.size() - 1) : Math.floorMod(at + delta, order.size());
        select(order.get(next));
        // the keyboard's selection must stay where the eye can follow: reveal it by its TARGET place
        HudLayout.Place p = places.get(order.get(next));
        if (p != null && camera.reveal(p.x(), p.y(), p.r(), getWidth(), getHeight(), !ReducedMotion.reduced())) {
            cameraMoved();
        }
    }

    private void popup(String id, MouseEvent e) {
        if (id != null) {
            select(id);
            onContextMenu.accept(id, new Point2D.Double(e.getX(), e.getY()));
        }
    }

    // ---- model → animation ----

    /** Recomputes targets for the current view and lets the springs go. */
    public void relayout() {
        int w = Math.max(200, getWidth());
        int h = Math.max(160, getHeight());
        places = HudLayout.layout(topology, view, w, h);
        boolean snap = ReducedMotion.reduced();
        double wx = w * 0.14;
        for (Map.Entry<String, HudLayout.Place> e : places.entrySet()) {
            HudLayout.Place p = e.getValue();
            Glide g = glides.get(e.getKey());
            if (g == null) {
                // a new node is born at the workstation, where its link starts, and grows into place
                g = new Glide(wx, h / 2.0, 0);
                glides.put(e.getKey(), g);
            }
            g.x.setTarget(p.x());
            g.y.setTarget(p.y());
            g.r.setTarget(p.r());
            OpsNode n = topology.node(e.getKey());
            if (n != null) {
                aimGauges(n, g);
            }
            if (snap) {
                g.snap();
            }
        }
        glides.keySet().removeIf(id -> topology.node(id) == null && !places.containsKey(id));
        if (selected != null && topology.node(selected) == null) {
            select(null);
        }
        if (getWidth() > 0 && getHeight() > 0) {
            if (camera.manual()) {
                camera.clamp(HudLayout.bounds(places), getWidth(), getHeight());
            } else {
                camera.fit(HudLayout.bounds(places), getWidth(), getHeight(), !snap);
            }
            onCamera.run();
        }
        if (accessible != null) {
            accessible.firePropertyChange(javax.accessibility.AccessibleContext.ACCESSIBLE_DESCRIPTION_PROPERTY,
                    null, accessible.getAccessibleDescription());
        }
        ensureFrames();
        repaint();
    }

    private void aimGauges(OpsNode n, Glide g) {
        Telemetry.Sample s = n.telemetry.latest();
        g.load.setTarget(s == null || Double.isNaN(s.loadShare()) ? 0 : Math.min(1.0, s.loadShare()));
        g.mem.setTarget(s == null || Double.isNaN(s.memShare()) ? 0 : Math.min(1.0, s.memShare()));
    }

    private void onNodeChanged(OpsNode node) {
        Glide g = glides.get(node.id);
        if (g != null) {
            double before = g.load.target();
            aimGauges(node, g);
            if (ReducedMotion.reduced()) {
                g.snap();
            } else if (node.kind == OpsKind.HOST && node.telemetry.size() > 0 && g.load.target() != before) {
                // a reading changed: the link carried it
                pulse(Topology.WORKSTATION_ID, node.id, HudStyle.PHOSPHOR);
            }
        }
        if (selected != null && selected.equals(node.id) && accessible != null) {
            accessible.firePropertyChange(javax.accessibility.AccessibleContext.ACCESSIBLE_NAME_PROPERTY,
                    null, accessibleSelection());
        }
        ensureFrames();
        repaint();
    }

    private void onEvent(OpsEvent event) {
        if (event.nodeId() == null || ReducedMotion.reduced()) {
            return;
        }
        OpsNode n = topology.node(event.nodeId());
        if (n == null) {
            return;
        }
        String hostId = n.kind == OpsKind.HOST ? n.id : n.hostId;
        if (hostId != null && topology.node(hostId) != null) {
            Color c = switch (event.kind()) {
                case FAIL -> HudStyle.TROUBLE;
                case OK -> HudStyle.READY;
                default -> HudStyle.PHOSPHOR;
            };
            pulse(Topology.WORKSTATION_ID, hostId, c);
        }
    }

    private void pulse(String from, String to, Color color) {
        if (pulses.size() > 40) {
            pulses.remove(0);
        }
        pulses.add(new Pulse(from, to, System.currentTimeMillis(), PULSE_MS, color));
        ensureFrames();
    }

    /** Starts the frame timer when there is something to move; stops it when there is not. */
    private void ensureFrames() {
        if (animating()) {
            if (!frames.isRunning()) {
                lastFrame = System.currentTimeMillis();
                frames.start();
            }
        } else if (frames.isRunning()) {
            frames.stop();
        }
    }

    private boolean animating() {
        if (!pulses.isEmpty() || !camera.settled()) {
            return true;
        }
        for (Glide g : glides.values()) {
            if (!g.settled()) {
                return true;
            }
        }
        if (ReducedMotion.reduced()) {
            return false;
        }
        for (OpsNode n : topology.nodes(OpsKind.HOST)) {
            if (n.status.inProgress()) {
                return true;
            }
        }
        return false;
    }

    private void frame(ActionEvent e) {
        long now = System.currentTimeMillis();
        double dt = Math.min(0.05, (now - lastFrame) / 1000.0);
        lastFrame = now;
        for (Glide g : glides.values()) {
            g.step(dt);
        }
        if (!camera.settled()) {
            camera.step(dt);
            onCamera.run();
        }
        pulses.removeIf(p -> now - p.startedAt > p.duration || glides.get(p.from) == null || glides.get(p.to) == null);
        if (!animating()) {
            frames.stop();
        }
        repaint();
    }

    // ---- hit testing ----

    String nodeAt(int px, int py) {
        String best = null;
        double bestD = Double.MAX_VALUE;
        for (Map.Entry<String, Glide> e : glides.entrySet()) {
            Glide g = e.getValue();
            if (g.r.value() <= 0) {
                continue;
            }
            double d = Point2D.distance(px, py, sx(g), sy(g));
            double hit = sr(g) + 8;
            if (d <= hit && d < bestD) {
                bestD = d;
                best = e.getKey();
            }
        }
        return best;
    }

    // ---- painting ----

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            paintGrid(g);
            paintEdges(g);
            paintPulses(g);
            for (String id : paintOrder()) {
                paintNode(g, id);
            }
            paintChrome(g);
        } finally {
            g.dispose();
        }
    }

    private List<String> paintOrder() {
        List<String> ids = new ArrayList<>(glides.keySet());
        // services under their hosts under the workstation; the selection last so its ring is on top
        ids.sort((a, b) -> {
            OpsNode na = topology.node(a);
            OpsNode nb = topology.node(b);
            int ka = na == null ? 0 : na.kind == OpsKind.SERVICE ? 0 : na.kind == OpsKind.HOST ? 1 : 2;
            int kb = nb == null ? 0 : nb.kind == OpsKind.SERVICE ? 0 : nb.kind == OpsKind.HOST ? 1 : 2;
            return Integer.compare(ka, kb);
        });
        if (selected != null && ids.remove(selected)) {
            ids.add(selected);
        }
        return ids;
    }

    /** The grid is the world's floor: it slides and scales with the camera, so a pan is felt and not only inferred. */
    private void paintGrid(Graphics2D g) {
        int w = getWidth();
        int h = getHeight();
        double fine = 24 * camera.zoom();
        if (fine >= 9) {
            // below nine pixels the fine lines are noise; the strong ones carry the floor alone
            g.setColor(HudStyle.GRID);
            gridLines(g, fine, w, h);
        }
        g.setColor(HudStyle.GRID_STRONG);
        gridLines(g, fine * 5, w, h);
    }

    private void gridLines(Graphics2D g, double step, int w, int h) {
        double x0 = Math.floorMod((long) Math.round(camera.panX() * 1000), (long) Math.round(step * 1000)) / 1000.0;
        double y0 = Math.floorMod((long) Math.round(camera.panY() * 1000), (long) Math.round(step * 1000)) / 1000.0;
        for (double x = x0; x < w; x += step) {
            int xi = (int) Math.round(x);
            g.drawLine(xi, 0, xi, h);
        }
        for (double y = y0; y < h; y += step) {
            int yi = (int) Math.round(y);
            g.drawLine(0, yi, w, yi);
        }
    }

    /** The curve an edge follows between two glides: a quad bowed a little off the straight line. */
    private QuadCurve2D curve(Glide a, Glide b) {
        double ax = sx(a);
        double ay = sy(a);
        double bx = sx(b);
        double by = sy(b);
        double mx = (ax + bx) / 2;
        double my = (ay + by) / 2;
        double dx = bx - ax;
        double dy = by - ay;
        double len = Math.max(1, Math.hypot(dx, dy));
        double bow = Math.min(40, len * 0.12);
        return new QuadCurve2D.Double(ax, ay, mx - dy / len * bow, my + dx / len * bow, bx, by);
    }

    private void paintEdges(Graphics2D g) {
        for (OpsEdge e : topology.edges()) {
            Glide a = glides.get(e.from());
            Glide b = glides.get(e.to());
            if (a == null || b == null || a.r.value() <= 0 || b.r.value() <= 0) {
                continue;
            }
            boolean lit = selected != null && (selected.equals(e.from()) || selected.equals(e.to()));
            Color c = HudStyle.edgeColor(e.evidence());
            g.setColor(lit ? c : withAlpha(c, 120));
            float[] dash = HudStyle.edgeDash(e.evidence());
            g.setStroke(dash == null ? new BasicStroke(lit ? 2.2f : 1.4f)
                    : new BasicStroke(lit ? 2.2f : 1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, dash, 0f));
            g.draw(curve(a, b));
        }
    }

    private void paintPulses(Graphics2D g) {
        long now = System.currentTimeMillis();
        for (Pulse p : pulses) {
            Glide a = glides.get(p.from);
            Glide b = glides.get(p.to);
            if (a == null || b == null) {
                continue;
            }
            double t = Math.min(1, (now - p.startedAt) / (double) p.duration);
            QuadCurve2D c = curve(a, b);
            // a point on the quad at t
            double x = (1 - t) * (1 - t) * c.getX1() + 2 * (1 - t) * t * c.getCtrlX() + t * t * c.getX2();
            double y = (1 - t) * (1 - t) * c.getY1() + 2 * (1 - t) * t * c.getCtrlY() + t * t * c.getY2();
            int alpha = (int) (220 * (1 - Math.abs(2 * t - 1) * 0.6));
            g.setColor(withAlpha(p.color, alpha));
            g.fill(new Ellipse2D.Double(x - 3.5, y - 3.5, 7, 7));
            g.setColor(withAlpha(p.color, alpha / 3));
            g.fill(new Ellipse2D.Double(x - 7, y - 7, 14, 14));
        }
    }

    private void paintNode(Graphics2D g, String id) {
        Glide gl = glides.get(id);
        OpsNode n = topology.node(id);
        if (gl == null || n == null) {
            return;
        }
        double r = sr(gl);
        if (r < 1) {
            return;
        }
        double x = sx(gl);
        double y = sy(gl);
        boolean sel = id.equals(selected);
        boolean hov = id.equals(hovered);
        switch (n.kind) {
            case WORKSTATION -> paintWorkstation(g, n, x, y, r, sel || hov);
            case HOST -> paintHost(g, n, gl, x, y, r, sel, hov);
            case SERVICE -> paintService(g, n, x, y, r, sel || hov);
        }
    }

    private void paintWorkstation(Graphics2D g, OpsNode n, double x, double y, double r, boolean lit) {
        double w = r * 2.2;
        double h = r * 1.5;
        RoundRectangle2D screen = new RoundRectangle2D.Double(x - w / 2, y - h / 2 - r * 0.15, w, h, 8, 8);
        g.setColor(withAlpha(HudStyle.PHOSPHOR, 28));
        g.fill(screen);
        g.setStroke(new BasicStroke(lit ? 2.4f : 1.6f));
        g.setColor(lit ? HudStyle.SELECTION : HudStyle.PHOSPHOR);
        g.draw(screen);
        g.fill(new RoundRectangle2D.Double(x - r * 0.5, y + h / 2 - r * 0.15 + 4, r, 4, 2, 2));
        label(g, n.label, x, y + h / 2 + r * 0.6, HudStyle.TEXT, HudStyle.ui(12f, true));
    }

    private void paintHost(Graphics2D g, OpsNode n, Glide gl, double x, double y, double r, boolean sel, boolean hov) {
        Color status = HudStyle.statusColor(n.status);
        // ground disc
        g.setColor(withAlpha(status, 22));
        g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        // the load gauge: an arc from the top, clockwise, its length the share of a core in use
        double loadShare = gl.load.value();
        double memShare = gl.mem.value();
        Telemetry.Sample sample = n.telemetry.latest();
        g.setStroke(new BasicStroke(Math.max(2f, (float) (r * 0.12)), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.setColor(withAlpha(status, 60));
        g.draw(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        if (sample != null && !Double.isNaN(sample.loadShare())) {
            g.setColor(status);
            g.draw(new Arc2D.Double(x - r, y - r, 2 * r, 2 * r, 90, -360 * Math.min(1, loadShare), Arc2D.OPEN));
        }
        double r2 = r * 0.72;
        if (sample != null && !Double.isNaN(sample.memShare())) {
            g.setStroke(new BasicStroke(Math.max(1.5f, (float) (r * 0.08)), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            g.setColor(withAlpha(HudStyle.SERVICE, 70));
            g.draw(new Ellipse2D.Double(x - r2, y - r2, 2 * r2, 2 * r2));
            g.setColor(HudStyle.SERVICE);
            g.draw(new Arc2D.Double(x - r2, y - r2, 2 * r2, 2 * r2, 90, -360 * Math.min(1, memShare), Arc2D.OPEN));
        }
        // still becoming: a sweep that turns with time (it IS the information: the host is not ready)
        if (n.status.inProgress()) {
            double angle = ReducedMotion.reduced() ? 90 : 90 - (System.currentTimeMillis() % 1400) / 1400.0 * 360;
            g.setStroke(new BasicStroke(Math.max(2f, (float) (r * 0.12)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(HudStyle.BECOMING);
            g.draw(new Arc2D.Double(x - r, y - r, 2 * r, 2 * r, angle, -70, Arc2D.OPEN));
        }
        // the core: a hexagon, the server itself
        double hr = r * 0.42;
        Path2D hex = new Path2D.Double();
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 6 + i * Math.PI / 3;
            double px = x + Math.cos(a) * hr;
            double py = y + Math.sin(a) * hr;
            if (i == 0) {
                hex.moveTo(px, py);
            } else {
                hex.lineTo(px, py);
            }
        }
        hex.closePath();
        g.setColor(withAlpha(status, n.status == HostStatus.READY ? 160 : 90));
        g.fill(hex);
        g.setStroke(new BasicStroke(1.4f));
        g.setColor(status);
        g.draw(hex);
        if (sel || hov) {
            g.setStroke(new BasicStroke(sel ? 2f : 1.2f));
            g.setColor(sel ? HudStyle.SELECTION : withAlpha(HudStyle.SELECTION, 120));
            double rr = r + 6;
            g.draw(new Ellipse2D.Double(x - rr, y - rr, 2 * rr, 2 * rr));
        }
        // text: name, then the status or the address, then readings when there is room
        Font name = HudStyle.ui(r >= 34 ? 12.5f : 11f, true);
        label(g, n.label, x, y + r + 16, HudStyle.TEXT, name);
        String second = n.status == HostStatus.READY && n.address != null ? n.address : statusWord(n.status);
        label(g, second, x, y + r + 30, n.status == HostStatus.READY ? HudStyle.MUTED : status, HudStyle.mono(10.5f));
        if (sample != null && r >= 34) {
            String reading = (Double.isNaN(sample.loadShare()) ? "" : "load " + fmt(sample.load1()))
                    + (Double.isNaN(sample.memShare()) ? "" : "  mem " + Math.round(sample.memShare() * 100) + "%");
            if (!reading.isBlank()) {
                label(g, reading.trim(), x, y + r + 44, HudStyle.MUTED, HudStyle.mono(10f));
            }
        }
        if (n.simulated) {
            badge(g, Bundle.HudCanvas_simBadge(), x + r * 0.7, y - r * 0.9);
        }
    }

    private void paintService(Graphics2D g, OpsNode n, double x, double y, double r, boolean lit) {
        RoundRectangle2D box = new RoundRectangle2D.Double(x - r, y - r, 2 * r, 2 * r, r * 0.6, r * 0.6);
        g.setColor(withAlpha(HudStyle.SERVICE, 40));
        g.fill(box);
        g.setStroke(new BasicStroke(lit ? 2.2f : 1.4f));
        g.setColor(lit ? HudStyle.SELECTION : HudStyle.SERVICE);
        g.draw(box);
        g.setColor(HudStyle.SERVICE);
        g.fill(new Ellipse2D.Double(x - r * 0.25, y - r * 0.25, r * 0.5, r * 0.5));
        if (r >= 16) {
            label(g, n.label, x, y + r + 14, HudStyle.TEXT, HudStyle.ui(11f, false));
            String ports = n.facts.get("ports");
            if (ports != null && r >= 22) {
                label(g, ports.length() > 28 ? ports.substring(0, 27) + "…" : ports, x, y + r + 27, HudStyle.MUTED, HudStyle.mono(9.5f));
            }
        } else {
            label(g, n.label, x, y + r + 12, HudStyle.MUTED, HudStyle.ui(9.5f, false));
        }
        if (n.simulated && r >= 16) {
            badge(g, Bundle.HudCanvas_simBadge(), x + r * 0.9, y - r * 1.1);
        }
    }

    private void paintChrome(Graphics2D g) {
        int w = getWidth();
        int h = getHeight();
        if (hub.simulating()) {
            g.setColor(HudStyle.BANNER);
            g.fillRect(0, 0, w, 22);
            g.setColor(HudStyle.BANNER_TEXT);
            g.setFont(HudStyle.ui(11.5f, true));
            FontMetrics fm = g.getFontMetrics();
            String text = Bundle.HudCanvas_banner();
            g.drawString(text, (w - fm.stringWidth(text)) / 2, 15);
        }
        // breadcrumb
        g.setFont(HudStyle.ui(11.5f, true));
        g.setColor(HudStyle.MUTED);
        String crumb = switch (view.level()) {
            case FLEET -> Bundle.HudCanvas_viewFleet();
            case SERVICES -> Bundle.HudCanvas_viewServices();
            case HOST -> {
                OpsNode host = topology.node(view.hostId());
                yield Bundle.HudCanvas_viewFleet() + " ▸ " + (host == null ? "?" : host.label);
            }
        };
        g.drawString(crumb, 12, hub.simulating() ? 40 : 18);
        // legend
        g.setFont(HudStyle.ui(10f, false));
        FontMetrics fm = g.getFontMetrics();
        String legend = Bundle.HudCanvas_legend();
        g.drawString(legend, w - fm.stringWidth(legend) - 12, h - 10);
        if (!camera.identity()) {
            // said only while it is true: an untouched HUD carries no readout
            g.drawString(Bundle.HudCanvas_zoomReadout(Long.toString(Math.round(camera.targetZoom() * 100))), 12, h - 10);
        }
        if (topology.nodes(OpsKind.HOST).isEmpty()) {
            g.setFont(HudStyle.ui(18f, true));
            g.setColor(HudStyle.TEXT);
            fm = g.getFontMetrics();
            String title = Bundle.HudCanvas_emptyTitle();
            int cx = (int) (w * 0.58);
            g.drawString(title, cx - fm.stringWidth(title) / 2, h / 2 - 10);
            g.setFont(HudStyle.ui(12f, false));
            g.setColor(HudStyle.MUTED);
            wrapped(g, Bundle.HudCanvas_emptyHint(), cx, h / 2 + 12, Math.min(420, w - 60));
        }
    }

    private void wrapped(Graphics2D g, String text, int cx, int y, int width) {
        FontMetrics fm = g.getFontMetrics();
        StringBuilder line = new StringBuilder();
        int yy = y;
        for (String word : text.split(" ")) {
            String trial = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(trial) > width && !line.isEmpty()) {
                g.drawString(line.toString(), cx - fm.stringWidth(line.toString()) / 2, yy);
                yy += fm.getHeight();
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(trial);
            }
        }
        if (!line.isEmpty()) {
            g.drawString(line.toString(), cx - fm.stringWidth(line.toString()) / 2, yy);
        }
    }

    private void label(Graphics2D g, String text, double cx, double baseline, Color color, Font font) {
        g.setFont(font);
        g.setColor(color);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (float) (cx - fm.stringWidth(text) / 2.0), (float) baseline);
    }

    private void badge(Graphics2D g, String text, double x, double y) {
        g.setFont(HudStyle.ui(9f, true));
        FontMetrics fm = g.getFontMetrics();
        int bw = fm.stringWidth(text) + 8;
        int bh = fm.getHeight() + 2;
        RoundRectangle2D box = new RoundRectangle2D.Double(x, y - bh / 2.0, bw, bh, 6, 6);
        g.setColor(HudStyle.BANNER);
        g.fill(box);
        g.setColor(HudStyle.BANNER_TEXT);
        g.drawString(text, (float) x + 4, (float) (y + fm.getAscent() / 2.0 - 1));
    }

    static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    /** The status as a word in the reader's language. */
    public static String statusWord(HostStatus s) {
        return switch (s) {
            case REQUESTED -> Bundle.HudCanvas_statusRequested();
            case PROVISIONING -> Bundle.HudCanvas_statusProvisioning();
            case BOOTING -> Bundle.HudCanvas_statusBooting();
            case CONFIGURING -> Bundle.HudCanvas_statusConfiguring();
            case READY -> Bundle.HudCanvas_statusReady();
            case UNREACHABLE -> Bundle.HudCanvas_statusUnreachable();
            case DESTROYING -> Bundle.HudCanvas_statusDestroying();
            case GONE -> Bundle.HudCanvas_statusGone();
        };
    }

    public static String kindWord(OpsKind k) {
        return switch (k) {
            case WORKSTATION -> Bundle.HudCanvas_kindWorkstation();
            case HOST -> Bundle.HudCanvas_kindHost();
            case SERVICE -> Bundle.HudCanvas_kindService();
        };
    }

    // ---- accessibility ----

    private String accessibleSelection() {
        OpsNode n = selected == null ? null : topology.node(selected);
        if (n == null) {
            return Bundle.HudCanvas_accessibleName();
        }
        String status = n.kind == OpsKind.HOST ? statusWord(n.status) : n.facts.getOrDefault("status", "");
        return Bundle.HudCanvas_accessibleSelected(n.label, kindWord(n.kind), status);
    }

    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessible == null) {
            accessible = new AccessibleHud();
        }
        return accessible;
    }

    /**
     * The HUD as assistive technology sees it: a list whose items are the
     * view's visible nodes, each selectable and pressable (press selects;
     * a second action opens a host), with its painted bounds — so VoiceOver
     * lands on a server, and so a walk that cannot deliver a mouse click
     * can still press one (the Block Studio idiom, ledger 48).
     */
    private final class AccessibleHud extends AccessibleJPanel implements javax.accessibility.AccessibleSelection {
        @Override
        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.LIST;
        }

        @Override
        public javax.accessibility.AccessibleSelection getAccessibleSelection() {
            return this;
        }

        // ---- selection: a screen reader (or macOS's AX bridge) selects a row; the HUD selects the node ----

        @Override
        public int getAccessibleSelectionCount() {
            return selected != null && HudLayout.visible(places).contains(selected) ? 1 : 0;
        }

        @Override
        public javax.accessibility.Accessible getAccessibleSelection(int i) {
            List<String> ids = HudLayout.visible(places);
            int at = selected == null ? -1 : ids.indexOf(selected);
            return i == 0 && at >= 0 ? new AccessibleNode(selected, at) : null;
        }

        @Override
        public boolean isAccessibleChildSelected(int i) {
            List<String> ids = HudLayout.visible(places);
            return i >= 0 && i < ids.size() && ids.get(i).equals(selected);
        }

        @Override
        public void addAccessibleSelection(int i) {
            List<String> ids = HudLayout.visible(places);
            if (i >= 0 && i < ids.size()) {
                select(ids.get(i));
            }
        }

        @Override
        public void removeAccessibleSelection(int i) {
            if (isAccessibleChildSelected(i)) {
                select(null);
            }
        }

        @Override
        public void clearAccessibleSelection() {
            select(null);
        }

        @Override
        public void selectAllAccessibleSelection() {
            // one node at a time
        }

        @Override
        public String getAccessibleName() {
            return accessibleSelection();
        }

        @Override
        public String getAccessibleDescription() {
            return Bundle.HudCanvas_accessibleDescription(topology.nodes(OpsKind.HOST).size(),
                    topology.nodes(OpsKind.SERVICE).size());
        }

        @Override
        public int getAccessibleChildrenCount() {
            return HudLayout.visible(places).size();
        }

        @Override
        public javax.accessibility.Accessible getAccessibleChild(int i) {
            List<String> ids = HudLayout.visible(places);
            if (i < 0 || i >= ids.size()) {
                return null;
            }
            return new AccessibleNode(ids.get(i), i);
        }
    }

    /** One node as a list item with bounds and two actions: select, open. */
    private final class AccessibleNode extends javax.accessibility.AccessibleContext
            implements javax.accessibility.Accessible, javax.accessibility.AccessibleAction,
            javax.accessibility.AccessibleComponent {

        private final String id;
        private final int index;

        AccessibleNode(String id, int index) {
            this.id = id;
            this.index = index;
            // macOS's bridge resolves a row's selection through its PARENT's AccessibleSelection,
            // and a context made by hand has no parent until one is set (walked 3.11.0)
            setAccessibleParent(HudCanvas.this);
        }

        @Override
        public javax.accessibility.AccessibleContext getAccessibleContext() {
            return this;
        }

        @Override
        public String getAccessibleName() {
            OpsNode n = topology.node(id);
            if (n == null) {
                return id;
            }
            String status = n.kind == OpsKind.HOST ? statusWord(n.status) : n.facts.getOrDefault("status", "");
            return n.label + ", " + kindWord(n.kind) + (status.isEmpty() ? "" : ", " + status);
        }

        @Override
        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.LIST_ITEM;
        }

        @Override
        public javax.accessibility.AccessibleStateSet getAccessibleStateSet() {
            javax.accessibility.AccessibleStateSet set = new javax.accessibility.AccessibleStateSet();
            set.add(javax.accessibility.AccessibleState.VISIBLE);
            set.add(javax.accessibility.AccessibleState.SHOWING);
            set.add(javax.accessibility.AccessibleState.SELECTABLE);
            set.add(javax.accessibility.AccessibleState.ENABLED);
            if (id.equals(selected)) {
                set.add(javax.accessibility.AccessibleState.SELECTED);
            }
            return set;
        }

        @Override
        public int getAccessibleIndexInParent() {
            return index;
        }

        @Override
        public int getAccessibleChildrenCount() {
            return 0;
        }

        @Override
        public javax.accessibility.Accessible getAccessibleChild(int i) {
            return null;
        }

        @Override
        public java.util.Locale getLocale() {
            return HudCanvas.this.getLocale();
        }

        @Override
        public javax.accessibility.AccessibleAction getAccessibleAction() {
            return this;
        }

        @Override
        public javax.accessibility.AccessibleComponent getAccessibleComponent() {
            return this;
        }

        // ---- actions ----

        @Override
        public int getAccessibleActionCount() {
            return 2;
        }

        @Override
        public String getAccessibleActionDescription(int i) {
            return i == 0 ? javax.accessibility.AccessibleAction.CLICK : i == 1 ? "open" : null;
        }

        @Override
        public boolean doAccessibleAction(int i) {
            if (i == 0) {
                select(id);
                return true;
            }
            if (i == 1) {
                select(id);
                drillInto(id);
                return true;
            }
            return false;
        }

        // ---- bounds: where the node is painted now ----

        private java.awt.Rectangle box() {
            Glide g = glides.get(id);
            if (g == null) {
                return new java.awt.Rectangle(0, 0, 0, 0);
            }
            // where it is PAINTED: through the camera, or a screen reader is pointed at the world
            int r = (int) Math.max(8, sr(g)) + 4;
            return new java.awt.Rectangle((int) sx(g) - r, (int) sy(g) - r, 2 * r, 2 * r);
        }

        @Override
        public java.awt.Rectangle getBounds() {
            return box();
        }

        @Override
        public void setBounds(java.awt.Rectangle r) {
        }

        @Override
        public java.awt.Point getLocation() {
            return box().getLocation();
        }

        @Override
        public void setLocation(java.awt.Point p) {
        }

        @Override
        public java.awt.Point getLocationOnScreen() {
            java.awt.Point p = box().getLocation();
            if (HudCanvas.this.isShowing()) {
                java.awt.Point origin = HudCanvas.this.getLocationOnScreen();
                p.translate(origin.x, origin.y);
            }
            return p;
        }

        @Override
        public java.awt.Dimension getSize() {
            return box().getSize();
        }

        @Override
        public void setSize(java.awt.Dimension d) {
        }

        @Override
        public boolean contains(java.awt.Point p) {
            return box().contains(p);
        }

        @Override
        public javax.accessibility.Accessible getAccessibleAt(java.awt.Point p) {
            return null;
        }

        @Override
        public Color getBackground() {
            return HudStyle.GROUND;
        }

        @Override
        public void setBackground(Color c) {
        }

        @Override
        public Color getForeground() {
            return HudStyle.TEXT;
        }

        @Override
        public void setForeground(Color c) {
        }

        @Override
        public java.awt.Cursor getCursor() {
            return HudCanvas.this.getCursor();
        }

        @Override
        public void setCursor(java.awt.Cursor c) {
        }

        @Override
        public Font getFont() {
            return HudCanvas.this.getFont();
        }

        @Override
        public void setFont(Font f) {
        }

        @Override
        public FontMetrics getFontMetrics(Font f) {
            return HudCanvas.this.getFontMetrics(f);
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void setEnabled(boolean b) {
        }

        @Override
        public boolean isVisible() {
            return true;
        }

        @Override
        public void setVisible(boolean b) {
        }

        @Override
        public boolean isShowing() {
            return HudCanvas.this.isShowing();
        }

        @Override
        public boolean isFocusTraversable() {
            return false;
        }

        @Override
        public void requestFocus() {
            HudCanvas.this.requestFocusInWindow();
        }

        @Override
        public void addFocusListener(java.awt.event.FocusListener l) {
        }

        @Override
        public void removeFocusListener(java.awt.event.FocusListener l) {
        }
    }
}
