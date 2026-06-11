package org.nmox.studio.rack.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.util.HashMap;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import org.nmox.studio.rack.devices.DeviceType;
import org.nmox.studio.rack.model.Cable;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * The rack itself: devices stacked between mounting rails. Front view
 * shows control surfaces; hit Tab (or the toolbar flip) to spin the
 * rack around and patch cables between jacks by dragging - straight
 * out of Reason. Devices drag in from the palette and reorder by
 * their title-bar grip.
 */
public class RackPanel extends JPanel implements Rack.Listener {

    private final Rack rack;
    private boolean front = true;

    // cable dragging (back view)
    private Port dragFrom;
    private Point dragPoint;

    // device reordering (front view)
    private RackDevice reordering;

    // recent signal flashes per cable, for the glow animation
    private final Map<Cable, Long> flashes = new HashMap<>();
    private final Timer flashTimer = new Timer(60, e -> {
        long now = System.currentTimeMillis();
        flashes.values().removeIf(t -> now - t > 700);
        repaint();
        if (flashes.isEmpty()) {
            ((Timer) e.getSource()).stop();
        }
    });

    public RackPanel(Rack rack) {
        this.rack = rack;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(RackStyle.RACK_BG);
        setFocusTraversalKeysEnabled(false);
        rack.addListener(this);

        setTransferHandler(new PaletteDropHandler());
        rebuild();
    }

    public Rack getRack() {
        return rack;
    }

    // ---- view flipping ----

    public boolean isFront() {
        return front;
    }

    public void flip() {
        setFront(!front);
    }

    public void setFront(boolean f) {
        if (front != f) {
            front = f;
            dragFrom = null;
            for (RackDevice d : rack.getDevices()) {
                d.setFront(f);
            }
            repaint();
        }
    }

    // ---- model sync ----

    private void rebuild() {
        removeAll();
        for (RackDevice d : rack.getDevices()) {
            d.setAlignmentX(CENTER_ALIGNMENT);
            d.setFront(front);
            installInteraction(d);
            add(d);
        }
        revalidate();
        repaint();
    }

    @Override
    public void structureChanged() {
        SwingUtilities.invokeLater(this::rebuild);
    }

    @Override
    public void cablesChanged() {
        SwingUtilities.invokeLater(this::repaint);
    }

    @Override
    public void signalTravelled(Cable cable) {
        SwingUtilities.invokeLater(() -> {
            if (!front) {
                flashes.put(cable, System.currentTimeMillis());
                if (!flashTimer.isRunning()) {
                    flashTimer.start();
                }
            }
        });
    }

    // ---- interaction ----

    private void installInteraction(RackDevice device) {
        // only install once per device
        for (var l : device.getMouseListeners()) {
            if (l instanceof DeviceMouse) {
                return;
            }
        }
        DeviceMouse handler = new DeviceMouse(device);
        device.addMouseListener(handler);
        device.addMouseMotionListener(handler);
    }

    private final class DeviceMouse extends MouseAdapter {

        private final RackDevice device;

        DeviceMouse(RackDevice device) {
            this.device = device;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (e.isPopupTrigger()) {
                showMenu(e);
                return;
            }
            if (!front) {
                Port p = device.portAt(e.getPoint());
                if (p != null) {
                    dragFrom = p;
                    dragPoint = SwingUtilities.convertPoint(device, e.getPoint(), RackPanel.this);
                    repaint();
                }
            } else if (device.isGrip(e.getPoint())) {
                reordering = device;
                setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            Point inRack = SwingUtilities.convertPoint(device, e.getPoint(), RackPanel.this);
            if (dragFrom != null) {
                dragPoint = inRack;
                repaint();
            } else if (reordering != null) {
                int targetIndex = indexForY(inRack.y);
                if (targetIndex != rack.indexOf(reordering)) {
                    rack.moveDevice(reordering, targetIndex);
                }
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.isPopupTrigger()) {
                showMenu(e);
            }
            if (dragFrom != null) {
                Point inRack = SwingUtilities.convertPoint(device, e.getPoint(), RackPanel.this);
                Port target = findSnapTarget(inRack);
                if (target != null) {
                    rack.connect(dragFrom, target);
                }
                dragFrom = null;
                dragPoint = null;
                repaint();
            }
            if (reordering != null) {
                reordering = null;
                setCursor(java.awt.Cursor.getDefaultCursor());
            }
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            if (!front) {
                device.setCursor(device.portAt(e.getPoint()) != null
                        ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                        : java.awt.Cursor.getDefaultCursor());
            } else {
                device.setCursor(device.isGrip(e.getPoint())
                        ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR)
                        : java.awt.Cursor.getDefaultCursor());
            }
        }

