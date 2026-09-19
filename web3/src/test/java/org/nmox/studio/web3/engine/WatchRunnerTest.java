package org.nmox.studio.web3.engine;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.web3.model.AbiEntry;
import org.nmox.studio.web3.model.AbiParam;
import org.nmox.studio.web3.model.ContractArtifact;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Watch orchestration, headless (ledger 113). Until this class the
 * only test of subscribe-or-poll, the head-gap backfill and the
 * generation guard was {@link WatchStreamAnvilLiveTest}, which every CI
 * runner skips for want of {@code anvil} — so the most concurrency-dense
 * code in the module was verified on no CI run at all.
 *
 * <p>Everything here is real but the chain: the production
 * {@link WatchSocket} over the JDK's own WebSocket client against the
 * scripted {@link FakeWsServer}, the production {@link JsonRpcClient}
 * over an in-memory {@link FakeChain} transport, and the production
 * {@link WatchReconciler} deciding every cursor.
 */
class WatchRunnerTest {

    private static final AbiEntry PING =
            AbiEntry.event("Ping", List.of(AbiParam.of("n", "uint256")));
    private static final ContractArtifact BEACON = new ContractArtifact(
            "Beacon", "src/Beacon.sol", List.of(PING), "0xaa", "0xbb");
    private static final EventMatcher MATCHER = EventMatcher.build(List.of(BEACON));
    private static final String TOPIC0 = "0x" + Keccak256.hashHex(PING.signature());
    private static final String ADDRESS = "0x00000000000000000000000000000000000000aa";

    private static final Duration TICK = Duration.ofMillis(100);
    private static final long DEADLINE_MS = 15_000;

    private final RecordingUi ui = new RecordingUi();
    private final TestSource source = new TestSource();
    private final WatchFeed feed = new WatchFeed();
    private WatchRunner runner;
    private WsNode node;

    @AfterEach
    void tearDown() throws IOException {
        if (runner != null) {
            runner.stop();
        }
        if (node != null) {
            node.close();
        }
    }

    private WatchRunner runner() {
        runner = new WatchRunner(source, feed, ui, 50, TICK);
        return runner;
    }

    // ---- the four laws the ledger named unverified -----------------------

    @Test
    @DisplayName("an endpoint that subscribes puts the session on the socket, and the seed tick still runs")
    void subscribesWhenTheEndpointSupportsIt() throws Exception {
        FakeChain chain = new FakeChain(10);
        source.client = new JsonRpcClient("http://chain.test", chain);
        node = new WsNode();
        source.wsUrl = node.url();

        assertThat(runner().running()).as("nothing runs before START").isFalse();
        runner.start();
        node.acceptAndSubscribe();
        ui.awaitPhase(WatchRunner.Phase.STREAMING);

        assertThat(runner.running()).isTrue();
        assertThat(ui.phases).as("the socket lane, never the fallback")
                .containsExactly(WatchRunner.Phase.STREAMING);
        assertThat(blockRows()).as("the seed tick fetched the head over HTTP").containsExactly(10L);
        assertThat(eventRows()).as("and its logs").containsExactly(10L);

        runner.stop();
        assertThat(runner.running()).as("STOP gives the lane back").isFalse();
    }

    @Test
    @DisplayName("a refused handshake falls back to polling and says so")
    void fallsBackToPollingWhenTheSocketRefuses() throws Exception {
        FakeChain chain = new FakeChain(7);
        source.client = new JsonRpcClient("http://chain.test", chain);
        source.wsUrl = "ws://127.0.0.1:" + deadPort() + "/never";

        runner().start();
        ui.awaitPhase(WatchRunner.Phase.FALLBACK);
        ui.awaitAdvanced(7);

        assertThat(ui.phases).containsExactly(WatchRunner.Phase.FALLBACK);
        assertThat(blockRows()).as("polling is the fallback AND the truth").contains(7L);
    }

    @Test
    @DisplayName("a socket cut mid-stream resumes polling with no duplicate block, no gap and every log once")
    void aCutSocketResumesPollingWithNoDuplicateAndNoGap() throws Exception {
        FakeChain chain = new FakeChain(10);
        source.client = new JsonRpcClient("http://chain.test", chain);
        node = new WsNode();
        source.wsUrl = node.url();

        runner().start();
        node.acceptAndSubscribe();
        ui.awaitPhase(WatchRunner.Phase.STREAMING); // the seed tick has run: block 10

        for (long n : new long[]{11, 12}) {
            chain.mineTo(n);
            node.head(n);
            node.log(n);
            ui.awaitAdvanced(n);
        }

        node.cut();
        ui.awaitPhase(WatchRunner.Phase.DROPPED);

        chain.mineTo(14); // 13 and 14 mined while nothing was watching
        ui.awaitAdvanced(14);

        assertThat(chain.blockFetches).as("every block fetched exactly once, in order")
                .containsExactly(10L, 11L, 12L, 13L, 14L);
        assertThat(blockRows()).as("no duplicate row, no gap")
                .containsExactly(14L, 13L, 12L, 11L, 10L); // the feed is newest-first
        assertThat(chain.logQueries)
                .as("the resume re-queries from block 11 — the overlap a WebSocket can reorder "
                        + "(saw " + chain.logQueries + ")")
                .anyMatch(query -> query.startsWith("11.."));
        assertThat(eventRows()).as("five logs, each exactly once across both lanes")
                .containsExactlyInAnyOrder(10L, 11L, 12L, 13L, 14L);
    }

