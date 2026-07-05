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
        ProcessSupport.BoundedResult r = ProcessSupport.runBounded(
                List.of("sh", "-c", "pwd"), dir, Duration.ofSeconds(20));

        assertThat(r.ok()).isTrue();
        // /var vs /private/var on macOS — compare canonical paths
        assertThat(new java.io.File(r.stdout().trim()).getCanonicalPath())
                .isEqualTo(dir.getCanonicalPath());
    }
}
