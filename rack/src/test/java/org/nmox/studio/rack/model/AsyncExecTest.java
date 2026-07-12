package org.nmox.studio.rack.model;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 41: exec()'s dotenv reads and the fork itself ride a lane, not
 * the caller's thread — while the OBSERVABLE contract stays synchronous:
 * the handle exists before exec returns (gates can't double-launch), a
 * second call stops the first, stop-before-spawn means no process ever
 * exists, and the exit callback fires exactly once in every phase. The
 * lane seam makes each phase deterministic; the queue-and-step tests
 * fail if the spawn moves back onto the caller.
 */
class AsyncExecTest {

    /** Captures lane tasks instead of running them, so tests step phases. */
    private final List<Runnable> lane = new CopyOnWriteArrayList<>();

    @AfterEach
    void restoreLane() {
        // back to a real async lane so later tests in the same JVM behave
        RackDevice.execLane = task ->
                org.openide.util.RequestProcessor.getDefault().post(task);
    }

    private TestDevice device() {
        RackDevice.execLane = lane::add;
        return new TestDevice();
    }

    private static final class TestDevice extends RackDevice {

        TestDevice() {
            super("test.exec", "EXEC", "", Color.GRAY, 1);
        }

        void run(List<String> cmd, java.util.function.Consumer<String> onLine,
                java.util.function.IntConsumer onExit) {
            exec(cmd, onLine, onExit);
        }

        boolean running() {
            return isProcessRunning();
        }

        void stop() {
            stopProcess();
        }
    }

    @Test
    @DisplayName("exec returns with a live handle BEFORE anything spawns — the fork is on the lane, not the caller")
    void execIsAsynchronousButObservablySynchronous() {
        TestDevice d = device();
        d.run(List.of("echo", "never-runs-in-this-test"), l -> { }, c -> { });

        // the caller's thread did no file IO and no fork: the work is queued
        assertThat(lane).as("the spawn work rides the lane").hasSize(1);
        // yet the contract is already observable: the device is running
        assertThat(d.running())
                .as("isProcessRunning answers true immediately — enableGate cannot double-launch")
                .isTrue();
        assertThat(d.isLive()).isTrue();
    }

    @Test
    @DisplayName("stop before the spawn: no process ever exists, and the exit contract still fires (-1)")
    void stopBeforeSpawnNeverForks() {
        TestDevice d = device();
        int[] exit = new int[]{Integer.MIN_VALUE};
        d.run(List.of("sleep", "60"), l -> { }, c -> exit[0] = c);

        d.stop();                       // user hits STOP before the lane ran
        assertThat(d.running()).isFalse();

        lane.get(0).run();              // now the lane task executes
        assertThat(exit[0])
                .as("a cancelled launch still reports completion")
                .isEqualTo(-1);
        assertThat(d.running()).isFalse();
    }

    @Test
    @DisplayName("a second exec cancels the first pending run — one process per device, unchanged")
    void secondExecCancelsFirstPending() {
        TestDevice d = device();
        int[] firstExit = new int[]{Integer.MIN_VALUE};
        d.run(List.of("sleep", "60"), l -> { }, c -> firstExit[0] = c);
        d.run(List.of("echo", "second"), l -> { }, c -> { });

        assertThat(lane).hasSize(2);
        lane.get(0).run();              // first task: sees cancelled, skips
        assertThat(firstExit[0]).isEqualTo(-1);
        assertThat(d.running()).as("the second run's pending handle survives").isTrue();
    }

    @Test
    @DisplayName("exec on a disposed device spawns nothing and still answers the caller")
    void disposedExecAnswers() {
        TestDevice d = device();
        d.dispose();
        int[] exit = new int[]{Integer.MIN_VALUE};
        d.run(List.of("echo", "no"), l -> { }, c -> exit[0] = c);
        assertThat(lane).as("nothing queued for a deleted device").isEmpty();
        assertThat(exit[0]).as("the caller is not left waiting forever").isEqualTo(-1);
    }

    @Test
    @DisplayName("panic on a pending run returns within its bound instead of hanging")
    void panicOnPendingIsBounded() {
        TestDevice d = device();
        d.run(List.of("sleep", "60"), l -> { }, c -> { });
        // the lane task never runs (simulating a fork stuck on a wedged
        // mount); panic must still return within its grace period
        long start = System.nanoTime();
        d.panic();
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertThat(ms).as("panic stays bounded on an unspawned run").isLessThan(5_000);
        assertThat(d.running()).isFalse();
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS,
            disabledReason = "spawns real POSIX echo/sleep to prove the live phases")
    @DisplayName("the happy path end-to-end: spawn on the lane, stream, exit exactly once")
    void happyPathStreamsAndExits() throws Exception {
        TestDevice d = device();
        List<String> lines = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        int[] exits = new int[1];
        d.run(List.of("echo", "hello-async"), lines::add, c -> {
            exits[0]++;
            done.countDown();
        });
        lane.get(0).run();              // real spawn happens on "the lane"
        assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();
        assertThat(lines).anyMatch(l -> l.contains("hello-async"));
        assertThat(exits[0]).as("exit fires exactly once").isEqualTo(1);
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS,
            disabledReason = "spawns a real POSIX sleep to exercise kill-during-live")
    @DisplayName("stop after the spawn kills the live process — and exit still fires once")
    void stopAfterSpawnKills() throws Exception {
        TestDevice d = device();
        CountDownLatch done = new CountDownLatch(1);
        d.run(List.of("sleep", "30"), l -> { }, c -> done.countDown());
        lane.get(0).run();
        assertThat(d.running()).isTrue();
        d.stop();
        assertThat(done.await(15, TimeUnit.SECONDS))
                .as("killing the live run fires its exit").isTrue();
        assertThat(d.running()).isFalse();
    }
}
