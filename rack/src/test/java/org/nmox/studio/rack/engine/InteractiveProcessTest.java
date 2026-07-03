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
}
