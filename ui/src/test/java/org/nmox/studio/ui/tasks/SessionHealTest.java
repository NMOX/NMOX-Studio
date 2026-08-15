package org.nmox.studio.ui.tasks;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The open-session heal (v2.9.0, the arc review's find): the runtime
 * only ever leaves an open pair as a card's LAST session and only one
 * on the whole board, but a keep-both merge of the checked-in
 * {@code .nmoxtasks.json} can violate both. clockedIn()/clockOut see
 * only the last pair, so a stray open pair would count to NOW forever
 * in the TIME report and the standup with no gesture able to close it.
 * fromJson heals: strays close at their OWN start (zero invented
 * time); when several cards are open, the latest start keeps the clock.
 */
class SessionHealTest {

    private static final long T = 1_786_795_200_000L;

    private static JSONObject card(String title, JSONArray sessions) {
        return new JSONObject().put("title", title)
                .put("created", T - 1000).put("sessions", sessions);
    }

    private static TaskBoard board(JSONObject... cards) {
        JSONArray arr = new JSONArray();
        for (JSONObject c : cards) {
            arr.put(c);
        }
        return TaskBoard.fromJson(new JSONObject()
                .put("version", 1)
                .put("columns", new JSONArray()
                        .put(new JSONObject().put("name", "Doing").put("cards", arr))
                        .put(new JSONObject().put("name", "Done")
                                .put("cards", new JSONArray())))
                .toString());
    }

    private static JSONArray pairs(long[]... ps) {
        JSONArray out = new JSONArray();
        for (long[] p : ps) {
            out.put(new JSONArray().put(p[0]).put(p[1]));
        }
        return out;
    }

    @Test
    @DisplayName("a non-last open pair closes at its own start — no phantom forever-running session")
    void middleOpenPairClosesAtItsStart() {
        TaskBoard b = board(card("merged", pairs(
                new long[]{T - 7_200_000L, 0L},      // the merge's stray open
                new long[]{T - 3_600_000L, T - 1_800_000L})));
        List<long[]> sessions = b.column(0).cards().get(0).sessions();
        assertThat(sessions.get(0)[1]).isEqualTo(T - 7_200_000L);
        assertThat(b.column(0).cards().get(0).clockedIn()).isFalse();
    }

    @Test
    @DisplayName("two running cards heal to one: the latest start keeps the clock")
    void secondRunningClockClosesAtItsStart() {
        TaskBoard b = board(
                card("older clock", pairs(new long[]{T - 7_200_000L, 0L})),
                card("newer clock", pairs(new long[]{T - 3_600_000L, 0L})));
        TaskBoard.Card older = b.column(0).cards().get(0);
        TaskBoard.Card newer = b.column(0).cards().get(1);
        assertThat(newer.clockedIn()).isTrue();
        assertThat(older.clockedIn()).isFalse();
        assertThat(older.sessions().get(0)[1]).isEqualTo(T - 7_200_000L);
        assertThat(b.runningCard().title()).isEqualTo("newer clock");
    }

    @Test
    @DisplayName("a lawful board is untouched: one open last pair survives the heal verbatim")
    void lawfulOpenPairSurvives() {
        TaskBoard b = board(card("working", pairs(
                new long[]{T - 7_200_000L, T - 5_400_000L},
                new long[]{T - 3_600_000L, 0L})));
        TaskBoard.Card c = b.column(0).cards().get(0);
        assertThat(c.clockedIn()).isTrue();
        assertThat(c.sessions().get(1)[1]).isZero();
    }
}
