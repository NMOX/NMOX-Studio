package org.nmox.studio.web3.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Watch poller's range math. The log-clamp cases are the v1.100.0
 * fix: a failing eth_getLogs never advances its cursor, so before the
 * clamp every retry widened the requested range without bound.
 */
class WatchCursorTest {

    private static final int CAP = 50;

    @Test
    @DisplayName("First tick fetches exactly the current block and starts logs there")
    void firstTick() {
        var plan = WatchCursor.plan(-1, Long.MAX_VALUE, 1_000, CAP);
        assertThat(plan.blockFrom()).isEqualTo(1_000);
        assertThat(plan.blockTo()).isEqualTo(1_000);
        assertThat(plan.logFrom()).isEqualTo(1_000);
        assertThat(plan.logTo()).isEqualTo(1_000);
        assertThat(plan.hasBlocks()).isTrue();
        assertThat(plan.hasLogs()).isTrue();
    }

    @Test
    @DisplayName("Steady state: one new block advances both lanes by one")
    void steadyState() {
        var plan = WatchCursor.plan(1_000, 1_001, 1_001, CAP);
        assertThat(plan.blockFrom()).isEqualTo(1_001);
        assertThat(plan.blockTo()).isEqualTo(1_001);
        assertThat(plan.logFrom()).isEqualTo(1_001);
        assertThat(plan.logTo()).isEqualTo(1_001);
    }

    @Test
    @DisplayName("No new blocks: both ranges are empty, not inverted fetches")
    void noNewBlocks() {
        var plan = WatchCursor.plan(1_000, 1_001, 1_000, CAP);
        assertThat(plan.hasBlocks()).isFalse();
        assertThat(plan.hasLogs()).isFalse();
    }

    @Test
    @DisplayName("A long block gap clamps the BLOCK lane to the cap window (the v1.33.0 law)")
    void blockGapClamped() {
        var plan = WatchCursor.plan(1_000, 1_001, 10_000, CAP);
        assertThat(plan.blockFrom()).isEqualTo(10_000 - CAP + 1);
        assertThat(plan.blockTo()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("A getLogs outage clamps the LOG lane too — the range can no longer grow unboundedly")
    void logOutageClamped() {
        // logsFrom stuck at 1_001 because every getLogs failed; the chain
        // moved 9_000 blocks. Before the clamp this requested
        // 1_001..10_000 — and wider on every retry.
        var plan = WatchCursor.plan(9_999, 1_001, 10_000, CAP);
        assertThat(plan.logFrom())
                .as("the log range resumes inside the same cap window as blocks")
                .isEqualTo(10_000 - CAP + 1);
        assertThat(plan.logTo()).isEqualTo(10_000);
        assertThat(plan.logTo() - plan.logFrom() + 1).isLessThanOrEqualTo(CAP);
    }

    @Test
    @DisplayName("A log cursor already inside the window is respected, not yanked to the floor")
    void logCursorInsideWindowKept() {
        var plan = WatchCursor.plan(995, 996, 1_000, CAP);
        assertThat(plan.logFrom()).isEqualTo(996);
        assertThat(plan.logTo()).isEqualTo(1_000);
    }
}
