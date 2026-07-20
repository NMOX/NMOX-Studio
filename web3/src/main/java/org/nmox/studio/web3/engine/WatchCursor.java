package org.nmox.studio.web3.engine;

/**
 * The Watch poller's range math, pure so the clamp laws are testable
 * without a chain. One call per tick: given the two cursors, the
 * chain's current block, and the catch-up cap, it yields the block
 * range to fetch and the log range to query — BOTH clamped to the cap
 * window ending at {@code current}.
 *
 * <p>The log clamp is the v1.100.0 fix: blocks were capped from day
 * one, but the log range was open-ended below. A failing
 * {@code eth_getLogs} never advances the log cursor, so every retry
 * widened {@code logsFrom..current} — and the response with it —
 * without bound. After an outage the watch now resumes inside the
 * same window the blocks do, honestly skipping what's too old to
 * backfill (exactly the block-lane behavior since v1.33.0).
 */
public final class WatchCursor {

    private WatchCursor() {
    }

    /**
     * One tick's fetch plan. Empty ranges have {@code from > to}
     * (check {@link #hasBlocks()} / {@link #hasLogs()}).
     */
    public record Plan(long blockFrom, long blockTo, long logFrom, long logTo) {

        public boolean hasBlocks() {
            return blockFrom <= blockTo;
        }

        public boolean hasLogs() {
            return logFrom <= logTo;
        }
    }

    /**
     * @param lastWatchedBlock the newest block already fed, or negative
     *        on the first tick of a watch
     * @param logsFromBlock the first block whose logs are still owed
     *        ({@code Long.MAX_VALUE} before the first tick)
     * @param current the chain head
     * @param catchUpCap the widest range either lane may fetch
     */
    public static Plan plan(long lastWatchedBlock, long logsFromBlock,
            long current, int catchUpCap) {
        long floor = current - catchUpCap + 1;
        boolean firstTick = lastWatchedBlock < 0;
        long blockFrom = firstTick ? current : Math.max(lastWatchedBlock + 1, floor);
        long logFrom = firstTick ? current : Math.max(logsFromBlock, floor);
        return new Plan(blockFrom, current, logFrom, current);
    }
}
