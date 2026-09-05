package org.nmox.studio.rack.mcp;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.nmox.studio.core.util.Threads;

/**
 * Resource subscriptions and the SSE streams that carry them (v2.84.0):
 * an agent that subscribed to {@code nmox://runs} learns a run started
 * without polling. The push is READ-ONLY by nature — a
 * {@code notifications/resources/updated} frame names a URI and nothing
 * more; the agent re-reads. Writes to client streams ride one named
 * daemon so a slow or dead client can never stall the registry thread
 * that noticed the change (LiveRuns fires on the caller — often the EDT);
 * a stream that fails is dropped, never retried. A keepalive comment
 * rides every stream on a schedule (the review's find): a client that
 * vanished without closing is only ever NOTICED by a write, and a quiet
 * IDE could otherwise hold eight ghost streams until the next event —
 * the cap refusing a live agent for the sake of dead ones.
 */
final class McpSubscriptions {

    /** The spec's log levels, least to most severe (logging/setLevel, v2.84.0). */
    static final List<String> LEVELS = List.of("debug", "info", "notice", "warning",
            "error", "critical", "alert", "emergency");
    /** Nothing floods unless asked: lifecycle lines only until a client lowers the level. */
    static final String DEFAULT_LEVEL = "info";
    /** Log frames queued but not yet written; past this a line is counted, not queued. */
    static final int MAX_PENDING = 1_000;

    /** The SSE comment a client's parser ignores and a dead socket refuses. */
    static final String KEEPALIVE = ": keepalive\n\n";
    static final long KEEPALIVE_MILLIS = 15_000;

    /** One attached GET stream. */
    private static final class Sink {
        final OutputStream out;
        final Runnable onClose;

        Sink(OutputStream out, Runnable onClose) {
            this.out = out;
            this.onClose = onClose;
        }
    }

    private final Set<String> subscribed = ConcurrentHashMap.newKeySet();
    private final List<Sink> sinks = new CopyOnWriteArrayList<>();
    private final long keepaliveMillis;
    private volatile int level = LEVELS.indexOf(DEFAULT_LEVEL);
    private final java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger dropped = new java.util.concurrent.atomic.AtomicInteger();
    private volatile ExecutorService writer;
    private volatile java.util.concurrent.ScheduledExecutorService keepalive;
    private volatile boolean closed;

    McpSubscriptions() {
        this(KEEPALIVE_MILLIS);
    }

    /** Test seam: a short keepalive period. */
    McpSubscriptions(long keepaliveMillis) {
        this.keepaliveMillis = keepaliveMillis;
    }

    void subscribe(String uri) {
        subscribed.add(uri);
    }

    void unsubscribe(String uri) {
        subscribed.remove(uri);
    }

    boolean isSubscribed(String uri) {
        return subscribed.contains(uri);
    }

    Set<String> subscribed() {
        return Set.copyOf(subscribed);
    }

    /** The SSE frame for an updated resource — the whole of what a client learns. */
    static String frame(String uri) {
        JSONObject msg = new JSONObject()
                .put("jsonrpc", "2.0")
                .put("method", "notifications/resources/updated")
                .put("params", new JSONObject().put("uri", uri));
        return "event: message\ndata: " + msg + "\n\n";
    }

    /** logging/setLevel: false for a level the spec does not name. */
    boolean setLevel(String name) {
        int i = LEVELS.indexOf(name);
        if (i < 0) {
            return false;
        }
        level = i;
        return true;
    }

    String level() {
        return LEVELS.get(level);
    }

    /** The SSE frame for one log message: level, logger, the line as data. */
    static String logFrame(String level, String logger, String data) {
        JSONObject msg = new JSONObject()
                .put("jsonrpc", "2.0")
                .put("method", "notifications/message")
                .put("params", new JSONObject().put("level", level).put("logger", logger).put("data", data));
        return "event: message\ndata: " + msg + "\n\n";
    }

