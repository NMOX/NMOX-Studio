package org.nmox.studio.rack.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.model.Cable;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;
import org.openide.DialogDescriptor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rack without a pointer (3.4): the device menu opens from the keyboard
 * on the FOCUSED device, and carries the cable work and the reorder the mouse
 * used to own — Patch Cable…, Unplug Cable…, Move Up, Move Down — each
 * ending in the model call the mouse gesture makes, so undo and the saved
 * patch are identical. Delete takes the device whose control has focus.
 */
class RackKeyboardCablesTest {

    /** A device with one trigger jack each way and one focusable control. */
    private static final class Jacks extends RackDevice {
        final RackButton go;

        Jacks(String id) {
            super(id, id.toUpperCase(), "JACKS", new Color(0, 0, 0), 1);
            addOutPort("out", "OUT", SignalType.TRIGGER);
            addInPort("run", "RUN", SignalType.TRIGGER);
            go = place(new RackButton("GO", RackStyle.GO), 40, 20);
        }

        @Override
        public void receive(Port in, Signal signal) {
        }
    }

    /** A device whose only jack is DATA: nothing else here can take it. */
    private static final class Lonely extends RackDevice {
        Lonely() {
            super("lonely", "LONELY", "DATA ONLY", new Color(0, 0, 0), 1);
            addOutPort("out", "DATA", SignalType.DATA);
        }
    }

