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
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;
import org.nmox.studio.core.util.Threads;

/**
 * Resource subscriptions and the SSE streams that carry them (v2.84.0):
 * an agent that subscribed to {@code nmox://runs} learns a run started
 * without polling; the same streams carry log messages. The push is
 * READ-ONLY by nature — a {@code notifications/resources/updated} frame
 * names a URI and nothing more; the agent re-reads.
 *
 * <p>Every stream has its OWN writer daemon (the shift review's find):
 * a socket write blocks when the client stops reading, and one writer
 * for all streams meant one stuck agent stalled every other agent's
 * pushes — and the keepalive that would have noticed it. Now a stuck
 * stream stalls only itself: its pending count climbs to the cap, its
 * overflow is counted, the others keep flowing. A stream that fails is
 * dropped, never retried; a keepalive comment rides every stream on a
 * schedule so a client that vanished without closing is noticed in
 * a quiet IDE too — the cap must never count ghosts.
 */
final class McpSubscriptions {

    /** The spec's log levels, least to most severe (logging/setLevel, v2.84.0). */
    static final List<String> LEVELS = List.of("debug", "info", "notice", "warning",
            "error", "critical", "alert", "emergency");
    /** Nothing floods unless asked: lifecycle lines only until a client lowers the level. */
    static final String DEFAULT_LEVEL = "info";
    /** Frames queued for one stream but not yet written; past this a log line is counted, not queued. */
    static final int MAX_PENDING = 1_000;

    /** Outline subscriptions that follow a file on disk: at most this many, polled on a schedule. */
    static final int MAX_FILE_WATCHES = 32;
    static final long FILE_POLL_MILLIS = 2_000;

    /** One watched file: its path and the stamp last seen. */
    private static final class Watched {
        final java.nio.file.Path path;
        volatile long mtime;
        volatile long size;

        Watched(java.nio.file.Path path, long mtime, long size) {
            this.path = path;
            this.mtime = mtime;
            this.size = size;
        }
    }

    /** The SSE comment a client's parser ignores and a dead socket refuses. */
    static final String KEEPALIVE = ": keepalive\n\n";
    static final long KEEPALIVE_MILLIS = 15_000;

    /** One attached GET stream: its socket, its close hook, its own writer and backlog. */
    private final class Sink {
        final OutputStream out;
        final Runnable onClose;
        final ExecutorService writer = Executors.newSingleThreadExecutor(
                r -> Threads.daemon(r, "nmox-agent-port-sse"));
        final AtomicInteger pending = new AtomicInteger();
        final AtomicInteger dropped = new AtomicInteger();

        Sink(OutputStream out, Runnable onClose) {
            this.out = out;
            this.onClose = onClose;
        }

        /** Queues one write; a sink whose writer is already shut down (dropped under a snapshot iteration) is left alone. */
        void submit(byte[] frames) {
            try {
                writer.execute(() -> {
                    try {
                        out.write(frames);
                        out.flush();
                    } catch (IOException gone) {
                        drop(this);
                    }
                });
            } catch (java.util.concurrent.RejectedExecutionException dropped) {
                // this sink was dropped between the sinks snapshot and the
                // submit — it is already closed; the frame dies with it.
                // The only guard: a flag check before execute would leave
                // the same window open, so the catch IS the mechanism
            }
        }
    }

    private final Set<String> subscribed = ConcurrentHashMap.newKeySet();
    private final List<Sink> sinks = new CopyOnWriteArrayList<>();
    private final long keepaliveMillis;
    private final long filePollMillis;
    private final java.util.Map<String, Watched> watched = new ConcurrentHashMap<>();
    private volatile java.util.concurrent.ScheduledExecutorService filePoll;
    private volatile int level = LEVELS.indexOf(DEFAULT_LEVEL);
    private volatile java.util.concurrent.ScheduledExecutorService keepalive;
    private volatile boolean closed;

    McpSubscriptions() {
        this(KEEPALIVE_MILLIS, FILE_POLL_MILLIS);
    }