    @Test
    @DisplayName("a head that skipped blocks backfills them AND their logs, which never came over the socket")
    void aHeadGapBackfillsTheSkippedBlocksAndTheirLogs() throws Exception {
        FakeChain chain = new FakeChain(10);
        source.client = new JsonRpcClient("http://chain.test", chain);
        node = new WsNode();
        source.wsUrl = node.url();

        runner().start();
        node.acceptAndSubscribe();
        ui.awaitPhase(WatchRunner.Phase.STREAMING);

        chain.mineTo(13);
        node.head(13); // 11 and 12 never announced: a missed notification
        ui.awaitAdvanced(13);

        assertThat(chain.blockFetches).as("the skipped blocks are fetched")
                .containsExactly(10L, 11L, 12L, 13L);
        assertThat(chain.logQueries).as("and their logs, over HTTP, because the socket never sent them")
                .contains("11..13");
        assertThat(eventRows()).as("every skipped block's event reached the feed")
                .containsExactlyInAnyOrder(10L, 11L, 12L, 13L);
    }

    @Test
    @DisplayName("a tick still blocked in an RPC when STOP lands cannot write the next session's feed")
    void aDyingTickCannotWriteTheNextSessionsFeed() throws Exception {
        FakeChain dying = new FakeChain(10);
        dying.hashPrefix = "0xdying";
        dying.wedgeBlockNumber();
        FakeChain fresh = new FakeChain(20);
        fresh.hashPrefix = "0xfresh";
        source.client = new JsonRpcClient("http://dying.test", dying);
        source.wsUrl = null; // straight to the poll lane

        runner().start();
        assertThat(dying.entered.await(DEADLINE_MS, TimeUnit.MILLISECONDS))
                .as("the first tick is inside eth_blockNumber").isTrue();

        runner.stop(); // the generation moves; shutdownNow interrupts but cannot join the RPC
        source.client = new JsonRpcClient("http://fresh.test", fresh);
        runner.start();
        ui.awaitAdvanced(20); // the new session owns the feed now

        dying.release(); // the old tick wakes up and runs to completion
        await("the dying tick finished its fetch", () -> !dying.blockFetches.isEmpty());

        assertThat(blockRows()).as("only the session that owns the pane wrote rows")
                .containsExactly(20L);
        assertThat(hashes()).as("not one row from the dying chain")
                .allMatch(h -> h.startsWith("0xfresh"));
        assertThat(ui.advanced).as("a stale tick never reports progress")
                .allMatch(block -> block == 20L);
    }

    // ---- the edges around them -------------------------------------------

    @Test
    @DisplayName("the filter moves a live logs subscription: the new one is made before the old is dropped")
    void theFilterMovesALiveLogsSubscription() throws Exception {
        FakeChain chain = new FakeChain(10);
        source.client = new JsonRpcClient("http://chain.test", chain);
        node = new WsNode();
        source.wsUrl = node.url();

        runner().start();
        node.acceptAndSubscribe();
        ui.awaitPhase(WatchRunner.Phase.STREAMING);

        String other = "0x00000000000000000000000000000000000000bb";
        source.addresses = List.of(other);
        runner.addressesChanged(List.of(other));

        JSONObject resubscribe = node.conn.readRequest();
        assertThat(resubscribe.getString("method")).isEqualTo("eth_subscribe");
        assertThat(resubscribe.getJSONArray("params").getJSONObject(1)
                .getJSONArray("address").getString(0)).isEqualTo(other);
        node.conn.reply(resubscribe, "0xlogs2");

        JSONObject unsubscribe = node.conn.readRequest();
        assertThat(unsubscribe.getString("method"))
                .as("the old subscription is dropped only after the new one is confirmed")
                .isEqualTo("eth_unsubscribe");
        assertThat(unsubscribe.getJSONArray("params").getString(0)).isEqualTo("0xlogs");
    }

