package org.nmox.studio.project;

import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openide.util.RequestProcessor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Workbench's toolchain detection must run off the caller's (EDT) thread.
 * On a fresh launch the aimed directory can be slow or permission-gated to
 * enumerate; the header refresh must return promptly and never block the EDT.
 *
 * <p>{@link ProjectExplorerTopComponent} is a JaCoCo-excluded TopComponent, so
 * the load-bearing behavior lives in {@link WorkbenchDetect} and is proven
 * here with a deliberately slow detection function.
 */
class WorkbenchDetectTest {

    private final RequestProcessor rp = new RequestProcessor("workbench-detect-test", 1, true);

    @Test
    @DisplayName("detectAsync returns before a slow detection completes")
    void detectAsyncDoesNotBlockCaller() throws Exception {
        CountDownLatch detectStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean detected = new AtomicBoolean(false);

        java.util.function.Function<File, List<String>> slow = dir -> {
            detectStarted.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            detected.set(true);
            return List.of("node");
        };

        long start = System.nanoTime();
        WorkbenchDetect.detectAsync(rp, new File("/anywhere"), slow, names -> { });
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).as("the call returns without waiting on detection").isLessThan(2_000);
        assertThat(detected.get()).as("detection has not run inline on the caller").isFalse();
        assertThat(detectStarted.await(3, TimeUnit.SECONDS))
                .as("detection was posted to the background RequestProcessor").isTrue();

        release.countDown();
    }

    @Test
    @DisplayName("detectAsync delivers the detected names to the apply callback")
    void detectAsyncDeliversResult() throws Exception {
        CountDownLatch applied = new CountDownLatch(1);
        AtomicReference<List<String>> got = new AtomicReference<>();

        WorkbenchDetect.detectAsync(rp, new File("/x"),
                dir -> List.of("rust", "node"),
                names -> {
                    got.set(names);
                    applied.countDown();
                });

        assertThat(applied.await(3, TimeUnit.SECONDS)).as("apply ran").isTrue();
        assertThat(got.get()).containsExactly("rust", "node");
    }

    @Test
    @DisplayName("the detection runs on a background thread, not the caller's")
    void detectionRunsOffCallerThread() throws Exception {
        Thread caller = Thread.currentThread();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Thread> ran = new AtomicReference<>();

        WorkbenchDetect.detectAsync(rp, new File("/y"),
                dir -> {
                    ran.set(Thread.currentThread());
                    return List.of();
                },
                names -> done.countDown());

        assertThat(done.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(ran.get()).as("detection thread differs from the caller").isNotSameAs(caller);
    }

    @Test
    @DisplayName("A wedged detection on one lane doesn't starve a fast one (ledger 61)")
    void oneWedgedDetectionDoesNotStarveOthers() throws Exception {
        // the production detector has 4 lanes; a dir stuck in uninterruptible
        // I/O must not block every other row's detection
        RequestProcessor multi = new RequestProcessor("detect-widen-test", 4, true);
        CountDownLatch wedgedStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch fastApplied = new CountDownLatch(1);

        // lane 1: a dir that never returns until released (the hung mount)
        WorkbenchDetect.detectAsync(multi, new File("/hung"), dir -> {
            wedgedStarted.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return List.of();
        }, names -> { });

        assertThat(wedgedStarted.await(3, TimeUnit.SECONDS)).isTrue();

        // lane 2: a fast dir — on a single-thread RP it would queue behind the
        // wedged one forever; with 4 lanes it completes now
        WorkbenchDetect.detectAsync(multi, new File("/fast"),
                dir -> List.of("node"), names -> fastApplied.countDown());

        assertThat(fastApplied.await(3, TimeUnit.SECONDS))
                .as("the fast detection completed despite the wedged lane").isTrue();

        release.countDown();
    }
}
