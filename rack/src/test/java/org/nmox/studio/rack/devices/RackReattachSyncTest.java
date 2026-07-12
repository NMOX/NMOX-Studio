package org.nmox.studio.rack.devices;

import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.model.Rack;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TAIL and TEMPO carry live timers whose faceplate (EYE led / tick LCD)
 * must not outlive {@code dispose()}. Undo of a device removal re-attaches
 * the SAME instance: before v1.50.0 the switch and display still read
 * "armed" while the timer stayed dead — a stale display. {@code onAttached()}
 * now re-runs each device's display/timer sync on every (re-)attach.
 */
class RackReattachSyncTest {

    private Rack rack;

    @BeforeEach
    void setUp() {
        rack = new Rack();
        rack.enableUndoCapture();
    }

    @AfterEach
    void tearDown() throws Exception {
        flush();
        rack.shutdown();
    }

    /** TEMPO syncs on the EDT (onEdt); TAIL syncs synchronously. Drain either way. */
    private static void flush() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    @DisplayName("TAIL re-syncs its follow poll and EYE led on undo re-attach")
    void tailReattachResyncsDisplay() {
        TailDevice tail = new TailDevice();
        rack.addDevice(tail);
        tail.applyState(Map.of("follow", "true"));
        assertThat(tail.isPolling()).as("armed FOLLOW polls the file").isTrue();
        assertThat(tail.displayInSync()).isTrue();

        rack.removeDevice(tail);
        assertThat(tail.isPolling()).as("dispose stopped the poll").isFalse();

        rack.undo(); // undo of the remove re-attaches this same instance
        assertThat(tail.isPolling())
                .as("undo re-attach re-arms the follow poll (stale display fixed)")
                .isTrue();
        assertThat(tail.displayInSync())
                .as("EYE led matches the FOLLOW switch after re-attach")
                .isTrue();
    }

    @Test
    @DisplayName("TEMPO re-syncs its transport clock on undo re-attach")
    void tempoReattachResyncsClock() throws Exception {
        TempoDevice tempo = new TempoDevice();
        rack.addDevice(tempo);
        tempo.applyState(Map.of("running", "true")); // syncTimer runs on the EDT
        flush();
        assertThat(tempo.isClockRunning()).as("armed CLOCK ticks").isTrue();

        rack.removeDevice(tempo); // dispose stops the timer (on the EDT)
        flush();
        assertThat(tempo.isClockRunning()).as("dispose stopped the clock").isFalse();

        rack.undo(); // undo of the remove re-attaches this same instance
        flush();
        assertThat(tempo.isClockRunning())
                .as("undo re-attach re-arms the transport clock (stale display fixed)")
                .isTrue();
    }
}
