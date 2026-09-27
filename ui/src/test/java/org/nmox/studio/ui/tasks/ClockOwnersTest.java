package org.nmox.studio.ui.tasks;

import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Clock sessions have owners (3.4, question 1).
 *
 * <p>Until 3.4 a session was a bare [start, end] pair and the board enforced
 * ONE running clock across the whole checked-in file: on a board a team
 * shares, Bob clocking in clocked Alice out, and two running clocks merged
 * from two machines were "healed" by closing one of them at zero credit.
 * The rule and the heal apply per owner now, and the TIME report and the
 * Standup count the current user's sessions only.
 */
class ClockOwnersTest {

    private static final long NOON = 1_786_795_200_000L;
    private static final long HOUR = TimeUnit.HOURS.toMillis(1);
    private static final String ME = TaskBoard.currentUser();
    private static final String TEAMMATE = ME + "-teammate";

    @Test
    @DisplayName("two people run two clocks at once: Bob clocking in never clocks Alice out")
    void clocksArePerPerson() {
        TaskBoard b = TaskBoard.starter("To Do", "Doing", "Done");
        TaskBoard.Card api = b.addCard(1, "api", "");
        TaskBoard.Card ui = b.addCard(1, "ui", "");
        assertThat(b.clockIn(api.id(), NOON, "alice")).isTrue();
        assertThat(b.clockIn(ui.id(), NOON + HOUR, "bob")).isTrue();
        assertThat(api.clockedIn("alice")).as("Alice's clock is hers").isTrue();
        assertThat(b.runningCard("alice").id()).isEqualTo(api.id());
        assertThat(b.runningCard("bob").id()).isEqualTo(ui.id());

        // one clock PER PERSON still holds: Alice moving closes only her own
        assertThat(b.clockIn(ui.id(), NOON + 2 * HOUR, "alice")).isTrue();
        assertThat(api.clockedIn("alice")).isFalse();
        assertThat(api.sessions("alice").get(0)[1]).isEqualTo(NOON + 2 * HOUR);
        assertThat(ui.clockedIn("bob")).as("Bob untouched").isTrue();
        assertThat(ui.clockedIn("alice")).isTrue();
        // and each clocks out only their own
        assertThat(b.clockOut(ui.id(), NOON + 3 * HOUR, "bob")).isTrue();
        assertThat(ui.clockedIn("alice")).isTrue();
    }

    @Test
    @DisplayName("owners survive the file round trip; old readers keep the pairs")
    void ownersRoundTrip() {
        TaskBoard b = TaskBoard.starter("To Do", "Doing", "Done");
        TaskBoard.Card c = b.addCard(1, "shared", "");
        b.clockIn(c.id(), NOON, "alice");
        b.clockOut(c.id(), NOON + HOUR, "alice");
        b.clockIn(c.id(), NOON, "bob");
        JSONObject card = new JSONObject(b.toJson()).getJSONArray("columns").getJSONObject(1)
                .getJSONArray("cards").getJSONObject(0);
        assertThat(card.getJSONArray("sessions").getJSONArray(0).length())
                .as("a pair stays a pair, so a 3.3 reader keeps the session").isEqualTo(2);
        assertThat(card.getJSONArray(TaskBoard.SESSION_OWNERS).toList()).containsExactly("alice", "bob");

        TaskBoard back = TaskBoard.fromJson(b.toJson(), "carol");
        TaskBoard.Card rc = back.column(1).cards().get(0);
        assertThat(rc.sessions("alice")).hasSize(1);
        assertThat(rc.clockedIn("bob")).isTrue();
        assertThat(rc.sessions("carol")).as("carol clocked nothing").isEmpty();
    }

    private static TaskBoard boardWith(JSONArray... cardSessions) {
        return boardWith(null, cardSessions);
    }

    private static TaskBoard boardWith(JSONArray[] owners, JSONArray... cardSessions) {
        JSONArray cards = new JSONArray();
        for (int i = 0; i < cardSessions.length; i++) {
            JSONObject c = new JSONObject().put("id", "c" + i).put("title", "card " + i)
                    .put("created", NOON - 10 * HOUR).put("sessions", cardSessions[i]);
            if (owners != null && owners[i] != null) {
                c.put(TaskBoard.SESSION_OWNERS, owners[i]);
            }
            cards.put(c);
        }
        return TaskBoard.fromJson(new JSONObject().put("version", 1).put("columns", new JSONArray()
                .put(new JSONObject().put("name", "Doing").put("cards", cards))
                .put(new JSONObject().put("name", "Done").put("cards", new JSONArray()))).toString(), "carol");
    }

    private static JSONArray pairs(long[]... ps) {
        JSONArray out = new JSONArray();
        for (long[] p : ps) {
            out.put(new JSONArray().put(p[0]).put(p[1]));
        }
        return out;
    }

