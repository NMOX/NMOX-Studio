package org.nmox.studio.web3.engine;

import java.io.IOException;
import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nmox.studio.core.http.HttpClientFactory;
import org.nmox.studio.core.http.LoopbackUrls;

/**
 * The Watch pane's live subscription: {@code eth_subscribe newHeads} and
 * {@code logs} over the JDK's WebSocket client on the IDE's shared HTTP
 * pool (ledger 12). It reports what arrives and when the socket dies; the
 * decisions — what to fetch, what is a duplicate, where polling resumes —
 * belong to {@link WatchReconciler}.
 *
 * <p>Laws carried from {@link JsonRpcClient}: every message is BOUNDED
 * ({@link WsFrames}: an oversize message is refused, logged and the socket
 * dropped, because a refused log cannot be recovered in place and polling
 * is the lane that can); the endpoint URL is never logged, thrown or shown
 * whole ({@link Redacted}); and nothing here blocks the EDT — {@link #open}
 * waits on the handshake, so call it off the EDT.
 *
 * <p>Events arrive on the HTTP client's listener thread, one message at a
 * time (the listener requests the next only after handling this one, which
 * is also the back-pressure). After {@link #close()} or a drop no event is
 * delivered again, and {@link Events#onDrop} fires at most once — never for
 * a close the caller asked for.
 */
