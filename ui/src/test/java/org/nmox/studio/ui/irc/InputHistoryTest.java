package org.nmox.studio.ui.irc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The input recall: Up walks back, Down walks forward and past the
 * newest entry restores the in-progress draft, the cap holds at 100,
 * and blanks/repeats don't clutter the list.
 */
class InputHistoryTest {

    @Test
    @DisplayName("Up walks back through sent lines, newest first")
    void upWalksBack() {
        InputHistory h = new InputHistory();
        h.add("one");
        h.add("two");
        h.add("three");
        assertThat(h.up("")).isEqualTo("three");
        assertThat(h.up("")).isEqualTo("two");
        assertThat(h.up("")).isEqualTo("one");
        assertThat(h.up("")).as("past the oldest stays at the oldest").isEqualTo("one");
    }

    @Test
    @DisplayName("Down walks forward and past the newest restores the draft")
    void downRestoresDraft() {
        InputHistory h = new InputHistory();
        h.add("one");
        h.add("two");
        assertThat(h.up("my draft")).isEqualTo("two");
        assertThat(h.up("ignored")).isEqualTo("one");
        assertThat(h.down()).isEqualTo("two");
        assertThat(h.down()).as("past the newest → the stashed draft").isEqualTo("my draft");
        assertThat(h.down()).as("not browsing anymore").isNull();
    }

    @Test
    @DisplayName("The cap holds at 100; older lines fall off")
    void capHolds() {
        InputHistory h = new InputHistory();
        for (int i = 0; i < 150; i++) {
            h.add("line" + i);
        }
        assertThat(h.size()).isEqualTo(100);
        assertThat(h.up("")).isEqualTo("line149");
        for (int i = 0; i < 200; i++) {
            h.up("");
        }
        assertThat(h.up("")).as("the oldest kept line is line50").isEqualTo("line50");
    }

    @Test
    @DisplayName("Blanks and immediate repeats are not recorded")
    void blanksAndRepeatsSkipped() {
        InputHistory h = new InputHistory();
        h.add("hello");
        h.add("hello");
        h.add("");
        h.add("   ");
        h.add(null);
        assertThat(h.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("Up with no history is a quiet null")
    void emptyHistoryIsNull() {
        assertThat(new InputHistory().up("draft")).isNull();
        assertThat(new InputHistory().down()).isNull();
    }

    @Test
    @DisplayName("Sending a line resets the browse cursor")
    void addResetsCursor() {
        InputHistory h = new InputHistory();
        h.add("one");
        h.add("two");
        assertThat(h.up("")).isEqualTo("two");
        h.add("three"); // sent something: browsing state gone
        assertThat(h.down()).isNull();
        assertThat(h.up("")).isEqualTo("three");
    }
}
