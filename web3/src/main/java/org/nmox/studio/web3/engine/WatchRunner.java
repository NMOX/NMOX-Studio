package org.nmox.studio.web3.engine;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nmox.studio.core.util.Threads;

/**
 * One Watch session's orchestration, off any paint thread: subscribe or
 * poll, backfill the blocks a head skipped, decode both lanes into the
 * feed, and hand the lane back to the poller when the socket dies
 * (ledger 12, ledger 113). Every decision it makes belongs to a
 * collaborator — {@link WatchEndpoint} says whether a subscription is
 * possible, {@link WatchReconciler} owns the cursors and the generation
 * guard, {@link WatchCursor} clamps the ranges, {@link WatchSocket}
 * carries the notifications, {@link WatchFeed} holds the rows — and this
 * class is only the order they run in.
 *
 * <p><b>No Swing here.</b> The pane it serves implements {@link Ui} and
 * does its own thread hop; every callback carries the
 * {@link WatchReconciler} that produced it, so the consumer can re-ask
 * {@link WatchReconciler#current()} AFTER that hop — a result belongs to
 * the session that produced it, and a hop is exactly where a session can
 * go stale under you.
 *
 * <p><b>Polling stays the fallback and the truth.</b> A refused
 * handshake, a dropped socket and a network with no WebSocket endpoint
 * all end in the same 2 s poll, resuming from the last block the stream
 * finished. {@link #start()} and {@link #stop()} are confined to one
 * thread (the pane's EDT); everything else runs on this session's own
 * daemon lane or the socket's listener thread.
 */
public final class WatchRunner {

    private static final Logger LOG = Logger.getLogger(WatchRunner.class.getName());

    /** How many missed blocks one Watch tick will backfill at most. */
    public static final int CATCHUP_CAP = 50;

    /** How often the poll lane asks the chain for its head. */
    public static final Duration POLL_PERIOD = Duration.ofSeconds(2);

    /** Which lane the Watch pane is on, and why. */
    public enum Phase {
        /** No WebSocket endpoint for this network — polling from the start. */
        POLLING,
        /** Subscriptions confirmed; the stream owns the lane. */
        STREAMING,
        /** The handshake failed; polling instead, said out loud. */
        FALLBACK,
        /** A live subscription dropped; polling resumes after the last watched block. */
        DROPPED
    }

    /**
     * The live studio state one session reads, always off the EDT.
     * Implementations answer from volatile fields the pane keeps current.
     */
    public interface Source {

        /** The connected client, or null when the studio has none. */
        JsonRpcClient client();

        /**
         * The WebSocket endpoint for this session, or null to poll.
         * Read once per session on the watch lane, because resolving it
         * can touch the keyring.
         */
        String wsUrl();

        /** The addresses whose logs this session fetches; may change mid-session. */
        List<String> addresses();

        /** The decoder for this session's logs; may change mid-session. */
        EventMatcher matcher();
    }

    /**
     * What the runner tells the pane. Called off the EDT — hop, then
     * re-ask {@code session.current()} before touching anything.
     */
    public interface Ui {

        /**
         * The lane changed.
         *
         * @param lastWatchedBlock the newest block the session finished,
         *        meaningful for {@link Phase#DROPPED}; negative before
         *        the first block
         */
        void status(WatchReconciler session, Phase phase, long lastWatchedBlock);

        /**
         * The feed grew — rows only, no new chain head. Fires once per
         * streamed log, so the consumer coalesces on its own thread.
         */
        void rowsChanged(WatchReconciler session);

        /** The session finished {@code block}: the feed grew and the head moved. */
        void advanced(WatchReconciler session, long block);

        /** An RPC failed; the chain is unreachable for now. */
        void failed(WatchReconciler session);
    }

    private final Source source;
    private final WatchFeed feed;
    private final Ui ui;
    private final int catchUpCap;
    private final Duration pollPeriod;

