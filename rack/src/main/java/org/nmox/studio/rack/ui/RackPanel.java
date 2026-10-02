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
import org.nmox.studio.core.util.PlainText;
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
@org.openide.util.NbBundle.Messages({
    "RackPanel_unplug=Unplug \"{0}\"",
    "RackPanel_howToUse=How to use {0}…",
    "RackPanel_howToBody={0} — {1}\n\n{2}",
    "RackPanel_openManifest=Open {0}",
    "RackPanel_removeDevice=Remove {0}",
    "RackPanel_rackEmpty=RACK EMPTY",
    "RackPanel_rackEmptyHint=Drag a device in from the shelf — or load a preset from the toolbar",
    "RackPanel_patchCable=Patch Cable…",
    "RackPanel_unplugCable=Unplug Cable…",
    "RackPanel_moveUp=Move Up",
    "RackPanel_moveDown=Move Down",
    "# {0} - the device the cable starts from",
    "RackPanel_patchTitle=Patch a Cable from {0}",
    "RackPanel_patchOk=Patch",
    "# {0} - the device",
    "RackPanel_patchNone={0} has no jack another racked device can take",
    "RackPanel_patchRefused=Not patched: that cable is already there, or it would make a loop",
    "# {0} - the new cable in words, e.g. OUT → MONITOR IN",
    "RackPanel_patched=Patched: {0}",
    "# {0} - the device",
    "RackPanel_unplugTitle=Unplug a Cable from {0}",
    "RackPanel_unplugOk=Unplug",
    "# {0} - the device",
    "RackPanel_unplugNone={0} has no cables",
    "# {0} - the removed cable in words",
    "RackPanel_unplugged=Unplugged: {0}"
})
public class RackPanel extends JPanel implements Rack.Listener {

    private final Rack rack;
    private boolean front = true;

    // cable dragging (back view)
    private Port dragFrom;
    final CablePatchGesture patchGesture = new CablePatchGesture(); // package-private: gesture-lifecycle tests arm it directly
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
        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        setBackground(RackStyle.RACK_BG);
        setFocusTraversalKeysEnabled(false);
        // the rack listener attaches in addNotify, NOT here (ledger item
        // 17): a constructor-wired listener on this long-lived panel kept
        // rebuilding faceplates into a CLOSED rack window on every preset
        // or Learning Space load, forever

        setTransferHandler(new PaletteDropHandler());

