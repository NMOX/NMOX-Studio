package org.nmox.studio.rack.engine;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The interactive-process capability, proven end to end against
 * {@code cat} — a program that echoes stdin to stdout — so the REPL's
 * type-in / read-out round trip is verified without any language
 * interpreter installed. This is the opposite guarantee to
 * CommandExecutor's: here stdin STAYS OPEN.
 *
 * <p>The shutdown half asserts {@link InteractiveProcess#rungsTaken()} —
 * the ladder as an OUTCOME — rather than the wall clock it used to time.
 * Every remaining duration here is a one-sided leash on an event that must
 * happen (a latch await, a hang guard), never a measurement of how fast the
 * machine is.
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

    /**
     * A child that ignores EOF (it never reads stdin) and TERM (trapped), so
     * only the KILL rung can end it — exactly the orphan shape the shutdown
     * reaper must never leave behind.
     */
    private static InteractiveProcess termTrapper(CountDownLatch exited) throws Exception {
        return InteractiveProcess.start(
                List.of("bash", "-c", "trap '' TERM; while true; do sleep 0.2; done"),
                null, l -> { }, e -> { }, code -> exited.countDown());
    }

    @Test
    @DisplayName("stop() escalates TERM to KILL, so a TERM-trapping REPL cannot orphan")
    void stopEscalatesToKillForTermTrappers() throws Exception {
        CountDownLatch exited = new CountDownLatch(1);
        InteractiveProcess repl = termTrapper(exited);
        assertThat(repl.isAlive()).isTrue();

        // graces shortened so the rung the trapper cannot survive arrives at
        // once: the child never exits, so a short wait can only EXPIRE — the
        // ladder below is the same whatever the scheduler is doing. The old
        // shape asserted the elapsed WALL CLOCK of the shipped graces, which
        // measured this machine (7,449 ms under load against a 5,000 ms
        // bound; 3,188 ms alone) rather than the escalation.
        repl.gracesForTest(50, 50);
        repl.stop();

        assertThat(repl.rungsTaken())
                .as("EOF offered, ignored; TERM sent, trapped; KILL — in that order")
                .containsExactly(InteractiveProcess.Rung.EOF,
                        InteractiveProcess.Rung.TERM,
                        InteractiveProcess.Rung.KILL);
        assertThat(exited.await(10, TimeUnit.SECONDS))
                .as("the TERM-trapping child was force-killed").isTrue();
        assertThat(repl.isAlive()).isFalse();
    }

    @Test
    @DisplayName("A child that quits on EOF is never signalled — the ladder stops at its first rung")
    void stopStopsAtEofForAWellBehavedChild() throws Exception {
        CountDownLatch exited = new CountDownLatch(1);
        InteractiveProcess repl = InteractiveProcess.start(
                List.of("cat"), null, l -> { }, e -> { }, code -> exited.countDown());
        assertThat(repl.isAlive()).isTrue();

        // a generous EOF grace so "cat took the EOF" cannot lose a race: an
        // unconditional escalation still shows its TERM here, whatever the
        // machine is doing
        repl.gracesForTest(TimeUnit.SECONDS.toMillis(30), 50);
        repl.stop();

        assertThat(repl.rungsTaken())
                .as("cat quits on end-of-input, so no signal is ever sent")
                .containsExactly(InteractiveProcess.Rung.EOF);
        assertThat(exited.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(repl.isAlive()).isFalse();
    }

    @Test
    @Timeout(60) // a leash against a HANG, not a measurement of speed
    @DisplayName("stop() with the SHIPPED graces still returns, and still kills the trapper")
    void shippedGracesEscalateAndStayBounded() throws Exception {
        // the end-to-end POSIX proof at the graces a user actually gets: no
        // seam, no stopwatch. Boundedness is proven by RETURNING at all —
        // an untimed waitFor on a child that never exits never comes back,
        // which is a hang, not a slow pass, so the leash above is one-sided.
        CountDownLatch exited = new CountDownLatch(1);
        InteractiveProcess repl = termTrapper(exited);
        assertThat(repl.isAlive()).isTrue();

        repl.stop();

        assertThat(repl.rungsTaken())
                .as("the shipped graces walk the whole ladder for a trapper")
                .containsExactly(InteractiveProcess.Rung.EOF,
                        InteractiveProcess.Rung.TERM,
                        InteractiveProcess.Rung.KILL);
        // the exit callback fires from waitFor() returning, so this latch is
        // the OS itself saying the child was reaped — the orphan guarantee,
        // asserted rather than timed
        assertThat(exited.await(10, TimeUnit.SECONDS))
                .as("nothing of the trapper survives stop()").isTrue();
        assertThat(repl.isAlive()).isFalse();
    }
}
