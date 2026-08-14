package org.nmox.studio.ui.tasks;

import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.json.JSONObject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Board Overview's numbers (v2.4.0), pinned pure: the done-stamp
 * lifecycle on the model, the WIP-now definition, the calendar-day
 * done windows, the flow bins, and the aging list — every claim the
 * dashboard face renders is asserted here so the panel stays only
 * rendering.
 */
class BoardStatsTest {

    private static final ZoneOffset UTC = ZoneOffset.UTC;
    /** A fixed "now": 2026-08-14 12:00 UTC. */
    private static final long NOW = 1_786_795_200_000L;

    private static long daysAgo(int d) {
        return NOW - TimeUnit.DAYS.toMillis(d);
    }

    // ---- the done-stamp lifecycle on the model ---------------------------

    @Test
    @DisplayName("Moving a card into the last column stamps done; back out clears it")
    void doneStampFollowsTheLastColumn() {
        TaskBoard b = TaskBoard.starter();
        TaskBoard.Card c = b.addCard(0, "ship it", "");
        assertThat(c.done()).isZero();
        b.moveCard(c.id(), 2, 0);
        assertThat(c.done()).isPositive();
        b.moveCard(c.id(), 1, 0);
        assertThat(c.done()).isZero();
    }

    @Test
    @DisplayName("A move WITHIN the last column keeps the original stamp")
    void reorderInDoneKeepsTheStamp() {
        TaskBoard b = TaskBoard.starter();
        TaskBoard.Card c = b.addCard(0, "a", "");
        b.addCard(2, "b", "");
        b.moveCard(c.id(), 2, 0);
        long stamped = c.done();
        b.moveCard(c.id(), 2, 1);
        assertThat(c.done()).isEqualTo(stamped);
    }

    @Test
    @DisplayName("A card born in the last column counts as finished")
    void bornDoneIsStamped() {
        TaskBoard b = TaskBoard.starter();
        TaskBoard.Card c = b.addCard(2, "already shipped", "");
        assertThat(c.done()).isPositive();
    }

    @Test
    @DisplayName("The done stamp round-trips through JSON; old files without it load fine")
    void doneStampRoundTrips() {
        TaskBoard b = TaskBoard.starter();
        TaskBoard.Card c = b.addCard(0, "x", "");
        b.moveCard(c.id(), 2, 0);
        TaskBoard back = TaskBoard.fromJson(b.toJson());
        assertThat(back.find(c.id()).done()).isEqualTo(c.done());
        // a v1 file that has never seen a stamp
        String v1 = new JSONObject().put("version", 1).put("columns",
                new org.json.JSONArray().put(new JSONObject()
                        .put("name", "Only")
                        .put("cards", new org.json.JSONArray().put(
                                new JSONObject().put("title", "old"))))).toString();
        assertThat(TaskBoard.fromJson(v1).columns().get(0).cards().get(0).done())
                .isZero();
    }

    // ---- the stats definitions ------------------------------------------

    /** A board whose stamps are set by hand through JSON, clock-free. */
    private static TaskBoard board(long... doneStamps) {
        org.json.JSONArray done = new org.json.JSONArray();
        for (long d : doneStamps) {
            done.put(new JSONObject().put("title", "d" + d)
                    .put("created", daysAgo(30)).put("done", d));
        }
        org.json.JSONArray cols = new org.json.JSONArray()
                .put(new JSONObject().put("name", "To Do").put("cards",
                        new org.json.JSONArray()
                                .put(new JSONObject().put("title", "ancient")
                                        .put("created", daysAgo(12)))
                                .put(new JSONObject().put("title", "fresh")
                                        .put("created", daysAgo(1)))))
                .put(new JSONObject().put("name", "Doing").put("wip", 1)
                        .put("cards", new org.json.JSONArray()
                                .put(new JSONObject().put("title", "one")
                                        .put("created", daysAgo(3)))
                                .put(new JSONObject().put("title", "two")
                                        .put("created", daysAgo(2)))))
                .put(new JSONObject().put("name", "Done").put("cards", done));
        return TaskBoard.fromJson(new JSONObject().put("version", 1)
                .put("columns", cols).toString());
    }

    @Test
    @DisplayName("WIP now counts the middle columns only — never To Do, never Done")
    void wipIsTheMiddle() {
        // Done must be NON-empty here: a mutant that counts the last
        // column only diverges when there is something in it to count
        BoardStats s = BoardStats.of(board(daysAgo(1), daysAgo(2)), NOW, UTC, 14, 5);
        assertThat(s.wipNow()).isEqualTo(2);
        assertThat(s.totalCards()).isEqualTo(6);
    }

    @Test
    @DisplayName("Done today and done this week are calendar windows in the given zone")
    void doneWindowsAreCalendarDays() {
        BoardStats s = BoardStats.of(
                board(NOW - 1000, daysAgo(2), daysAgo(6), daysAgo(8)),
                NOW, UTC, 14, 5);
        assertThat(s.doneToday()).isEqualTo(1);
        assertThat(s.doneThisWeek()).isEqualTo(3); // the 8-day-old one is out
    }

    @Test
    @DisplayName("Flow bins land each stamp on its day, oldest first, newest last")
    void flowBinsByDay() {
        BoardStats s = BoardStats.of(
                board(NOW - 1000, daysAgo(2), daysAgo(2), daysAgo(13)),
                NOW, UTC, 14, 5);
        int[] flow = s.flow();
        assertThat(flow).hasSize(14);
        assertThat(flow[13]).isEqualTo(1);  // today
        assertThat(flow[11]).isEqualTo(2);  // two days back
        assertThat(flow[0]).isEqualTo(1);   // thirteen days back, oldest bin
    }

    @Test
    @DisplayName("The aging list is oldest-first, excludes Done, and caps at the limit")
    void agingListOrdersAndCaps() {
        BoardStats s = BoardStats.of(board(daysAgo(1)), NOW, UTC, 14, 3);
        assertThat(s.oldestActive()).hasSize(3);
        assertThat(s.oldestActive().get(0).title()).isEqualTo("ancient");
        assertThat(s.oldestActive().get(0).ageDays()).isEqualTo(12);
        assertThat(s.oldestActive().get(0).column()).isEqualTo("To Do");
    }

    @Test
    @DisplayName("Over-limit columns carry the v1.323.0 advisory verdict into the stats")
    void overLimitVerdictCarries() {
        BoardStats s = BoardStats.of(board(), NOW, UTC, 14, 5);
        assertThat(s.columnStats().get(1).overLimit()).isTrue();  // Doing 2/1
        assertThat(s.columnStats().get(0).overLimit()).isFalse();
    }

    @Test
    @DisplayName("A single-column board has no middle: WIP is honestly zero")
    void singleColumnBoardHasNoWip() {
        TaskBoard b = new TaskBoard();
        b.addColumn("Everything", 0);
        b.addCard(0, "x", "");
        BoardStats s = BoardStats.of(b, NOW, UTC, 14, 5);
        assertThat(s.wipNow()).isZero();
    }
}
