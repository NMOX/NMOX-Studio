package org.nmox.studio.rack.ui;

import java.awt.Color;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v1.95.1 review's two gesture-lifecycle findings, pinned:
 *
 * <p>HIGH — an armed click-patch surviving a rack FLIP is invisible
 * (the preview only paints on the rear), so the next rear jack click
 * silently patched a cable the user believed cancelled. A flip must
 * drop the gesture.
 *
 * <p>MED — an armed gesture surviving a structural REBUILD (device
 * removal, preset/patch load, undo) holds a Port of a disposed device;
 * the next click connected a ghost cable to a device no longer in the
 * rack. A rebuild must drop the gesture.
 */
class RackPanelGestureCancelTest {

    private static final class Jacks extends RackDevice {
        final Port out;

        Jacks(String id) {
            super(id, id.toUpperCase(), "JACKS", new Color(0, 0, 0), 1);
            out = addOutPort("out", "OUT", SignalType.TRIGGER);
            addInPort("run", "RUN", SignalType.TRIGGER);
        }

        @Override
        public void receive(Port in, Signal signal) {
        }
    }

    @Test
    @DisplayName("Flipping the rack drops an armed click-patch — no invisible armed state")
    void flipCancelsArmedGesture() throws Exception {
        Rack rack = new Rack();
        try {
            Jacks a = new Jacks("a");
            rack.addDevice(a);
            RackPanel[] panel = new RackPanel[1];
            SwingUtilities.invokeAndWait(() -> {
                panel[0] = new RackPanel(rack);
                panel[0].patchGesture.press(a.out);
                panel[0].patchGesture.release(true, null); // click = armed sticky
                assertThat(panel[0].patchGesture.isSticky()).isTrue();

                panel[0].setFront(!panel[0].isFront());
                assertThat(panel[0].patchGesture.isTracking())
                        .as("a flip must cancel the armed patch — on the front it is invisible")
                        .isFalse();
            });
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("A structural rebuild (device removal, patch load) drops an armed click-patch")
    void rebuildCancelsArmedGesture() throws Exception {
        Rack rack = new Rack();
        try {
            Jacks a = new Jacks("a");
            Jacks b = new Jacks("b");
            rack.addDevice(a);
            rack.addDevice(b);
            RackPanel[] panel = new RackPanel[1];
            SwingUtilities.invokeAndWait(() -> {
                panel[0] = new RackPanel(rack);
                panel[0].addNotify(); // attach the structure listener
                panel[0].patchGesture.press(a.out);
                panel[0].patchGesture.release(true, null);
                assertThat(panel[0].patchGesture.isSticky()).isTrue();
            });

            rack.removeDevice(a); // fires structureChanged -> invokeLater(rebuild)
            SwingUtilities.invokeAndWait(() -> { });

            SwingUtilities.invokeAndWait(() -> {
                assertThat(panel[0].patchGesture.isTracking())
                        .as("the armed gesture held a Port of the removed device — "
                                + "a later click would patch a ghost cable to a disposed device")
                        .isFalse();
                panel[0].removeNotify();
            });
        } finally {
            rack.shutdown();
        }
    }
}