    /**
     * Bumped on every {@link #stop()}. {@code shutdownNow} interrupts but
     * does not JOIN a tick blocked in an RPC (up to the client's timeout);
     * a quick STOP→START or a network switch could otherwise leave the
     * dying tick writing the cursors and the feed the new session now
     * owns. Every session compares its own generation with this one before
     * each write ({@link WatchReconciler#current()}). AtomicLong (not a
     * volatile ++): the increment must be atomic for SpotBugs'
     * VO_VOLATILE_INCREMENT law even though stop() is single-threaded.
     */
    private final AtomicLong generation = new AtomicLong();

    /** The running session's lane; null when stopped. Confined to the caller's thread. */
    private ScheduledExecutorService exec;

    /**
     * The running session — its cursors, its generation and its live
     * subscription. Confined to the caller's thread: START sets it, STOP
     * retires and clears it. The poller, the stream's head drain and the
     * socket's listener each hold the session they were started for, never
     * this field, so a late result can only ask a retired session.
     */
    private WatchReconciler session;

    public WatchRunner(Source source, WatchFeed feed, Ui ui) {
        this(source, feed, ui, CATCHUP_CAP, POLL_PERIOD);
    }

    /** Seam: the cap and the poll period are parameters so a test can drive ticks. */
    WatchRunner(Source source, WatchFeed feed, Ui ui, int catchUpCap, Duration pollPeriod) {
        this.source = source;
        this.feed = feed;
        this.ui = ui;
        this.catchUpCap = catchUpCap;
        this.pollPeriod = pollPeriod;
    }

    /** True while a session owns the lane. */
    public boolean running() {
        return exec != null;
    }

    /**
     * Clears the feed and starts a session: subscribe when the source
     * names a WebSocket endpoint, else poll. A no-op while one runs.
     */
    public void start() {
        if (exec != null) {
            return;
        }
        synchronized (feed) {
            feed.clear(); // under the feed's monitor: a stale lane's add can't land after it
        }
        // every STOP bumped the generation, so this value is this session's alone
        WatchReconciler fresh = new WatchReconciler(
                generation.get(), generation::get, catchUpCap);
        session = fresh;
        ScheduledExecutorService lane = Executors.newSingleThreadScheduledExecutor(
                r -> Threads.daemon(r, "Contract Studio watch"));
        exec = lane;
        lane.execute(() -> open(fresh, lane));
    }

    /**
     * Ends the session: the generation moves first, so any in-flight tick,
     * head or log has already lost ownership by the time the socket closes
     * and the lane shuts down. Idempotent.
     */
    public void stop() {
        generation.incrementAndGet(); // any in-flight tick, head or log loses ownership
        WatchReconciler dying = session;
        session = null;
        if (dying != null) {
            dying.retire(); // closes the live subscription, if there is one
        }
        if (exec != null) {
            exec.shutdownNow();
            exec = null;
        }
    }

    /** The address filter changed: a live logs subscription follows it. */
    public void addressesChanged(List<String> addresses) {
        WatchReconciler live = session;
        if (live != null && live.attached() instanceof StreamHandler stream) {
            stream.addressesChanged(addresses);
        }
    }

    // ---- the session ---------------------------------------------------

    /**
     * On the watch lane: subscribe when the source names a WebSocket
     * endpoint ({@link WatchEndpoint}), else poll. A failed handshake says
     * so in one status and polls — polling is the fallback and the truth.
     * The socket is attached to the session under the lock STOP retires it
     * with, so a STOP during the handshake can never leave a socket open.
     */
    private void open(WatchReconciler session, ScheduledExecutorService lane) {
        if (!session.current()) {
            return;
        }
        String wsUrl = source.wsUrl(); // off the EDT: resolving it can read the keyring
        if (wsUrl == null) {
            schedulePolling(session, lane);
            ui.status(session, Phase.POLLING, session.lastBlock());
            return;
        }
        StreamHandler handler = new StreamHandler(session, lane);
        try {
            WatchSocket socket = WatchSocket.open(wsUrl, source.addresses(), handler,
                    WatchSocket.HANDSHAKE_TIMEOUT);
            handler.socket = socket;
            if (!session.attach(handler) || !session.startStreaming()) {
                socket.close(); // STOP won the race
                return;
            }
        } catch (IOException noSubscription) {
            // the message is redacted by WatchSocket: host only, never the path or key
            LOG.log(Level.INFO, "Watch polls instead: {0}", noSubscription.getMessage());
            if (session.current()) {
                schedulePolling(session, lane);
                ui.status(session, Phase.FALLBACK, session.lastBlock());
            }
            return;
        }
        tick(session); // the seed: the current block and its logs, as the poller's first tick
        ui.status(session, Phase.STREAMING, session.lastBlock());
    }

