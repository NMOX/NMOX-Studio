package org.nmox.studio.apiclient.api;

import java.io.File;

/**
 * A 1.5 s stat-poll over one file — {@code .nmoxapi.json} — so edits
 * made outside the studio (hand edit, git checkout, another tool) are
 * noticed. The first tick primes the baseline and fires nothing; after
 * that, each mtime+size change fires exactly once, on the pulse's own
 * daemon thread (never the EDT — callers marshal themselves).
 *
 * <p>{@link #tick()} is synchronous so tests drive it deterministically;
 * {@link #start} merely loops it.
 */
public final class WorkspaceFilePulse {

    /** Called on the pulse thread; -1/-1 means the file is gone. */
    public interface Sink {
        void fileChanged(long mtime, long size);
    }

    public static final long DEFAULT_INTERVAL_MS = 1500;

    private final File file;
    private final Sink sink;
    private long lastMtime;
    private long lastSize;
    private boolean primed;

    private volatile boolean running;
    private Thread thread;

    public WorkspaceFilePulse(File file, Sink sink) {
        this.file = file;
        this.sink = sink;
    }

    public synchronized void start(long intervalMs) {
        if (running) {
            return;
        }
        running = true;
        long interval = Math.max(200, intervalMs);
        thread = new Thread(() -> {
            while (running) {
                tick();
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException stopped) {
                    return;
                }
            }
        }, "nmox-api-pulse");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    /** One synchronous poll: fire when the stamp moved since last tick. */
    void tick() {
        long mtime = file.isFile() ? file.lastModified() : -1;
        long size = file.isFile() ? file.length() : -1;
        if (!primed) {
            primed = true;
            lastMtime = mtime;
            lastSize = size;
            return;
        }
        boolean moved = mtime != lastMtime || size != lastSize;
        lastMtime = mtime;
        lastSize = size;
        if (moved) {
            sink.fileChanged(mtime, size);
        }
    }
}
