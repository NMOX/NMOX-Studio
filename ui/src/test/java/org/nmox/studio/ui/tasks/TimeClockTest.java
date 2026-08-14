package org.nmox.studio.ui.tasks;

import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The time clock (v2.6.0): clock in to a card, clock out, read the
 * report. The rules pinned here are the ones a tracker lives or dies
 * by — one running clock on the whole board, sessions that survive the
 * file round trip, and report numbers CLIPPED to their day so a
 * session spanning midnight never double-counts.
 */
class TimeClockTest {

    private static final long NOON = 1_786_795_200_000L; // a fixed instant
    private static final long HOUR = TimeUnit.HOURS.toMillis(1);

    // ---- lifecycle -------------------------------------------------------

    @Test
    @DisplayName("Clock in, clock out: the session is recorded with both stamps")
    void basicSession() {
        TaskBoard b = TaskBoard.starter();
        TaskBoard.Card c = b.addCard(0, "write the report", "");
        assertThat(b.clockIn(c.id(), NOON)).isTrue();
        assertThat(c.clockedIn()).isTrue();
        assertThat(b.runningCard().id()).isEqualTo(c.id());
        assertThat(b.clockOut(c.id(), NOON + 2 * HOUR)).isTrue();
        assertThat(c.clockedIn()).isFalse();
        assertThat(c.sessions()).hasSize(1);
        assertThat(c.sessions().get(0)).containsExactly(NOON, NOON + 2 * HOUR);
    }

    @Test
    @DisplayName("One clock on the whole board: clocking in elsewhere closes the running session")
    void switchingClocksOutTheOldCard() {
        TaskBoard b = TaskBoard.starter();
        TaskBoard.Card first = b.addCard(0, "first", "");
        TaskBoard.Card second = b.addCard(1, "second", "");
        b.clockIn(first.id(), NOON);
        assertThat(b.clockIn(second.id(), NOON + HOUR)).isTrue();
        assertThat(first.clockedIn()).isFalse();
        assertThat(first.sessions().get(0)[1]).isEqualTo(NOON + HOUR);
        assertThat(b.runningCard().id()).isEqualTo(second.id());
    }

    @Test
    @DisplayName("A double clock-in on the same card is refused; so is a clock-out with no clock")
    void doubleVerbsRefused() {
        TaskBoard b = TaskBoard.starter();
        TaskBoard.Card c = b.addCard(0, "x", "");
        b.clockIn(c.id(), NOON);
        assertThat(b.clockIn(c.id(), NOON + HOUR)).isFalse();
        assertThat(c.sessions()).hasSize(1);
        b.clockOut(c.id(), NOON + 2 * HOUR);
        assertThat(b.clockOut(c.id(), NOON + 3 * HOUR)).isFalse();
    }

    @Test
    @DisplayName("A session under a minute is dropped whole — an accidental click is not work")
    void blipSessionsDropped() {
        TaskBoard b = TaskBoard.starter();
        TaskBoard.Card c = b.addCard(0, "x", "");
        b.clockIn(c.id(), NOON);
        assertThat(b.clockOut(c.id(), NOON + 30_000L)).isTrue();
        assertThat(c.sessions()).isEmpty();
    }

    // ---- persistence -----------------------------------------------------

    @Test
    @DisplayName("Sessions — including a still-running one — survive the JSON round trip")
    void sessionsRoundTrip() {
        TaskBoard b = TaskBoard.starter();
        TaskBoard.Card c = b.addCard(0, "x", "");
        b.clockIn(c.id(), NOON - 3 * HOUR);
        b.clockOut(c.id(), NOON - HOUR);
        b.clockIn(c.id(), NOON);
        TaskBoard back = TaskBoard.fromJson(b.toJson());
        TaskBoard.Card rc = back.find(c.id());
        assertThat(rc.sessions()).hasSize(2);
        assertThat(rc.sessions().get(0)).containsExactly(NOON - 3 * HOUR, NOON - HOUR);
        assertThat(rc.clockedIn()).isTrue();
        assertThat(back.runningCard().id()).isEqualTo(c.id());
    }

    // ---- the report ------------------------------------------------------

    /** noon UTC on the report day, so day boundaries are unambiguous. */
    private static BoardStats statsAt(TaskBoard b, long now) {
        return BoardStats.of(b, now, ZoneOffset.UTC, 14, 5);
    }

    @Test
    @DisplayName("The report says what you worked on and for how long, most-today first")
    void reportListsWork() {
        TaskBoard b = TaskBoard.starter();
        TaskBoard.Card big = b.addCard(0, "the big feature", "");
        TaskBoard.Card small = b.addCard(1, "a quick fix", "");
        b.clockIn(big.id(), NOON - 4 * HOUR);
        b.clockOut(big.id(), NOON - HOUR);      // 3h today
        b.clockIn(small.id(), NOON - HOUR);
        b.clockOut(small.id(), NOON);           // 1h today
        BoardStats s = statsAt(b, NOON);
        assertThat(s.timeEntries()).extracting(BoardStats.TimeEntry::title)
                .containsExactly("the big feature", "a quick fix");
        assertThat(s.timeEntries().get(0).todayMs()).isEqualTo(3 * HOUR);
        assertThat(s.trackedTodayMs()).isEqualTo(4 * HOUR);
        assertThat(s.trackedWeekMs()).isEqualTo(4 * HOUR);
    }

    @Test
    @DisplayName("A session spanning midnight is clipped: only its today part counts today")
    void midnightSessionClipped() {
        TaskBoard b = TaskBoard.starter();
        TaskBoard.Card c = b.addCard(0, "night shift", "");
        // NOON is 12:00 UTC; a session from 22:00 yesterday to 02:00 today
        long start = NOON - 14 * HOUR;
        b.clockIn(c.id(), start);
        b.clockOut(c.id(), NOON - 10 * HOUR);
        BoardStats s = statsAt(b, NOON);
        assertThat(s.timeEntries().get(0).todayMs()).isEqualTo(2 * HOUR);
        assertThat(s.timeEntries().get(0).weekMs()).isEqualTo(4 * HOUR);
    }

    @Test
    @DisplayName("A running clock counts up to NOW in the report")
    void runningSessionCounts() {
        TaskBoard b = TaskBoard.starter();
        TaskBoard.Card c = b.addCard(0, "in progress", "");
        b.clockIn(c.id(), NOON - HOUR);
        BoardStats s = statsAt(b, NOON);
        assertThat(s.timeEntries().get(0).running()).isTrue();
        assertThat(s.timeEntries().get(0).todayMs()).isEqualTo(HOUR);
    }

    @Test
    @DisplayName("Durations read human: 1h 05m / 45m / <1m")
    void durationSpelling() {
        assertThat(BoardStats.duration(65 * 60_000L)).isEqualTo("1h 05m");
        assertThat(BoardStats.duration(45 * 60_000L)).isEqualTo("45m");
        assertThat(BoardStats.duration(30_000L)).isEqualTo("<1m");
    }
}
