package org.nmox.studio.rack.devices;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TAIL and TEMPO are timers, not processes — but a kill -9 mid-follow or
 * mid-clock should still come back armed. They report {@code isResumable}
 * from their switch (not {@code isLive}, which stays process-only so the
 * status line's "running" count doesn't swell), and {@code resume} re-arms.
 */
class TimerResumeTest {

    @Test
    @DisplayName("An idle TAIL/TEMPO is neither live nor resumable")
    void idleNotResumable() {
        TailDevice tail = new TailDevice();
        TempoDevice tempo = new TempoDevice();
        try {
            assertThat(tail.isLive()).isFalse();
            assertThat(tail.isResumable()).isFalse();
            assertThat(tempo.isResumable()).isFalse();
        } finally {
            tail.dispose();
            tempo.dispose();
        }
    }

    @Test
    @DisplayName("Arming FOLLOW/CLOCK makes them resumable without making them 'live'")
    void armedIsResumableNotLive() {
        TailDevice tail = new TailDevice();
        try {
            tail.applyState(Map.of("follow", "true"));
            assertThat(tail.isResumable()).as("a following TAIL resurrects").isTrue();
            assertThat(tail.isLive()).as("but it holds no process").isFalse();
        } finally {
            tail.dispose();
        }

        TempoDevice tempo = new TempoDevice();
        try {
            tempo.applyState(Map.of("running", "true"));
            assertThat(tempo.isResumable()).as("a clocking TEMPO resurrects").isTrue();
            assertThat(tempo.isLive()).isFalse();
        } finally {
            tempo.dispose();
        }
    }

    @Test
    @DisplayName("resume() re-arms the switch after a crash")
    void resumeRearms() {
        TailDevice tail = new TailDevice();
        try {
            assertThat(tail.isResumable()).isFalse();
            tail.resume();
            flushEdt(); // resume() re-arms on the EDT; drain it before asserting
            assertThat(tail.isResumable()).as("resume turns FOLLOW back on").isTrue();
        } finally {
            tail.dispose();
        }
    }

    private static void flushEdt() {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ex) {
            throw new AssertionError("EDT flush interrupted", ex);
        }
    }
}
