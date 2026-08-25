package org.nmox.studio.ui.tasks;

import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The standup's rules (v2.8.0): calendar-day bucketing that agrees
 * with the overview's TIME report, running clocks marked, empty
 * sections omitted, commits windowed since yesterday. NOON is fixed
 * and the zone is UTC so day boundaries are unambiguous.
 */
class StandupReportTest {

    private static final long NOON = 1_786_795_200_000L;
    private static final long HOUR = TimeUnit.HOURS.toMillis(1);

    private static String report(TaskBoard b, List<StandupReport.Commit> commits) {
        return StandupReport.build(b, commits, NOON, ZoneOffset.UTC);
    }

    @Test
    @DisplayName("done stamps bucket by calendar day: yesterday's finish is Yesterday, today's is Today")
    void doneStampsBucketByDay() {
        // stamps set via JSON so the clock is fixed (moveCard stamps now())
        TaskBoard b = TaskBoard.fromJson(new org.json.JSONObject()
                .put("version", 1)
                .put("columns", new org.json.JSONArray()
                        .put(new org.json.JSONObject().put("name", "Doing")
                                .put("cards", new org.json.JSONArray()))
                        .put(new org.json.JSONObject().put("name", "Done")
                                .put("cards", new org.json.JSONArray()
                                        .put(new org.json.JSONObject()
                                                .put("title", "shipped yesterday")
                                                .put("done", NOON - 20 * HOUR))
                                        .put(new org.json.JSONObject()
                                                .put("title", "shipped today")
                                                .put("done", NOON - 2 * HOUR)))))
                .toString());
        String md = report(b, List.of());
        assertThat(md).contains("### Yesterday\n- shipped yesterday (done)");
        assertThat(md).contains("### Today\n- shipped today (done)");
    }

    @Test
    @DisplayName("a midnight-spanning session splits between Yesterday and Today, like the TIME report")
    void midnightSessionSplits() {
        TaskBoard b = TaskBoard.starter();
        TaskBoard.Card c = b.addCard(1, "night work", "");
        b.clockIn(c.id(), NOON - 14 * HOUR);  // 22:00 yesterday
        b.clockOut(c.id(), NOON - 10 * HOUR); // 02:00 today
        String md = report(b, List.of());
        assertThat(md).contains("### Yesterday\n- night work (2h 00m)");
        assertThat(md).contains("### Today\n- night work (2h 00m)");
    }

    @Test
    @DisplayName("a running clock appears under Today, marked running, counted to now")
    void runningClockMarked() {
        TaskBoard b = TaskBoard.starter();
        TaskBoard.Card c = b.addCard(1, "in flight", "");
        b.clockIn(c.id(), NOON - HOUR);
        String md = report(b, List.of());
        assertThat(md).contains("- in flight (1h 00m, clock running)");
        assertThat(md).doesNotContain("### Yesterday");
    }

    @Test
    @DisplayName("blockers list the register's triple; a finished blocker stays out")
    void blockersSection() {
        TaskBoard b = TaskBoard.starter();
        TaskBoard.Card stuck = b.addCard(1, "waiting on cert", "");
        b.block(stuck.id(), "alice", "order the cert");
        TaskBoard.Card done = b.addCard(1, "was stuck", "");
        b.block(done.id(), "bob", "irrelevant");
        b.moveCard(done.id(), 2, 0);
        String md = report(b, List.of());
        assertThat(md).contains(
                "### Blockers\n- waiting on cert — alice · unblock: order the cert");
        assertThat(md).doesNotContain("was stuck —");
    }

    @Test
    @DisplayName("commits since yesterday appear; older ones are windowed out")
    void commitsWindowed() {
        TaskBoard b = TaskBoard.starter();
        b.addCard(0, "keeps sections honest", "");
        String md = report(b, List.of(
                new StandupReport.Commit("abc1234 fix the flake", NOON - 3 * HOUR),
                new StandupReport.Commit("def5678 ancient work", NOON - 60 * HOUR)));
        assertThat(md).contains("### Commits (since yesterday)\n- abc1234 fix the flake");
        assertThat(md).doesNotContain("def5678");
    }

    @Test
    @DisplayName("sections with nothing to say are OMITTED, never rendered empty")
    void emptySectionsOmitted() {
        TaskBoard b = TaskBoard.starter();
        b.addCard(0, "untouched card", "");
        String md = report(b, List.of());
        assertThat(md).startsWith("## Standup — ");
        assertThat(md).doesNotContain("### Yesterday");
        assertThat(md).doesNotContain("### Today");
        assertThat(md).doesNotContain("### Blockers");
        assertThat(md).doesNotContain("### Commits");
    }

    @Test
    @DisplayName("the header carries the sprint: day-of inside the window, name alone outside, nothing without one")
    void sprintHeader() {
        TaskBoard none = new TaskBoard();
        assertThat(report(none, List.of())).doesNotContain("·");

        TaskBoard inside = new TaskBoard();
        // NOON is day 3 of a window that started two days earlier
        inside.setSprint("Sprint 8", NOON - 2 * 86400000L, NOON + 11 * 86400000L);
        assertThat(report(inside, List.of()))
                .contains("· Sprint 8 · day 3 of 14");

        TaskBoard future = new TaskBoard();
        future.setSprint("Sprint 9", NOON + 5 * 86400000L, NOON + 18 * 86400000L);
        String md = report(future, List.of());
        assertThat(md).contains("· Sprint 9");
        assertThat(md).doesNotContain("day ");
    }

}