    @Test
    @DisplayName("a chain that refuses greys the chip instead of throwing on the lane")
    void anUnreachableChainIsReportedNotThrown() throws Exception {
        FakeChain chain = new FakeChain(10);
        chain.refuse = true;
        source.client = new JsonRpcClient("http://chain.test", chain);
        source.wsUrl = null;

        runner().start();
        await("the failure reached the pane", () -> !ui.failures.isEmpty());

        assertThat(blockRows()).as("a refused tick writes nothing").isEmpty();
        assertThat(ui.advanced).as("and reports no progress").isEmpty();
    }

    @Test
    @DisplayName("someone else's event shape is skipped, not decoded — the block still lands")
    void anUnknownEventShapeIsSkipped() throws Exception {
        FakeChain chain = new FakeChain(10);
        chain.logTopic = "0x" + "9".repeat(64); // a contract nobody scanned
        source.client = new JsonRpcClient("http://chain.test", chain);
        source.wsUrl = null;

        runner().start();
        ui.awaitAdvanced(10);

        assertThat(blockRows()).contains(10L);
        assertThat(eventRows()).as("a chain full of other people's events is normal").isEmpty();
    }

    // ---- helpers ---------------------------------------------------------

    private List<Long> blockRows() {
        List<Long> out = new ArrayList<>();
        for (WatchFeed.Row row : feed.rows()) {
            if (row instanceof WatchFeed.BlockRow block) {
                out.add(block.number());
            }
        }
        return out;
    }

    private List<String> hashes() {
        List<String> out = new ArrayList<>();
        for (WatchFeed.Row row : feed.rows()) {
            if (row instanceof WatchFeed.BlockRow block) {
                out.add(block.hash());
            }
        }
        return out;
    }

    private List<Long> eventRows() {
        List<Long> out = new ArrayList<>();
        for (WatchFeed.Row row : feed.rows()) {
            if (row instanceof WatchFeed.EventRow event) {
                out.add(event.blockNumber());
            }
        }
        return out;
    }