public final class WatchSocket implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(WatchSocket.class.getName());

    /** The handshake and subscription-confirmation budget. */
    public static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(5);

    /** What the socket reports. */
    public interface Events {

        /** A {@code newHeads} notification's block number. */
        void onHead(long blockNumber);

        /** A {@code logs} notification; {@code removed} marks a reorged-out log. */
        void onLog(JsonRpcClient.LogEntry log, boolean removed);

        /** The socket died; the reason is redacted and meant for the log. */
        void onDrop(String reason);
    }

    private enum Kind {
        HEADS, LOGS, UNSUBSCRIBE
    }

    private record Pending(Kind kind, CompletableFuture<String> reply) {
    }

    private final String url;
    private final Events events;
    private final WsFrames frames;
    private final AtomicBoolean finished = new AtomicBoolean();
    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<Long, Pending> pending = new ConcurrentHashMap<>();
    private volatile String headsSubscription;
    private volatile String logsSubscription;
    private volatile WebSocket socket;
    /** Sends are chained: the JDK refuses a send while the previous one is incomplete. */
    private CompletableFuture<?> sendChain = CompletableFuture.completedFuture(null);

    private WatchSocket(String url, Events events, int maxMessageChars) {
        this.url = url;
        this.events = events;
        this.frames = new WsFrames(maxMessageChars);
    }

    /**
     * Connects, subscribes to {@code newHeads} and — when there are
     * addresses to watch — to their {@code logs}, and returns once both
     * subscriptions are confirmed. Off the EDT.
     *
     * @throws IOException when the handshake or a subscription fails or
     *         times out; the message is redacted
     */
    public static WatchSocket open(String wsUrl, List<String> addresses, Events events,
            Duration timeout) throws IOException {
        return open(wsUrl, addresses, events, timeout, WsFrames.MAX_MESSAGE_CHARS);
    }

    /** Seam: the message cap is a parameter so a test can cross it cheaply. */
    static WatchSocket open(String wsUrl, List<String> addresses, Events events,
            Duration timeout, int maxMessageChars) throws IOException {
        WatchSocket watch = new WatchSocket(wsUrl, events, maxMessageChars);
        try {
            // inside the redacting try: URI.create echoes its whole input
            URI uri = URI.create(resolveLoopback(wsUrl));
            watch.socket = HttpClientFactory.shared().newWebSocketBuilder()
                    .connectTimeout(timeout)
                    .buildAsync(uri, watch.new Listener())
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            watch.subscribe(Kind.HEADS, new JSONArray().put("newHeads"))
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!addresses.isEmpty()) {
                watch.subscribe(Kind.LOGS, logsParams(addresses))
                        .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            }
            return watch;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            watch.close();
            throw new IOException("Interrupted while subscribing at " + Redacted.url(wsUrl));
        } catch (ExecutionException | TimeoutException | RuntimeException failed) {
            watch.close();
            Throwable cause = failed instanceof ExecutionException && failed.getCause() != null
                    ? failed.getCause() : failed;
            // no cause attached: nested messages may echo the full URL
            throw new IOException("Cannot subscribe at " + Redacted.url(wsUrl) + " — "
                    + (cause instanceof Exception e
                            ? JsonRpcClient.sanitizeMessage(e, wsUrl)
                            : cause.getClass().getSimpleName()));
        }
    }

    /**
     * Swaps the logs subscription to a new address set: the new one is
     * confirmed BEFORE the old one is dropped, so no block falls between
     * them (the overlap is what {@link WatchReconciler} dedupes). An empty
     * set just drops the old one. Off the EDT.
     */
    public void resubscribeLogs(List<String> addresses, Duration timeout) throws IOException {
        String old = logsSubscription;
        try {
            if (addresses.isEmpty()) {
                logsSubscription = null;
            } else {
                subscribe(Kind.LOGS, logsParams(addresses))
                        .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while resubscribing at " + Redacted.url(url));
        } catch (ExecutionException | TimeoutException | RuntimeException failed) {
            throw new IOException("Cannot resubscribe at " + Redacted.url(url));
        }
        if (old != null && !old.equals(logsSubscription)) {
            request(Kind.UNSUBSCRIBE, "eth_unsubscribe", new JSONArray().put(old));
        }
    }

    /** Closes without reporting a drop; idempotent, never blocks. */
    @Override
    public void close() {
        if (finished.compareAndSet(false, true)) {
            abandon();
        }
    }

    /** True once closed or dropped. */
    public boolean finished() {
        return finished.get();
    }

    // ---- plumbing ----------------------------------------------------------

    private CompletableFuture<String> subscribe(Kind kind, JSONArray params) {
        return request(kind, "eth_subscribe", params);
    }

    private CompletableFuture<String> request(Kind kind, String method, JSONArray params) {
        long id = nextId.getAndIncrement();
        CompletableFuture<String> reply = new CompletableFuture<>();
        pending.put(id, new Pending(kind, reply));
        String text = new JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .put("method", method)
                .put("params", params)
                .toString();
        send(text).whenComplete((sent, failure) -> {
            if (failure != null) {
                pending.remove(id);
                reply.completeExceptionally(failure);
            }
        });
        if (finished.get()) {
            pending.remove(id);
            reply.completeExceptionally(new IOException("the subscription socket is closed"));
        }
        return reply;
    }

    private synchronized CompletableFuture<?> send(String text) {
        WebSocket ws = socket;
        if (ws == null || finished.get()) {
            return CompletableFuture.failedFuture(new IOException("the subscription socket is closed"));
        }
        CompletableFuture<?> next = sendChain
                .handle((ignored, previousFailure) -> null)
                .thenCompose(ignored -> ws.sendText(text, true));
        sendChain = next;
        return next;
    }

    private static JSONArray logsParams(List<String> addresses) {
        return new JSONArray().put("logs")
                .put(new JSONObject().put("address", new JSONArray(addresses)));
    }

    /** {@code ws://localhost:…} gets the same loopback-stack answer HTTP does (v1.260.0). */
    static String resolveLoopback(String wsUrl) {
        String trimmed = wsUrl.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        boolean secure = lower.startsWith("wss://");
        if (!secure && !lower.startsWith("ws://")) {
            return trimmed;
        }
        String rest = trimmed.substring(secure ? "wss://".length() : "ws://".length());
        String resolved = LoopbackUrls.resolve((secure ? "https://" : "http://") + rest);
        return (secure ? "wss" : "ws") + resolved.substring(resolved.indexOf(':'));
    }

    private void drop(String reason) {
        if (finished.compareAndSet(false, true)) {
            abandon();
            events.onDrop(reason);
        }
    }

    private void abandon() {
        WebSocket ws = socket;
        if (ws != null) {
            ws.abort();
        }
        IOException closed = new IOException("the subscription socket is closed");
        for (Long id : pending.keySet()) {
            Pending p = pending.remove(id);
            if (p != null) {
                p.reply().completeExceptionally(closed);
            }
        }
    }

    /** One whole message from the node. Package-private seam for tests. */
    void handle(String text) {
        JSONObject message;
        try {
            message = new JSONObject(text);
        } catch (RuntimeException notJson) {
            LOG.log(Level.FINE, "Ignored a non-JSON message from {0}", Redacted.url(url));
            return;
        }
        if (message.has("id") && !message.isNull("id")) {
            reply(message);
            return;
        }
        if (!"eth_subscription".equals(message.optString("method", ""))) {
            return;
        }
        JSONObject params = message.optJSONObject("params");
        if (params == null) {
            return;
        }
        String subscription = params.optString("subscription", "");
        JSONObject result = params.optJSONObject("result");
        if (result == null || subscription.isEmpty()) {
            return;
        }
        try {
            if (subscription.equals(headsSubscription)) {
                String number = result.optString("number", "");
                if (!number.isEmpty()) {
                    events.onHead(JsonRpcClient.hexToLong(number));
                }
            } else if (subscription.equals(logsSubscription)) {
                events.onLog(JsonRpcClient.logEntry(result), result.optBoolean("removed", false));
            }
        } catch (RuntimeException malformed) {
            LOG.log(Level.FINE, "Ignored a malformed notification from {0}", Redacted.url(url));
        }
    }

    private void reply(JSONObject message) {
        Pending p = pending.remove(message.optLong("id", -1));
        if (p == null) {
            return;
        }
        JSONObject error = message.optJSONObject("error");
        if (error != null) {
            p.reply().completeExceptionally(new IOException("the node refused the subscription ("
                    + JsonRpcClient.toRpcException(error).getMessage() + ")"));
            return;
        }
        if (p.kind() == Kind.UNSUBSCRIBE) {
            p.reply().complete("");
            return;
        }
        Object result = message.opt("result");
        if (!(result instanceof String id) || id.isEmpty()) {
            p.reply().completeExceptionally(new IOException("the node answered without a subscription id"));
            return;
        }
        // mapped HERE, on the listener thread, before the next message is
        // requested — so the first notification can never beat its own id
        if (p.kind() == Kind.HEADS) {
            headsSubscription = id;
        } else {
            logsSubscription = id;
        }
        p.reply().complete(id);
    }

    private final class Listener implements WebSocket.Listener {

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (finished.get()) {
                return null;
            }
            WsFrames.Result part = frames.append(data, last);
            if (part.kind() == WsFrames.Kind.OVERSIZE) {
                LOG.log(Level.WARNING, "Refused a subscription message over {0} characters from {1}",
                        new Object[]{WsFrames.MAX_MESSAGE_CHARS, Redacted.url(url)});
                drop("a message over the size cap was refused");
                return null;
            }
            if (part.kind() == WsFrames.Kind.MESSAGE) {
                handle(part.text());
            }
            if (!finished.get()) {
                webSocket.request(1);
            }
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            if (!finished.get()) {
                webSocket.request(1); // JSON-RPC speaks text; binary is skipped, never buffered
            }
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            drop("closed by the node (status " + statusCode + ")");
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            drop(error instanceof Exception e
                    ? JsonRpcClient.sanitizeMessage(e, url)
                    : error.getClass().getSimpleName());
        }
    }
}