        // clicking the rails or empty rack deselects
        MouseAdapter background = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                setSelected(null);
                if (patchGesture.press(null) == CablePatchGesture.Action.CANCEL) {
                    dragFrom = null;
                    dragPoint = null;
                    repaint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                // an armed click-patch preview follows the cursor over the
                // rails and empty rack too, not just over device bounds
                if (patchGesture.isSticky()) {
                    dragPoint = e.getPoint();
                    repaint();
                }
            }
        };
        addMouseListener(background);
        addMouseMotionListener(background);
        // Escape drops an armed click-to-click patch (v1.95.0)
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                javax.swing.KeyStroke.getKeyStroke("ESCAPE"), "cancel-patch");
        getActionMap().put("cancel-patch", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (patchGesture.escape() == CablePatchGesture.Action.CANCEL) {
                    dragFrom = null;
                    dragPoint = null;
                    repaint();
                }
            }
        });
        // with the rack itself focused (after a click on a faceplate), the
        // menu key opens the SELECTED device's menu — the device the
        // highlight shows; on a focused control the device binding answers.
        // The ANCESTOR map, not WHEN_FOCUSED: a focused-component binding
        // makes the focus policy treat the panel as a Tab stop, and a Tab on
        // the panel flips the rack — so Tab from the shelf landed here and
        // never reached a faceplate (the 3.4 review). The ancestor map still
        // answers when the panel itself holds focus.
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(javax.swing.KeyStroke.getKeyStroke("shift F10"), "selected-menu");
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(javax.swing.KeyStroke.getKeyStroke("CONTEXT_MENU"), "selected-menu");
        getActionMap().put("selected-menu", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (selected != null && rack.getDevices().contains(selected)) {
                    openDeviceMenu(selected, null);
                }
            }
        });
        rebuild();
    }

    // ---- selection ----

    /** The device a Delete keypress would unrack. */
    public RackDevice getSelected() {
        return selected;
    }

    void setSelected(RackDevice device) {
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

    /**
     * The racked device that holds {@code c}, or null. A control's device is
     * its nearest {@link RackDevice} ancestor that this rack still mounts.
     */
    RackDevice deviceOf(java.awt.Component c) {
        for (java.awt.Component p = c; p != null && p != this; p = p.getParent()) {
            if (p instanceof RackDevice d && rack.getDevices().contains(d)) {
                return d;
            }
        }
        return null;
    }

    /**
     * The device a Delete keypress unracks with {@code focus} as the focus
     * owner: the device holding the focused control when a control inside one
     * has focus, else the selected one (3.4). Before, Delete took whatever was
     * last selected WITH THE MOUSE — so a keyboard user standing on SOLDER's
     * STOP, having clicked MONITOR a minute earlier, removed MONITOR.
     */
    public RackDevice removeTarget(java.awt.Component focus) {
        RackDevice holding = deviceOf(focus);
        return holding != null ? holding : selected;
    }

    /** Unracks {@link #removeTarget} for {@code focus}; the Delete key, routed by the window. */
    public void removeFor(java.awt.Component focus) {
        RackDevice doomed = removeTarget(focus);
        if (doomed != null) {
            if (doomed == selected) {
                selected = null;
            }
            rack.removeDevice(doomed);
        }
    }

    /**
     * Selection follows keyboard focus: tabbing onto a device's control
     * selects that device, so the highlight a sighted keyboard user sees is
     * the device Delete and the device menu act on. Attached in addNotify,
     * detached in removeNotify, like every other listener this panel holds.
     */
    private final java.beans.PropertyChangeListener focusFollower = e -> {
        if (e.getNewValue() instanceof java.awt.Component c) {
            RackDevice d = deviceOf(c);
            if (d != null) {
                setSelected(d);
            }
        }
    };
    private boolean focusFollowerAttached;

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
        if (!focusFollowerAttached) {
            java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .addPropertyChangeListener("permanentFocusOwner", focusFollower);
            focusFollowerAttached = true;
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
        if (focusFollowerAttached) {
            java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removePropertyChangeListener("permanentFocusOwner", focusFollower);
            focusFollowerAttached = false;
        }
        uninstallInteraction();
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
            cancelPatchGesture();
            for (RackDevice d : rack.getDevices()) {
                d.setFront(f);
            }
            repaint();
        }
    }

    /**
     * Drop any in-flight cable gesture — armed click-patch or live drag.
     * Called on flip and on every structural rebuild: an armed gesture
     * surviving a flip is invisible (the preview only paints on the
     * rear), so the next rear jack click would silently patch a cable
     * the user believed cancelled; surviving a rebuild it can hold a
     * Port of a removed/replaced device and connect a ghost cable to a
     * disposed device (the v1.95.1 review's HIGH + MED findings).
     */
    void cancelPatchGesture() {
        patchGesture.escape();
        dragFrom = null;
    }

    // ---- model sync ----

    private void rebuild() {
        removeAll();
        cancelPatchGesture();
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
        // the keyboard's route to the device menu (3.4): Shift+F10 or the
        // context-menu key on any focused control inside the device. The
        // binding lives on the DEVICE, in the ancestor-of-focused map, so it
        // fires only when the focus is inside this device — the menu's target
        // is the focused device by construction, the Popups law for a key.
        // Installed before the once-per-panel check below: a second panel
        // leaving the hierarchy takes its own action with it, and this one's
        // must come back on the next rebuild.
        var im = device.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        im.put(javax.swing.KeyStroke.getKeyStroke("shift F10"), DEVICE_MENU_KEY);
        im.put(javax.swing.KeyStroke.getKeyStroke("CONTEXT_MENU"), DEVICE_MENU_KEY);
        if (!(device.getActionMap().get(DEVICE_MENU_KEY) instanceof DeviceMenuKey k && k.owner() == this)) {
            device.getActionMap().put(DEVICE_MENU_KEY, new DeviceMenuKey(device));
        }
        // Install once per device PER PANEL. The devices are shared and outlive
        // this panel (componentClosed keeps the rack alive), so a bare
        // `instanceof DeviceMouse` guard would match a DIFFERENT panel's handler
        // — a second RackPanel over the same rack would then skip its own wiring
        // (dead interaction) and route events into the stale panel (a leak).
        // Match only OUR handler.
        for (var l : device.getMouseListeners()) {
            if (l instanceof DeviceMouse dm && dm.owner() == this) {
                return;
            }
        }
        DeviceMouse handler = new DeviceMouse(device);
        device.addMouseListener(handler);
        device.addMouseMotionListener(handler);
    }

    /** The action-map key of the device menu's keyboard binding. */
    static final String DEVICE_MENU_KEY = "nmox-device-menu";

    /**
     * Opens a device's menu from the keyboard, at the focused control. Owned
     * by one panel, like {@link DeviceMouse}, so a second panel over the same
     * rack replaces it and a panel leaving the hierarchy takes only its own.
     */
    private final class DeviceMenuKey extends javax.swing.AbstractAction {

        private final RackDevice device;

        DeviceMenuKey(RackDevice device) {
            this.device = device;
        }

        RackPanel owner() {
            return RackPanel.this;
        }

        @Override
        public void actionPerformed(java.awt.event.ActionEvent e) {
            // an ancestor-map binding reports the DEVICE as its source, not
            // the control that holds focus (the 3.4 review's probe): anchor
            // at the focus owner, which is what the keyboard user is on
            openDeviceMenu(device, java.awt.KeyboardFocusManager
                    .getCurrentKeyboardFocusManager().getFocusOwner());
        }
    }

    /**
     * Shows {@code device}'s menu from the keyboard, anchored under the
     * focused control when there is one, else at the device's title.
     */
    void openDeviceMenu(RackDevice device, java.awt.Component at) {
        setSelected(device);
        java.awt.Point p = new java.awt.Point(RackStyle.EAR_WIDTH + 14, 24);
        if (at != null && at != device && SwingUtilities.isDescendingFrom(at, device)) {
            p = SwingUtilities.convertPoint(at, 0, at.getHeight(), device);
        }
        menuShower.show(buildMenu(device, null), device, p.x, p.y);
    }

    /** How a menu is shown; tests capture it, since a headless JVM shows none. */
    interface MenuShower {
        void show(JPopupMenu menu, java.awt.Component invoker, int x, int y);
    }

    MenuShower menuShower = JPopupMenu::show;

    /**
     * Where a dialog is shown. Production asks the platform; tests answer as
     * the user would, since a headless JVM has no dialog to press a button in.
     */
    static java.util.function.Function<org.openide.DialogDescriptor, Object> dialogs =
            d -> DialogDisplayer.getDefault().notify(d);

    /** Puts {@link #dialogs} back to production; tests restore through here. */
    static void resetDialogs() {
        dialogs = d -> DialogDisplayer.getDefault().notify(d);
    }

    /**
     * Detach this panel's mouse handlers from the shared devices. Symmetric with
     * {@link #installInteraction}: the devices survive the window, so a panel
     * that leaves the hierarchy without pulling its handlers pins itself in
     * memory and keeps reacting to events it should no longer see.
     */
    private void uninstallInteraction() {
        for (RackDevice device : rack.getDevices()) {
            for (var l : device.getMouseListeners()) {
                if (l instanceof DeviceMouse dm && dm.owner() == this) {
                    device.removeMouseListener(dm);
                    device.removeMouseMotionListener(dm);
                }
            }
            if (device.getActionMap().get(DEVICE_MENU_KEY) instanceof DeviceMenuKey k
                    && k.owner() == this) {
                device.getActionMap().remove(DEVICE_MENU_KEY);
            }
        }
    }

    /**
     * The per-device mouse handler behind selection, drag-reorder, and
     * rear-view cable patching. One instance is installed on each racked
     * device by this panel; because devices are shared Swing components
     * that can outlive a panel, the handler carries its {@link #owner()}
     * so install/uninstall stay identity-aware (the v1.108.0 review: a
     * bare {@code instanceof} guard matched a DIFFERENT panel's handler,
     * leaking the old panel and leaving the new one unwired).
     */
    private final class DeviceMouse extends MouseAdapter {

        private final RackDevice device;

        DeviceMouse(RackDevice device) {
            this.device = device;
        }

        /** The panel that owns this handler — for identity-aware install/uninstall. */
        RackPanel owner() {
            return RackPanel.this;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            // a click on a faceplate (not on one of its controls, which take
            // focus themselves) leaves the keyboard on the rack: Tab flips it
            // and Delete takes the device just selected, as before 3.4
            RackPanel.this.requestFocusInWindow();
            if (e.isPopupTrigger()) {
                showMenu(e);
                return;
            }
            if (!front) {
                Port p = device.portAt(e.getPoint());
                switch (patchGesture.press(p)) {
                    case CONNECT -> {
                        rack.connect(patchGesture.from(), p);
                        patchGesture.reset();
                        dragFrom = null;
                        dragPoint = null;
                        repaint();
                    }
                    case TRACK -> {
                        dragFrom = patchGesture.from();
                        dragPoint = SwingUtilities.convertPoint(device, e.getPoint(), RackPanel.this);
                        repaint();
                    }
                    case CANCEL -> {
                        dragFrom = null;
                        dragPoint = null;
                        repaint();
                    }
                    case NONE -> {
                    }
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
                boolean onSource = portLocation(dragFrom).distance(inRack) < 18;
                switch (patchGesture.release(onSource, target)) {
                    case CONNECT -> {
                        rack.connect(dragFrom, target);
                        patchGesture.reset();
                        dragFrom = null;
                        dragPoint = null;
                    }
                    case TRACK -> {
                        // armed by a click: the preview persists and follows
                        // the mouse until the second click (v1.95.0)
                        dragPoint = inRack;
                    }
                    case CANCEL -> {
                        dragFrom = null;
                        dragPoint = null;
                    }
                    case NONE -> {
                    }
                }
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
                if (patchGesture.isSticky()) {
                    dragPoint = SwingUtilities.convertPoint(device, e.getPoint(), RackPanel.this);
                    repaint();
                }
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
            Port p = front ? null : device.portAt(e.getPoint());
            menuShower.show(buildMenu(device, p), device, e.getX(), e.getY());
        }
    }

    /**
     * A device's menu, the same one whether a right-click or the keyboard
     * opened it. {@code jack} is the rear jack under a right-click, or null.
     *
     * <p>Since 3.4 it carries the keyboard-complete cable work — Patch Cable…,
     * Unplug Cable… — and Move Up / Move Down, so everything the mouse does to
     * a racked device (patch, unplug, reorder, remove) has a route that needs
     * no pointer.
     */
    JPopupMenu buildMenu(RackDevice device, Port jack) {
        JPopupMenu menu = new JPopupMenu();
        menu.getAccessibleContext().setAccessibleName(device.getBusName());
        if (jack != null && !rack.cablesAt(jack).isEmpty()) {
            JMenuItem unplug = new JMenuItem(PlainText.plain(Bundle.RackPanel_unplug(jack.getLabel())));
            unplug.addActionListener(a -> rack.disconnectAll(jack));
            menu.add(unplug);
            menu.addSeparator();
        }
        DeviceCatalog.byId(device.getTypeId()).ifPresent(entry -> {
            JMenuItem howTo = new JMenuItem(PlainText.plain(Bundle.RackPanel_howToUse(device.getTitle())));
            howTo.addActionListener(a -> DialogDisplayer.getDefault().notify(
                    new NotifyDescriptor.Message(
                            org.nmox.studio.core.util.PlainDialogs.plain(Bundle.RackPanel_howToBody(entry.title(), entry.description(),
                                    entry.usage().replace("\n", "\n\n")), "Message"),
                            NotifyDescriptor.INFORMATION_MESSAGE)));
            menu.add(howTo);
            menu.addSeparator();
        });
        // manifest-backed devices open their configuration file straight
        // from the faceplate: NPM-9000 → package.json, DYNAMO → its
        // taskfile, ARTISAN → composer.json, GOVERNOR → .gas-snapshot
        device.primaryManifest().ifPresent(manifest -> {
            JMenuItem open = new JMenuItem(PlainText.plain(Bundle.RackPanel_openManifest(manifest.getName())));
            open.addActionListener(a -> openInEditor(manifest));
            menu.add(open);
            menu.addSeparator();
        });
        JMenuItem patch = new JMenuItem(Bundle.RackPanel_patchCable());
        patch.addActionListener(a -> patchCableFrom(device));
        menu.add(patch);
        JMenuItem unplugOne = new JMenuItem(Bundle.RackPanel_unplugCable());
        unplugOne.setEnabled(!device.cablesInWords().isEmpty());
        unplugOne.addActionListener(a -> unplugCableFrom(device));
        menu.add(unplugOne);
        menu.addSeparator();
        int at = rack.indexOf(device);
        JMenuItem up = new JMenuItem(Bundle.RackPanel_moveUp());
        up.setEnabled(at > 0);
        up.addActionListener(a -> moveBy(device, -1));
        menu.add(up);
        JMenuItem down = new JMenuItem(Bundle.RackPanel_moveDown());
        down.setEnabled(at >= 0 && at < rack.getDevices().size() - 1);
        down.addActionListener(a -> moveBy(device, 1));
        menu.add(down);
        menu.addSeparator();
        JMenuItem remove = new JMenuItem(PlainText.plain(Bundle.RackPanel_removeDevice(device.getTitle())));
        remove.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
                java.awt.event.KeyEvent.VK_DELETE, 0));
        remove.addActionListener(a -> rack.removeDevice(device));
        menu.add(remove);
        return menu;
    }

    /**
     * Moves a device one slot up ({@code -1}) or down ({@code 1}) through
     * {@link Rack#moveDevice}, the call the grip drag makes, so undo and the
     * saved order are the drag's. The keyboard stays with the device: its
     * first control is refocused after the rebuild the move triggers.
     */
    void moveBy(RackDevice device, int step) {
        int at = rack.indexOf(device);
        int to = at + step;
        if (at < 0 || to < 0 || to >= rack.getDevices().size()) {
            return;
        }
        rack.moveDevice(device, to);
        setSelected(device);
        // the move's rebuild is posted (structureChanged); removeAll moves
        // focus off the device and the focus-follows-selection listener then
        // selects whichever device caught it — so after the rebuild, the
        // selection and the keyboard both go back to the moved device (the
        // 3.4 review: the javadoc promised this and nothing did it)
        SwingUtilities.invokeLater(() -> {
            if (rack.getDevices().contains(device)) {
                setSelected(device);
                java.awt.Component first = firstFocusable(device);
                if (first != null) {
                    first.requestFocusInWindow();
                }
            }
        });
    }

    /** The first control under {@code c} that takes keyboard focus, in component order. */
    static java.awt.Component firstFocusable(java.awt.Component c) {
        if (c != null && c.isFocusable() && c.isEnabled() && !(c instanceof RackDevice)) {
            return c;
        }
        if (c instanceof java.awt.Container k) {
            for (java.awt.Component child : k.getComponents()) {
                java.awt.Component hit = firstFocusable(child);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    /** Patch Cable…: the dialog, then the drag's own {@link Rack#connect}, and a word on the status line either way. */
    void patchCableFrom(RackDevice device) {
        CableDialogs.PatchPanel panel = new CableDialogs.PatchPanel(rack, device);
        if (panel.isEmpty()) {
            status(Bundle.RackPanel_patchNone(device.getBusName()));
            return;
        }
        String ok = Bundle.RackPanel_patchOk();
        org.openide.DialogDescriptor d = new org.openide.DialogDescriptor(panel,
                Bundle.RackPanel_patchTitle(device.getBusName()), true,
                new Object[]{ok, org.openide.DialogDescriptor.CANCEL_OPTION}, ok,
                org.openide.DialogDescriptor.DEFAULT_ALIGN, null, null);
        if (!ok.equals(dialogs.apply(d))) {
            return;
        }
        Cable made = panel.apply();
        status(made == null ? Bundle.RackPanel_patchRefused()
                : Bundle.RackPanel_patched(device.cableInWords(made)));
    }

    /**
     * Unplug Cable…: the device's cables by name, one removed through
     * {@link Rack#disconnect} (undoable). Cancel is the default button, since
     * the gesture takes something away (the v1.98.0 safe default).
     */
    void unplugCableFrom(RackDevice device) {
        CableDialogs.UnplugPanel panel = new CableDialogs.UnplugPanel(rack, device);
        if (panel.isEmpty()) {
            status(Bundle.RackPanel_unplugNone(device.getBusName()));
            return;
        }
        String ok = Bundle.RackPanel_unplugOk();
        org.openide.DialogDescriptor d = new org.openide.DialogDescriptor(panel,
                Bundle.RackPanel_unplugTitle(device.getBusName()), true,
                new Object[]{ok, org.openide.DialogDescriptor.CANCEL_OPTION},
                org.openide.DialogDescriptor.CANCEL_OPTION,
                org.openide.DialogDescriptor.DEFAULT_ALIGN, null, null);
        if (!ok.equals(dialogs.apply(d))) {
            return;
        }
        Object chosen = panel.cable.getSelectedItem();
        Cable gone = panel.apply();
        if (gone != null) {
            status(Bundle.RackPanel_unplugged(String.valueOf(chosen)));
        }
    }

    private static void status(String text) {
        org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                org.nmox.studio.core.util.PlainStatus.text(text));
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
        // The invitation belongs on any bare rail, not only on a rack with
        // nothing in it. The starter rack mounts one MONITOR (v1.278.0), so
        // getDevices().isEmpty() was false from the first launch and this
        // silkscreen — the only place the rack says where devices come from —
        // had never been seen by a new user. It now paints in whatever space
        // is left below the stack, and retires as the rack fills.
        paintEmptyRack(g, railX1, railX2, stackBottom());
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

    /** The bottom of the mounted stack: where the bare rails begin. */
    int stackBottom() {
        int bottom = 0;
        for (java.awt.Component c : getComponents()) {
            bottom = Math.max(bottom, c.getY() + c.getHeight());
        }
        return bottom;
    }

    /**
     * Bare rails invite: etched silkscreen in the space below the stack.
     * "RACK EMPTY" is only true of an empty rack, so a rack with devices
     * gets the hint alone — the line that names the two doors devices come
     * through, the shelf and the Presets menu.
     */
    private void paintEmptyRack(Graphics2D g, int railX1, int railX2, int stackBottom) {
        boolean empty = rack.getDevices().isEmpty();
        int free = getHeight() - stackBottom;
        if (!empty && free < BARE_RAIL_MIN) {
            return; // a full rack has nothing to explain
        }
        int cx = (railX1 + railX2) / 2;
        int cy = empty ? Math.max(70, getHeight() / 3) : stackBottom + Math.min(free / 2, 90);
        if (empty) {
            g.setFont(RackStyle.TITLE_FONT);
            g.setColor(new Color(255, 255, 255, 40));
            String big = Bundle.RackPanel_rackEmpty();
            g.drawString(big, cx - g.getFontMetrics().stringWidth(big) / 2, cy);
            cy += 22;
        }
        g.setFont(RackStyle.LABEL_FONT);
        g.setColor(new Color(255, 255, 255, 30));
        String hint = Bundle.RackPanel_rackEmptyHint();
        g.drawString(hint, cx - g.getFontMetrics().stringWidth(hint) / 2, cy);
    }

    /** Two rack units of bare rail: less than that and the line would crowd the stack. */
    static final int BARE_RAIL_MIN = RackStyle.UNIT * 2;

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

    /**
     * The Swing {@link TransferHandler} that accepts drags from the
     * device palette: while a drag hovers, it lights the rack slot the
     * device would land in; on drop it asks {@code DeviceCatalog} to
     * build the device and mounts it at that slot. This is how new
     * devices enter the rack — the palette only ever hands over a
     * type-id string, never a live component.
     */
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
