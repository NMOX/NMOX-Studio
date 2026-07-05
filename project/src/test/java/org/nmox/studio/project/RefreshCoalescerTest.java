package org.nmox.studio.project;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Workbench's anti-storm guarantee: a burst of window-registry events
 * (PROP_OPENED / PROP_ACTIVATED — ~10 fire in a tight run as the suite tabs
 * open at startup) must collapse to a single panel refresh, not one refresh
 * per event. Each refresh rebuilds the whole panel and spawns a background
 * detection task per row; running it N times per startup is what let the burst
 * compound into an EDT-starving post storm once v1.33.1 removed the blocking
 * $HOME walk that used to mask it.
 *
 * <p>The dispatcher is injected so the test drives scheduling deterministically
 * instead of racing the real EDT.
 */
class RefreshCoalescerTest {

    @Test
    @DisplayName("a burst of N requests before the refresh runs collapses to ONE refresh")
    void burstCollapsesToOne() {
        AtomicInteger refreshes = new AtomicInteger();
        Deque<Runnable> queue = new ArrayDeque<>();
        // dispatcher that defers (like invokeLater): nothing runs until drained
        RefreshCoalescer c = new RefreshCoalescer(refreshes::incrementAndGet, queue::add);

        // simulate the startup storm: hundreds of registry events arrive before
        // the EDT gets a chance to run the queued refresh
        for (int i = 0; i < 500; i++) {
            c.request();
        }

        assertThat(queue).as("only one refresh was ever scheduled").hasSize(1);

        // drain the EDT: the single refresh runs
        drain(queue);
        assertThat(refreshes.get()).as("500 requests cost exactly one refresh").isEqualTo(1);
    }

    @Test
    @DisplayName("a request arriving DURING a refresh schedules exactly one more")
    void requestDuringRefreshSchedulesOneMore() {
        Deque<Runnable> queue = new ArrayDeque<>();
        AtomicInteger refreshes = new AtomicInteger();
        RefreshCoalescer[] holder = new RefreshCoalescer[1];
        RefreshCoalescer c = new RefreshCoalescer(() -> {
            refreshes.incrementAndGet();
            // a registry event lands while we are mid-refresh — must not be
            // swallowed; it should queue a fresh run (the coalescer clears its
            // pending flag before running the body)
            if (refreshes.get() == 1) {
                holder[0].request();
            }
        }, queue::add);
        holder[0] = c;

        c.request();
        drain(queue);

        // one initial + one re-requested mid-refresh = two total, then quiescent
        assertThat(refreshes.get()).isEqualTo(2);
        assertThat(queue).as("no further refreshes pending").isEmpty();
    }

    @Test
    @DisplayName("sequential (non-overlapping) requests each run once")
    void sequentialRequestsEachRun() {
        Deque<Runnable> queue = new ArrayDeque<>();
        AtomicInteger refreshes = new AtomicInteger();
        RefreshCoalescer c = new RefreshCoalescer(refreshes::incrementAndGet, queue::add);

        c.request();
        drain(queue);
        c.request();
        drain(queue);

        assertThat(refreshes.get()).as("two well-separated requests run twice").isEqualTo(2);
    }

    private static void drain(Deque<Runnable> queue) {
        while (!queue.isEmpty()) {
            queue.poll().run();
        }
    }
}
