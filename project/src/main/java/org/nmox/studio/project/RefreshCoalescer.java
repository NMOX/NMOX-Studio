package org.nmox.studio.project;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Collapses a burst of refresh requests into a single deferred run.
 *
 * <p>The Workbench reacts to window-registry events (PROP_OPENED /
 * PROP_ACTIVATED). At startup the platform opens and activates ~10
 * TopComponents in a tight run, so those events arrive in a dense burst.
 * Running a full panel refresh for every one of them — each refresh spawning a
 * background detection task per row that posts back to the EDT — is what let
 * the startup burst compound into an EDT-starving event-posting storm (the
 * v1.33.1 async refactor removed the blocking $HOME walk that used to mask it
 * by hanging on the very first reaction).
 *
 * <p>The coalescer keeps at most one refresh in flight: a request that arrives
 * while one is already pending is dropped, because the pending refresh will
 * already read the latest state when it runs. N requests therefore cost one
 * refresh, not N.
 *
 * <p>The dispatch seam is injectable so a headless test can drive it
 * synchronously and assert the bound; production posts to the EDT.
 */
final class RefreshCoalescer {

    /** How a coalesced refresh is scheduled. Production: SwingUtilities::invokeLater. */
    interface Dispatcher {
        void dispatch(Runnable r);
    }

    private final Runnable refresh;
    private final Dispatcher dispatcher;
    private final AtomicBoolean pending = new AtomicBoolean(false);

    /** Production constructor: coalesced refreshes run on the EDT. */
    RefreshCoalescer(Runnable refresh) {
        this(refresh, javax.swing.SwingUtilities::invokeLater);
    }

    RefreshCoalescer(Runnable refresh, Dispatcher dispatcher) {
        this.refresh = refresh;
        this.dispatcher = dispatcher;
    }

    /**
     * Requests a refresh. If one is already queued this is a no-op, so a burst
     * of requests collapses to a single run.
     */
    void request() {
        if (pending.compareAndSet(false, true)) {
            dispatcher.dispatch(() -> {
                // clear BEFORE running: a request that lands during the refresh
                // must queue a fresh run rather than be swallowed by this one.
                pending.set(false);
                refresh.run();
            });
        }
    }
}
