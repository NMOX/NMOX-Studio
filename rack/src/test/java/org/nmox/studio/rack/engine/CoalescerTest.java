package org.nmox.studio.rack.engine;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The storm law's mechanism: any burst inside the window is ONE
 * downstream dispatch. A kit writing ten manifests must produce one
 * re-sync per device, not ten.
 */
class CoalescerTest {

    @Test
    @DisplayName("STORM LAW: ten offers inside the window dispatch exactly once, all items")
    void burstDispatchesOnce() throws Exception {
        List<List<Path>> batches = Collections.synchronizedList(new java.util.ArrayList<>());
        CountDownLatch dispatched = new CountDownLatch(1);
        Coalescer<Path> coalescer = new Coalescer<>(250, batch -> {
            batches.add(batch);
            dispatched.countDown();
        });
        try {
            for (int i = 0; i < 10; i++) {
                coalescer.offer(List.of(Path.of("/p/file" + i + ".json")));
            }
            assertThat(dispatched.await(5, TimeUnit.SECONDS)).isTrue();
            // wait out another full window: a leaky second dispatch would land now
            Thread.sleep(400);
            assertThat(batches).as("one dispatch per burst").hasSize(1);
            assertThat(batches.get(0)).hasSize(10);
        } finally {
            coalescer.close();
        }
    }

    @Test
    @DisplayName("repeated items inside a burst are deduplicated, order kept")
    void dedupesWithinBurst() throws Exception {
        List<List<Path>> batches = Collections.synchronizedList(new java.util.ArrayList<>());
        CountDownLatch dispatched = new CountDownLatch(1);
        Coalescer<Path> coalescer = new Coalescer<>(150, batch -> {
            batches.add(batch);
            dispatched.countDown();
        });
        try {
            Path first = Path.of("/p/package.json");
            Path second = Path.of("/p/composer.json");
            coalescer.offer(List.of(first));
            coalescer.offer(List.of(first, second));
            coalescer.offer(List.of(first));
            assertThat(dispatched.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(batches.get(0)).containsExactly(first, second);
        } finally {
            coalescer.close();
        }
    }

    @Test
    @DisplayName("a second burst after the window closes dispatches again")
    void secondBurstDispatchesAgain() throws Exception {
        List<List<Path>> batches = Collections.synchronizedList(new java.util.ArrayList<>());
        // Await the FIRST dispatch instead of sleeping past the window: on a
        // loaded CI runner the scheduler thread can lag arbitrarily, and a
        // sleep-timed second offer then merges into the still-open first
        // burst — correct coalescing, broken test (flaked on macOS CI,
        // 2026-08-03). flush() clears flushScheduled BEFORE calling
        // downstream, so once the latch trips the next offer arms a new
        // window deterministically.
        CountDownLatch first = new CountDownLatch(1);
        CountDownLatch two = new CountDownLatch(2);
        Coalescer<Path> coalescer = new Coalescer<>(100, batch -> {
            batches.add(batch);
            first.countDown();
            two.countDown();
        });
        try {
            coalescer.offer(List.of(Path.of("/p/a.json")));
            assertThat(first.await(5, TimeUnit.SECONDS))
                    .as("first window flushes").isTrue();
            coalescer.offer(List.of(Path.of("/p/b.json")));
            assertThat(two.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(batches).hasSize(2);
        } finally {
            coalescer.close();
        }
    }

    @Test
    @DisplayName("null and empty offers are ignored")
    void ignoresEmpty() throws Exception {
        List<List<Path>> batches = Collections.synchronizedList(new java.util.ArrayList<>());
        Coalescer<Path> coalescer = new Coalescer<>(50, batches::add);
        try {
            coalescer.offer(null);
            coalescer.offer(List.of());
            Thread.sleep(200);
            assertThat(batches).isEmpty();
        } finally {
            coalescer.close();
        }
    }

    @Test
    @DisplayName("close drops the pending batch instead of dispatching it")
    void closeDropsPending() throws Exception {
        List<List<Path>> batches = Collections.synchronizedList(new java.util.ArrayList<>());
        Coalescer<Path> coalescer = new Coalescer<>(300, batches::add);
        coalescer.offer(List.of(Path.of("/p/a.json")));
        coalescer.close();
        Thread.sleep(450);
        assertThat(batches).isEmpty();
        // offering after close must not throw
        coalescer.offer(List.of(Path.of("/p/b.json")));
    }
}