    /** Test seam: a short keepalive period. */
    McpSubscriptions(long keepaliveMillis) {
        this(keepaliveMillis, FILE_POLL_MILLIS);
    }

    /** Test seam: short keepalive and file-poll periods. */
    McpSubscriptions(long keepaliveMillis, long filePollMillis) {
        this.keepaliveMillis = keepaliveMillis;
        this.filePollMillis = filePollMillis;
    }

    void subscribe(String uri) {
        subscribed.add(uri);
    }

    void unsubscribe(String uri) {
        subscribed.remove(uri);
        watched.remove(uri);
    }

    /**
     * Subscribes an outline instance ({@code nmox://outline/{file}}) that
     * FOLLOWS its file on disk (v2.84.0): the file must be a regular file
     * inside {@code root} (a path that escapes is refused, never read);
     * at most {@link #MAX_FILE_WATCHES}. Returns null on success, else
     * the refusal — "not found" shapes for the protocol's -32002, the
     * cap for its -32602.
     */
    String subscribeFile(String uri, java.io.File root, String file) {
        if (root == null || file == null || file.isBlank()) {
            return "not found: no aimed project";
        }
        java.nio.file.Path base = root.toPath().toAbsolutePath().normalize();
        java.nio.file.Path target = base.resolve(file).toAbsolutePath().normalize();
        if (!target.startsWith(base) || !java.nio.file.Files.isRegularFile(target)) {
            return "not found: " + file;
        }
        if (!watched.containsKey(uri) && watched.size() >= MAX_FILE_WATCHES) {
            return "capped: at most " + MAX_FILE_WATCHES + " file subscriptions";
        }
        try {
            watched.put(uri, new Watched(target,
                    java.nio.file.Files.getLastModifiedTime(target).toMillis(), java.nio.file.Files.size(target)));
        } catch (IOException gone) {
            return "not found: " + file;
        }
        subscribed.add(uri);
        if (filePoll == null) {
            synchronized (this) {
                if (filePoll == null && !closed) {
                    filePoll = Executors.newSingleThreadScheduledExecutor(
                            r -> Threads.daemon(r, "nmox-agent-port-files"));
                    filePoll.scheduleAtFixedRate(this::pollFiles, filePollMillis, filePollMillis, TimeUnit.MILLISECONDS);
                }
            }
        }
        return null;
    }

    int watchedFiles() {
        return watched.size();
    }

    /** One poll: a file whose stamp moved (or that vanished) announces its URI; a vanished file announces once and is dropped. */
    void pollFiles() {
        for (java.util.Map.Entry<String, Watched> e : watched.entrySet()) {
            Watched w = e.getValue();
            try {
                long m = java.nio.file.Files.getLastModifiedTime(w.path).toMillis();
                long s = java.nio.file.Files.size(w.path);
                if (m != w.mtime || s != w.size) {
                    w.mtime = m;
                    w.size = s;
                    updated(e.getKey());
                }
            } catch (IOException vanished) {
                watched.remove(e.getKey());
                updated(e.getKey());
                subscribed.remove(e.getKey());
            }
        }
    }

    boolean isSubscribed(String uri) {
        return subscribed.contains(uri);
    }

