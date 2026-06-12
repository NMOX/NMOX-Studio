package org.nmox.studio.rack.engine;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

class CommandExecutorTest {

    @Test
    @DisplayName("Should strip ANSI color and OSC sequences")
    void shouldStripAnsi() {
        assertThat(CommandExecutor.stripAnsi("[32m✓ built in 1.2s[0m"))
                .isEqualTo("✓ built in 1.2s");
        assertThat(CommandExecutor.stripAnsi("]0;titleplain")).isEqualTo("plain");
        assertThat(CommandExecutor.stripAnsi("no escapes")).isEqualTo("no escapes");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisplayName("Children must get the non-interactive guard environment")
    void shouldInjectGuardEnvironment() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        StringBuilder output = new StringBuilder();
        CommandExecutor.run("test", new File("."), Map.of(),
                List.of("sh", "-c", "echo $npm_config_yes/$GIT_TERMINAL_PROMPT/$NO_COLOR"),
                output::append, code -> done.countDown());

        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(output.toString()).isEqualTo("true/0/1");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisplayName("stderr keeps its identity: bus lines are tagged by stream, devices see both")
    void shouldSeparateStderrOnTheBus() throws Exception {
        List<String> busLines = new java.util.concurrent.CopyOnWriteArrayList<>();
        RackBus.Listener tap = (device, line, err) -> busLines.add(device + "|" + line + "|" + err);
        RackBus.subscribe(tap);
        try {
            CountDownLatch done = new CountDownLatch(1);
            List<String> deviceLines = new java.util.concurrent.CopyOnWriteArrayList<>();
            CommandExecutor.run("streams", new File("."), Map.of(),
                    List.of("sh", "-c", "echo plain; echo broken 1>&2"),
                    deviceLines::add, code -> done.countDown());

            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(busLines).contains(
                    "streams|plain|false", "streams|broken|true");
            // lifecycle markers ride the bus too - the flight recorder needs them
            assertThat(busLines).anyMatch(l -> l.startsWith("streams|$ sh"));
            assertThat(busLines).anyMatch(l -> l.startsWith("streams|[exit 0]"));
            // the device callback still hears both streams (parsers need both)
            assertThat(deviceLines).containsExactlyInAnyOrder("plain", "broken");
        } finally {
            RackBus.unsubscribe(tap);
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisplayName("A command that reads stdin must finish instantly, not hang")
    void shouldNotHangOnStdinReads() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger exit = new AtomicInteger(99);
        CommandExecutor.run("test", new File("."), Map.of(),
                List.of("sh", "-c", "read answer"),
                line -> { }, code -> {
                    exit.set(code);
                    done.countDown();
                });

        // stdin is /dev/null: `read` sees EOF and fails immediately
        assertThat(done.await(5, TimeUnit.SECONDS))
                .as("process must not wait for interactive input")
                .isTrue();
        assertThat(exit.get()).isNotEqualTo(0);
    }
}
