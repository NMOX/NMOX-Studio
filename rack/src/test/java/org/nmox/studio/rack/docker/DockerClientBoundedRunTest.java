package org.nmox.studio.rack.docker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The timeout guarantee on {@link DockerClient#run}, proven against a
 * deliberately silent long-running command (the shape of a docker CLI
 * stuck on a wedged daemon: no output, pipe open, never exits within
 * the budget). The old implementation read stdout to EOF BEFORE
 * starting the timeout clock, so this exact shape pinned a pool thread
 * for the child's whole lifetime — with only four fixed pool threads,
 * four wedged calls froze every docker feature until IDE restart.
 */
@DisabledOnOs(OS.WINDOWS) // relies on POSIX sleep/echo
class DockerClientBoundedRunTest {

    @Test
    @DisplayName("A silent, never-finishing CLI is killed at the timeout, not at EOF")
    void silentHangIsBoundedByTheTimeout() {
        DockerClient client = new DockerClient("sleep");

        long start = System.nanoTime();
        DockerClient.Result r = client.run(1, "5"); // sleep 5, budget 1s
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs)
                .as("run() returns at the timeout — the old code blocked on "
                        + "stdout EOF for the full 5s of the child's life")
                .isLessThan(4_000);
        assertThat(r.ok()).isFalse();
        assertThat(r.exit()).isEqualTo(-1);
        assertThat(r.stderr()).contains("timed out after 1s");
    }

    @Test
    @DisplayName("A normal fast command keeps the success contract: exit 0 and full stdout")
    void fastCommandContractUnchanged() {
        DockerClient client = new DockerClient("echo");

        DockerClient.Result r = client.run(10, "hello", "world");

        assertThat(r.ok()).isTrue();
        assertThat(r.exit()).isZero();
        assertThat(r.stdout().trim()).isEqualTo("hello world");
        assertThat(r.stderr()).isEmpty();
    }

    @Test
    @DisplayName("A missing binary keeps the not-found contract")
    void missingBinaryContractUnchanged() {
        DockerClient client = new DockerClient("nmox-definitely-not-a-real-binary-xyz");

        DockerClient.Result r = client.run(5, "anything");

        assertThat(r.ok()).isFalse();
        assertThat(r.exit()).isEqualTo(-1);
        assertThat(r.stderr()).contains("not found");
    }
}