    Set<String> subscribed() {
        return Set.copyOf(subscribed);
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

    /** The SSE frame for an updated resource — the whole of what a client learns. */
    static String frame(String uri) {
        JSONObject msg = new JSONObject()
                .put("jsonrpc", "2.0")
                .put("method", "notifications/resources/updated")
                .put("params", new JSONObject().put("uri", uri));
        return "event: message\ndata: " + msg + "\n\n";
    }

    /** The SSE frame for one log message: level, logger, the line as data. */
    static String logFrame(String level, String logger, String data) {
        JSONObject msg = new JSONObject()
                .put("jsonrpc", "2.0")
                .put("method", "notifications/message")
                .put("params", new JSONObject().put("level", level).put("logger", logger).put("data", data));
        return "event: message\ndata: " + msg + "\n\n";
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

    int attachedCount() {
        return sinks.size();
    }

    /**
     * Announces that {@code uris} changed: every attached client gets a
     * frame for each URI it subscribed to. Returns at once — each
     * stream's writer does the work.
     */
    void updated(String... uris) {
        StringBuilder frames = new StringBuilder();
        for (String u : uris) {
            if (subscribed.contains(u)) {
                frames.append(frame(u));
            }
        }
        if (frames.length() == 0 || sinks.isEmpty()) {
            return;
        }
        byte[] bytes = frames.toString().getBytes(StandardCharsets.UTF_8);
        for (Sink s : sinks) {
            s.submit(bytes);
        }
    }

    /**
     * A log line for every attached stream, if {@code level} reaches the
     * set one. Bounded per stream the honest way: a build printing
     * faster than a client reads never grows that stream's backlog past
     * {@link #MAX_PENDING} — the overflow is counted and announced as
     * ONE line when the stream moves again, never silently lost. A line
     * is pending until it is WRITTEN, so a client that stops reading
     * holds its count at the cap.
     */
    void log(String level, String logger, String data) {
        int i = LEVELS.indexOf(level);
        if (i < 0 || i < this.level || sinks.isEmpty()) {
            return;
        }
        byte[] line = logFrame(level, logger, data).getBytes(StandardCharsets.UTF_8);
        for (Sink s : sinks) {
            if (s.pending.get() >= MAX_PENDING) {
                s.dropped.incrementAndGet();
                continue;
            }
            s.pending.incrementAndGet();
            try {
                s.writer.execute(() -> {
                    try {
                        int lost = s.dropped.getAndSet(0);
                        if (lost > 0) {
                            s.out.write(logFrame("warning", "agent-port",
                                    lost + " log lines dropped — the stream fell behind").getBytes(StandardCharsets.UTF_8));
                        }
                        s.out.write(line);
                        s.out.flush();
                    } catch (IOException gone) {
                        drop(s);
                    } finally {
                        s.pending.decrementAndGet();
                    }
                });
            } catch (java.util.concurrent.RejectedExecutionException alreadyDropped) {
                s.pending.decrementAndGet();
            }
        }
    }

    /** One keepalive pass: a comment to every stream that is not already known stuck; the dead are dropped. */
    void keepalive() {
        if (closed) {
            return;
        }
        byte[] bytes = KEEPALIVE.getBytes(StandardCharsets.UTF_8);
        for (Sink s : sinks) {
            if (s.pending.get() < MAX_PENDING) {
                s.submit(bytes);
            }
        }
    }

    /** Test barrier: every queued write on every stream has run. */
    void awaitIdle() throws InterruptedException {
        for (Sink s : sinks) {
            try {
                s.writer.submit(() -> { }).get(5, TimeUnit.SECONDS);
            } catch (java.util.concurrent.RejectedExecutionException dropped) {
                // dropped meanwhile: nothing left to wait for
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private void drop(Sink s) {
        if (sinks.remove(s)) {
            // shutdown, not shutdownNow: drop() runs INSIDE the sink's own
            // writer task, and shutdownNow would discard the frames queued
            // behind it — including a test barrier's no-op, whose future then
            // never completes (seen as a 5 s awaitIdle timeout under load).
            // Queued writes to the closed stream fail harmlessly into a
            // second drop that finds nothing to remove
            s.writer.shutdown();
            try {
                s.out.close();
            } catch (IOException ignored) {
                // already gone
            }
            s.onClose.run();
        }
    }

    /** Closes every stream and stops the keepalive — the port is stopping. */
    void close() {
        closed = true;
        java.util.concurrent.ScheduledExecutorService k = keepalive;
        if (k != null) {
            k.shutdownNow();
        }
        java.util.concurrent.ScheduledExecutorService f = filePoll;
        if (f != null) {
            f.shutdownNow();
        }
        watched.clear();
        for (Sink s : new ArrayList<>(sinks)) {
            drop(s);
        }
    }
}
