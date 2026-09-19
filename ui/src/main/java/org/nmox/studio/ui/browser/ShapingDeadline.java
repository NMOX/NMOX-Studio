package org.nmox.studio.ui.browser;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One deadline for the Browser's first-page wait, shared by every open
 * (ledger 101).
 *
 * <p>The Browser's first page waits for complex-text shaping to install before
 * loading, because a page laid out before the switch keeps the old widths while
 * it paints with the new glyphs (v2.172.0). Shaping installs <b>once per
 * JVM</b> — {@code ComplexTextShaping.install()} is synchronized and memoizes
 * its outcome — so the wait's deadline belongs to the JVM, not to the tab.
 *
 * <p>It used to belong to the tab. Each open posted its own full-budget wait
 * onto a throughput-1 lane, so a second open could not begin its wait until the
 * first one's had elapsed. Where shaping is genuinely slow — an update-center
 * install whose conf lacks the attach flags falls back to the
 * {@code ShapingAttach} helper, on a 30 s leash — a second open showed a blank
 * pane for about five seconds and a third for seven and a half, and that delay
 * landed on exactly the page the user was waiting for.
 *
 * <p><b>Zero means do not wait, and callers must honour that literally.</b>
 * Read from the platform's own bytecode, {@code Task.waitFinished(0)} does not
 * return at once: it logs "infinite wait, again" and loops on an untimed
 * {@code Object.wait()}. Passing a remaining time that has reached zero would
 * turn a bounded wait into an unbounded one, on the lane holding the user's
 * first page. So an expired deadline returns {@code 0} and a live one always
 * returns at least {@code 1}.
 *
 * <p>Pure by construction: the clock is an argument, so every rule here is a
 * unit test rather than a sleep.
 */
final class ShapingDeadline {

    private final long budgetNs;

    /** Zero until the first {@link #leftMs} arms it; the instant the wait gives up. */
    private final AtomicLong deadlineNs = new AtomicLong();

    ShapingDeadline(long budgetMs) {
        if (budgetMs <= 0) {
            throw new IllegalArgumentException("a budget of " + budgetMs
                    + " ms would mean an unbounded wait, which is the defect this class exists to remove");
        }
        this.budgetNs = TimeUnit.MILLISECONDS.toNanos(budgetMs);
    }

    /**
     * Milliseconds still owed, arming the deadline on the first call.
     *
     * @param nowNs a reading of a monotonic clock, normally {@link System#nanoTime()}
     * @return {@code 0} when the deadline has passed — meaning <b>do not
     *         wait</b>, never "wait forever" — otherwise at least {@code 1}
     */
    long leftMs(long nowNs) {
        long deadline = deadlineNs.get();
        if (deadline == 0) {
            // nanoTime's origin is arbitrary, so the sum can be any long — and a
            // deadline of exactly zero would read as unarmed on every later call,
            // re-arming the wait per open, which is the defect itself
            long armed = nowNs + budgetNs;
            deadlineNs.compareAndSet(0, armed == 0 ? 1 : armed);
            deadline = deadlineNs.get(); // whichever caller won
        }
        long leftNs = deadline - nowNs;
        if (leftNs <= 0) {
            return 0;
        }
        // round UP: a sub-millisecond remainder is still a wait, and rounding it
        // down would hand back the value that means no bound at all
        return Math.max(1, TimeUnit.NANOSECONDS.toMillis(leftNs));
    }

    /** True once a first caller has fixed the deadline. */
    boolean armed() {
        return deadlineNs.get() != 0;
    }
}
