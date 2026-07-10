package org.nmox.studio.infra.model;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.openide.util.RequestProcessor;

/**
 * The workspace autosave's off-EDT write lane (debt ledger #16).
 *
 * <p>The studio's Swing debounce timer stays the CLOCK; only the write
 * body rides here. One single-throughput {@link RequestProcessor} is
 * the whole design, because it buys three guarantees at once:
 *
 * <ul>
 *   <li><b>Serialized writes.</b> Throughput 1 means a queued write and
 *       a fresh debounce fire can never interleave two file writes —
 *       the module's general-purpose processor has throughput &gt; 1
 *       (or carries multi-second network work), which is why saves get
 *       their own named lane.</li>
 *   <li><b>Stamp atomicity.</b> The self-write stamp is recorded by the
 *       SAME task that performs the write, and the foreign-vs-own
 *       verdict rides {@link #classify}, which queues BEHIND every
 *       pending write+stamp pair. A pulse tick that races a landing
 *       write therefore always re-checks after the stamp exists — our
 *       own save can never masquerade as a foreign edit.</li>
 *   <li><b>Close flush.</b> {@link #flush} drains the lane so the last
 *       debounced edit is on disk before the studio tears down.</li>
 * </ul>
 *
 * <p>Callers snapshot the workspace to a JSON string ON the EDT (the
 * model is EDT-confined) and hand the lane a closure over that string
 * plus the already-bound target file — the lane never reads the model.
 */
public final class SaveLane {

    private final RequestProcessor rp;

    /** @param name the thread name, e.g. {@code "API Studio workspace saves"} */
    public SaveLane(String name) {
        this.rp = new RequestProcessor(name, 1);
    }

    /**
     * Queues a workspace write. The runnable must perform the write AND
     * record the self-write stamp — one task, so no observer scheduled
     * on this lane can ever see the write without its stamp.
     */
    public void save(Runnable write) {
        rp.post(write);
    }

    /**
     * Queues a foreign-vs-own verdict behind every pending write. By the
     * time the runnable executes, any write the caller's poll may have
     * raced has landed WITH its stamp recorded.
     */
    public void classify(Runnable verdict) {
        rp.post(verdict);
    }

    /**
     * Blocks until everything queued so far has landed — the close-time
     * flush. A synchronous wait on the EDT is deliberate and bounded:
     * the lane only ever carries millisecond-scale local file writes,
     * and a closing studio must not race its own last save (the write
     * has to complete before the component is torn down).
     *
     * @return false when the drain timed out; the write is not lost —
     *         the lane thread still finishes it, we just stop waiting
     */
    public boolean flush(long timeout, TimeUnit unit) {
        CountDownLatch drained = new CountDownLatch(1);
        rp.post(drained::countDown);
        try {
            return drained.await(timeout, unit);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