    private void schedulePolling(WatchReconciler session, ScheduledExecutorService lane) {
        try {
            lane.scheduleWithFixedDelay(() -> tick(session), 0,
                    pollPeriod.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException stopped) {
            // STOP shut the lane down first — nothing left to poll for
        }
    }

    /**
     * One poll, on the watch lane: new blocks (deduped by the feed) and,
     * for the watched addresses, logs since the last polled block, decoded
     * against the scanned events. Errors are reported; they never raise a
     * dialog. Also the stream's seed tick.
     */
    private void tick(WatchReconciler session) {
        JsonRpcClient c = source.client();
        if (c == null || !session.current()) {
            return;
        }
        try {
            long current = c.blockNumber();
            // both lanes clamped to the cap window (the log clamp is the
            // v1.100.0 fix: a failing getLogs never advanced its cursor,
            // so retries widened the range — and the response — unboundedly)
            WatchCursor.Plan plan = session.pollPlan(current);
            for (long n = plan.blockFrom(); n <= plan.blockTo(); n++) {
                feedBlock(session, c.getBlockByNumber(String.valueOf(n), false));
            }
            List<String> addresses = source.addresses();
            EventMatcher matcher = source.matcher();
            boolean consumedLogs = plan.hasLogs() && !addresses.isEmpty();
            if (consumedLogs) {
                for (String address : addresses) {
                    feedLogs(c, session, matcher, address, plan.logFrom(), plan.logTo());
                }
            }
            if (!session.commitPoll(plan, current, consumedLogs)) {
                return; // the watch was re-armed mid-tick — the new session
                        // owns the cursors; a dying tick must not tear them
            }
            ui.advanced(session, current);
        } catch (IOException | RuntimeException pollFailed) {
            ui.failed(session);
        }
    }

    /**
     * One {@code newHeads} notification, on the watch lane: the same block
     * fetch the poller makes (so the row renders identically), and — when
     * the head skipped blocks — the same log fetch for them, because their
     * logs never came over the socket.
     */
    private void streamHead(WatchReconciler session, long head) {
        WatchReconciler.HeadPlan plan = session.onHead(head);
        JsonRpcClient c = source.client();
        if (!plan.hasBlocks() || c == null) {
            return;
        }
        try {
            for (long n = plan.blockFrom(); n <= plan.blockTo(); n++) {
                feedBlock(session, c.getBlockByNumber(String.valueOf(n), false));
            }
            if (plan.gap()) {
                EventMatcher matcher = source.matcher();
                for (String address : source.addresses()) {
                    feedLogs(c, session, matcher, address, plan.blockFrom(), plan.blockTo());
                }
            }
            if (session.commitHead(head)) {
                ui.advanced(session, head);
            }
        } catch (IOException | RuntimeException fetchFailed) {
            ui.failed(session); // uncommitted: the next head re-plans from the last finished block
        }
    }

    private void feedBlock(WatchReconciler session, JsonRpcClient.Block block) {
        if (block == null) {
            return;
        }
        synchronized (feed) {
            if (session.current()) {
                feed.addBlock(block.number(), block.txCount(), block.gasUsed(),
                        block.gasLimit(), block.hash());
            }
        }
    }

    /** Fetches and decodes one address's logs for the block range; skips unknown topics. */
    private void feedLogs(JsonRpcClient c, WatchReconciler session, EventMatcher matcher,
            String address, long fromBlock, long toBlock) throws IOException {
        for (JsonRpcClient.LogEntry log
                : c.getLogs(address, String.valueOf(fromBlock), String.valueOf(toBlock))) {
            feedLog(session, WatchReconciler.Lane.POLL, matcher, log, false);
        }
    }

    /**
     * The one decode both lanes share: unknown event shapes are skipped, a
     * log either lane already delivered is dropped by the session, and the
     * add happens under the feed's monitor only while the session is
     * current. True when a row was added.
     */
    private boolean feedLog(WatchReconciler session, WatchReconciler.Lane lane,
            EventMatcher matcher, JsonRpcClient.LogEntry log, boolean removed) {
        if (log.topics().isEmpty()) {
            return false;
        }
        EventMatcher.Match match = matcher.match(log.topics().get(0));
        if (match == null) {
            return false; // someone else's event shape — normal, skip
        }
        if (!session.acceptLog(lane, log, removed)) {
            return false;
        }
        Map<String, String> decoded;
        try {
            decoded = matcher.decodedDisplay(match, log.topics(), log.data());
        } catch (RuntimeException malformed) {
            decoded = Map.of("note", "decode failed: " + malformed.getMessage());
        }
        synchronized (feed) {
            if (!session.current()) {
                return false;
            }
            feed.addEvent(log.blockNumber(), match.contractName(),
                    match.event().name(), decoded);
        }
        return true;
    }

    /**
     * The live subscription's side of one Watch session: heads coalesce
     * onto the watch lane (a burst of heads queues one drain, so the queue
     * is bounded), logs decode on the listener thread (the socket reads the
     * next message only after this one — that is the back-pressure), and a
     * drop hands the lane back to the poller with one status. The session
     * closes it on STOP.
     */
    private final class StreamHandler implements WatchSocket.Events, AutoCloseable {

        private final WatchReconciler session;
        private final ScheduledExecutorService lane;
        private final AtomicLong pendingHead = new AtomicLong(-1);
        private final AtomicBoolean drainQueued = new AtomicBoolean();
        volatile WatchSocket socket;

        StreamHandler(WatchReconciler session, ScheduledExecutorService lane) {
            this.session = session;
            this.lane = lane;
        }

        @Override
        public void onHead(long blockNumber) {
            pendingHead.accumulateAndGet(blockNumber, Math::max);
            if (drainQueued.compareAndSet(false, true)) {
                if (!post(this::drainHeads)) {
                    drainQueued.set(false);
                }
            }
        }

        private void drainHeads() {
            drainQueued.set(false); // before the read: a head landing now queues its own drain
            streamHead(session, pendingHead.get());
        }

        @Override
        public void onLog(JsonRpcClient.LogEntry log, boolean removed) {
            if (!watches(log.address())) {
                return; // the filter narrowed since the subscription was made
            }
            if (feedLog(session, WatchReconciler.Lane.STREAM, source.matcher(), log, removed)) {
                ui.rowsChanged(session);
            }
        }

        @Override
        public void onDrop(String reason) {
            post(() -> {
                WatchReconciler.Resume resume = session.onDrop();
                if (resume == null) {
                    return; // stopped, or already fell back: one line, not two
                }
                LOG.log(Level.INFO,
                        "Watch subscription dropped ({0}); polling resumes after block {1}",
                        new Object[]{reason, resume.lastWatchedBlock()});
                schedulePolling(session, lane);
                ui.status(session, Phase.DROPPED, Math.max(0, resume.lastWatchedBlock()));
            });
        }

        /** The address filter changed while streaming: move the logs subscription. */
        void addressesChanged(List<String> addresses) {
            post(() -> {
                WatchSocket s = socket;
                if (s == null || !session.streaming()) {
                    return;
                }
                try {
                    s.resubscribeLogs(addresses, WatchSocket.HANDSHAKE_TIMEOUT);
                } catch (IOException failed) {
                    s.close();
                    onDrop(failed.getMessage());
                }
            });
        }

        private boolean watches(String address) {
            for (String watched : source.addresses()) {
                if (watched.equalsIgnoreCase(address)) {
                    return true;
                }
            }
            return false;
        }

        private boolean post(Runnable task) {
            try {
                lane.execute(task);
                return true;
            } catch (RejectedExecutionException stopped) {
                return false;
            }
        }

        @Override
        public void close() {
            WatchSocket s = socket;
            if (s != null) {
                s.close();
            }
        }
    }
}
