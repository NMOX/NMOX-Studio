package org.nmox.studio.ui.browser;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Browser's first-page wait belongs to the JVM, not to the tab (ledger 101).
 *
 * <p>The clock is an argument, so these are behaviours rather than constants —
 * the deadline is driven forward and what a caller is handed is read back. That
 * distinction is not academic here: v2.182.0 shipped a release whose headline
 * was a behaviour, proved only by asserting the constants the code asks with,
 * and the behaviour was absent.
 */
class ShapingDeadlineTest {

    private static final long BUDGET_MS = 2500;

    private static long ms(long n) {
        return TimeUnit.MILLISECONDS.toNanos(n);
    }

    @Test
    @DisplayName("the FIRST caller is owed the whole budget")
    void theFirstCallerWaitsTheWholeBudget() {
        ShapingDeadline d = new ShapingDeadline(BUDGET_MS);
        assertThat(d.armed()).as("nothing is fixed until somebody asks").isFalse();
        assertThat(d.leftMs(ms(1_000))).isEqualTo(BUDGET_MS);
        assertThat(d.armed()).isTrue();
    }

    @Test
    @DisplayName("a SECOND open is owed only what is left — this is ledger 101 itself, where each open used to be owed the whole budget again")
    void aLaterOpenWaitsOnlyTheRemainder() {
        ShapingDeadline d = new ShapingDeadline(BUDGET_MS);
        long open = ms(1_000);
        assertThat(d.leftMs(open)).isEqualTo(BUDGET_MS);

        // the tab is closed and reopened two seconds later
        long reopen = open + ms(2_000);
        assertThat(d.leftMs(reopen))
                .as("500 ms of the one budget remain; the old shape would hand out 2500 again")
                .isEqualTo(500);
    }

    @Test
    @DisplayName("an open after the deadline waits NOT AT ALL, and says so as 0 — the value the caller must never pass on")
    void anOpenAfterTheDeadlineDoesNotWait() {
        ShapingDeadline d = new ShapingDeadline(BUDGET_MS);
        long open = ms(1_000);
        d.leftMs(open);

        assertThat(d.leftMs(open + ms(BUDGET_MS)))
                .as("exactly at the deadline is already expired")
                .isZero();
        assertThat(d.leftMs(open + ms(60_000))).isZero();
    }

    @Test
    @DisplayName("a live deadline NEVER answers 0 — because Task.waitFinished(0) is an infinite wait, so 0 must mean 'do not wait' and nothing else")
    void aLiveDeadlineNeverAnswersTheInfiniteWaitValue() {
        ShapingDeadline d = new ShapingDeadline(BUDGET_MS);
        long open = ms(1_000);
        d.leftMs(open);

        // walk the clock to within a nanosecond of the deadline: every reading
        // while time is still owed must be a real, positive bound
        for (long offsetNs : List.of(1L, 1_000L, ms(1) - 1, ms(1), ms(BUDGET_MS) - 1)) {
            assertThat(d.leftMs(open + offsetNs))
                    .as("owed time at +%d ns must not read as the infinite-wait value", offsetNs)
                    .isPositive();
        }
    }

    @Test
    @DisplayName("time never runs backwards for a caller: the remainder only shrinks")
    void theRemainderOnlyShrinks() {
        ShapingDeadline d = new ShapingDeadline(BUDGET_MS);
        long open = ms(1_000);
        long previous = Long.MAX_VALUE;
        for (long elapsed = 0; elapsed <= BUDGET_MS + 100; elapsed += 100) {
            long left = d.leftMs(open + ms(elapsed));
            assertThat(left).as("remainder at +%d ms", elapsed).isLessThanOrEqualTo(previous);
            previous = left;
        }
        assertThat(previous).as("and it ends at the do-not-wait value").isZero();
    }

    @Test
    @DisplayName("concurrent first opens agree on ONE deadline — two tabs racing must not each arm their own")
    void concurrentFirstOpensShareOneDeadline() throws Exception {
        ShapingDeadline d = new ShapingDeadline(BUDGET_MS);
        int racers = 8;
        CountDownLatch ready = new CountDownLatch(racers);
        CountDownLatch go = new CountDownLatch(1);
        AtomicLong disagreements = new AtomicLong();
        long[] answers = new long[racers];

        Thread[] threads = new Thread[racers];
        for (int i = 0; i < racers; i++) {
            int slot = i;
            // every racer reads the SAME clock value, so one shared deadline
            // means one shared answer; a per-caller deadline would also agree
            // here, which is why the decisive assertion is the one after
            threads[i] = new Thread(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    disagreements.incrementAndGet();
                    return;
                }
                answers[slot] = d.leftMs(ms(1_000));
            });
            threads[i].start();
        }
        ready.await(5, TimeUnit.SECONDS);
        go.countDown();
        for (Thread t : threads) {
            t.join(5_000);
        }

        assertThat(disagreements.get()).isZero();
        assertThat(answers).as("every racer is owed the same budget").containsOnly(BUDGET_MS);
        assertThat(d.leftMs(ms(1_000) + ms(2_000)))
                .as("and the race armed exactly one deadline: 500 ms remain, not 2500")
                .isEqualTo(500);
    }

    @Test
    @DisplayName("nanoTime's origin is arbitrary, so a deadline landing on the unarmed sentinel still counts as armed")
    void aDeadlineLandingOnZeroIsStillArmed() {
        ShapingDeadline d = new ShapingDeadline(BUDGET_MS);
        long now = -ms(BUDGET_MS); // now + budget == 0 exactly
        assertThat(d.leftMs(now)).isEqualTo(BUDGET_MS);
        assertThat(d.armed()).as("0 is the unarmed sentinel, so the deadline is nudged off it").isTrue();
        assertThat(d.leftMs(now + ms(2_000)))
                .as("and it does not silently re-arm for the next open")
                .isEqualTo(500);
    }

    @Test
    @DisplayName("a budget that would mean an unbounded wait is refused at construction, not discovered on the lane")
    void anUnboundedBudgetIsRefused() {
        assertThatThrownBy(() -> new ShapingDeadline(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unbounded");
        assertThatThrownBy(() -> new ShapingDeadline(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