        private void showMenu(MouseEvent e) {
            JPopupMenu menu = new JPopupMenu();
            if (!front) {
                Port p = device.portAt(e.getPoint());
                if (p != null && !rack.cablesAt(p).isEmpty()) {
                    JMenuItem unplug = new JMenuItem("Unplug \"" + p.getLabel() + "\"");
                    unplug.addActionListener(a -> rack.disconnectAll(p));
                    menu.add(unplug);
                    menu.addSeparator();
                }
            }
            JMenuItem remove = new JMenuItem("Remove " + device.getTitle());
            remove.addActionListener(a -> rack.removeDevice(device));
            menu.add(remove);
            menu.show(device, e.getX(), e.getY());
        }
    }

    /** Maps a y coordinate in rack space to a device insertion index. */
    private int indexForY(int y) {
        var devices = rack.getDevices();
        for (int i = 0; i < devices.size(); i++) {
            RackDevice d = devices.get(i);
            if (y < d.getY() + d.getHeight() / 2) {
                return i;
            }
        }
        return Math.max(0, devices.size() - (reordering != null ? 1 : 0));
    }

    private Port portAtRackPoint(Point p) {
        for (RackDevice d : rack.getDevices()) {
            if (p.y >= d.getY() && p.y < d.getY() + d.getHeight()) {
                return d.portAt(new Point(p.x - d.getX(), p.y - d.getY()));
            }
        }
        return null;
    }

