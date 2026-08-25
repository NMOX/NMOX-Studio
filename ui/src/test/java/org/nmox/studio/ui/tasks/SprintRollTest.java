package org.nmox.studio.ui.tasks;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The roll-over suggestion's two rules (v2.38.1). */
class SprintRollTest {

    @Test
    @DisplayName("trailing numbers increment; unnumbered names gain a counter")
    void names() {
        assertThat(SprintRoll.nextName("Sprint 12")).isEqualTo("Sprint 13");
        assertThat(SprintRoll.nextName("Sprint 9 ")).isEqualTo("Sprint 10");
        assertThat(SprintRoll.nextName("Hardening")).isEqualTo("Hardening 2");
        assertThat(SprintRoll.nextName("v2.4")).isEqualTo("v2.5");
        assertThat(SprintRoll.nextName("Sprint 99999999999999999999"))
                .as("a 20-digit tail is a name, not a counter")
                .isEqualTo("Sprint 99999999999999999999 2");
    }

    @Test
    @DisplayName("the window starts the day after and keeps the closed sprint's length")
    void window() {
        LocalDate[] next = SprintRoll.nextWindow(
                LocalDate.of(2026, 8, 11), LocalDate.of(2026, 8, 24));
        assertThat(next[0]).isEqualTo(LocalDate.of(2026, 8, 25));
        assertThat(next[1]).isEqualTo(LocalDate.of(2026, 9, 7));
        // a one-day sprint rolls to a one-day sprint
        LocalDate[] oneDay = SprintRoll.nextWindow(
                LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 24));
        assertThat(oneDay[0]).isEqualTo(oneDay[1]);
    }
    @Test
    @DisplayName("velocity line: newest first, capped at three, honest null when empty")
    void velocity() {
        java.util.List<TaskBoard.ClosedSprint> h = new java.util.ArrayList<>();
        assertThat(SprintRoll.velocityLine(h)).isNull();
        h.add(new TaskBoard.ClosedSprint("S1", 1, 2, 12, ""));
        assertThat(SprintRoll.velocityLine(h))
                .isEqualTo("Velocity — last 1 sprint: 12 done (avg 12)");
        h.add(new TaskBoard.ClosedSprint("S2", 3, 4, 9, ""));
        h.add(new TaskBoard.ClosedSprint("S3", 5, 6, 14, ""));
        h.add(new TaskBoard.ClosedSprint("S4", 7, 8, 7, ""));
        assertThat(SprintRoll.velocityLine(h))
                .as("newest first, only the last three")
                .isEqualTo("Velocity — last 3 sprints: 7, 14, 9 done (avg 10)");
    }

}
