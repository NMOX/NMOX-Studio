package org.nmox.studio.core.process;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessSupportTest {

    @Test
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
    @DisplayName("runBounded reports a nonzero exit honestly")
    void shouldReportNonzeroExit() throws Exception {
        ProcessSupport.BoundedResult r = ProcessSupport.runBounded(
                List.of("sh", "-c", "exit 3"), null, Duration.ofSeconds(20));

        assertThat(r.exitCode()).isEqualTo(3);
        assertThat(r.ok()).isFalse();
        assertThat(r.timedOut()).isFalse();
    }

    @Test
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
        // outlive the sweep. destroyForcibly is asynchronous, so poll briefly.
        java.util.Set<Long> orphans = livingSleepPids();
        orphans.removeAll(sleepsBefore);
        long grace = System.nanoTime();
        while (!orphans.isEmpty() && (System.nanoTime() - grace) < 3_000_000_000L) {
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
}
