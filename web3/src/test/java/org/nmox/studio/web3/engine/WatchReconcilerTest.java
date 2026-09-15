package org.nmox.studio.web3.engine;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two Watch lanes' shared cursors (ledger 12): the stream resumes as
 * polling without a duplicate or a gap, the 50-block clamp holds in both,
 * a stale session writes nothing, and a log either lane already delivered
 * is dropped.
 */
class WatchReconcilerTest {

    private static final int CAP = 50;

    private final AtomicLong live = new AtomicLong(7);

    private WatchReconciler session() {
        return new WatchReconciler(live.get(), live::get, CAP);
    }

    private static JsonRpcClient.LogEntry log(long block, String tx, long index) {
        return new JsonRpcClient.LogEntry("0xC0", List.of("0xtopic"), "0x", block, tx, index);
    }

    /** Streams heads first..last exactly as the studio does: plan, fetch, commit. */
    private static void stream(WatchReconciler r, long first, long last) {
        for (long head = first; head <= last; head++) {
            WatchReconciler.HeadPlan plan = r.onHead(head);
            assertThat(plan.hasBlocks()).isTrue();
            assertThat(r.commitHead(head)).isTrue();
        }
    }

    @Test
    @DisplayName("the poll lane's plan is exactly WatchCursor's, and a commit advances it")
    void pollLaneIsTheCursor() {
        WatchReconciler r = session();
        WatchCursor.Plan first = r.pollPlan(1_000);
        assertThat(first).isEqualTo(WatchCursor.plan(-1, Long.MAX_VALUE, 1_000, CAP));
        assertThat(r.commitPoll(first, 1_000, true)).isTrue();
        assertThat(r.lastBlock()).isEqualTo(1_000);
        assertThat(r.pollPlan(1_003)).isEqualTo(WatchCursor.plan(1_000, 1_001, 1_003, CAP));
    }

    @Test
    @DisplayName("a first poll without logs starts the log cursor at the watch-start block")
    void firstPollWithoutLogs() {
        WatchReconciler r = session();
        assertThat(r.commitPoll(r.pollPlan(500), 500, false)).isTrue();
        assertThat(r.logsFromBlock()).isEqualTo(500);
    }

    @Test
    @DisplayName("heads arrive in order: each fetches its one block, and a repeat fetches nothing")
    void headsInOrder() {
        WatchReconciler r = session();
        r.commitPoll(r.pollPlan(100), 100, false); // the seed tick
        assertThat(r.startStreaming()).isTrue();

        WatchReconciler.HeadPlan next = r.onHead(101);
        assertThat(next.blockFrom()).isEqualTo(101);
        assertThat(next.blockTo()).isEqualTo(101);
        assertThat(next.gap()).isFalse();
        assertThat(r.commitHead(101)).isTrue();

        assertThat(r.onHead(101).hasBlocks()).as("a repeated head").isFalse();
        assertThat(r.onHead(99).hasBlocks()).as("an older head").isFalse();
    }

    @Test
    @DisplayName("a head that skips blocks fetches them all and flags the gap for a log backfill")
    void headGapBackfills() {
        WatchReconciler r = session();
        r.commitPoll(r.pollPlan(100), 100, false);
        r.startStreaming();
        WatchReconciler.HeadPlan plan = r.onHead(104);
        assertThat(plan.blockFrom()).isEqualTo(101);
        assertThat(plan.blockTo()).isEqualTo(104);
        assertThat(plan.gap()).isTrue();
    }

    @Test
    @DisplayName("a long silence on the stream clamps to the cap window, like the poller")
    void headGapClamped() {
        WatchReconciler r = session();
        r.commitPoll(r.pollPlan(100), 100, false);
        r.startStreaming();
        WatchReconciler.HeadPlan plan = r.onHead(10_000);
        assertThat(plan.blockFrom()).isEqualTo(10_000 - CAP + 1);
        assertThat(plan.blockTo() - plan.blockFrom() + 1).isEqualTo(CAP);
    }

    @Test
    @DisplayName("a head before any block is finished fetches just that block")
    void firstHeadWithoutSeed() {
        WatchReconciler r = session();
        r.startStreaming();
        WatchReconciler.HeadPlan plan = r.onHead(42);
        assertThat(plan.blockFrom()).isEqualTo(42);
        assertThat(plan.blockTo()).isEqualTo(42);
        assertThat(plan.gap()).isFalse();
    }

