package org.nmox.studio.rack.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Merges bursts into single dispatches: any number of {@link #offer}s
 * inside the window becomes exactly ONE downstream call carrying every
 * distinct item, delivered on the coalescer's own background thread
 * once the window (measured from the burst's first offer) closes.
 * A kit writing ten manifests must produce one re-sync, not ten —
 * the storm law every new listener reaction in the rack obeys.
 */
public final class Coalescer<T> {

    private final long windowMs;
    private final Consumer<List<T>> downstream;
    private final ScheduledExecutorService scheduler;
    private final LinkedHashSet<T> pending = new LinkedHashSet<>();
    private boolean flushScheduled;

    public Coalescer(long windowMs, Consumer<List<T>> downstream) {
        this.windowMs = Math.max(0, windowMs);
        this.downstream = downstream;
        ScheduledThreadPoolExecutor stpe = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "nmox-coalescer");
            t.setDaemon(true);
            return t;
        });
        stpe.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        this.scheduler = stpe;
    }

    /** Adds items to the burst; the first offer of a burst arms the flush. */
    public void offer(Collection<? extends T> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        synchronized (pending) {
            pending.addAll(items);
            if (flushScheduled) {
                return;
            }
            flushScheduled = true;
        }
        try {
            scheduler.schedule(this::flush, windowMs, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // closed mid-offer: the pending batch dies with the coalescer
        }
    }

    private void flush() {
        List<T> batch;
        synchronized (pending) {
            batch = new ArrayList<>(pending);
            pending.clear();
            flushScheduled = false;
        }
        if (!batch.isEmpty()) {
            downstream.accept(batch);
        }
    }

    /** Stops the flush thread; pending items are dropped. */
    public void close() {
        scheduler.shutdownNow();
        synchronized (pending) {
            pending.clear();
            flushScheduled = false;
        }
    }
}
