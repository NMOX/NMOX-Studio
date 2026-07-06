package org.nmox.studio.project;

import java.lang.reflect.Field;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.service.RackService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The workbench's rack listener must live exactly as long as the window
 * is open. The leak this pins: a listener added in the constructor and
 * never removed kept rebuilding the CLOSED workbench offscreen — full
 * panel rebuild plus per-project detectAsync walks — on every project
 * switch, forever.
 *
 * <p>{@code ProjectExplorerTopComponent} is pure-Swing and
 * JaCoCo-excluded; like {@code FileTreePanelEdtTest} this proves just
 * the load-bearing lifecycle invariant. The rack keeps its listener
 * list private, so the count is read reflectively — brittle only if the
 * field is renamed, in which case this test fails loudly, not silently.
 */
class WorkbenchRackListenerLifecycleTest {

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

        ProjectExplorerTopComponent[] tc = new ProjectExplorerTopComponent[1];
        SwingUtilities.invokeAndWait(() -> tc[0] = new ProjectExplorerTopComponent());
        assertThat(rackListenerCount())
                .as("construction alone attaches nothing — the old leak started here")
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
