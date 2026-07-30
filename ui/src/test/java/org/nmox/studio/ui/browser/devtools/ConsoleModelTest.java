package org.nmox.studio.ui.browser.devtools;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Console ring's hostile-page laws: cap 1000 entries exactly,
 * 8k text truncation with an honest marker, dropped counter, clear.
 */
class ConsoleModelTest {

    @Test
    @DisplayName("holds exactly CAP entries; the oldest drop and are counted")
    void capIsExact() {
        ConsoleModel m = new ConsoleModel();
        for (int i = 0; i < ConsoleModel.CAP; i++) {
            m.add("log", "line " + i, i);
        }
        assertThat(m.entries()).hasSize(ConsoleModel.CAP);
        assertThat(m.droppedCount()).isZero();

        m.add("log", "one more", 9999);
        assertThat(m.entries()).hasSize(ConsoleModel.CAP);
        assertThat(m.droppedCount()).isEqualTo(1);
        assertThat(m.entries().get(0).text()).isEqualTo("line 1"); // oldest gone
        assertThat(m.entries().get(ConsoleModel.CAP - 1).text()).isEqualTo("one more");
    }

    @Test
    @DisplayName("text past 8000 chars is truncated with the marker")
    void truncatesHostileText() {
        ConsoleModel m = new ConsoleModel();
        m.add("log", "x".repeat(100_000), 0);
        String stored = m.entries().get(0).text();
        assertThat(stored).hasSize(ConsoleModel.TEXT_CAP + ConsoleModel.TRUNCATED.length());
        assertThat(stored).endsWith(ConsoleModel.TRUNCATED);
        // and text at the cap is untouched
        m.clear();
        m.add("log", "y".repeat(ConsoleModel.TEXT_CAP), 0);
        assertThat(m.entries().get(0).text()).doesNotContain(ConsoleModel.TRUNCATED);
    }

    @Test
    @DisplayName("null text becomes empty; hostile levels collapse to log")
    void nullAndHostileInput() {
        ConsoleModel m = new ConsoleModel();
        m.add(null, null, 0);
        assertThat(m.entries().get(0).text()).isEmpty();
        assertThat(m.entries().get(0).level()).isEqualTo("log");
        m.add("<script>alert(1)</script>", "t", 0);
        assertThat(m.entries().get(1).level()).isEqualTo("log");
        m.add("error", "t", 0);
        assertThat(m.entries().get(2).level()).isEqualTo("error");
    }

    @Test
    @DisplayName("clear empties the ring and resets the dropped counter")
    void clearResets() {
        ConsoleModel m = new ConsoleModel();
        for (int i = 0; i < ConsoleModel.CAP + 5; i++) {
            m.add("log", "line", 0);
        }
        assertThat(m.droppedCount()).isEqualTo(5);
        m.clear();
        assertThat(m.entries()).isEmpty();
        assertThat(m.droppedCount()).isZero();
    }

    @Test
    @DisplayName("the change listener fires on add and clear")
    void listenerFires() {
        ConsoleModel m = new ConsoleModel();
        AtomicInteger fired = new AtomicInteger();
        m.setListener(fired::incrementAndGet);
        m.add("log", "a", 0);
        m.clear();
        assertThat(fired.get()).isEqualTo(2);
    }
}