    @Test
    @DisplayName("RESUME: after a drop, polling continues from the block after the last streamed one — no dup, no gap")
    void resumeAfterDropHasNoDupAndNoGap() {
        WatchReconciler r = session();
        r.commitPoll(r.pollPlan(100), 100, true);
        r.startStreaming();
        stream(r, 101, 105);

        WatchReconciler.Resume resume = r.onDrop();
        assertThat(resume).isNotNull();
        assertThat(resume.lastWatchedBlock()).isEqualTo(105);
        assertThat(r.streaming()).isFalse();

        WatchCursor.Plan plan = r.pollPlan(108);
        assertThat(plan.blockFrom())
                .as("block 105 was streamed — re-fetching it is the dup, skipping 106 the gap")
                .isEqualTo(106);
        assertThat(plan.blockTo()).isEqualTo(108);
        assertThat(plan.logFrom())
                .as("logs re-query the overlap; the identity set drops the repeats")
                .isEqualTo(105 - WatchReconciler.RESUME_LOG_OVERLAP + 1);
        assertThat(r.commitPoll(plan, 108, true)).isTrue();
        assertThat(r.pollPlan(109).blockFrom()).isEqualTo(109);
    }

    @Test
    @DisplayName("a drop after a long outage still resumes inside the cap window")
    void resumeClamped() {
        WatchReconciler r = session();
        r.commitPoll(r.pollPlan(100), 100, true);
        r.startStreaming();
        stream(r, 101, 101);
        r.onDrop();
        WatchCursor.Plan plan = r.pollPlan(9_000);
        assertThat(plan.blockFrom()).isEqualTo(9_000 - CAP + 1);
        assertThat(plan.logFrom()).isEqualTo(9_000 - CAP + 1);
    }

    @Test
    @DisplayName("a drop before anything streamed resumes as a first poll tick")
    void resumeBeforeFirstBlock() {
        WatchReconciler r = session();
        r.startStreaming();
        WatchReconciler.Resume resume = r.onDrop();
        assertThat(resume.lastWatchedBlock()).isNegative();
        assertThat(resume.logsFromBlock()).isEqualTo(Long.MAX_VALUE);
        assertThat(r.pollPlan(300).blockFrom()).isEqualTo(300);
    }

    @Test
    @DisplayName("a drop reported twice (error, then close) hands back the lane once")
    void dropIsReportedOnce() {
        WatchReconciler r = session();
        r.startStreaming();
        assertThat(r.onDrop()).isNotNull();
        assertThat(r.onDrop()).isNull();
        assertThat(r.onHead(10).hasBlocks()).as("the dropped stream plans nothing").isFalse();
        assertThat(r.commitHead(10)).isFalse();
    }

    @Test
    @DisplayName("GENERATION: once STOP bumps the generation, every lane of the old session is stale")
    void staleGenerationIsDropped() {
        WatchReconciler r = session();
        r.commitPoll(r.pollPlan(100), 100, false);
        r.startStreaming();
        WatchCursor.Plan inFlight = r.pollPlan(101);

        live.incrementAndGet(); // STOP / a network switch / a closed tab — no retire() needed

        assertThat(r.current()).isFalse();
        assertThat(r.commitPoll(inFlight, 101, true)).as("a dying tick").isFalse();
        assertThat(r.onHead(102).hasBlocks()).as("a queued head").isFalse();
        assertThat(r.commitHead(102)).as("a head mid-fetch").isFalse();
        assertThat(r.acceptLog(WatchReconciler.Lane.STREAM, log(101, "0xa", 0), false))
                .as("a log the socket delivered after STOP").isFalse();
        assertThat(r.acceptLog(WatchReconciler.Lane.POLL, log(101, "0xa", 0), false)).isFalse();
        assertThat(r.onDrop()).as("a stale drop resumes nothing").isNull();
        assertThat(r.startStreaming()).isFalse();
        assertThat(r.lastBlock()).as("the cursors were not torn").isEqualTo(100);
    }

    @Test
    @DisplayName("a socket that finishes opening after STOP is refused and left for the caller to close")
    void attachAfterStopIsRefused() {
        WatchReconciler r = session();
        live.incrementAndGet();
        AtomicInteger closes = new AtomicInteger();
        assertThat(r.attach(closes::incrementAndGet)).isFalse();
        assertThat(r.attached()).isNull();
        assertThat(closes).hasValue(0);
    }

