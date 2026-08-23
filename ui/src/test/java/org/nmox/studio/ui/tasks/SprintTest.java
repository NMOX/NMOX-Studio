package org.nmox.studio.ui.tasks;

import java.time.ZoneId;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sprint's contract (v2.37.0, the scrum-master pass): the window
 * round-trips, an invalid window heals to none (the v2.9.0 law),
 * closing archives-and-clears without touching a single card, the
 * burndown is reconstructed from done stamps alone, and the report
 * omits what it cannot say. Boards with controlled done stamps are
 * built as JSON — the runtime only ever stamps wall-clock now.
 */
class SprintTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final long DAY = 24L * 60 * 60 * 1000;
    // 2026-08-10T00:00Z
    private static final long START = 1786320000000L;

    /** A board with a Falcon sprint and cards carrying explicit done stamps. */
    private static TaskBoard board(long... doneStamps) {
        JSONArray cards = new JSONArray();
        int n = 0;
        for (long stamp : doneStamps) {
            cards.put(new JSONObject()
                    .put("id", "card-" + (++n))
                    .put("title", stamp == 0 ? "open-" + n : "done-" + n)
                    .put("created", START)
                    .put("done", stamp));
        }
        JSONObject root = new JSONObject()
                .put("version", 1)
                .put("sprint", new JSONObject()
                        .put("name", "Falcon")
                        .put("start", START)
                        .put("end", START + 9 * DAY))
                .put("columns", new JSONArray()
                        .put(new JSONObject().put("name", "Doing").put("cards", cards))
                        .put(new JSONObject().put("name", "Done")
                                .put("cards", new JSONArray())));
        return TaskBoard.fromJson(root.toString());
    }

    @Test
    @DisplayName("the sprint window round-trips through the file")
    void sprintRoundTrips() {
        TaskBoard back = TaskBoard.fromJson(board().toJson());
        assertThat(back.hasSprint()).isTrue();
        assertThat(back.sprintName()).isEqualTo("Falcon");
        assertThat(back.sprintStart()).isEqualTo(START);
        assertThat(back.sprintEnd()).isEqualTo(START + 9 * DAY);
    }

    @Test
    @DisplayName("a mangled window heals to no sprint — never poisons the ceremonies")
    void mangledWindowHeals() {
        String json = board().toJson().replace(String.valueOf(START + 9 * DAY),
                String.valueOf(START - DAY)); // end before start
        assertThat(TaskBoard.fromJson(json).hasSprint()).isFalse();
    }

    @Test
    @DisplayName("closeSprint archives name/window/done/retro, clears — cards untouched")
    void closeArchivesAndClears() {
        TaskBoard b = board(START + 2 * DAY, 0);
        b.setRetro("keep: the walks");
        int cardsBefore = b.columns().stream().mapToInt(c -> c.cards().size()).sum();

        TaskBoard.ClosedSprint closed = b.closeSprint();

        assertThat(closed.name()).isEqualTo("Falcon");
        assertThat(closed.done()).isEqualTo(1);
        assertThat(closed.retro()).isEqualTo("keep: the walks");
        assertThat(b.hasSprint()).isFalse();
        assertThat(b.retro()).isEmpty();
        assertThat(b.sprintHistory()).hasSize(1);
        assertThat(b.columns().stream().mapToInt(c -> c.cards().size()).sum())
                .as("closing a sprint is bookkeeping, not cleanup")
                .isEqualTo(cardsBefore);
        TaskBoard back = TaskBoard.fromJson(b.toJson());
        assertThat(back.sprintHistory()).hasSize(1);
        assertThat(back.sprintHistory().get(0).done()).isEqualTo(1);
        assertThat(back.sprintHistory().get(0).retro()).isEqualTo("keep: the walks");
    }

    @Test
    @DisplayName("burndown reconstructs remaining-per-day from done stamps alone")
    void burndownFromStamps() {
        TaskBoard b = board(START + DAY + 1000, START + 3 * DAY + 1000, 0);
        long now = START + 4 * DAY + 1000; // day 5

        BoardStats.Burndown burn = BoardStats.burndown(b, now, UTC);

        assertThat(burn.committed()).isEqualTo(3);
        assertThat(burn.totalDays()).isEqualTo(10);
        assertThat(burn.remainingPerDay())
                .as("day1: 3 · day2: one done → 2 · day3: 2 · day4: two done → 1 · day5: 1")
                .containsExactly(3, 2, 2, 1, 1);
    }

    @Test
    @DisplayName("the report names done/open and OMITS empty sections")
    void reportSpeaksAndOmits() {
        TaskBoard b = board(START + DAY, 0);
        String md = SprintReport.build(b, START + 2 * DAY, UTC);
        assertThat(md).contains("# Sprint Falcon");
        assertThat(md).contains("## Done — 1").contains("- done-1");
        assertThat(md).contains("## Open at close").contains("- open-2");
        assertThat(md)
                .as("no blockers, no clock, no retro, no history → sections absent")
                .doesNotContain("## Still blocked")
                .doesNotContain("## Time")
                .doesNotContain("## Retro")
                .doesNotContain("## Velocity");
    }

    @Test
    @DisplayName("velocity appears once a sprint has been archived")
    void velocityFromHistory() {
        TaskBoard b = board(START + DAY);
        b.closeSprint();
        b.setSprint("Griffin", START + 14 * DAY, START + 23 * DAY);
        String md = SprintReport.build(b, START + 15 * DAY, UTC);
        assertThat(md).contains("## Velocity")
                .contains("- Falcon: 1 done")
                .contains("- Griffin (this sprint): 0 done");
    }
}