    private static int deadPort() throws IOException {
        try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return s.getLocalPort(); // closed on the way out: nothing listens there
        }
    }

    private static void await(String what, java.util.function.BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(DEADLINE_MS);
        while (!condition.getAsBoolean()) {
            assertThat(System.nanoTime()).as(what).isLessThan(deadline);
            Thread.sleep(10);
        }
    }

    /** The pane's side, recorded. */
    private static final class RecordingUi implements WatchRunner.Ui {

        private final List<WatchRunner.Phase> phases = new CopyOnWriteArrayList<>();
        private final List<Long> advanced = new CopyOnWriteArrayList<>();
        private final List<Long> droppedAt = new CopyOnWriteArrayList<>();
        private final List<String> failures = new CopyOnWriteArrayList<>();

        @Override
        public void status(WatchReconciler session, WatchRunner.Phase phase, long lastBlock) {
            phases.add(phase);
            droppedAt.add(lastBlock);
        }

        @Override
        public void rowsChanged(WatchReconciler session) {
            // the pane repaints; nothing to record beyond the feed itself
        }

        @Override
        public void advanced(WatchReconciler session, long block) {
            advanced.add(block);
        }

        @Override
        public void failed(WatchReconciler session) {
            failures.add("failed");
        }

        void awaitPhase(WatchRunner.Phase phase) throws InterruptedException {
            await("the session reports " + phase + " (saw " + phases + ")",
                    () -> phases.contains(phase));
        }

        void awaitAdvanced(long block) throws InterruptedException {
            await("the session advanced to " + block + " (saw " + advanced + ")",
                    () -> advanced.contains(block));
        }
    }

    /** The live studio state, as volatile fields a test sets. */
    private static final class TestSource implements WatchRunner.Source {

        private volatile JsonRpcClient client;
        private volatile String wsUrl;
        private volatile List<String> addresses = List.of(ADDRESS);

        @Override
        public JsonRpcClient client() {
            return client;
        }

        @Override
        public String wsUrl() {
            return wsUrl;
        }

        @Override
        public List<String> addresses() {
            return addresses;
        }

        @Override
        public EventMatcher matcher() {
            return MATCHER;
        }
    }

    /**
     * A chain in memory behind the production {@link JsonRpcClient}: every
     * block carries one {@code Ping} log, and every fetch is recorded so a
     * duplicate or a gap is visible as itself rather than through the
     * feed's own hash dedup.
     */
    private static final class FakeChain implements JsonRpcClient.Transport {

        private volatile long head;
        private volatile String hashPrefix = "0xblock";
        private volatile String logTopic = TOPIC0;
        private volatile boolean refuse;
        private final List<Long> blockFetches = new CopyOnWriteArrayList<>();
        private final List<String> logQueries = new CopyOnWriteArrayList<>();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final AtomicReference<CountDownLatch> gate = new AtomicReference<>();

        FakeChain(long head) {
            this.head = head;
        }

        void mineTo(long block) {
            head = block;
        }

        /** The next {@code eth_blockNumber} blocks until {@link #release()}. */
        void wedgeBlockNumber() {
            gate.set(new CountDownLatch(1));
        }

        void release() {
            CountDownLatch latch = gate.getAndSet(null);
            if (latch != null) {
                latch.countDown();
            }
        }

        @Override
        public String post(String url, String jsonBody) throws IOException {
            if (refuse) {
                throw new IOException("the node at chain.test is not answering");
            }
            JSONObject request = new JSONObject(jsonBody);
            JSONArray params = request.optJSONArray("params");
            return switch (request.getString("method")) {
                case "eth_blockNumber" -> {
                    wait4Gate();
                    yield result(hex(head));
                }
                case "eth_getBlockByNumber" -> {
                    long n = JsonRpcClient.hexToLong(params.getString(0));
                    blockFetches.add(n);
                    yield result(block(n));
                }
                case "eth_getLogs" -> {
                    JSONObject filter = params.getJSONObject(0);
                    long from = JsonRpcClient.hexToLong(filter.getString("fromBlock"));
                    long to = JsonRpcClient.hexToLong(filter.getString("toBlock"));
                    logQueries.add(from + ".." + to);
                    JSONArray logs = new JSONArray();
                    for (long n = from; n <= Math.min(to, head); n++) {
                        logs.put(log(n));
                    }
                    yield result(logs);
                }
                default -> throw new IOException("the test chain knows no "
                        + request.getString("method"));
            };
        }

        /**
         * A real RPC blocked in a socket read does not answer an interrupt,
         * which is exactly why {@code shutdownNow} cannot join a dying tick
         * — so neither does this one.
         */
        private void wait4Gate() {
            CountDownLatch latch = gate.get();
            if (latch == null) {
                return;
            }
            entered.countDown();
            boolean interrupted = false;
            while (true) {
                try {
                    latch.await();
                    break;
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        private JSONObject block(long number) {
            return new JSONObject()
                    .put("number", hex(number))
                    .put("hash", hashPrefix + number)
                    .put("timestamp", hex(1_700_000_000L + number))
                    .put("gasUsed", hex(21_000))
                    .put("gasLimit", hex(30_000_000))
                    .put("transactions", new JSONArray().put("0xtx" + number));
        }

        private JSONObject log(long number) {
            return new JSONObject()
                    .put("address", ADDRESS)
                    .put("topics", new JSONArray().put(logTopic))
                    .put("data", word(number))
                    .put("blockNumber", hex(number))
                    .put("transactionHash", "0xtx" + number)
                    .put("logIndex", hex(0));
        }

        private static String result(Object value) {
            return new JSONObject().put("jsonrpc", "2.0").put("id", 1)
                    .put("result", value).toString();
        }

        private static String hex(long value) {
            return "0x" + Long.toHexString(value);
        }
    }

    static String word(long value) {
        return "0x" + String.format(Locale.ROOT, "%064x", value);
    }

    /**
     * A node that speaks the subscription half: it accepts one connection,
     * confirms {@code newHeads} and {@code logs}, then lets the test push
     * notifications and cut the wire.
     */
    private static final class WsNode implements AutoCloseable {

        private final FakeWsServer server = new FakeWsServer();
        private FakeWsServer.Conn conn;

        WsNode() throws IOException {
        }

        String url() {
            return server.url();
        }

        /** Blocks until the runner's handshake and both subscriptions land. */
        void acceptAndSubscribe() throws IOException {
            conn = server.accept();
            conn.handshake();
            JSONObject heads = conn.readRequest();
            assertThat(heads.getJSONArray("params").getString(0)).isEqualTo("newHeads");
            conn.reply(heads, "0xheads");
            JSONObject logs = conn.readRequest();
            assertThat(logs.getJSONArray("params").getString(0)).isEqualTo("logs");
            conn.reply(logs, "0xlogs");
        }

        void head(long number) throws IOException {
            conn.notify("0xheads", new JSONObject().put("number", "0x" + Long.toHexString(number)));
        }

        void log(long number) throws IOException {
            conn.notify("0xlogs", new JSONObject()
                    .put("address", ADDRESS)
                    .put("topics", new JSONArray().put(TOPIC0))
                    .put("data", word(number))
                    .put("blockNumber", "0x" + Long.toHexString(number))
                    .put("transactionHash", "0xtx" + number)
                    .put("logIndex", "0x0"));
        }

        /** The drop: TCP torn down with no close frame, as a crashed node does it. */
        void cut() throws IOException {
            conn.kill();
        }

        @Override
        public void close() throws IOException {
            if (conn != null) {
                conn.close();
            }
            server.close();
        }
    }
}
