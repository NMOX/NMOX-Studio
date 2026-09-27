package org.nmox.studio.ui.browser.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.KeyStroke;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.util.KeyboardAccess;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Motion timeline without a mouse (3.4, question 3): every gesture the
 * mouse had — select a stop, move it, edit it, add one, scrub — through the
 * strip's own key bindings, on the same clamped model the drag uses.
 */
class TimelineStripKeyboardTest {

    private final List<Integer> scrubs = new ArrayList<>();
    private final AtomicInteger changes = new AtomicInteger();
    private final List<String> edits = new ArrayList<>();

    private TimelineStrip strip() {
        TimelineStrip s = new TimelineStrip(scrubs::add, changes::incrementAndGet,
                (prop, pct) -> edits.add(prop + "@" + pct));
        s.setSize(520, 100);
        s.model().setStop("opacity", 0, "0");
        s.model().setStop("opacity", 50, "0.5");
        s.model().setStop("opacity", 100, "1");
        s.model().setStop("transform", 40, "translateX(10px)");
        s.refresh();
        return s;
    }

    private static void key(TimelineStrip s, String stroke) {
        assertThat(KeyboardAccess.perform(s, KeyStroke.getKeyStroke(stroke)))
                .as(stroke + " is bound").isTrue();
    }

    @Test
    @DisplayName("arrow keys walk the stops and the tracks, and a screen reader hears which stop")
    void arrowsSelect() {
        TimelineStrip s = strip();
        key(s, "RIGHT");
        assertThat(s.selectedProperty()).isEqualTo("opacity");
        assertThat(s.selectedPercent()).isZero();
        key(s, "RIGHT");
        assertThat(s.selectedPercent()).isEqualTo(50);
        assertThat(s.getAccessibleContext().getAccessibleName())
                .isEqualTo("Animation timeline: opacity at 50%, 0.5");
        key(s, "LEFT");
        assertThat(s.selectedPercent()).isZero();
        key(s, "DOWN");
        assertThat(s.selectedProperty()).isEqualTo("transform");
        assertThat(s.selectedPercent()).as("the nearest stop on the next track").isEqualTo(40);
    }

    @Test
    @DisplayName("Enter edits the selected stop, as a double-click on its diamond does")
    void enterEdits() {
        TimelineStrip s = strip();
        key(s, "ENTER");
        assertThat(edits).as("nothing selected, nothing to edit").isEmpty();
        key(s, "RIGHT");
        key(s, "RIGHT");
        key(s, "ENTER");
        assertThat(edits).containsExactly("opacity@50");
    }

    @Test
    @DisplayName("Shift+arrows move the stop one percent, clamped between its neighbours as a drag is")
    void shiftMoves() {
        TimelineStrip s = strip();
        key(s, "RIGHT");
        key(s, "RIGHT");
        key(s, "shift RIGHT");
        assertThat(s.selectedPercent()).isEqualTo(51);
        assertThat(s.model().stops("opacity")).containsKey(51).doesNotContainKey(50);
        assertThat(changes).hasPositiveValue();
        key(s, "LEFT");
        key(s, "shift RIGHT");
        assertThat(s.model().stops("opacity")).as("the stop at 0 moved to 1").containsKey(1);
    }

    @Test
    @DisplayName("[ and ] scrub, and Insert or + adds a stop at the playhead; Delete still removes")
    void scrubAddDelete() {
        TimelineStrip s = strip();
        key(s, "CLOSE_BRACKET");
        key(s, "CLOSE_BRACKET");
        assertThat(s.scrubPercentForTest()).isEqualTo(10);
        assertThat(scrubs).containsExactly(5, 10);
        key(s, "OPEN_BRACKET");
        assertThat(s.scrubPercentForTest()).isEqualTo(5);
        key(s, "INSERT");
        assertThat(s.model().stops("opacity")).containsKey(5);
        assertThat(s.selectedPercent()).isEqualTo(5);
        key(s, "DELETE");
        assertThat(s.model().stops("opacity")).doesNotContainKey(5);
        assertThat(s.getAccessibleContext().getAccessibleName()).isEqualTo("Animation timeline");
    }
}
