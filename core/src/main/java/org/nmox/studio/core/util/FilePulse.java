package org.nmox.studio.core.util;

import java.io.File;

/**
 * A 1.5 s stat-poll over one file so edits made outside the product
 * (hand edit, git checkout, another tool) are noticed. Born as API
 * Studio's {@code WorkspaceFilePulse} (v1.35 era); promoted to core in
 * v2.7.0 when the Task Board became its second consumer — every
 * per-project studio file wants exactly this shape, and a copy per
 * module is how the six studios drifted before the v1.35.0 sweep. The first tick primes the baseline and fires nothing; after
 * that, each mtime+size change fires exactly once, on the pulse's own
 * daemon thread (never the EDT — callers marshal themselves).
 *
 * <p>{@link #tick()} is synchronous so tests drive it deterministically;
 * {@link #start} merely loops it.
 */
public final class FilePulse {

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

    public FilePulse(File file, Sink sink) {
        this.file = file;
        this.sink = sink;
    }

    public synchronized void start(long intervalMs) {
        if (running) {
            return;
        }
        running = true;
        long interval = Math.max(200, intervalMs);
        // one long-lived sleep loop with its own start/stop lifecycle — a
        // dedicated daemon thread, not a shared-pool slot held for hours
        thread = new Thread(() -> {
            while (running) {
                tick();
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException stopped) {
                    return;
                }
            }
        }, "nmox-file-pulse");
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
