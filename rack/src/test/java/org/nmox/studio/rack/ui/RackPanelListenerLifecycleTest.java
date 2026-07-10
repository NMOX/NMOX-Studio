package org.nmox.studio.rack.ui;

import java.lang.reflect.Field;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.devices.DeviceType;
import org.nmox.studio.rack.model.Rack;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger item 17: the rack panel's model listener must live exactly as
 * long as the panel is in the hierarchy. The waste this pins: a listener
 * added in the constructor and never removed kept rebuilding faceplates
 * into a CLOSED rack window on every preset/Learning Space load. The
 * re-sync half: re-attaching must rebuild, so a rack that changed while
 * the window was closed renders correctly on reopen.
 *
 * <p>Same shape as {@code WorkbenchRackListenerLifecycleTest}: the panel
 * is pure-Swing and JaCoCo-excluded, so this proves just the
 * load-bearing lifecycle invariant, reading the private listener list
 * reflectively (a rename fails loudly, not silently).
 */
class RackPanelListenerLifecycleTest {

    private static int listenerCount(Rack rack) throws Exception {
        Field f = Rack.class.getDeclaredField("listeners");
        f.setAccessible(true);
        return ((List<?>) f.get(rack)).size();
    }

    @Test
    @DisplayName("addNotify attaches the rack listener exactly once; removeNotify detaches it")
    void listenerLivesExactlyAsLongAsThePanelIsInTheHierarchy() throws Exception {
        Rack rack = new Rack();
        try {
            int base = listenerCount(rack);

            RackPanel[] panel = new RackPanel[1];
            SwingUtilities.invokeAndWait(() -> panel[0] = new RackPanel(rack));
            assertThat(listenerCount(rack))
                    .as("construction alone attaches nothing — the old leak started here")
                    .isEqualTo(base);

            SwingUtilities.invokeAndWait(panel[0]::addNotify);
            assertThat(listenerCount(rack)).as("entering the hierarchy attaches").isEqualTo(base + 1);

            SwingUtilities.invokeAndWait(panel[0]::addNotify);
            assertThat(listenerCount(rack))
                    .as("a second addNotify must not double-attach").isEqualTo(base + 1);

            SwingUtilities.invokeAndWait(panel[0]::removeNotify);
            assertThat(listenerCount(rack)).as("leaving the hierarchy detaches").isEqualTo(base);

            SwingUtilities.invokeAndWait(panel[0]::addNotify);
            assertThat(listenerCount(rack)).as("re-entering re-attaches").isEqualTo(base + 1);
            SwingUtilities.invokeAndWait(panel[0]::removeNotify);
            assertThat(listenerCount(rack)).isEqualTo(base);
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("Re-attach re-syncs: devices added while detached appear on reopen")
    void reattachRebuildsWhatChangedWhileClosed() throws Exception {
        Rack rack = new Rack();
        try {
            RackPanel[] panel = new RackPanel[1];
            SwingUtilities.invokeAndWait(() -> {
                panel[0] = new RackPanel(rack);
                panel[0].addNotify();
                panel[0].removeNotify();
            });

            // the rack changes while the panel is out of the hierarchy —
            // a preset load into a closed window
            SwingUtilities.invokeAndWait(() -> rack.addDevice(DeviceType.CONSOLE.create()));
            rack.awaitRouterIdle();
            SwingUtilities.invokeAndWait(() -> { });
            assertThat(panel[0].getComponentCount())
                    .as("detached: no offscreen rebuild happened").isZero();

            SwingUtilities.invokeAndWait(panel[0]::addNotify);
            SwingUtilities.invokeAndWait(() -> { }); // drain the rebuild's invokeLater
            assertThat(panel[0].getComponentCount())
                    .as("re-attach rebuilds from the model").isEqualTo(1);
            SwingUtilities.invokeAndWait(panel[0]::removeNotify);
        } finally {
            rack.shutdown();
        }
    }
}