    /**
     * The jack a dragged cable would land on: the nearest port that is
     * compatible with the drag source, within a forgiving snap radius.
     */
    private Port findSnapTarget(Point p) {
        if (dragFrom == null || p == null) {
            return null;
        }
        Port best = null;
        double bestDist = 30;
        for (RackDevice d : rack.getDevices()) {
            for (Port port : d.getPorts()) {
                if (!dragFrom.canConnectTo(port)) {
                    continue;
                }
                double dist = portLocation(port).distance(p);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = port;
                }
            }
        }
        return best;
    }

    private Point portLocation(Port port) {
        RackDevice d = port.getDevice();
        return new Point(d.getX() + port.getX(), d.getY() + port.getY());
    }

    // ---- painting ----

    @Override
    protected void paintComponent(Graphics gr) {
        super.paintComponent(gr);
        Graphics2D g = (Graphics2D) gr.create();
        RackStyle.antialias(g);
        // rack rails behind the device stack
        int railX1 = (getWidth() - RackStyle.RACK_WIDTH) / 2 - 10;
        int railX2 = railX1 + RackStyle.RACK_WIDTH + 20;
        g.setColor(RackStyle.RAIL);
        g.fillRect(railX1, 0, 10, getHeight());
        g.fillRect(railX2 - 10, 0, 10, getHeight());
        g.setColor(RackStyle.RAIL_EDGE);
        g.drawRect(railX1, -1, 10, getHeight() + 1);
        g.drawRect(railX2 - 10, -1, 10, getHeight() + 1);
        g.dispose();
    }

    @Override
    public void paint(Graphics gr) {
        super.paint(gr);
        if (front) {
            return;
        }
        Graphics2D g = (Graphics2D) gr.create();
        RackStyle.antialias(g);
        long now = System.currentTimeMillis();
        for (Cable cable : rack.getCables()) {
            Long flash = flashes.get(cable);
            float glow = flash == null ? 0f : Math.max(0f, 1f - (now - flash) / 700f);
            paintCable(g, portLocation(cable.getFrom()), portLocation(cable.getTo()),
                    cable.getColor(), glow);
        }
        if (dragFrom != null && dragPoint != null) {
            // light up every jack this cable could land on; the snap
            // target gets the bright ring
            Port snap = findSnapTarget(dragPoint);
            Color hint = dragFrom.getType().cableColor(0);
            for (RackDevice d : rack.getDevices()) {
                for (Port p : d.getPorts()) {
                    if (!dragFrom.canConnectTo(p)) {
                        continue;
                    }
                    Point loc = portLocation(p);
                    boolean hot = p == snap;
                    g.setColor(hot ? Color.WHITE
                            : new Color(hint.getRed(), hint.getGreen(), hint.getBlue(), 150));
                    g.setStroke(new BasicStroke(hot ? 3f : 2f));
                    int r = hot ? 16 : 13;
                    g.draw(new Ellipse2D.Float(loc.x - r, loc.y - r, r * 2, r * 2));
                }
            }
            paintCable(g, portLocation(dragFrom), snap != null ? portLocation(snap) : dragPoint,
                    hint, 0.6f);
        }
        g.dispose();
    }

    private void paintCable(Graphics2D g, Point a, Point b, Color color, float glow) {
        double dist = a.distance(b);
        double sag = Math.min(170, 45 + dist * 0.28);
        CubicCurve2D curve = new CubicCurve2D.Double(
                a.x, a.y,
                a.x + (b.x - a.x) * 0.25, Math.max(a.y, b.y) + sag * 0.7 + (a.y - b.y) * 0.1,
                a.x + (b.x - a.x) * 0.75, Math.max(a.y, b.y) + sag,
                b.x, b.y);

        // shadow
        g.setColor(new Color(0, 0, 0, 110));
        g.setStroke(new BasicStroke(5.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.translate(0, 3);
        g.draw(curve);
        g.translate(0, -3);

        // body
        g.setColor(color);
        g.setStroke(new BasicStroke(3.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(curve);

        // top highlight
        g.setColor(new Color(255, 255, 255, 70));
        g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(curve);

        // signal glow
        if (glow > 0.01f) {
            g.setColor(new Color(255, 255, 255, (int) (140 * glow)));
            g.setStroke(new BasicStroke(3.6f + 4f * glow, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(curve);
        }

        // plug bodies sunk into the jacks
        for (Point end : new Point[]{a, b}) {
            g.setColor(new Color(28, 28, 30));
            g.fill(new Ellipse2D.Float(end.x - 7, end.y - 7, 14, 14));
            g.setColor(color.darker());
            g.fill(new Ellipse2D.Float(end.x - 5, end.y - 5, 10, 10));
            g.setColor(new Color(255, 255, 255, 60));
            g.draw(new Ellipse2D.Float(end.x - 7, end.y - 7, 14, 14));
        }
    }

    @Override
    public Dimension getPreferredSize() {
        int h = 0;
        for (RackDevice d : rack.getDevices()) {
            h += d.getPreferredSize().height;
        }
        return new Dimension(RackStyle.RACK_WIDTH + 72, Math.max(h, 200));
    }

    // ---- palette drop ----

    private final class PaletteDropHandler extends TransferHandler {

        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.stringFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            try {
                String id = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                DeviceType type = DeviceType.byId(id);
                if (type == null) {
                    return false;
                }
                int index = support.isDrop()
                        ? indexForY(support.getDropLocation().getDropPoint().y)
                        : rack.getDevices().size();
                rack.addDevice(type.create(), index);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }
}