    @Test
    @DisplayName("retire closes the attached socket once and ends the session")
    void retireClosesTheSocket() {
        WatchReconciler r = session();
        AtomicInteger closes = new AtomicInteger();
        assertThat(r.attach(closes::incrementAndGet)).isTrue();
        r.startStreaming();
        r.retire();
        r.retire();
        assertThat(closes).hasValue(1);
        assertThat(r.current()).isFalse();
        assertThat(r.attach(closes::incrementAndGet)).isFalse();
    }

    @Test
    @DisplayName("a drop closes the attached socket too")
    void dropClosesTheSocket() {
        WatchReconciler r = session();
        AtomicInteger closes = new AtomicInteger();
        r.attach(closes::incrementAndGet);
        r.startStreaming();
        r.onDrop();
        assertThat(closes).hasValue(1);
        assertThat(r.attached()).isNull();
    }

    @Test
    @DisplayName("DEDUPE: a log the stream delivered is dropped when the resume re-fetches it over HTTP")
    void logsDedupeAcrossLanes() {
        WatchReconciler r = session();
        r.commitPoll(r.pollPlan(100), 100, true);
        r.startStreaming();
        assertThat(r.acceptLog(WatchReconciler.Lane.STREAM, log(101, "0xAB", 0), false)).isTrue();
        assertThat(r.acceptLog(WatchReconciler.Lane.STREAM, log(101, "0xab", 1), false))
                .as("same tx, next index: a different log").isTrue();
        stream(r, 101, 101);
        r.onDrop();

        assertThat(r.acceptLog(WatchReconciler.Lane.POLL, log(101, "0xab", 0), false))
                .as("the overlap re-query returns the same log (hash case differs)").isFalse();
        assertThat(r.acceptLog(WatchReconciler.Lane.POLL, log(102, "0xcd", 0), false)).isTrue();
        assertThat(r.acceptLog(WatchReconciler.Lane.STREAM, log(102, "0xef", 0), false))
                .as("the socket's last words after the drop").isFalse();
    }

    @Test
    @DisplayName("removed (reorged-out) logs never reach the feed")
    void removedLogsRefused() {
        WatchReconciler r = session();
        r.startStreaming();
        assertThat(r.acceptLog(WatchReconciler.Lane.STREAM, log(5, "0xa", 0), true)).isFalse();
    }

    @Test
    @DisplayName("a log with no index is identified by its content, so identical logs still dedupe")
    void logsWithoutIndex() {
        WatchReconciler r = session();
        JsonRpcClient.LogEntry a = new JsonRpcClient.LogEntry("0xC0", List.of("0x1"), "0x01", 9, "0xt");
        JsonRpcClient.LogEntry b = new JsonRpcClient.LogEntry("0xC0", List.of("0x1"), "0x02", 9, "0xt");
        assertThat(r.acceptLog(WatchReconciler.Lane.POLL, a, false)).isTrue();
        assertThat(r.acceptLog(WatchReconciler.Lane.POLL, a, false)).isFalse();
        assertThat(r.acceptLog(WatchReconciler.Lane.POLL, b, false)).isTrue();
    }

    @Test
    @DisplayName("the identity set forgets blocks behind the cap window and refuses logs that old")
    void identitySetIsWindowed() {
        WatchReconciler r = session();
        r.commitPoll(r.pollPlan(10), 10, true);
        assertThat(r.acceptLog(WatchReconciler.Lane.POLL, log(10, "0xa", 0), false)).isTrue();
        r.commitPoll(r.pollPlan(200), 200, true);
        assertThat(r.acceptLog(WatchReconciler.Lane.POLL, log(10, "0xa", 0), false))
                .as("below the window: refused rather than re-admitted as new").isFalse();
        assertThat(r.acceptLog(WatchReconciler.Lane.POLL, log(200 - CAP + 1, "0xb", 0), false)).isTrue();
    }

    @Test
    @DisplayName("the identity set is hard-bounded: a flood evicts the oldest blocks first")
    void identitySetIsBounded() {
        WatchReconciler r = session();
        for (int i = 0; i < WatchReconciler.MAX_SEEN_LOGS; i++) {
            assertThat(r.acceptLog(WatchReconciler.Lane.POLL, log(1, "0xa", i), false)).isTrue();
        }
        assertThat(r.acceptLog(WatchReconciler.Lane.POLL, log(2, "0xb", 0), false)).isTrue();
        assertThat(r.acceptLog(WatchReconciler.Lane.POLL, log(1, "0xa", 0), false))
                .as("block 1's identities were evicted to hold the bound").isTrue();
    }

    @Test
    @DisplayName("a cap below one is refused at construction")
    void capValidated() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new WatchReconciler(0, () -> 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
