package org.nmox.studio.dbstudio.engine;

/**
 * The cross-thread cancel seam for {@link MongoBackend} (debt ledger
 * 10) — the MongoDB analogue of {@code JdbcCore.CancelHook}.
 *
 * <p>The sync driver has no per-operation cancel handle, but since 5.0
 * its blocking socket I/O is interruptible: interrupting the thread
 * inside {@code runCommand} makes the driver close that pooled
 * connection and throw {@code MongoInterruptedException}, while the
 * {@code MongoClient} and its other connections stay usable. So a
 * cancel records the request and interrupts the one thread running a
 * console command — never any other thread:
 * <ul>
 *   <li>{@link #cancel()} only interrupts between {@link #begin()} and
 *       {@link #end()} (or {@link #stopInterrupting()}); when idle it
 *       does nothing, so a late click cannot interrupt the pool thread
 *       that later runs someone else's work;</li>
 *   <li>{@link #end()} swallows an interrupt this latch delivered, so
 *       the executing thread leaves the run clean;</li>
 *   <li>{@link #stopInterrupting()} ends interruptibility early, which
 *       is what lets the backend still send {@code killCursors} after a
 *       cancel interrupted the command.</li>
 * </ul>
 * All state is guarded by one small lock, never the backend's monitor
 * (which the running command holds).
 */
final class MongoCancel {

    private final Object lock = new Object();
    private Thread running; // guarded by lock
    private boolean requested; // guarded by lock

    /** Marks the calling thread as the one running a command; clears any old request. */
    void begin() {
        synchronized (lock) {
            requested = false;
            running = Thread.currentThread();
        }
    }

    /** True once {@link #cancel()} landed on the current run. */
    boolean requested() {
        synchronized (lock) {
            return requested;
        }
    }

    /** Requests cancellation of the running command; a no-op when idle. Any thread. */
    void cancel() {
        synchronized (lock) {
            if (running != null) {
                requested = true;
                running.interrupt();
            }
        }
    }

    /**
     * Ends interruptibility for the rest of the run (cancel still counts
     * as requested) and swallows an interrupt already delivered, so the
     * calling thread can make one more server call.
     */
    void stopInterrupting() {
        synchronized (lock) {
            if (running == Thread.currentThread()) {
                running = null;
            }
            if (requested) {
                Thread.interrupted();
            }
        }
    }

    /** Ends the run: no further interrupts, and a delivered one is swallowed. */
    void end() {
        stopInterrupting();
    }
}
