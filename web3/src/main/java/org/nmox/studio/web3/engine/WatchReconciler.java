package org.nmox.studio.web3.engine;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.LongSupplier;

/**
 * One Watch session's cursors and the decisions that keep two lanes honest
 * — the 2 s poller and the {@code eth_subscribe} stream (ledger 12). Pure
 * and synchronized: the watch daemon thread, the WebSocket listener thread
 * and the EDT all ask it, and none of them keeps a cursor of its own.
 *
 * <p><b>A result belongs to the session that produced it.</b> Every
 * decision first asks {@link #current()}: the session's generation must
 * still be the studio's live generation (STOP, a network switch and a
 * closed tab all bump it). A tick blocked in an RPC, a head queued behind
 * it, a log the socket delivered a moment after STOP — each answers
 * "stale" and writes nothing. That is the v1.100.0 generation guard,
 * carried from the poller to the stream.
 *
 * <p><b>Polling stays the fallback and the truth.</b> The stream only
 * moves the same two cursors the poller uses ({@link WatchCursor}), so a
 * dropped socket resumes polling from the last block the stream finished:
 * no block is fetched twice and none is skipped, and the 50-block clamp
 * still bounds a long outage. Logs are the one lane a WebSocket can
 * reorder (a block's logs may arrive after its head), so the resume
 * re-queries the last {@value #RESUME_LOG_OVERLAP} blocks' logs over HTTP
 * and the log identity set drops the repeats.
 */
public final class WatchReconciler {

    /** Which lane a log arrived on. */
    public enum Lane {
        /** {@code eth_getLogs} over HTTP — the poller, the seed, a gap backfill. */
        POLL,
        /** An {@code eth_subscription} notification. */
        STREAM
    }

    /**
     * The blocks one stream head asks for. {@code gap} is true when the
     * head skipped blocks (a missed notification, or blocks mined before
     * the subscription was confirmed): those blocks' logs never came over
     * the socket, so the caller backfills them over HTTP.
     */
    public record HeadPlan(long blockFrom, long blockTo, boolean gap) {

        static final HeadPlan NONE = new HeadPlan(1, 0, false);

        public boolean hasBlocks() {
            return blockFrom <= blockTo;
        }
    }

    /** Where polling resumes after the stream drops. */
    public record Resume(long lastWatchedBlock, long logsFromBlock) {
    }

    /** How many trailing blocks' logs a resume re-queries (the rest are deduped). */
    public static final int RESUME_LOG_OVERLAP = 2;

    /** The log identity set's hard bound — the oldest blocks fall out first. */
    public static final int MAX_SEEN_LOGS = 20_000;

    private final long generation;
    private final LongSupplier liveGeneration;
    private final int cap;

    private long lastBlock = -1;
    private long logsFrom = Long.MAX_VALUE;
    private boolean streaming;
    private boolean retired;
    private AutoCloseable attached;
    private final TreeMap<Long, Set<String>> seenLogs = new TreeMap<>();
    private int seenCount;

    /**
     * @param generation     the studio's generation when this session started
     * @param liveGeneration the studio's generation now
     * @param catchUpCap     the widest block range either lane may fetch
     */
    public WatchReconciler(long generation, LongSupplier liveGeneration, int catchUpCap) {
        if (catchUpCap < 1) {
            throw new IllegalArgumentException("catchUpCap must be at least 1");
        }
        this.generation = generation;
        this.liveGeneration = liveGeneration;
        this.cap = catchUpCap;
    }

    /** True while this session still owns the Watch pane. */
    public synchronized boolean current() {
        return !retired && generation == liveGeneration.getAsLong();
    }

    /** True while the stream lane is live (subscriptions confirmed, not dropped). */
    public synchronized boolean streaming() {
        return streaming;
    }

    /** The newest block either lane has finished; negative before the first. */
    public synchronized long lastBlock() {
        return lastBlock;
    }

    /** The first block whose logs the poll lane still owes. */
    public synchronized long logsFromBlock() {
        return logsFrom;
    }

    // ---- the poll lane --------------------------------------------------

    /** One poll tick's clamped fetch plan — exactly {@link WatchCursor#plan}. */
    public synchronized WatchCursor.Plan pollPlan(long head) {
        return WatchCursor.plan(lastBlock, logsFrom, head, cap);
    }

    /**
     * Records a finished poll tick. Stale sessions change nothing and
     * answer false: the tick must not tear the cursors a newer session owns.
     */
    public synchronized boolean commitPoll(WatchCursor.Plan plan, long head, boolean consumedLogs) {
        if (!current()) {
            return false;
        }
        boolean firstTick = lastBlock < 0;
        if (consumedLogs) {
            logsFrom = plan.logTo() + 1;
        } else if (firstTick) {
            logsFrom = head; // the watch-start block
        }
        lastBlock = head;
        pruneSeen();
        return true;
    }

