package org.nmox.studio.rack.ui;

import java.awt.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.nmox.studio.rack.ui.CablePatchGesture.Action.CANCEL;
import static org.nmox.studio.rack.ui.CablePatchGesture.Action.CONNECT;
import static org.nmox.studio.rack.ui.CablePatchGesture.Action.NONE;
import static org.nmox.studio.rack.ui.CablePatchGesture.Action.TRACK;

/**
 * Click-to-click patching (v1.95.0), found by real use: the rear rack
 * is wider than a default window, so a press-drag-release between
 * far-apart jacks is physically awkward. Click arms (the preview
 * follows the mouse), click a compatible jack connects, empty space or
 * Escape cancels — and the classic drag keeps working through the same
 * machine.
 */
class CablePatchGestureTest {

    private static final class Jacks extends RackDevice {
        final Port out;
        final Port in;

        Jacks(String id) {
            super(id, id.toUpperCase(), "JACKS", new Color(0, 0, 0), 1);
            out = addOutPort("out", "OUT", SignalType.TRIGGER);
            in = addInPort("run", "RUN", SignalType.TRIGGER);
        }

        @Override
        public void receive(Port in, Signal signal) {
        }
    }

    private final Jacks a = new Jacks("a");
    private final Jacks b = new Jacks("b");
    private final CablePatchGesture g = new CablePatchGesture();

    @Test
    @DisplayName("Classic drag still works: press a jack, release on a compatible one")
    void classicDrag() {
        assertThat(g.press(a.out)).isEqualTo(TRACK);
        assertThat(g.release(false, b.in)).isEqualTo(CONNECT);
    }

    @Test
    @DisplayName("A click arms; the second click on a compatible jack connects")
    void clickClickConnects() {
        assertThat(g.press(a.out)).isEqualTo(TRACK);
        assertThat(g.release(true, null)).as("release on the source = click, arms").isEqualTo(TRACK);
        assertThat(g.isSticky()).isTrue();
        assertThat(g.press(b.in)).as("second click completes").isEqualTo(CONNECT);
    }

    @Test
    @DisplayName("Armed: clicking empty space or an incompatible jack cancels; a same-side jack re-arms")
    void armedCancelsAndRearms() {
        g.press(a.out);
        g.release(true, null);
        assertThat(g.press(null)).as("empty space drops the cable").isEqualTo(CANCEL);
        assertThat(g.isTracking()).isFalse();

        g.press(a.out);
        g.release(true, null);
        assertThat(g.press(b.out)).as("another OUT jack re-arms from there").isEqualTo(TRACK);
        assertThat(g.from()).isSameAs(b.out);
        assertThat(g.press(a.in)).isEqualTo(CONNECT);
    }

    @Test
    @DisplayName("Escape cancels an armed patch; idle Escape is a no-op")
    void escapeCancels() {
        assertThat(g.escape()).isEqualTo(NONE);
        g.press(a.out);
        g.release(true, null);
        assertThat(g.escape()).isEqualTo(CANCEL);
        assertThat(g.isTracking()).isFalse();
    }

    @Test
    @DisplayName("A drag released over nothing cancels exactly as before")
    void dragToNowhereCancels() {
        g.press(a.out);
        assertThat(g.release(false, null)).isEqualTo(CANCEL);
        assertThat(g.isTracking()).isFalse();
    }
}
