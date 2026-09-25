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
    @DisplayName("git is git wherever it is installed, and nothing else is")
    void recognisesGit() {
        assertThat(TerminalReaper.isGit("/Library/Developer/CommandLineTools/usr/bin/git")).isTrue();
        assertThat(TerminalReaper.isGit("/opt/homebrew/Cellar/git/2.51.0/bin/git")).isTrue();
        assertThat(TerminalReaper.isGit("C:\\Program Files\\Git\\mingw64\\bin\\git.exe")).isTrue();
        assertThat(TerminalReaper.isGit("/usr/bin/git-lfs")).isFalse();
        assertThat(TerminalReaper.isGit("/bin/zsh")).isFalse();
        assertThat(TerminalReaper.isGit(null)).isFalse();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisplayName("a git under a Terminal is given its grace before the hang-up, and the grace is bounded")
    void gitFinishesFirst(@TempDir Path dir) throws Exception {
        Path bin = Files.createDirectories(dir.resolve("bin"));
        Path git = bin.resolve("git");
        Files.writeString(git, "#!/bin/sh\nsleep \"$1\"\n");
        git.toFile().setExecutable(true);
        Path helper = dir.resolve("helper");
        Files.writeString(helper, "#!/bin/sh\n\"$1\" \"$2\"\nsleep 30\n");
        helper.toFile().setExecutable(true);
        Process quick = new ProcessBuilder(helper.toString(), git.toString(), "0.4").start();
        Process slow = new ProcessBuilder(helper.toString(), git.toString(), "30").start();
        try {
            Thread.sleep(200); // let each helper start its git
            long t0 = System.currentTimeMillis();
            TerminalReaper.awaitGit(java.util.List.of(quick.toHandle()), 5_000);
            assertThat(quick.toHandle().descendants().filter(p -> TerminalReaper.isGit(
                    p.info().command().orElse(null))).count()).as("the git finished first").isZero();
            long waited = System.currentTimeMillis() - t0;
            assertThat(waited).as("it waited for git, not for the grace").isLessThan(4_000);
            long t1 = System.currentTimeMillis();
            TerminalReaper.awaitGit(java.util.List.of(slow.toHandle()), 300);
            assertThat(System.currentTimeMillis() - t1).as("a git that will not finish costs only the grace")
                    .isLessThan(3_000);
        } finally {
            quick.descendants().forEach(ProcessHandle::destroyForcibly);
            slow.descendants().forEach(ProcessHandle::destroyForcibly);
            quick.destroyForcibly();
            slow.destroyForcibly();
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisplayName("hanging up waits for the Terminal's git to finish before ending its helper")
    void hangUpLetsGitLand(@TempDir Path dir) throws Exception {
        Path git = Files.createDirectories(dir.resolve("bin")).resolve("git");
        Path landed = dir.resolve("landed");
        Files.writeString(git, "#!/bin/sh\nsleep 0.5\ntouch \"" + landed + "\"\n");
        git.toFile().setExecutable(true);
        Path pty = Files.createDirectories(dir.resolve("dlight_test").resolve("h").resolve("1")).resolve("pty");
        Files.writeString(pty, "#!/bin/sh\n\"$1\"\nsleep 30\n");
        pty.toFile().setExecutable(true);
        Process helper = new ProcessBuilder(pty.toString(), git.toString()).start();
        try {
            Thread.sleep(150); // the helper has started its git
            assertThat(TerminalReaper.hangUp(Stream.of(fake(helper.toHandle(), pty.toString())))).isEqualTo(1);
            assertThat(Files.exists(landed)).as("git finished before the hang-up").isTrue();
            assertThat(helper.waitFor(5, TimeUnit.SECONDS)).as("then the helper ended").isTrue();
        } finally {
            helper.descendants().forEach(ProcessHandle::destroyForcibly);
            helper.destroyForcibly();
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
