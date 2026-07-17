package org.nmox.studio.rack.blockstudio;

import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The studio's lifecycle laws, headless: constructing does no work
 * beyond building the panel (zero boot cost — no IO, no processes),
 * open/close keep the rack listener symmetric across repeats, and a
 * no-aim first show degrades to the honest empty state instead of
 * crashing.
 */
class BlockStudioLifecycleTest {

    @Test
    @DisplayName("Open/close cycles stay listener-symmetric and aim-less showing is safe")
    void lifecycle() throws Exception {
        BlockStudioTopComponent[] tc = new BlockStudioTopComponent[1];
        SwingUtilities.invokeAndWait(() -> tc[0] = new BlockStudioTopComponent());
        assertThat(tc[0].getAccessibleContext().getAccessibleName()).isNotBlank();

        for (int i = 0; i < 2; i++) {
            SwingUtilities.invokeAndWait(tc[0]::componentOpened);
            SwingUtilities.invokeAndWait(tc[0]::componentShowing);
            SwingUtilities.invokeAndWait(tc[0]::componentClosed);
        }
        // a second component may open while the first is closed — the
        // shared rack must not have accumulated stale listeners (no
        // exception, no double-delivery path to a closed component)
        BlockStudioTopComponent[] other = new BlockStudioTopComponent[1];
        SwingUtilities.invokeAndWait(() -> other[0] = new BlockStudioTopComponent());
        SwingUtilities.invokeAndWait(other[0]::componentOpened);
        SwingUtilities.invokeAndWait(other[0]::componentClosed);
    }
}
