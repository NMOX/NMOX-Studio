package org.nmox.studio.rack.engine;

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
 * The interactive-process capability, proven end to end against
 * {@code cat} — a program that echoes stdin to stdout — so the REPL's
 * type-in / read-out round trip is verified without any language
 * interpreter installed. This is the opposite guarantee to
 * CommandExecutor's: here stdin STAYS OPEN.
 */
@DisabledOnOs(OS.WINDOWS) // relies on the POSIX `cat`
class InteractiveProcessTest {

    @Test
    @DisplayName("A line sent to stdin comes back on stdout — the REPL round trip")
    void echoRoundTrip() throws Exception {
        List<String> out = new CopyOnWriteArrayList<>();
        CountDownLatch gotLine = new CountDownLatch(1);
        AtomicInteger exit = new AtomicInteger(Integer.MIN_VALUE);
        CountDownLatch exited = new CountDownLatch(1);

        InteractiveProcess repl = InteractiveProcess.start(
                List.of("cat"), null,
                line -> { out.add(line); gotLine.countDown(); },
                err -> { },
                code -> { exit.set(code); exited.countDown(); });

        assertThat(repl.isAlive()).isTrue();
        repl.send("hello repl");
        assertThat(gotLine.await(3, TimeUnit.SECONDS))
                .as("cat echoed the line back within 3s").isTrue();
        assertThat(out).contains("hello repl");

        repl.stop(); // closes stdin: cat sees EOF and exits 0
        assertThat(exited.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(exit.get()).isZero();
        assertThat(repl.isAlive()).isFalse();
    }

    @Test
    @DisplayName("Starting a missing binary throws so the device can speak human")
    void missingBinaryThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, () ->
                InteractiveProcess.start(
                        List.of("nmox-definitely-not-a-real-binary-xyz"), null,
                        l -> { }, e -> { }, c -> { }));
    }

    @Test
    @DisplayName("A throwing line consumer does not kill the pump — later lines still arrive")
    void throwingConsumerDoesNotKillThePump() throws Exception {
        List<String> out = new CopyOnWriteArrayList<>();
        CountDownLatch gotSecond = new CountDownLatch(1);

        InteractiveProcess repl = InteractiveProcess.start(
                List.of("cat"), null,
                line -> {
                    out.add(line);
                    if (line.contains("boom")) {
                        // the old pump died here: the pipe then filled, the
                        // interpreter blocked on write, and the REPL hung
                        throw new IllegalStateException("consumer bug");
                    }
                    if (line.contains("after")) {
                        gotSecond.countDown();
                    }
                },
                err -> { },
                code -> { });
        try {
            repl.send("boom");
            repl.send("after");
            assertThat(gotSecond.await(3, TimeUnit.SECONDS))
                    .as("the pump survives a consumer exception").isTrue();
            assertThat(out).contains("boom", "after");
        } finally {
            repl.stop();
        }
    }

    @Test
    @DisplayName("stop() escalates TERM to KILL, so a TERM-trapping REPL cannot orphan")
    void stopEscalatesToKillForTermTrappers() throws Exception {
        CountDownLatch exited = new CountDownLatch(1);
        // ignores both EOF (never reads stdin) and TERM (trapped): only the
        // KILL rung of the ladder can end it — exactly the orphan shape the
        // shutdown reaper must never leave behind
        InteractiveProcess repl = InteractiveProcess.start(
                List.of("bash", "-c", "trap '' TERM; while true; do sleep 0.2; done"),
                null, l -> { }, e -> { }, code -> exited.countDown());

        assertThat(repl.isAlive()).isTrue();
        long start = System.nanoTime();
        repl.stop(); // synchronous and bounded: ~2s worst case
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).as("stop stays bounded for the shutdown path")
                .isLessThan(5_000);
        assertThat(exited.await(5, TimeUnit.SECONDS))
                .as("the TERM-trapping child was force-killed").isTrue();
        assertThat(repl.isAlive()).isFalse();
    }
}
