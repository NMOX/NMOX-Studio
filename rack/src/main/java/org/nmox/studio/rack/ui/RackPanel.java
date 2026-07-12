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
import org.nmox.studio.rack.devices.DeviceCatalog;
import org.nmox.studio.rack.model.Cable;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.ui.controls.RackStyle;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;

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

    // the selected device (front view): Delete unracks it
    private RackDevice selected;

    // palette drag-over insertion slot (-1 = no drag in progress)
    private int dropIndex = -1;
    private long dropSeenAt;
    private final Timer dropClearTimer = new Timer(150, e -> {
        if (dropIndex >= 0 && System.currentTimeMillis() - dropSeenAt > 300) {
            dropIndex = -1;
            repaint();
        }
        if (dropIndex < 0) {
            ((Timer) e.getSource()).stop();
        }
    });

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

    /** True while this panel is in the hierarchy and listening to the rack. */
    private boolean listenerAttached;

    public RackPanel(Rack rack) {
        this.rack = rack;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(RackStyle.RACK_BG);
        setFocusTraversalKeysEnabled(false);
        // the rack listener attaches in addNotify, NOT here (ledger item
        // 17): a constructor-wired listener on this long-lived panel kept
        // rebuilding faceplates into a CLOSED rack window on every preset
        // or Learning Space load, forever

        setTransferHandler(new PaletteDropHandler());

        // clicking the rails or empty rack deselects
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                setSelected(null);
            }
        });
        rebuild();
    }

    // ---- selection ----

    /** The device a Delete keypress would unrack. */
    public RackDevice getSelected() {
        return selected;
    }

    private void setSelected(RackDevice device) {
        if (selected != device) {
            selected = device;
            repaint();
        }
    }

    /** Unracks the selected device (the Delete key, routed by the window). */
    public void removeSelected() {
        if (selected != null) {
            RackDevice doomed = selected;
            selected = null;
            rack.removeDevice(doomed);
        }
    }

    public Rack getRack() {
        return rack;
    }

    /**
     * Listen exactly while in the hierarchy; the re-sync rebuild on attach
     * catches everything the model did while we weren't showing (presets,
     * Learning Spaces, undo — the events the old constructor wiring spent
     * offscreen repaints on).
     */
    @Override
    public void addNotify() {
        super.addNotify();
        if (!listenerAttached) {
            rack.addListener(this);
            listenerAttached = true;
        }
        rebuild();
    }

    /** Stop listening and stop animation timers when the panel leaves the hierarchy. */
    @Override
    public void removeNotify() {
        if (listenerAttached) {
            rack.removeListener(this);
            listenerAttached = false;
        }
        flashTimer.stop();
        dropClearTimer.stop();
        super.removeNotify();
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
        if (selected != null && !rack.getDevices().contains(selected)) {
            selected = null;
        }
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
            } else {
                setSelected(device);
                if (device.isGrip(e.getPoint())) {
                    reordering = device;
                    setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));
                }
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
            DeviceCatalog.byId(device.getTypeId()).ifPresent(entry -> {
                JMenuItem howTo = new JMenuItem("How to use " + device.getTitle() + "…");
                howTo.addActionListener(a -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(
                                entry.title() + " — " + entry.description() + "\n\n"
                                        + entry.usage().replace("\n", "\n\n"),
                                NotifyDescriptor.INFORMATION_MESSAGE)));
                menu.add(howTo);
                menu.addSeparator();
            });
            // manifest-backed devices open their configuration file straight
            // from the faceplate: NPM-9000 → package.json, DYNAMO → its
            // taskfile, ARTISAN → composer.json, GOVERNOR → .gas-snapshot
            device.primaryManifest().ifPresent(manifest -> {
                JMenuItem open = new JMenuItem("Open " + manifest.getName());
                open.addActionListener(a -> openInEditor(manifest));
                menu.add(open);
                menu.addSeparator();
            });
            JMenuItem remove = new JMenuItem("Remove " + device.getTitle());
            remove.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
                    java.awt.event.KeyEvent.VK_DELETE, 0));
            remove.addActionListener(a -> rack.removeDevice(device));
            menu.add(remove);
            menu.show(device, e.getX(), e.getY());
        }
    }

    /** The Project Studio file tree's open-file idiom: DataObject → OpenCookie. */
    private static void openInEditor(java.io.File file) {
        try {
            org.openide.filesystems.FileObject fo = org.openide.filesystems.FileUtil
                    .toFileObject(org.openide.filesystems.FileUtil.normalizeFile(file));
            if (fo != null) {
                org.openide.cookies.OpenCookie open = org.openide.loaders.DataObject
                        .find(fo).getLookup().lookup(org.openide.cookies.OpenCookie.class);
                if (open != null) {
                    open.open();
                }
            }
        } catch (java.io.IOException | RuntimeException ignored) {
            // file vanished or no editor support; the click just does nothing
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
        paintRailHardware(g, railX1, railX2);
        if (rack.getDevices().isEmpty()) {
            paintEmptyRack(g, railX1, railX2);
        }
        g.dispose();
    }

    /**
     * Cage-nut holes punched down both rails on the half-unit grid, with
     * a unit number etched at every unit boundary - the part of a real
     * rack you line the screws up against.
     */
    private void paintRailHardware(Graphics2D g, int railX1, int railX2) {
        int unit = RackStyle.UNIT;
        g.setFont(RackStyle.TINY_FONT);
        for (int y = unit / 4; y < getHeight(); y += unit / 2) {
            for (int x : new int[]{railX1 + 2, railX2 - 8}) {
                // square hole, punched dark with a lit bottom edge
                g.setColor(new Color(8, 8, 10));
                g.fillRoundRect(x, y - 3, 6, 6, 2, 2);
                g.setColor(new Color(255, 255, 255, 28));
                g.drawLine(x, y + 3, x + 6, y + 3);
            }
        }
        g.setColor(new Color(120, 122, 128, 110));
        for (int u = 0; u * unit < getHeight(); u++) {
            String n = String.format("%02d", u + 1);
            g.drawString(n, railX1 - g.getFontMetrics().stringWidth(n) - 3, u * unit + unit / 2 + 3);
        }
    }

    /** An empty rack invites: etched silkscreen between bare rails. */
    private void paintEmptyRack(Graphics2D g, int railX1, int railX2) {
        int cx = (railX1 + railX2) / 2;
        int cy = Math.max(70, getHeight() / 3);
        g.setFont(RackStyle.TITLE_FONT);
        g.setColor(new Color(255, 255, 255, 40));
        String big = "RACK EMPTY";
        g.drawString(big, cx - g.getFontMetrics().stringWidth(big) / 2, cy);
        g.setFont(RackStyle.LABEL_FONT);
        g.setColor(new Color(255, 255, 255, 30));
        String hint = "Drag a device in from the shelf — or load a preset from the toolbar";
        g.drawString(hint, cx - g.getFontMetrics().stringWidth(hint) / 2, cy + 22);
    }

    @Override
    public void paint(Graphics gr) {
        super.paint(gr);
        if (front) {
            Graphics2D g = (Graphics2D) gr.create();
            RackStyle.antialias(g);
            paintSeams(g);
            paintSelection(g);
            paintInsertionSlot(g);
            g.dispose();
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

    /**
     * Ambient occlusion at every device boundary: each unit throws a
     * sliver of shadow onto the one below, the way stacked hardware does.
     */
    private void paintSeams(Graphics2D g) {
        var devices = rack.getDevices();
        for (int i = 1; i < devices.size(); i++) {
            RackDevice d = devices.get(i);
            int y = d.getY();
            g.setPaint(new java.awt.GradientPaint(0, y, new Color(0, 0, 0, 90),
                    0, y + 5, new Color(0, 0, 0, 0)));
            g.fillRect(d.getX(), y, d.getWidth(), 5);
        }
    }

    /** The selected device wears its accent as a halo; lifted = brighter. */
    private void paintSelection(Graphics2D g) {
        RackDevice d = reordering != null ? reordering : selected;
        if (d == null) {
            return;
        }
        Color accent = d.getAccent();
        boolean lifted = reordering != null;
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(),
                lifted ? 200 : 130));
        g.setStroke(new BasicStroke(lifted ? 2.5f : 1.6f));
        g.drawRect(d.getX(), d.getY(), d.getWidth() - 1, d.getHeight() - 1);
        if (lifted) {
            // a lifted unit floats: deepen its shadow on the device below
            g.setPaint(new java.awt.GradientPaint(0, d.getY() + d.getHeight(),
                    new Color(0, 0, 0, 130),
                    0, d.getY() + d.getHeight() + 12, new Color(0, 0, 0, 0)));
            g.fillRect(d.getX(), d.getY() + d.getHeight(), d.getWidth(), 12);
        }
    }

    /** The lit slot a palette drag would drop into. */
    private void paintInsertionSlot(Graphics2D g) {
        if (dropIndex < 0) {
            return;
        }
        var devices = rack.getDevices();
        int x = (getWidth() - RackStyle.RACK_WIDTH) / 2;
        int y = dropIndex < devices.size()
                ? devices.get(dropIndex).getY()
                : (devices.isEmpty() ? 8
                        : devices.get(devices.size() - 1).getY()
                        + devices.get(devices.size() - 1).getHeight());
        g.setColor(new Color(RackStyle.GO.getRed(), RackStyle.GO.getGreen(),
                RackStyle.GO.getBlue(), 70));
        g.fillRect(x, y - 3, RackStyle.RACK_WIDTH, 6);
        g.setColor(RackStyle.GO);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(x, y, x + RackStyle.RACK_WIDTH, y);
        // chevrons pointing at the slot from both rails
        var left = new java.awt.Polygon(
                new int[]{x - 12, x - 2, x - 12}, new int[]{y - 6, y, y + 6}, 3);
        var right = new java.awt.Polygon(
                new int[]{x + RackStyle.RACK_WIDTH + 12, x + RackStyle.RACK_WIDTH + 2,
                    x + RackStyle.RACK_WIDTH + 12}, new int[]{y - 6, y, y + 6}, 3);
        g.fill(left);
        g.fill(right);
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
            boolean ok = support.isDataFlavorSupported(DataFlavor.stringFlavor);
            if (ok && support.isDrop()) {
                // light the slot the device would land in
                int index = indexForY(support.getDropLocation().getDropPoint().y);
                dropSeenAt = System.currentTimeMillis();
                if (index != dropIndex) {
                    dropIndex = index;
                    repaint();
                }
                if (!dropClearTimer.isRunning()) {
                    dropClearTimer.start();
                }
            }
            return ok;
        }

        @Override
        public boolean importData(TransferSupport support) {
            int index = dropIndex >= 0 && support.isDrop()
                    ? dropIndex
                    : rack.getDevices().size();
            dropIndex = -1;
            repaint();
            try {
                String id = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                DeviceCatalog.Entry type = DeviceCatalog.byId(id).orElse(null);
                if (type == null) {
                    return false;
                }
                RackDevice fresh = type.create();
                rack.addDevice(fresh, index);
                setSelected(fresh);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }
}
