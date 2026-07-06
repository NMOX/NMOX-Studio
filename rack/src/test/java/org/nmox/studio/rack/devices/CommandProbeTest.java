package org.nmox.studio.rack.devices;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The quiet status probe, held to three promises: line-by-line delivery
 * with the exit code after, a throwing line consumer that can neither
 * skip the remaining lines nor swallow onExit, and a missing binary
 * that still reports its failure code. (The 15s bound itself lives in
 * ProcessSupport.runBounded, pinned by core's tests.)
 */
@DisabledOnOs(OS.WINDOWS) // relies on POSIX printf
class CommandProbeTest {

    @Test
    @DisplayName("Delivers output line by line, then the exit code")
    void deliversLinesThenExit() throws Exception {
        List<String> lines = new CopyOnWriteArrayList<>();
        AtomicInteger exit = new AtomicInteger(Integer.MIN_VALUE);
        CountDownLatch done = new CountDownLatch(1);

        CommandProbe.run(null, List.of("printf", "one\\ntwo\\n"),
                lines::add, code -> {
                    exit.set(code);
                    done.countDown();
                });

        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(lines).containsExactly("one", "two");
        assertThat(exit.get()).isZero();
    }

    @Test
    @DisplayName("A throwing line consumer skips neither the later lines nor onExit")
    void throwingConsumerCannotSkipOnExit() throws Exception {
        List<String> delivered = new CopyOnWriteArrayList<>();
        AtomicInteger exitCalls = new AtomicInteger();
        AtomicInteger exit = new AtomicInteger(Integer.MIN_VALUE);
        CountDownLatch done = new CountDownLatch(1);

        CommandProbe.run(null, List.of("printf", "boom\\nsafe\\n"),
                line -> {
                    delivered.add(line);
                    if ("boom".equals(line)) {
                        throw new IllegalStateException("consumer bug");
                    }
                }, code -> {
                    exit.set(code);
                    exitCalls.incrementAndGet();
                    done.countDown();
                });

        assertThat(done.await(10, TimeUnit.SECONDS))
                .as("onExit fires despite the consumer exception").isTrue();
        assertThat(delivered).as("the line after the throw still arrives")
                .containsExactly("boom", "safe");
        assertThat(exit.get()).isZero();
        assertThat(exitCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("A missing binary reports -1, exactly once")
    void missingBinaryReportsFailureCode() throws Exception {
        AtomicInteger exit = new AtomicInteger(Integer.MIN_VALUE);
        CountDownLatch done = new CountDownLatch(1);

        CommandProbe.run(null, List.of("nmox-definitely-not-a-real-binary-xyz"),
                line -> {
                }, code -> {
                    exit.set(code);
                    done.countDown();
                });

        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(exit.get()).isEqualTo(-1);
    }
}
