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

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("EADDRINUSE is recognized and translated with the port (v1.264.0)")
    void portInUseSpeaksHuman() {
        // node's shape and the plain-english shape both trip the detector
        org.assertj.core.api.Assertions.assertThat(CommandExecutor.looksLikePortInUse(
                "Error: listen EADDRINUSE: address already in use 0.0.0.0:8080")).isTrue();
        // each branch ALONE must also trip it: node's full line satisfies
        // both OR'd conditions at once, and a mutant that killed only the
        // EADDRINUSE half survived exactly that redundancy (caught by
        // javap during this fix's own mutation proof)
        org.assertj.core.api.Assertions.assertThat(CommandExecutor.looksLikePortInUse(
                "code: 'EADDRINUSE',")).isTrue();
        org.assertj.core.api.Assertions.assertThat(CommandExecutor.looksLikePortInUse(
                "bind: Address already in use")).isTrue();
        org.assertj.core.api.Assertions.assertThat(CommandExecutor.looksLikePortInUse(
                "compiled successfully")).isFalse();
        String human = CommandExecutor.friendlyPortInUse(
                "Error: listen EADDRINUSE: address already in use 0.0.0.0:8080");
        org.assertj.core.api.Assertions.assertThat(human)
                .contains("Port 8080")
                .contains("already being used")
                .contains("SONAR");
        // no port on the line -> still a full sentence, no nulls
        org.assertj.core.api.Assertions.assertThat(
                CommandExecutor.friendlyPortInUse("EADDRINUSE"))
                .startsWith("\u21b3 This port");
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("a Node-too-old refusal is recognized and translated (v1.318.0)")
    void nodeFloorSpeaksHuman() {
        // The two spellings measured live on node 22.9.0 \u2014 the Angular
        // walk in the shipped 1.317.0 hit the first one at first Run,
        // raw and red, after npm install had sailed through (npm does
        // not enforce engines). Verbatim lines, not paraphrases.
        org.assertj.core.api.Assertions.assertThat(CommandExecutor.looksLikeNodeTooOld(
                "The Angular CLI requires a minimum Node.js version of v20.19 or v22.12."))
                .isTrue();
        org.assertj.core.api.Assertions.assertThat(CommandExecutor.looksLikeNodeTooOld(
                "Vite requires Node.js version 20.19+ or 22.12+. "
                + "Please upgrade your Node.js version."))
                .isTrue();
        // the CLI's preceding "detected" line must NOT trip it \u2014 only the
        // refusal names a requirement
        org.assertj.core.api.Assertions.assertThat(CommandExecutor.looksLikeNodeTooOld(
                "Node.js version v22.9.0 detected."))
                .isFalse();
        // npm's EBADENGINE is a WARNING the install proceeds past \u2014
        // deliberately not rescued
        org.assertj.core.api.Assertions.assertThat(CommandExecutor.looksLikeNodeTooOld(
                "npm warn EBADENGINE Unsupported engine"))
                .isFalse();
        org.assertj.core.api.Assertions.assertThat(CommandExecutor.looksLikeNodeTooOld(
                "compiled successfully"))
                .isFalse();
        org.assertj.core.api.Assertions.assertThat(CommandExecutor.friendlyNodeTooOld())
                .startsWith("\u21b3 ")
                .contains("nvm install --lts")
                .contains("Environment Doctor");
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("all three humanizers are wired into the pump, not just defined")
    void humanizersAreWiredIntoThePump() throws Exception {
        // A predicate with green tests and no call site is a payload
        // without a gate (the v1.216.0 class): deleting the pump block
        // would leave every test above passing while the product says
        // nothing. Pin the WIRING for both translations.
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/org/nmox/studio/rack/engine/CommandExecutor.java"));
        org.assertj.core.api.Assertions.assertThat(src)
                .contains("looksLikePortInUse(clean)")
                .contains("looksLikeNodeTooOld(clean)");
        // the third wall (v2.69.0): the strip-types refusal is consulted on the same pump path
        org.assertj.core.api.Assertions.assertThat(src)
                .as("NodeTypeStripping.wall must be consulted by the pump — a wall with no call site is a payload without a gate")
                .contains("NodeTypeStripping.wall(clean)");
    }
}
