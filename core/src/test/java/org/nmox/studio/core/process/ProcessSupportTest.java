package org.nmox.studio.core.process;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessSupportTest {

    /**
     * Do we have a WORKING POSIX shell? Most fixtures below build their
     * process shapes with {@code sh -c} — that is the only portable way
     * to make a shell spawn a grandchild, flood a pipe, or write to both
     * streams at once.
     *
     * <p>On Linux and macOS this is always true. On Windows `sh` comes
     * from Git Bash being on PATH, which the runner image controls and
     * has changed under us before (2026-07-31: five of these fixtures
     * began failing on windows-latest with no code change — main was
     * green at 14:03 and red at 15:11 on a DOCS-ONLY commit, and `sh`
     * started but exited non-zero rather than being absent). Asserting
     * the precondition instead of the OS keeps Windows coverage whenever
     * its shell genuinely works, and skips honestly when it doesn't —
     * where a bare @DisabledOnOs would give up that coverage forever.
     *
     * <p>The product itself never spawns `sh` on Windows: ToolLocator
     * resolves .exe/.cmd, so this is a FIXTURE dependency, not a
     * shipping one.
     */
    static boolean posixShellWorks() {
        try {
            ProcessSupport.BoundedResult r = ProcessSupport.runBounded(
                    List.of("sh", "-c", "exit 7"), null, Duration.ofSeconds(10));
            return r.exitCode() == 7 && !r.timedOut();
        } catch (Exception noShell) {
            return false;
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIf("posixShellWorks")
    @DisplayName("runBounded captures stdout and stderr separately, UTF-8")
    void shouldCaptureBothStreams() throws Exception {
        ProcessSupport.BoundedResult r = ProcessSupport.runBounded(
                List.of("sh", "-c", "printf 'héllo-out'; printf 'wörld-err' 1>&2"),
                null, Duration.ofSeconds(20));

        assertThat(r.ok()).isTrue();
        assertThat(r.exitCode()).isZero();
        assertThat(r.timedOut()).isFalse();
        assertThat(r.stdout()).isEqualTo("héllo-out");
        assertThat(r.stderr()).isEqualTo("wörld-err");
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIf("posixShellWorks")
    @DisplayName("runBounded reports a nonzero exit honestly")
    void shouldReportNonzeroExit() throws Exception {
        ProcessSupport.BoundedResult r = ProcessSupport.runBounded(
                List.of("sh", "-c", "exit 3"), null, Duration.ofSeconds(20));

        assertThat(r.exitCode()).isEqualTo(3);
        assertThat(r.ok()).isFalse();
        assertThat(r.timedOut()).isFalse();
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIf("posixShellWorks")
    @DisplayName("timeout is real even when the child stays silent with the pipe open")
    @Timeout(15)
    void shouldKillSilentChildOnTimeout() throws Exception {
        // The historic bug shape: a child that writes nothing and never exits.
        // A read-to-EOF-first caller hangs forever here; runBounded must come
        // back shortly after the deadline with the child killed.
        long start = System.nanoTime();
        ProcessSupport.BoundedResult r = ProcessSupport.runBounded(
                List.of("sh", "-c", "sleep 60"), null, Duration.ofMillis(500));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(r.timedOut()).isTrue();
        assertThat(r.ok()).isFalse();
        assertThat(r.exitCode()).isEqualTo(-1);
        assertThat(elapsedMs).isLessThan(10_000);
    }

    @Test
    @DisplayName("timeout kills grandchildren holding the pipe, not just the shell")
    @Timeout(25)
    @org.junit.jupiter.api.condition.DisabledOnOs(
            value = org.junit.jupiter.api.condition.OS.WINDOWS,
            disabledReason = "Git Bash breaks the Windows parent-PID chain at exec "
                    + "(the MSYS fork stub exits), so the sleep grandchild is invisible "
                    + "to any pure-Java descendants() walk — proven on the runner: both "
                    + "drains block their full 5s even with the tree fully born (3s "
                    + "deadline). Native Windows process trees keep intact chains and "
                    + "ARE swept — shouldKillSilentChildOnTimeout passes there; only "
                    + "this MSYS-shell fixture shape cannot be built on Windows.")
    void shouldKillGrandchildHoldingPipeOnTimeout() throws Exception {
        // The Linux CI failure mode: a shell that SPAWNS its command (dash;
        // any `cmd &`) dies on destroyForcibly while the grandchild keeps the
        // pipe's write end open — the drains never see EOF. `sleep 60 & wait`
        // forces that shape on every OS; the descendant sweep must clear it.
        java.util.Set<Long> sleepsBefore = livingSleepPids();

        long start = System.nanoTime();
        ProcessSupport.BoundedResult r = ProcessSupport.runBounded(
                List.of("sh", "-c", "sleep 60 & wait"), null, Duration.ofMillis(500));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(r.timedOut()).isTrue();
        assertThat(r.exitCode()).isEqualTo(-1);
        assertThat(elapsedMs).isLessThan(10_000);

        // The orphan guarantee, asserted directly: no sleep grandchild may
        // outlive the sweep. destroyForcibly + OS reaping is asynchronous and
        // the guarantee is EVENTUAL, not instant — under a loaded full-reactor
        // build the reap can lag well past a second, so poll generously (this
        // is not masking a race: the product does kill the tree, the window
        // just has to outlast CPU contention, not measure it).
        java.util.Set<Long> orphans = livingSleepPids();
        orphans.removeAll(sleepsBefore);
        long grace = System.nanoTime();
        while (!orphans.isEmpty() && (System.nanoTime() - grace) < 15_000_000_000L) {
            Thread.sleep(100);
            orphans = livingSleepPids();
            orphans.removeAll(sleepsBefore);
        }
        assertThat(orphans)
                .as("sleep grandchildren still alive after killTree")
                .isEmpty();
    }

    /** PIDs of live processes whose command names them "sleep". */
    private static java.util.Set<Long> livingSleepPids() {
        return ProcessHandle.allProcesses()
                .filter(ph -> ph.info().command()
                        .map(c -> c.replace('\\', '/'))
                        .map(c -> c.substring(c.lastIndexOf('/') + 1))
                        .map(name -> name.equals("sleep") || name.equals("sleep.exe"))
                        .orElse(false))
                .map(ProcessHandle::pid)
                .collect(java.util.stream.Collectors.toCollection(java.util.HashSet::new));
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIf("posixShellWorks")
    @DisplayName("killTreeAndWait returns only once the tree is confirmed dead")
    @Timeout(20)
    void shouldConfirmTreeDeadInKillTreeAndWait() throws Exception {
        // The Windows lesson behind this API: destroyForcibly is async, and a
        // dying process still holds its file/cwd locks — callers who delete
        // those files next need the confirmed-dead handshake, not the kill.
        Process p = ProcessSupport.builder(List.of("sh", "-c", "sleep 60")).start();
        assertThat(p.isAlive()).isTrue();

        boolean dead = ProcessSupport.killTreeAndWait(p, Duration.ofSeconds(10));

        assertThat(dead).isTrue();
        assertThat(p.isAlive()).isFalse();
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIf("posixShellWorks")
    @DisplayName("a child chatty on stderr cannot deadlock the pipe")
    @Timeout(30)
    void shouldDrainChattyStderrWithoutDeadlock() throws Exception {
        // >64KB to stderr before stdout closes — the classic sequential-drain
        // deadlock. Both streams drain concurrently, so this must complete.
        ProcessSupport.BoundedResult r = ProcessSupport.runBounded(
                List.of("sh", "-c",
                        "i=0; while [ $i -lt 3000 ]; do echo 'stderr line padding padding padding' 1>&2; i=$((i+1)); done; echo done-out"),
                null, Duration.ofSeconds(25));

        assertThat(r.ok()).isTrue();
        assertThat(r.stdout()).contains("done-out");
        assertThat(r.stderr()).contains("stderr line padding");
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIf("posixShellWorks")
    @DisplayName("a runaway child that floods stdout is capped, not left to OOM the IDE")
    @Timeout(30)
    void shouldCapRunawayOutput() throws Exception {
        // ~20 MB to stdout, far past the capture ceiling, then the child exits
        // (head closes the pipe, yes takes SIGPIPE). The OLD unbounded drain
        // appended every byte into a StringBuilder — a big enough flood at the
        // 30s leash was hundreds of MB to GB of heap and an OutOfMemoryError
        // that took down the whole IDE. The cap must hold the capture at the
        // ceiling while still draining to EOF (so the child never deadlocks).
        ProcessSupport.BoundedResult r = ProcessSupport.runBounded(
                List.of("sh", "-c", "yes | head -c 20000000"),
                null, Duration.ofSeconds(25));

        assertThat(r.stdout().length())
                .as("stdout capture is held at the ceiling, not the full 20 MB flood")
                .isLessThanOrEqualTo(ProcessSupport.MAX_CAPTURE_CHARS);
        assertThat(r.stdout().length())
                .as("the flood really did exceed the ceiling (the test is meaningful)")
                .isEqualTo(ProcessSupport.MAX_CAPTURE_CHARS);
        assertThat(r.truncated())
                .as("the dropped tail is reported honestly, not hidden")
                .isTrue();
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIf("posixShellWorks")
    @DisplayName("normal short output is never flagged truncated")
    void shouldNotFlagShortOutput() throws Exception {
        ProcessSupport.BoundedResult r = ProcessSupport.runBounded(
                List.of("sh", "-c", "echo hello"), null, Duration.ofSeconds(20));

        assertThat(r.truncated()).isFalse();
        assertThat(r.stdout()).isEqualTo("hello\n");
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIf("posixShellWorks")
    @DisplayName("workingDir is honored")
    void shouldRunInWorkingDir(@org.junit.jupiter.api.io.TempDir java.io.File dir) throws Exception {
        // Comparing the shell's pwd output to the Java path breaks on two
        // OSes for the same reason — the shell's spelling of the directory is
        // not Java's: macOS prints /var for /private/var, and Git Bash on
        // Windows prints its virtual /tmp mount for %TEMP%. Touching a marker
        // file in the cwd proves workingDir was honored without ever
        // comparing path strings.
        ProcessSupport.BoundedResult r = ProcessSupport.runBounded(
                List.of("sh", "-c", ": > cwd-marker"), dir, Duration.ofSeconds(20));

        assertThat(r.ok()).isTrue();
        assertThat(new java.io.File(dir, "cwd-marker")).exists();
    }

    @Test
    @DisplayName("the null device matches this OS")
    void nullDeviceMatchesOs() {
        String name = ProcessSupport.nullDevice().getPath();
        assertThat(name).isIn("/dev/null", "NUL");
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIf("posixShellWorks")
    @DisplayName("an interrupted caller gets an IOException, a killed child, and its flag back")
    @Timeout(15)
    void shouldFailFastWhenCallerInterrupted() {
        Thread.currentThread().interrupt();
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    ProcessSupport.runBounded(List.of("sh", "-c", "sleep 30"),
                            null, Duration.ofSeconds(20)))
                    .isInstanceOf(java.io.IOException.class)
                    .hasMessageContaining("Interrupted waiting for sh");
            assertThat(Thread.interrupted())
                    .as("the interrupt is restored for the caller's own handling")
                    .isTrue();
        } finally {
            Thread.interrupted(); // leave the worker thread clean
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIf("posixShellWorks")
    @DisplayName("killTreeAndWait with no time budget reports honestly instead of blocking")
    @Timeout(15)
    void killTreeAndWaitZeroBudgetIsHonest() throws Exception {
        Process p = ProcessSupport.builder(List.of("sh", "-c", "sleep 30")).start();
        try {
            // zero budget: the kill is issued but nothing waits — the verdict
            // must be "not confirmed dead", never a hang
            ProcessSupport.killTreeAndWait(p, Duration.ZERO);
            assertThat(ProcessSupport.killTreeAndWait(p, Duration.ofSeconds(10)))
                    .as("a real budget confirms the tree is gone")
                    .isTrue();
            assertThat(p.isAlive()).isFalse();
        } finally {
            ProcessSupport.killTree(p);
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIf("posixShellWorks")
    @DisplayName("an interrupted killTreeAndWait still kills, then returns with the flag set")
    @Timeout(15)
    void killTreeAndWaitInterruptedStillKills() throws Exception {
        Process p = ProcessSupport.builder(List.of("sh", "-c", "sleep 30")).start();
        try {
            Thread.currentThread().interrupt();
            ProcessSupport.killTreeAndWait(p, Duration.ofSeconds(10));
            assertThat(Thread.interrupted())
                    .as("the interrupt survives the bounded wait").isTrue();
            // the kill itself was issued regardless of the early return
            assertThat(ProcessSupport.killTreeAndWait(p, Duration.ofSeconds(10))).isTrue();
        } finally {
            Thread.interrupted();
            ProcessSupport.killTree(p);
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIf("posixShellWorks")
    @DisplayName("a flood arriving after unaligned output still lands exactly at the ceiling")
    @Timeout(40)
    void shouldCapUnalignedFlood() throws Exception {
        // the first write leaves the capture at a non-chunk-aligned length, so
        // the ceiling is reached mid-read — the partial-append edge of the cap
        ProcessSupport.BoundedResult r = ProcessSupport.runBounded(
                List.of("sh", "-c", "head -c 1000 /dev/zero | tr '\\0' 'a'; sleep 1; "
                        + "yes | head -c 10000000"),
                null, Duration.ofSeconds(30));

        assertThat(r.truncated()).isTrue();
        assertThat(r.stdout().length())
                .as("whatever the read alignment, the capture stops exactly at the cap")
                .isEqualTo(ProcessSupport.MAX_CAPTURE_CHARS);
    }
}
