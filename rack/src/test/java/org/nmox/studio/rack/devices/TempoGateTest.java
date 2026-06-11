package org.nmox.studio.rack.devices;

import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.model.Signal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ENABLE gate makes GATE outputs (SURGE.RUNNING, WORMHOLE.LIVE)
 * actually consumable: the clock runs exactly while the gate is high.
 */
class TempoGateTest {

    @Test
    @DisplayName("ENABLE gate high starts the clock, low stops it")
    void gateDrivesTheClock() throws Exception {
        TempoDevice tempo = new TempoDevice();

        tempo.receive(tempo.getPort("enable"), Signal.gate(true));
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(tempo.getState()).containsEntry("running", "true");

        tempo.receive(tempo.getPort("enable"), Signal.gate(false));
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(tempo.getState()).containsEntry("running", "false");

        tempo.dispose();
    }
}