    /**
     * A log line for every attached stream, if {@code level} reaches the
     * set one. Bounded the honest way: a build printing faster than a
     * client reads never grows the queue past {@link #MAX_PENDING} —
     * the overflow is counted and announced as ONE line when the queue
     * drains, never silently lost.
     */
    void log(String level, String logger, String data) {
        int i = LEVELS.indexOf(level);
        if (i < 0 || i < this.level || sinks.isEmpty()) {
            return;
        }
        if (pending.get() >= MAX_PENDING) {
            dropped.incrementAndGet();
            return;
        }
        pending.incrementAndGet();
        submit(() -> {
            try {
                int lost = dropped.getAndSet(0);
                StringBuilder frames = new StringBuilder();
                if (lost > 0) {
                    frames.append(logFrame("warning", "agent-port", lost + " log lines dropped — the stream fell behind"));
                }
                frames.append(logFrame(level, logger, data));
                byte[] bytes = frames.toString().getBytes(StandardCharsets.UTF_8);
                for (Sink s : sinks) {
                    try {
                        s.out.write(bytes);
                        s.out.flush();
                    } catch (IOException gone) {
                        drop(s);
                    }
                }
            } finally {
                // a line is pending until it is WRITTEN: a client that stops
                // reading holds the count at the cap, and the overflow is
                // counted instead of queued
                pending.decrementAndGet();
            }
        });
    }

    /** Attaches a client stream; {@code onClose} runs once when it is dropped. */
    void attach(OutputStream out, Runnable onClose) {
        sinks.add(new Sink(out, onClose));
        if (keepalive == null) {
            synchronized (this) {
                if (keepalive == null && !closed) {
                    keepalive = Executors.newSingleThreadScheduledExecutor(
                            r -> Threads.daemon(r, "nmox-agent-port-keepalive"));
                    keepalive.scheduleAtFixedRate(this::keepalive, keepaliveMillis, keepaliveMillis, TimeUnit.MILLISECONDS);
                }
            }
        }
    }

    /** One keepalive pass: a comment to every stream, on the writer; the dead are dropped. */
    void keepalive() {
        if (sinks.isEmpty()) {
            return;
        }
        submit(() -> {
            for (Sink s : sinks) {
                try {
                    s.out.write(KEEPALIVE.getBytes(StandardCharsets.UTF_8));
                    s.out.flush();
                } catch (IOException gone) {
                    drop(s);
                }
            }
        });
    }

    int attachedCount() {
        return sinks.size();
    }

    /**
     * Announces that {@code uris} changed: every attached client gets a
     * frame for each URI it subscribed to. Returns at once — the writes
     * ride the daemon.
     */
    void updated(String... uris) {
        List<String> hit = new ArrayList<>();
        for (String u : uris) {
            if (subscribed.contains(u)) {
                hit.add(u);
            }
        }
        if (hit.isEmpty() || sinks.isEmpty()) {
            return;
        }
        submit(() -> {
            for (Sink s : sinks) {
                try {
                    for (String u : hit) {
                        s.out.write(frame(u).getBytes(StandardCharsets.UTF_8));
                    }
                    s.out.flush();
                } catch (IOException gone) {
                    drop(s);
                }
            }
        });
    }

    private void submit(Runnable write) {
        try {
            writer().execute(write);
        } catch (java.util.concurrent.RejectedExecutionException stopping) {
            // close() raced a listener (LiveRuns fires on the EDT): the port is
            // going away and the frame with it — this catch IS the guard, no
            // flag check precedes it (a flag would make it unprovable)
        }
    }

    /** Test barrier: every queued write has run. */
    void awaitIdle() throws InterruptedException {
        ExecutorService w = writer;
        if (w == null) {
            return;
        }
        try {
            w.submit(() -> { }).get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }

    private void drop(Sink s) {
        if (sinks.remove(s)) {
            try {
                s.out.close();
            } catch (IOException ignored) {
                // already gone
            }
            s.onClose.run();
        }
    }

    /** Closes every stream and stops the writer — the port is stopping. */
    void close() {
        closed = true;
        java.util.concurrent.ScheduledExecutorService k = keepalive;
        if (k != null) {
            k.shutdownNow();
        }
        for (Sink s : new ArrayList<>(sinks)) {
            drop(s);
        }
        ExecutorService w = writer;
        if (w != null) {
            w.shutdownNow();
        }
    }

    private ExecutorService writer() {
        ExecutorService w = writer;
        if (w == null) {
            synchronized (this) {
                if (writer == null) {
                    writer = Executors.newSingleThreadExecutor(r -> Threads.daemon(r, "nmox-agent-port-sse"));
                }
                w = writer;
            }
        }
        return w;
    }
}
