package org.nmox.studio.web3.engine;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The production JDK WebSocket client against a scripted RFC 6455 server:
 * subscriptions are made and confirmed, notifications arrive as heads and
 * logs, a dropped connection is reported once, an oversize message is
 * refused, and no failure message carries the endpoint's path.
 */
class WatchSocketTest {

    private static final Duration T = Duration.ofSeconds(5);

    private FakeWsServer server;
    private ExecutorService side;
    private final BlockingQueue<Long> heads = new LinkedBlockingQueue<>();
    private final BlockingQueue<JsonRpcClient.LogEntry> logs = new LinkedBlockingQueue<>();
    private final BlockingQueue<Boolean> removed = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> drops = new LinkedBlockingQueue<>();

    private final WatchSocket.Events events = new WatchSocket.Events() {
        @Override
        public void onHead(long blockNumber) {
            heads.add(blockNumber);
        }

        @Override
        public void onLog(JsonRpcClient.LogEntry log, boolean wasRemoved) {
            logs.add(log);
            removed.add(wasRemoved);
        }

        @Override
        public void onDrop(String reason) {
            drops.add(reason);
        }
    };

    @BeforeEach
    void start() throws IOException {
        server = new FakeWsServer();
        side = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "watch-socket-test");
            t.setDaemon(true);
            return t;
        });
    }

    @AfterEach
    void stop() throws IOException {
        side.shutdownNow();
        server.close();
    }

    /** Opens against the fake server, answering the subscriptions. */
    private record Opened(WatchSocket socket, FakeWsServer.Conn conn) {
    }

    private Opened openWithLogs(int cap) throws Exception {
        Future<WatchSocket> opening = side.submit(() ->
                WatchSocket.open(server.url(), List.of("0xC0FFEE"), events, T, cap));
        FakeWsServer.Conn conn = server.accept();
        conn.handshake();
        JSONObject heads = conn.readRequest();
        assertThat(heads.getString("method")).isEqualTo("eth_subscribe");
        assertThat(heads.getJSONArray("params").getString(0)).isEqualTo("newHeads");
        conn.reply(heads, "0xheads");
        JSONObject logsRequest = conn.readRequest();
        JSONArray params = logsRequest.getJSONArray("params");
        assertThat(params.getString(0)).isEqualTo("logs");
        assertThat(params.getJSONObject(1).getJSONArray("address").getString(0))
                .as("the logs subscription carries the watched addresses").isEqualTo("0xC0FFEE");
        conn.reply(logsRequest, "0xlogs");
        return new Opened(opening.get(10, TimeUnit.SECONDS), conn);
    }

    private static JSONObject logJson(String block, String index, boolean wasRemoved) {
        return new JSONObject().put("address", "0xc0ffee")
                .put("topics", new JSONArray().put("0xtopic"))
                .put("data", "0x01").put("blockNumber", block)
                .put("transactionHash", "0xtx").put("logIndex", index)
                .put("removed", wasRemoved);
    }

    @Test
    @DisplayName("subscribes to newHeads and logs, and delivers each notification to its lane")
    void subscribesAndDelivers() throws Exception {
        Opened o = openWithLogs(WsFrames.MAX_MESSAGE_CHARS);
        o.conn().notify("0xheads", new JSONObject().put("number", "0x10"));
        o.conn().notify("0xlogs", logJson("0x10", "0x3", false));
        o.conn().notify("0xstranger", new JSONObject().put("number", "0x99"));
        o.conn().notify("0xheads", new JSONObject().put("number", "0x11"));

        assertThat(heads.poll(5, TimeUnit.SECONDS)).isEqualTo(16L);
        JsonRpcClient.LogEntry log = logs.poll(5, TimeUnit.SECONDS);
        assertThat(log).isNotNull();
        assertThat(log.blockNumber()).isEqualTo(16);
        assertThat(log.logIndex()).isEqualTo(3);
        assertThat(log.topics()).containsExactly("0xtopic");
        // the listener hands over the log BEFORE the removed flag, so the flag
        // is awaited too: an untimed poll lost that race on windows-latest (PR 779)
        assertThat(removed.poll(5, TimeUnit.SECONDS)).isFalse();
        assertThat(heads.poll(5, TimeUnit.SECONDS))
                .as("an unknown subscription is ignored, the next head still arrives").isEqualTo(17L);

        o.socket().close();
        assertThat(o.socket().finished()).isTrue();
        assertThat(drops.poll(300, TimeUnit.MILLISECONDS))
                .as("a close the caller asked for is not a drop").isNull();
    }

    @Test
    @DisplayName("garbage and malformed notifications are skipped without losing the stream")
    void malformedIsSkipped() throws Exception {
        Opened o = openWithLogs(WsFrames.MAX_MESSAGE_CHARS);
        o.conn().sendText("not json at all");
        o.conn().notify("0xheads", new JSONObject().put("number", "0xzz"));
        o.conn().notify("0xheads", new JSONObject());
        o.conn().sendText(new JSONObject().put("jsonrpc", "2.0").put("method", "eth_subscription")
                .toString());
        o.conn().sendText(new JSONObject().put("jsonrpc", "2.0").put("id", 999).put("result", "0x1")
                .toString());
        o.conn().notify("0xheads", new JSONObject().put("number", "0x20"));
        assertThat(heads.poll(5, TimeUnit.SECONDS)).isEqualTo(32L);
        assertThat(drops).isEmpty();
        o.socket().close();
    }

    @Test
    @DisplayName("a connection that dies without a close frame is reported once, and nothing arrives after")
    void droppedConnectionReportedOnce() throws Exception {
        Opened o = openWithLogs(WsFrames.MAX_MESSAGE_CHARS);
        o.conn().kill();
        String reason = drops.poll(10, TimeUnit.SECONDS);
        assertThat(reason).isNotNull().doesNotContain("SECRETKEY");
        assertThat(o.socket().finished()).isTrue();
        assertThat(drops.poll(300, TimeUnit.MILLISECONDS)).as("reported once").isNull();
        o.socket().close(); // idempotent after a drop
    }

    @Test
    @DisplayName("a node's close frame is a drop too")
    void closeFrameIsADrop() throws Exception {
        Opened o = openWithLogs(WsFrames.MAX_MESSAGE_CHARS);
        o.conn().sendText(""); // an empty message is fine
        byte[] close = {(byte) 0x88, 2, 0x03, (byte) 0xE8}; // close, status 1000
        o.conn().sendRaw(close);
        assertThat(drops.poll(10, TimeUnit.SECONDS)).contains("1000");
    }

    @Test
    @DisplayName("CAP: a message over the cap is refused, nothing of it is delivered, and the lane drops to polling")
    void oversizeMessageDrops() throws Exception {
        Opened o = openWithLogs(4_096);
        String huge = new JSONObject().put("jsonrpc", "2.0").put("method", "eth_subscription")
                .put("params", new JSONObject().put("subscription", "0xheads")
                        .put("result", new JSONObject().put("number", "0x5")
                                .put("padding", "x".repeat(200_000))))
                .toString();
        try {
            o.conn().sendText(huge);
        } catch (java.net.SocketException clientHungUp) {
            // the client refusing the frame and dropping the socket mid-write
            // IS the behaviour under test: Windows reports that reset on the
            // writer ("an established connection was aborted"), macOS and
            // Linux usually let the write finish into the dead socket
        }
        assertThat(drops.poll(10, TimeUnit.SECONDS)).contains("size cap");
        assertThat(heads).as("the refused head was never parsed").isEmpty();
        assertThat(o.socket().finished()).isTrue();
    }

    @Test
    @DisplayName("a refused subscription fails the open, redacted, and reports no drop")
    void refusedSubscription() throws Exception {
        Future<WatchSocket> opening = side.submit(() ->
                WatchSocket.open(server.url(), List.of(), events, T));
        FakeWsServer.Conn conn = server.accept();
        conn.handshake();
        JSONObject request = conn.readRequest();
        conn.sendText(new JSONObject().put("jsonrpc", "2.0").put("id", request.getLong("id"))
                .put("error", new JSONObject().put("code", -32601).put("message", "method not found"))
                .toString());
        assertThatThrownBy(() -> opening.get(10, TimeUnit.SECONDS))
                .hasCauseInstanceOf(IOException.class)
                .cause()
                .hasMessageContaining("ws://127.0.0.1")
                .hasMessageContaining("refused")
                .hasMessageNotContaining("SECRETKEY");
        assertThat(drops.poll(300, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @DisplayName("an answer without a subscription id fails the open")
    void missingSubscriptionId() throws Exception {
        Future<WatchSocket> opening = side.submit(() ->
                WatchSocket.open(server.url(), List.of(), events, T));
        FakeWsServer.Conn conn = server.accept();
        conn.handshake();
        conn.reply(conn.readRequest(), JSONObject.NULL);
        assertThatThrownBy(() -> opening.get(10, TimeUnit.SECONDS))
                .cause().hasMessageContaining("subscription id");
    }

    @Test
    @DisplayName("a node that never finishes the handshake fails the open within the budget, redacted")
    void handshakeTimeout() throws Exception {
        Future<WatchSocket> opening = side.submit(() ->
                WatchSocket.open(server.url(), List.of(), events, Duration.ofMillis(500)));
        FakeWsServer.Conn conn = server.accept();
        conn.readUpgradeOnly();
        assertThatThrownBy(() -> opening.get(10, TimeUnit.SECONDS))
                .cause().isInstanceOf(IOException.class)
                .hasMessageNotContaining("SECRETKEY");
        conn.close();
    }

    @Test
    @DisplayName("nothing listening fails the open, redacted")
    void nothingListening() throws Exception {
        String url = server.url();
        server.close();
        assertThatThrownBy(() -> WatchSocket.open(url, List.of(), events, T))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Cannot subscribe at ws://127.0.0.1")
                .hasMessageNotContaining("SECRETKEY");
        assertThatThrownBy(() -> WatchSocket.open("ws://bad host/v2/SECRETKEY", List.of(), events, T))
                .isInstanceOf(IOException.class)
                .hasMessageNotContaining("SECRETKEY");
    }

    @Test
    @DisplayName("resubscribing confirms the new logs subscription BEFORE dropping the old one")
    void resubscribeNewBeforeOld() throws Exception {
        Opened o = openWithLogs(WsFrames.MAX_MESSAGE_CHARS);
        Future<?> swapping = side.submit(() -> {
            o.socket().resubscribeLogs(List.of("0xBEEF"), T);
            return null;
        });
        JSONObject subscribe = o.conn().readRequest();
        assertThat(subscribe.getString("method")).isEqualTo("eth_subscribe");
        assertThat(subscribe.getJSONArray("params").getJSONObject(1).getJSONArray("address")
                .getString(0)).isEqualTo("0xBEEF");
        o.conn().reply(subscribe, "0xlogs2");
        swapping.get(10, TimeUnit.SECONDS);
        JSONObject unsubscribe = o.conn().readRequest();
        assertThat(unsubscribe.getString("method")).isEqualTo("eth_unsubscribe");
        assertThat(unsubscribe.getJSONArray("params").getString(0)).isEqualTo("0xlogs");
        o.conn().reply(unsubscribe, true);

        o.conn().notify("0xlogs", logJson("0x1", "0x0", false));
        o.conn().notify("0xlogs2", logJson("0x2", "0x0", true));
        JsonRpcClient.LogEntry delivered = logs.poll(5, TimeUnit.SECONDS);
        assertThat(delivered.blockNumber()).as("the old subscription's logs are ignored").isEqualTo(2);
        // the listener hands over the log BEFORE the removed flag, so the flag
        // is awaited too: an untimed poll lost that race on windows-latest (PR 779)
        assertThat(removed.poll(5, TimeUnit.SECONDS)).as("removed passes through for the session to refuse").isTrue();

        Future<?> emptying = side.submit(() -> {
            o.socket().resubscribeLogs(List.of(), T);
            return null;
        });
        emptying.get(10, TimeUnit.SECONDS);
        assertThat(o.conn().readRequest().getJSONArray("params").getString(0)).isEqualTo("0xlogs2");
        o.socket().close();
        assertThatThrownBy(() -> o.socket().resubscribeLogs(List.of("0x1"), T))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("ws://localhost gets the loopback-stack answer; other hosts pass through verbatim")
    void loopbackResolution() {
        assertThat(WatchSocket.resolveLoopback("wss://eth.example/v2/k?x=1"))
                .isEqualTo("wss://eth.example/v2/k?x=1");
        assertThat(WatchSocket.resolveLoopback("ws://127.0.0.1:8545")).isEqualTo("ws://127.0.0.1:8545");
        assertThat(WatchSocket.resolveLoopback("ws://localhost:1/p")).startsWith("ws://");
        assertThat(WatchSocket.resolveLoopback("http://x")).isEqualTo("http://x");
    }
}
