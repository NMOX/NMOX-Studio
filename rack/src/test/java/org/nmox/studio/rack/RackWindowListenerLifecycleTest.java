package org.nmox.studio.rack;

import java.lang.reflect.Field;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.service.RackService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger item 17, window half: the Rack window's project-label listener
 * must live exactly as long as the window is open — the constructor
 * wiring it replaced kept re-labelling a closed window on every project
 * switch. Same shape as {@code WorkbenchRackListenerLifecycleTest}; the
 * TopComponent is pure-Swing and JaCoCo-excluded, so this proves just
 * the lifecycle invariant, reading the rack's private listener list
 * reflectively (a rename fails loudly, not silently).
 */
class RackWindowListenerLifecycleTest {

    private static int rackListenerCount() throws Exception {
        Rack rack = RackService.getDefault().getRack();
        Field f = Rack.class.getDeclaredField("listeners");
        f.setAccessible(true);
        return ((List<?>) f.get(rack)).size();
    }

    @Test
    @DisplayName("Open attaches the rack listener exactly once; close detaches it")
    void listenerLivesExactlyAsLongAsTheWindowIsOpen() throws Exception {
        // force the singleton (and its own internal listeners) BEFORE baselining
        RackService.getDefault().getRack();
        int base = rackListenerCount();

        RackTopComponent[] tc = new RackTopComponent[1];
        SwingUtilities.invokeAndWait(() -> tc[0] = new RackTopComponent());
        assertThat(rackListenerCount())
                .as("construction alone attaches nothing — neither the window's own "
                        + "listener nor the embedded RackPanel's (which waits for addNotify)")
                .isEqualTo(base);

        SwingUtilities.invokeAndWait(tc[0]::componentOpened);
        assertThat(rackListenerCount()).as("open attaches the listener").isEqualTo(base + 1);

        SwingUtilities.invokeAndWait(tc[0]::componentOpened);
        assertThat(rackListenerCount())
                .as("a second open must not double-attach (CopyOnWriteArrayList "
                        + "would double-fire every event)")
                .isEqualTo(base + 1);

        SwingUtilities.invokeAndWait(tc[0]::componentClosed);
        assertThat(rackListenerCount()).as("close detaches the listener").isEqualTo(base);

        SwingUtilities.invokeAndWait(tc[0]::componentOpened);
        assertThat(rackListenerCount()).as("reopen re-attaches").isEqualTo(base + 1);
        SwingUtilities.invokeAndWait(tc[0]::componentClosed);
        assertThat(rackListenerCount()).isEqualTo(base);
    }
}
