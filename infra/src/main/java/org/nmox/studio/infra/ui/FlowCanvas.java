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

/**
 * The flow canvas, Node-RED style: a dotted dark grid, rounded nodes
 * in category colors with port nubs on their left/right edges, and
 * horizontal bezier wires. Drag nodes to arrange, drag from an output
 * nub to wire, drag empty space to pan, scroll to zoom, Delete to
 * remove, double-click to configure.
 */
public class FlowCanvas extends JPanel {

    /** What the host window wants to know about. */
    public interface Callbacks {
        void nodeDoubleClicked(InfraNode node);

        void nodeContextMenu(InfraNode node, Point screenPoint);

        void selectionChanged(InfraNode node);
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

    public FlowCanvas(InfraGraph graph, Callbacks callbacks) {
        this.graph = graph;
        this.callbacks = callbacks;
        setBackground(CANVAS_BG);
        setFocusable(true);
        graph.addListener(new InfraGraph.Listener() {
            @Override
            public void graphChanged() {
                javax.swing.SwingUtilities.invokeLater(FlowCanvas.this::repaint);
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
        selectedNode = node;
        selectedWire = wire;
        callbacks.selectionChanged(node);
        repaint();
    }

    private void deleteSelection() {
        if (selectedNode != null) {
            graph.removeNode(selectedNode);
            select(null, null);
        } else if (selectedWire != null) {
            graph.disconnect(selectedWire);
            select(null, null);
        }
    }

    /** Centers the view on the design. */
    public void fit() {
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
            double zx = getWidth() / (double) Math.max(200, maxX - minX + 120);
            double zy = getHeight() / (double) Math.max(160, maxY - minY + 120);
            zoom = Math.max(0.4, Math.min(1.5, Math.min(zx, zy)));
            panX = -minX * zoom + 60;
            panY = -minY * zoom + 60;
        }
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

    private boolean onOutputNub(InfraNode node, Point2D world) {
        return node != null
                && Math.abs(world.getX() - (node.x + NODE_W)) < 9 / zoom + 4
                && Math.abs(world.getY() - (node.y + NODE_H / 2.0)) < 12;
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
        g.dispose();
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
            String status = node.doId != null && node.status.isEmpty() ? "live" : node.status;
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
        return "<html><b>" + node.kind.getDisplayName() + "</b> " + node.label
                + "<br>$" + String.format("%.2f", node.monthlyUsd()) + "/mo"
                + (node.doId != null ? "<br>live: " + node.doId : "<br>design only")
                + "</html>";
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
            if (node != null && onOutputNub(node, world)) {
                wireFrom = node;
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
                InfraNode target = nodeAt(toWorld(e.getPoint()));
                if (target != null) {
                    graph.connect(wireFrom, target);
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