    // ---- the stream lane ------------------------------------------------

    /** Arms the stream lane; false when the session went stale meanwhile. */
    public synchronized boolean startStreaming() {
        if (!current()) {
            return false;
        }
        streaming = true;
        return true;
    }

    /**
     * The blocks a {@code newHeads} notification asks for: nothing for a
     * stale session, a dropped stream, or a head already finished; else
     * from the block after the last finished one — clamped to the cap
     * window, so a long silence backfills no more than the poller would.
     */
    public synchronized HeadPlan onHead(long head) {
        if (!current() || !streaming || head <= lastBlock) {
            return HeadPlan.NONE;
        }
        if (lastBlock < 0) {
            return new HeadPlan(head, head, false);
        }
        long from = Math.max(lastBlock + 1, head - cap + 1);
        return new HeadPlan(from, head, from < head);
    }

    /** Records a finished head; false (and no change) when stale or dropped. */
    public synchronized boolean commitHead(long head) {
        if (!current() || !streaming) {
            return false;
        }
        if (head > lastBlock) {
            lastBlock = head;
            logsFrom = head + 1;
        }
        pruneSeen();
        return true;
    }

    /**
     * Whether a log is new to the feed. Stale sessions, removed (reorged)
     * logs, stream logs after the stream dropped, logs older than the cap
     * window, and a log already delivered by either lane all answer false.
     */
    public synchronized boolean acceptLog(Lane lane, JsonRpcClient.LogEntry log, boolean removed) {
        if (!current() || removed) {
            return false;
        }
        if (lane == Lane.STREAM && !streaming) {
            return false; // the socket's last words after a drop — polling owns the lane now
        }
        if (lastBlock >= 0 && log.blockNumber() < lastBlock - cap + 1) {
            return false; // below the window the identity set still remembers
        }
        String tx = log.txHash() == null ? "" : log.txHash().toLowerCase(Locale.ROOT);
        String key = log.logIndex() >= 0
                ? tx + '#' + log.logIndex()
                : tx + "#?" + log.address() + '|' + log.topics() + '|' + log.data();
        if (!seenLogs.computeIfAbsent(log.blockNumber(), b -> new HashSet<>()).add(key)) {
            return false;
        }
        seenCount++;
        while (seenCount > MAX_SEEN_LOGS && !seenLogs.isEmpty()) {
            seenCount -= seenLogs.pollFirstEntry().getValue().size();
        }
        return true;
    }

    /**
     * The stream dropped: hands the lane back to the poller. Answers null
     * for a stale session or a stream that already fell back — so a drop
     * reported twice (an error, then a close) says so once.
     */
    public Resume onDrop() {
        AutoCloseable toClose;
        Resume resume;
        synchronized (this) {
            if (!current() || !streaming) {
                return null;
            }
            streaming = false;
            logsFrom = lastBlock < 0
                    ? Long.MAX_VALUE
                    : Math.max(0, lastBlock - RESUME_LOG_OVERLAP + 1);
            resume = new Resume(lastBlock, logsFrom);
            toClose = attached;
            attached = null;
        }
        closeQuietly(toClose);
        return resume;
    }

    // ---- the session's resource ----------------------------------------

    /**
     * Hands the session its live subscription. False when the session went
     * stale while the socket was opening: the caller closes it. Under the
     * same lock {@link #retire} takes, so a STOP can never miss a socket.
     */
    public synchronized boolean attach(AutoCloseable resource) {
        if (!current()) {
            return false;
        }
        attached = resource;
        return true;
    }

    /** The attached resource, or null. */
    public synchronized AutoCloseable attached() {
        return attached;
    }

    /** Ends the session: no decision answers yes again, and the resource closes. */
    public void retire() {
        AutoCloseable toClose;
        synchronized (this) {
            retired = true;
            streaming = false;
            toClose = attached;
            attached = null;
        }
        closeQuietly(toClose);
    }

    private void pruneSeen() {
        if (lastBlock < 0) {
            return;
        }
        long floor = lastBlock - cap + 1;
        while (!seenLogs.isEmpty() && seenLogs.firstKey() < floor) {
            Map.Entry<Long, Set<String>> oldest = seenLogs.pollFirstEntry();
            seenCount -= oldest.getValue().size();
        }
    }

    private static void closeQuietly(AutoCloseable resource) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Exception ignored) {
            // closing a dying socket has nothing left to say
        }
    }
}