    private Rack rack;
    private Jacks a;
    private Jacks b;
    private RackPanel panel;
    private final List<Object[]> shown = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        rack = new Rack();
        rack.enableUndoCapture();
        a = new Jacks("a");
        b = new Jacks("b");
        rack.addDevice(a);
        rack.addDevice(b);
        SwingUtilities.invokeAndWait(() -> {
            panel = new RackPanel(rack);
            panel.menuShower = (menu, invoker, x, y) -> shown.add(new Object[]{menu, invoker});
        });
    }

    @AfterEach
    void tearDown() {
        RackPanel.resetDialogs();
        rack.shutdown();
    }

    private void onEdt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    private static JMenuItem item(JPopupMenu menu, String text) {
        for (Component c : menu.getComponents()) {
            if (c instanceof JMenuItem m && text.equals(m.getText())) {
                return m;
            }
        }
        throw new AssertionError("no menu item " + text);
    }

    @SuppressWarnings("unchecked")
    private static <T> void choose(JComboBox<?> combo, T value) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (((CableDialogs.Choice<T>) combo.getItemAt(i)).value() == value) {
                combo.setSelectedIndex(i);
                return;
            }
        }
        throw new AssertionError("not offered: " + value);
    }

    @Test
    @DisplayName("Shift+F10 and the context-menu key are bound on every device, where focus inside it reaches them")
    void menuKeysAreBound() {
        for (RackDevice d : List.of(a, b)) {
            var im = d.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
            assertThat(im.get(KeyStroke.getKeyStroke("shift F10"))).isEqualTo(RackPanel.DEVICE_MENU_KEY);
            assertThat(im.get(KeyStroke.getKeyStroke("CONTEXT_MENU"))).isEqualTo(RackPanel.DEVICE_MENU_KEY);
            assertThat(d.getActionMap().get(RackPanel.DEVICE_MENU_KEY)).isNotNull();
        }
    }

    @Test
    @DisplayName("the menu key on a focused control opens THAT device's menu, whatever was selected before")
    void menuTargetsTheFocusedDevice() throws Exception {
        onEdt(() -> {
            panel.openDeviceMenu(a, null);   // A selected, as a mouse user left it
            shown.clear();
            b.getActionMap().get(RackPanel.DEVICE_MENU_KEY).actionPerformed(
                    new ActionEvent(b.go, ActionEvent.ACTION_PERFORMED, "shift F10"));
        });
        assertThat(shown).hasSize(1);
        assertThat(shown.get(0)[1]).as("the menu opens on the focused device").isSameAs(b);
        assertThat(((JPopupMenu) shown.get(0)[0]).getAccessibleContext().getAccessibleName())
                .isEqualTo(b.getBusName());
    }

    @Test
    @DisplayName("after a keyboard move the moved device is selected again, even if focus-follows-selection took another (the review)")
    void moveKeepsTheMovedDevice() throws Exception {
        onEdt(() -> {
            panel.moveBy(b, -1);
            // the rebuild's removeAll moves focus; the focus listener selects whoever caught it
            panel.setSelected(a);
        });
        onEdt(() -> { });
        onEdt(() -> {
            assertThat(rack.getDevices()).containsExactly(b, a);
            assertThat(panel.getSelected()).isSameAs(b);
            assertThat(RackPanel.firstFocusable(b)).isSameAs(b.go);
        });
    }

    @Test
    @DisplayName("Move Up / Move Down reorder through the drag's own call, undoably, and grey at the ends")
    void moveUpAndDown() throws Exception {
        onEdt(() -> {
            JPopupMenu top = panel.buildMenu(a, null);
            assertThat(item(top, Bundle.RackPanel_moveUp()).isEnabled()).isFalse();
            assertThat(item(top, Bundle.RackPanel_moveDown()).isEnabled()).isTrue();
            item(top, Bundle.RackPanel_moveDown()).doClick();
        });
        assertThat(rack.getDevices()).containsExactly(b, a);
        assertThat(rack.undoLabel()).contains("Move");
        onEdt(() -> {
            JPopupMenu bottom = panel.buildMenu(a, null);
            assertThat(item(bottom, Bundle.RackPanel_moveDown()).isEnabled()).isFalse();
            item(bottom, Bundle.RackPanel_moveUp()).doClick();
        });
        assertThat(rack.getDevices()).containsExactly(a, b);
    }

    @Test
    @DisplayName("Patch Cable… offers only legal choices and patches through Rack.connect, undoably")
    void patchCableFromTheMenu() throws Exception {
        RackPanel.dialogs = d -> {
            CableDialogs.PatchPanel p = (CableDialogs.PatchPanel) d.getMessage();
            // every combination the dialog can hold is a legal cable
            for (int i = 0; i < p.intoJack.getItemCount(); i++) {
                Port into = (Port) p.intoJack.getItemAt(i).value();
                assertThat(((Port) ((CableDialogs.Choice<?>) p.fromJack.getSelectedItem()).value())
                        .canConnectTo(into)).isTrue();
            }
            choose(p.fromJack, a.getPort("out"));
            choose(p.toDevice, b);
            choose(p.intoJack, b.getPort("run"));
            return d.getOptions()[0];
        };
        onEdt(() -> item(panel.buildMenu(a, null), Bundle.RackPanel_patchCable()).doClick());

        assertThat(rack.getCables()).hasSize(1);
        Cable c = rack.getCables().get(0);
        assertThat(c.getFrom()).isSameAs(a.getPort("out"));
        assertThat(c.getTo()).isSameAs(b.getPort("run"));
        assertThat(rack.undoLabel()).as("the drag's own undo entry").isEqualTo("Patch cable");
        assertThat(a.cablesInWords()).isEqualTo("OUT → B RUN");
    }

    @Test
    @DisplayName("the patch dialog never lists a device that cannot take the chosen jack")
    void patchDialogOffersOnlyTargets() throws Exception {
        onEdt(() -> {
            CableDialogs.PatchPanel p = new CableDialogs.PatchPanel(rack, a);
            choose(p.fromJack, a.getPort("out"));
            List<Object> devices = new ArrayList<>();
            for (int i = 0; i < p.toDevice.getItemCount(); i++) {
                devices.add(p.toDevice.getItemAt(i).value());
            }
            assertThat(devices).as("only B has an input for A's OUT; A's own jacks are never offered")
                    .containsExactly(b);
            for (int i = 0; i < p.intoJack.getItemCount(); i++) {
                assertThat(((Port) p.intoJack.getItemAt(i).value()).getDirection())
                        .isEqualTo(Port.Direction.IN);
            }
        });
    }

    @Test
    @DisplayName("a device with nothing patchable opens no dialog")
    void nothingPatchableOpensNoDialog() throws Exception {
        Lonely lonely = new Lonely();
        rack.addDevice(lonely);
        boolean[] asked = {false};
        RackPanel.dialogs = d -> {
            asked[0] = true;
            return d.getOptions()[0];
        };
        onEdt(() -> panel.patchCableFrom(lonely));
        assertThat(asked[0]).isFalse();
        assertThat(rack.getCables()).isEmpty();
    }

    @Test
    @DisplayName("Unplug Cable… lists the device's cables by name, defaults to Cancel, and removes the chosen one undoably")
    void unplugCableFromTheMenu() throws Exception {
        Cable ab = rack.connect(a.getPort("out"), b.getPort("run"));
        Cable ba = rack.connect(b.getPort("out"), a.getPort("run"));
        // ba closes a loop, so the rack refuses it: one cable either way
        assertThat(ba).isNull();
        List<String> offered = new ArrayList<>();
        RackPanel.dialogs = d -> {
            assertThat(d.getDefaultValue()).as("Cancel is the default button: the gesture takes something away")
                    .isEqualTo(DialogDescriptor.CANCEL_OPTION);
            CableDialogs.UnplugPanel p = (CableDialogs.UnplugPanel) d.getMessage();
            for (int i = 0; i < p.cable.getItemCount(); i++) {
                offered.add(p.cable.getItemAt(i).label());
            }
            choose(p.cable, ab);
            return d.getOptions()[0];
        };
        onEdt(() -> {
            JMenuItem unplug = item(panel.buildMenu(b, null), Bundle.RackPanel_unplugCable());
            assertThat(unplug.isEnabled()).isTrue();
            unplug.doClick();
        });
        assertThat(offered).containsExactly("RUN ← A OUT");
        assertThat(rack.getCables()).isEmpty();
        rack.undo();
        assertThat(rack.getCables()).containsExactly(ab);
    }

    @Test
    @DisplayName("Cancel in the unplug dialog leaves every cable where it is")
    void unplugCancelKeepsTheCable() throws Exception {
        Cable ab = rack.connect(a.getPort("out"), b.getPort("run"));
        RackPanel.dialogs = d -> DialogDescriptor.CANCEL_OPTION;
        onEdt(() -> panel.unplugCableFrom(a));
        assertThat(rack.getCables()).containsExactly(ab);
    }

    @Test
    @DisplayName("Unplug Cable… is greyed on a device with no cables")
    void unplugGreyWithoutCables() throws Exception {
        onEdt(() -> assertThat(item(panel.buildMenu(a, null), Bundle.RackPanel_unplugCable()).isEnabled())
                .isFalse());
    }

    @Test
    @DisplayName("Delete takes the device holding the focused control, else the selected one")
    void deleteTakesTheFocusedDevice() throws Exception {
        onEdt(() -> {
            panel.openDeviceMenu(a, null);   // A selected
            assertThat(panel.removeTarget(b.go)).as("focus on B's GO").isSameAs(b);
            assertThat(panel.removeTarget(panel)).as("focus on the rack itself").isSameAs(a);
            assertThat(panel.removeTarget(null)).as("no focus").isSameAs(a);
            panel.removeFor(b.go);
        });
        assertThat(rack.getDevices()).containsExactly(a);
    }
}