    @Test
    @DisplayName("a merged board with Alice's and Bob's clocks running keeps BOTH running — no zero-credit heal")
    void twoOwnersBothRunningSurviveTheHeal() {
        TaskBoard b = boardWith(
                new JSONArray[]{new JSONArray().put("alice"), new JSONArray().put("bob")},
                pairs(new long[]{NOON - 2 * HOUR, 0L}),
                pairs(new long[]{NOON - HOUR, 0L}));
        assertThat(b.column(0).cards().get(0).clockedIn("alice")).isTrue();
        assertThat(b.column(0).cards().get(1).clockedIn("bob")).isTrue();
    }

    @Test
    @DisplayName("one owner running on two cards still heals: the latest start keeps that owner's clock")
    void oneOwnerTwoRunningStillHeals() {
        TaskBoard b = boardWith(
                new JSONArray[]{new JSONArray().put("alice"), new JSONArray().put("alice")},
                pairs(new long[]{NOON - 2 * HOUR, 0L}),
                pairs(new long[]{NOON - HOUR, 0L}));
        TaskBoard.Card older = b.column(0).cards().get(0);
        assertThat(older.clockedIn("alice")).isFalse();
        assertThat(older.sessions("alice").get(0)[1]).as("closed at its own start").isEqualTo(NOON - 2 * HOUR);
        assertThat(b.runningCard("alice").title()).isEqualTo("card 1");
    }

    @Test
    @DisplayName("sessions without an owner are the reader's, and are written back without one")
    void legacySessionsAreTheReaders() {
        TaskBoard b = boardWith(pairs(new long[]{NOON - 2 * HOUR, NOON - HOUR}));
        TaskBoard.Card c = b.column(0).cards().get(0);
        assertThat(c.sessions("carol")).hasSize(1);
        JSONObject card = new JSONObject(b.toJson()).getJSONArray("columns").getJSONObject(0)
                .getJSONArray("cards").getJSONObject(0);
        assertThat(card.has(TaskBoard.SESSION_OWNERS))
                .as("reading an old board never signs its hours with the reader's name").isFalse();
    }

    @Test
    @DisplayName("an owners array a merge put out of step with the sessions is ignored, never misattributed")
    void misalignedOwnersAreIgnored() {
        TaskBoard b = boardWith(
                new JSONArray[]{new JSONArray().put("alice")},
                pairs(new long[]{NOON - 3 * HOUR, NOON - 2 * HOUR}, new long[]{NOON - HOUR, NOON}));
        TaskBoard.Card c = b.column(0).cards().get(0);
        assertThat(c.sessions("alice")).isEmpty();
        assertThat(c.sessions("carol")).hasSize(2);
    }

    @Test
    @DisplayName("the TIME report counts the current user's clock, not a teammate's on the same card")
    void timeReportIsMine() {
        TaskBoard b = TaskBoard.starter("To Do", "Doing", "Done");
        TaskBoard.Card c = b.addCard(1, "pairing", "");
        b.clockIn(c.id(), NOON - 3 * HOUR, TEAMMATE);
        b.clockOut(c.id(), NOON - HOUR, TEAMMATE);     // 2h theirs
        b.clockIn(c.id(), NOON - 2 * HOUR, ME);
        b.clockOut(c.id(), NOON - HOUR, ME);           // 1h mine
        BoardStats s = BoardStats.of(b, NOON, ZoneOffset.UTC, 14, 5);
        assertThat(s.trackedTodayMs()).isEqualTo(HOUR);
    }

    @Test
    @DisplayName("the Standup counts the current user's clock and says whose it is")
    void standupIsMine() {
        TaskBoard b = TaskBoard.starter("To Do", "Doing", "Done");
        TaskBoard.Card theirs = b.addCard(1, "their card", "");
        b.clockIn(theirs.id(), NOON - 3 * HOUR, TEAMMATE);
        b.clockOut(theirs.id(), NOON - HOUR, TEAMMATE);
        String md = StandupReport.build(b, List.of(), NOON, ZoneOffset.UTC);
        assertThat(md).as("a teammate's hours are not my standup").doesNotContain("their card");
        assertThat(md.lines().findFirst().orElseThrow()).contains(ME);
    }

    @Test
    @DisplayName("the sprint report is the team's: every owner's time counts")
    void sprintReportIsTheTeams() {
        TaskBoard b = TaskBoard.starter("To Do", "Doing", "Done");
        b.setSprint("S1", NOON - 24 * HOUR, NOON);
        TaskBoard.Card c = b.addCard(1, "shared", "");
        b.clockIn(c.id(), NOON - 3 * HOUR, TEAMMATE);
        b.clockOut(c.id(), NOON - 2 * HOUR, TEAMMATE);
        b.clockIn(c.id(), NOON - 2 * HOUR, ME);
        b.clockOut(c.id(), NOON - HOUR, ME);
        assertThat(SprintReport.build(b, NOON, ZoneOffset.UTC)).contains("2h 00m");
    }
}
