package org.nmox.studio.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/** The Terminal's shells are hung up when the IDE quits (3.2 walk). */
class TerminalReaperTest {

    @Test
    @DisplayName("the pty helper is recognised by where native execution unpacks it, and nothing else is")
    void recognisesTheHelper() {
        assertThat(TerminalReaper.isPtyHelper(
                "/private/var/folders/xn/abc/T/dlight_david/b8d4fb8/01680434851/pty")).isTrue();
        assertThat(TerminalReaper.isPtyHelper("/tmp/dlight_ana/1/2/pty")).isTrue();
        assertThat(TerminalReaper.isPtyHelper("/bin/zsh")).isFalse();
        assertThat(TerminalReaper.isPtyHelper("/usr/local/bin/pty")).as("a pty not unpacked by the platform").isFalse();
        assertThat(TerminalReaper.isPtyHelper("/tmp/dlight_ana/1/2/pty-other")).isFalse();
        assertThat(TerminalReaper.isPtyHelper(null)).isFalse();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisplayName("hanging up ends the helper among the children and leaves every other child running")
    void endsOnlyTheHelper(@TempDir Path dir) throws Exception {
        Path helperDir = Files.createDirectories(dir.resolve("dlight_test").resolve("h").resolve("1"));
        Path pty = helperDir.resolve("pty");
        Path sleeper = dir.resolve("sleeper");
        for (Path p : new Path[] {pty, sleeper}) {
            Files.writeString(p, "#!/bin/sh\nexec sleep 30\n");
            p.toFile().setExecutable(true);
        }
        Process helper = start(pty);
        Process other = start(sleeper);
        try {
            // the script execs sleep, so the command reads sleep; the census
            // is fed the handle with the helper's own path as its command
            int asked = TerminalReaper.hangUp(Stream.of(
                    fake(helper.toHandle(), pty.toString()), fake(other.toHandle(), sleeper.toString())));
            assertThat(asked).isEqualTo(1);
            assertThat(helper.waitFor(5, TimeUnit.SECONDS)).as("the helper ended").isTrue();
            assertThat(other.isAlive()).as("another child is not the reaper's").isTrue();
        } finally {
            helper.destroyForcibly();
            other.destroyForcibly();
        }
    }

    @Test
    @DisplayName("the hook is registered at startup")
    void registersTheHook() {
        new TerminalReaper().run();
        assertThat(TerminalReaper.register()).as("registered, and a second call adds nothing").isTrue();
    }

    private static Process start(Path script) throws IOException {
        return new ProcessBuilder(script.toString()).redirectErrorStream(true).start();
    }

    /** A handle whose command is {@code command}, everything else the real one's. */
    private static ProcessHandle fake(ProcessHandle real, String command) {
        return new ProcessHandle() {
            @Override public long pid() { return real.pid(); }
            @Override public java.util.Optional<ProcessHandle> parent() { return real.parent(); }
            @Override public Stream<ProcessHandle> children() { return real.children(); }
            @Override public Stream<ProcessHandle> descendants() { return real.descendants(); }
            @Override public ProcessHandle.Info info() {
                ProcessHandle.Info i = real.info();
                return new ProcessHandle.Info() {
                    @Override public java.util.Optional<String> command() { return java.util.Optional.of(command); }
                    @Override public java.util.Optional<String> commandLine() { return i.commandLine(); }
                    @Override public java.util.Optional<String[]> arguments() { return i.arguments(); }
                    @Override public java.util.Optional<java.time.Instant> startInstant() { return i.startInstant(); }
                    @Override public java.util.Optional<java.time.Duration> totalCpuDuration() { return i.totalCpuDuration(); }
                    @Override public java.util.Optional<String> user() { return i.user(); }
                };
            }
            @Override public java.util.concurrent.CompletableFuture<ProcessHandle> onExit() { return real.onExit(); }
            @Override public boolean supportsNormalTermination() { return real.supportsNormalTermination(); }
            @Override public boolean destroy() { return real.destroy(); }
            @Override public boolean destroyForcibly() { return real.destroyForcibly(); }
            @Override public boolean isAlive() { return real.isAlive(); }
            @Override public int compareTo(ProcessHandle o) { return real.compareTo(o); }
        };
    }
}
