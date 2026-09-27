package org.nmox.studio.rack.ui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.nmox.studio.core.util.PlainTables;
import org.nmox.studio.core.util.PlainText;
import org.nmox.studio.rack.model.Cable;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;

/**
 * Cable work without a pointer (3.4). Patching ran only through mouse
 * events on the rear of the rack — a drag or two clicks on painted jacks —
 * and the only Unplug lived in a menu a right-click on a jack opened, so a
 * keyboard or screen-reader user could neither wire the rack nor take a
 * wire out. These are the two dialogs the device menu opens instead.
 *
 * <p>Both end in the SAME model call the mouse makes: {@link Rack#connect}
 * for a patch (the drag's call, so the undo entry and the saved patch are
 * identical) and {@link Rack#disconnect} for an unplug, which records its
 * own undo. Only legal choices are offered — a jack no racked device can
 * take is never listed, a device with no compatible jack is never listed —
 * so the dialog cannot build a cable the drag would have refused; what
 * {@code connect} still refuses (a cable that exists, a feedback loop) is
 * said out loud by the caller.
 */
@org.openide.util.NbBundle.Messages({
    "# {0} - a jack label as it is printed on the faceplate",
    "CableDialogs_jackOut={0} (output)",
    "# {0} - a jack label as it is printed on the faceplate",
    "CableDialogs_jackIn={0} (input)",
    "CableDialogs_fromJack=From jack:",
    "CableDialogs_toDevice=To device:",
    "CableDialogs_intoJack=Into jack:",
    "CableDialogs_cable=Cable:"
})
final class CableDialogs {

    private CableDialogs() {
    }

    /** A combo item: the value, and the words the combo shows for it. */
    record Choice<T>(T value, String label) {

        @Override
        public String toString() {
            return label;
        }
    }

    /** Every jack of {@code source} that some other racked device can take. */
    static List<Port> patchableJacks(Rack rack, RackDevice source) {
        List<Port> out = new ArrayList<>();
        for (Port p : source.getPorts()) {
            if (!targets(rack, p).isEmpty()) {
                out.add(p);
            }
        }
        return out;
    }

    /** The racked devices with at least one jack {@code from} can be cabled to. */
    static List<RackDevice> targets(Rack rack, Port from) {
        List<RackDevice> out = new ArrayList<>();
        for (RackDevice d : rack.getDevices()) {
            if (!compatibleJacks(d, from).isEmpty()) {
                out.add(d);
            }
        }
        return out;
    }

    /** The jacks of {@code target} that {@code from} can be cabled to. */
    static List<Port> compatibleJacks(RackDevice target, Port from) {
        List<Port> out = new ArrayList<>();
        for (Port p : target.getPorts()) {
            if (from.canConnectTo(p)) {
                out.add(p);
            }
        }
        return out;
    }

    /** A jack as the dialogs name it: its faceplate label and its direction. */
    static String jackLabel(Port p) {
        return p.getDirection() == Port.Direction.OUT
                ? Bundle.CableDialogs_jackOut(p.getLabel())
                : Bundle.CableDialogs_jackIn(p.getLabel());
    }

    /** A combo whose items render as text, never as markup: labels come from drop-in devices too. */
    private static <T> JComboBox<Choice<T>> combo(String name) {
        JComboBox<Choice<T>> c = new JComboBox<>();
        c.setRenderer(PlainTables.plain(new javax.swing.DefaultListCellRenderer()));
        // the label's words without its colon: a name, not a prompt
        c.getAccessibleContext().setAccessibleName(
                name.replaceFirst("[\\s\\u00a0]*[:：]\\s*$", ""));
        return c;
    }

    private static JLabel labelFor(String text, JComboBox<?> field) {
        JLabel l = new JLabel(PlainText.plain(text));
        l.setLabelFor(field);
        return l;
    }

