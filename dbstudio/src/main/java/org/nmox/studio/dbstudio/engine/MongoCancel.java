package org.nmox.studio.dbstudio.engine;

/**
 * The cross-thread cancel bookkeeping for {@link MongoBackend} (debt
 * ledger 10), the MongoDB analogue of {@code JdbcCore.CancelHook}.
 *
 * <p>It records that a cancel was requested for the command now running,
 * which stops cursor paging before the next {@code getMore} and turns the
 * resulting error into a "Cancelled" result. It can also interrupt the one
 * thread running a console command. The interrupt is NOT what stops a
 * command blocked in a socket read (the 5.11 driver's read cannot be
 * interrupted, which was measured against mongo:7). That job belongs to
 * {@link MongoServerCancel}'s {@code killOp}. The interrupt covers the waits
 * that do respond to it: pool checkout and server selection. It never
 * reaches any other thread:
 * <ul>
 *   <li>{@link #request()} and {@link #interruptRunner()} act only
 *       between {@link #begin()} and {@link #end()} (or
 *       {@link #stopInterrupting()}); when idle they do nothing, so a late
 *       click cannot interrupt the pool thread that later runs other
 *       work;</li>
 *   <li>{@link #end()} swallows an interrupt this latch delivered, so
 *       the executing thread leaves the run clean;</li>
 *   <li>{@link #stopInterrupting()} ends interruptibility early, which
 *       is what lets the backend still send {@code killCursors} after a
 *       cancel.</li>
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

    /** True once a cancel landed on the current run. */
    boolean requested() {
        synchronized (lock) {
            return requested;
        }
    }

    /**
     * Records a cancel for the running command. Returns false (and records
     * nothing) when no command is running.
     */
    boolean request() {
        synchronized (lock) {
            if (running == null) {
                return false;
            }
            requested = true;
            return true;
        }
    }

    /** Interrupts the running thread if a cancel was requested and it is still interruptible. */
    void interruptRunner() {
        synchronized (lock) {
            if (running != null && requested) {
                running.interrupt();
            }
        }
    }

    /** {@link #request()} then {@link #interruptRunner()}; a no-op when idle. Any thread. */
    void cancel() {
        if (request()) {
            interruptRunner();
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
