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
        assertThat(TerminalReaper.isPtyHelper("/tmp/dlight_ana/1/2/pty (deleted)"))
                .as("Linux names a removed executable so").isTrue();
        assertThat(TerminalReaper.isPtyHelper("C:\\Users\\ana\\AppData\\Local\\Temp\\dlight_ana\\1\\pty.exe")).isTrue();
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

    /**
     * A fake pty helper at {@code dir/dlight_test/h/1/pty} running
     * {@code body}, then {@code exec sleep 30} so the helper's own pid is
     * what a hang-up ends. The gits it runs are the REAL git: a shell script
     * named git reads as {@code /bin/sh}, and a copy of {@code sleep} named
     * git is killed at launch on macOS — the first two cuts of these tests,
     * the first of which passed recognising nothing.
     */
    private static Path fakePty(Path dir, String body) throws IOException {
        Path pty = Files.createDirectories(dir.resolve("dlight_test").resolve("h").resolve("1")).resolve("pty");
        Files.writeString(pty, "#!/bin/sh\n" + body + "\nexec sleep 30\n");
        pty.toFile().setExecutable(true);
        return pty;
    }

    /** git with no user, system or harness configuration (a developer's GIT_EDITOR=true would win otherwise). */
    private static ProcessBuilder isolatedGit(ProcessBuilder pb) {
        pb.environment().remove("GIT_EDITOR");
        pb.environment().remove("VISUAL");
        pb.environment().remove("EDITOR");
        pb.environment().put("GIT_CONFIG_GLOBAL", "/dev/null");
        pb.environment().put("GIT_CONFIG_NOSYSTEM", "1");
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");
        return pb.redirectErrorStream(true);
    }

    /**
     * A helper whose git is {@code git commit} in a fresh repository, its
     * editor a script named like the launcher ({@code nmox-editor}) that
     * waits {@code editorSeconds} and gives up, so the commit aborts.
     */
    private static Process gitCommitting(Path dir, String editorSeconds) throws Exception {
        Path repo = Files.createDirectories(dir.resolve("repo"));
        Path editor = dir.resolve("nmox-editor");
        Files.writeString(editor, "#!/bin/sh\nsleep " + editorSeconds + "\nexit 1\n");
        editor.toFile().setExecutable(true);
        for (String[] cmd : new String[][] {{"git", "init", "-q"}}) {
            assertThat(isolatedGit(new ProcessBuilder(cmd).directory(repo.toFile())).start()
                    .waitFor(20, TimeUnit.SECONDS)).isTrue();
        }
        Files.writeString(repo.resolve("a.txt"), "a\n");
        assertThat(isolatedGit(new ProcessBuilder("git", "add", "a.txt").directory(repo.toFile())).start()
                .waitFor(20, TimeUnit.SECONDS)).isTrue();
        Path pty = fakePty(dir, "cd \"$1\" && git -c core.editor=\"$2\" -c user.name=t -c user.email=t@example.com commit -q");
        return isolatedGit(new ProcessBuilder(pty.toString(), repo.toString(), editor.toString())).start();
    }

    private static ProcessHandle gitUnder(Process helper) throws InterruptedException {
        for (int i = 0; i < 250; i++) {
            java.util.Optional<ProcessHandle> g = helper.descendants()
                    .filter(p -> TerminalReaper.isGit(p.info().command().orElse(null))).findFirst();
            if (g.isPresent()) {
                return g.get();
            }
            Thread.sleep(20);
        }
        throw new AssertionError("the helper never started its git");
    }

    /** Waits until git has its nmox editor beneath it. */
    private static void editorUnder(ProcessHandle git) throws InterruptedException {
        for (int i = 0; i < 250 && !TerminalReaper.editsInNmox(git); i++) {
            Thread.sleep(20);
        }
        assertThat(TerminalReaper.editsInNmox(git)).as("git started its nmox editor").isTrue();
    }

    /**
     * Gathers every process beneath the helper and beneath each one seen
     * before BEFORE killing any, so none is orphaned by its parent dying
     * first (8th review: a leftover {@code sleep 30} per run).
     */
    private static void killAll(java.util.List<ProcessHandle> seen, Process helper) {
        java.util.Set<ProcessHandle> all = new java.util.LinkedHashSet<>();
        helper.descendants().forEach(all::add);
        for (ProcessHandle h : seen) {
            h.descendants().forEach(all::add);
            all.add(h);
        }
        all.forEach(ProcessHandle::destroyForcibly);
        helper.destroyForcibly();
    }

    @Test
    @DisplayName("an nmox editor anywhere on a command line is recognised, other editors are not")
    void recognisesNmoxEditors() {
        assertThat(TerminalReaper.namesNmox("/bin/sh /usr/local/bin/nmox -w .git/COMMIT_EDITMSG")).isTrue();
        assertThat(TerminalReaper.namesNmox("/bin/sh /tmp/w/bin/nmoxw --jdkhome /x -w .git/COMMIT_EDITMSG")).isTrue();
        assertThat(TerminalReaper.namesNmox("C:\\Program Files\\NMOX Studio\\bin\\nmox -w m")).isTrue();
        assertThat(TerminalReaper.namesNmox("vim .git/COMMIT_EDITMSG")).isFalse();
        assertThat(TerminalReaper.namesNmox("less -R")).isFalse();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisplayName("hanging up waits for a git whose editor is nmox, then ends the helper")
    void hangUpLetsGitLand(@TempDir Path dir) throws Exception {
        Process helper = gitCommitting(dir, "1");
        java.util.List<ProcessHandle> all = new java.util.ArrayList<>();
        try {
            ProcessHandle git = gitUnder(helper);
            editorUnder(git);
            all.addAll(helper.descendants().toList());
            Path pty = dir.resolve("dlight_test").resolve("h").resolve("1").resolve("pty");
            assertThat(TerminalReaper.hangUp(Stream.of(fake(helper.toHandle(), pty.toString())))).isEqualTo(1);
            assertThat(git.isAlive()).as("git finished before the hang-up").isFalse();
            assertThat(helper.waitFor(5, TimeUnit.SECONDS)).as("then the helper ended").isTrue();
        } finally {
            killAll(all, helper);
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisplayName("any other git (a pager, another editor) holds the quit up not at all")
    void otherGitIsNotWaitedFor(@TempDir Path dir) throws Exception {
        Path pty = fakePty(dir, "sleep 30 | git hash-object --stdin");
        Process helper = isolatedGit(new ProcessBuilder(pty.toString())).start();
        java.util.List<ProcessHandle> all = new java.util.ArrayList<>();
        try {
            ProcessHandle git = gitUnder(helper);
            all.addAll(helper.descendants().toList());
            long t0 = System.currentTimeMillis();
            TerminalReaper.hangUp(Stream.of(fake(helper.toHandle(), pty.toString())));
            assertThat(System.currentTimeMillis() - t0).as("no grace for a git with no nmox editor").isLessThan(1_500);
            assertThat(git.isAlive()).isTrue();
        } finally {
            killAll(all, helper);
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisplayName("a git whose nmox editor will not finish costs only the grace")
    void graceIsBounded(@TempDir Path dir) throws Exception {
        Process helper = gitCommitting(dir, "30");
        java.util.List<ProcessHandle> all = new java.util.ArrayList<>();
        try {
            ProcessHandle git = gitUnder(helper);
            editorUnder(git);
            all.addAll(helper.descendants().toList());
            long t0 = System.currentTimeMillis();
            TerminalReaper.awaitGit(java.util.List.of(helper.toHandle()), 300);
            long waited = System.currentTimeMillis() - t0;
            assertThat(waited).as("it waited for the grace").isGreaterThanOrEqualTo(250).isLessThan(3_000);
            assertThat(git.isAlive()).isTrue();
        } finally {
            killAll(all, helper);
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