    private static void row(JPanel p, int y, JLabel label, JComboBox<?> field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = y;
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.LINE_START;
        p.add(label, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        p.add(field, c);
    }

    /**
     * Patch a cable from one device: its jack, then a device that can take
     * it, then that device's compatible jack. Each choice narrows the next,
     * so every combination the dialog can hold is a legal cable.
     */
    static final class PatchPanel extends JPanel {

        private final Rack rack;
        final JComboBox<Choice<Port>> fromJack;
        final JComboBox<Choice<RackDevice>> toDevice;
        final JComboBox<Choice<Port>> intoJack;

        PatchPanel(Rack rack, RackDevice source) {
            super(new GridBagLayout());
            this.rack = rack;
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            fromJack = combo(Bundle.CableDialogs_fromJack());
            toDevice = combo(Bundle.CableDialogs_toDevice());
            intoJack = combo(Bundle.CableDialogs_intoJack());
            row(this, 0, labelFor(Bundle.CableDialogs_fromJack(), fromJack), fromJack);
            row(this, 1, labelFor(Bundle.CableDialogs_toDevice(), toDevice), toDevice);
            row(this, 2, labelFor(Bundle.CableDialogs_intoJack(), intoJack), intoJack);
            for (Port p : patchableJacks(rack, source)) {
                fromJack.addItem(new Choice<>(p, jackLabel(p)));
            }
            fromJack.addActionListener(e -> refillDevices());
            toDevice.addActionListener(e -> refillJacks());
            refillDevices();
        }

        /** Nothing on this device can be cabled to anything racked. */
        boolean isEmpty() {
            return fromJack.getItemCount() == 0;
        }

        private Port from() {
            Object o = fromJack.getSelectedItem();
            return o instanceof Choice<?> c ? (Port) c.value() : null;
        }

        private void refillDevices() {
            toDevice.removeAllItems();
            Port from = from();
            if (from != null) {
                for (RackDevice d : targets(rack, from)) {
                    toDevice.addItem(new Choice<>(d, d.getBusName()));
                }
            }
            refillJacks();
        }

        private void refillJacks() {
            intoJack.removeAllItems();
            Port from = from();
            Object o = toDevice.getSelectedItem();
            if (from != null && o instanceof Choice<?> c) {
                for (Port p : compatibleJacks((RackDevice) c.value(), from)) {
                    intoJack.addItem(new Choice<>(p, jackLabel(p)));
                }
            }
        }

        /**
         * Patches the chosen cable through {@link Rack#connect}, the call the
         * drag makes. Null when nothing is chosen or the rack refuses it (the
         * cable exists already, or it would close a feedback loop).
         */
        Cable apply() {
            Port from = from();
            Object o = intoJack.getSelectedItem();
            if (from == null || !(o instanceof Choice<?> c)) {
                return null;
            }
            return rack.connect(from, (Port) c.value());
        }
    }

    /** Unplug one of a device's cables, each named the way the device's description says it. */
    static final class UnplugPanel extends JPanel {

        private final Rack rack;
        final JComboBox<Choice<Cable>> cable;

        UnplugPanel(Rack rack, RackDevice device) {
            super(new GridBagLayout());
            this.rack = rack;
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            cable = combo(Bundle.CableDialogs_cable());
            row(this, 0, labelFor(Bundle.CableDialogs_cable(), cable), cable);
            for (Cable c : rack.getCables()) {
                String words = device.cableInWords(c);
                if (words != null) {
                    cable.addItem(new Choice<>(c, words));
                }
            }
        }

        boolean isEmpty() {
            return cable.getItemCount() == 0;
        }

        /** Removes the chosen cable (undoable); the cable, or null when none was chosen. */
        Cable apply() {
            Object o = cable.getSelectedItem();
            if (!(o instanceof Choice<?> c)) {
                return null;
            }
            Cable doomed = (Cable) c.value();
            rack.disconnect(doomed);
            return doomed;
        }
    }
}
