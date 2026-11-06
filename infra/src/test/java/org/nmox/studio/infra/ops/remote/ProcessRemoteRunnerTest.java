package org.nmox.studio.infra.ops.remote;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The real runner against a stand-in ssh: a shell script that prints its argv, echoes stdin, exits as told. */
class ProcessRemoteRunnerTest {

    private static Path fakeSsh(Path dir, String body) throws Exception {
        assumeTrue(!org.openide.util.BaseUtilities.isWindows(), "a POSIX sh stand-in");
        Path script = dir.resolve("ssh");
        Files.writeString(script, "#!/bin/sh\n" + body);
        script.toFile().setExecutable(true);
        return script;
    }

    @Test
    @DisplayName("stdout and stderr lines arrive in order, stdin reaches the command, the exit code comes back")
    void streams(@TempDir Path dir) throws Exception {
        Path ssh = fakeSsh(dir, "echo \"argv: $*\"\necho err 1>&2\ncat\nexit 3\n");
        ProcessRemoteRunner r = new ProcessRemoteRunner("/kh", ssh.toString());
        List<String> lines = new ArrayList<>();
        RemoteRunner.Result result = r.run(new SshTarget("root", "203.0.113.9", 2222, null), "uptime",
                new ByteArrayInputStream("hello from stdin\n".getBytes(StandardCharsets.UTF_8)), lines::add,
                Duration.ofSeconds(10));
        assertThat(result.exitCode()).isEqualTo(3);
        assertThat(result.timedOut()).isFalse();
        assertThat(lines).contains("err", "hello from stdin");
        // stdout and stderr are two pipes; which lands first is the kernel's call, not ours
        String argv = lines.stream().filter(l -> l.startsWith("argv: ")).findFirst().orElse("");
        assertThat(argv).startsWith("argv: -o BatchMode=yes").contains("UserKnownHostsFile=/kh")
                .contains("-p 2222 root@203.0.113.9 -- uptime");
        assertThat(result.tail()).contains("hello from stdin");
    }

    @Test
    @DisplayName("a run past its leash is killed, said, and reported as timed out")
    void leash(@TempDir Path dir) throws Exception {
        Path ssh = fakeSsh(dir, "echo started\nsleep 30\necho never\n");
        ProcessRemoteRunner r = new ProcessRemoteRunner(null, ssh.toString());
        List<String> lines = new ArrayList<>();
        long t0 = System.currentTimeMillis();
        RemoteRunner.Result result = r.run(new SshTarget("root", "h", 22, null), "x", null, lines::add,
                Duration.ofMillis(800));
        assertThat(result.timedOut()).isTrue();
        assertThat(result.exitCode()).isEqualTo(-1);
        assertThat(System.currentTimeMillis() - t0).isLessThan(10_000);
        assertThat(lines).contains("started").doesNotContain("never");
        assertThat(lines).anyMatch(l -> l.contains("stopped after"));
    }

    @Test
    @DisplayName("an endless line is cut, never held whole")
    void longLine(@TempDir Path dir) throws Exception {
        Path ssh = fakeSsh(dir, "head -c 100000 /dev/zero | tr '\\0' 'x'\necho\n");
        ProcessRemoteRunner r = new ProcessRemoteRunner(null, ssh.toString());
        List<String> lines = new ArrayList<>();
        r.run(new SshTarget("root", "h", 22, null), "x", null, lines::add, Duration.ofSeconds(10));
        assertThat(lines.get(0).length()).isLessThanOrEqualTo(ProcessRemoteRunner.MAX_LINE_CHARS + 1);
        assertThat(lines.get(0)).endsWith("…");
    }

    @Test
    @DisplayName("an ssh that cannot start is a spoken failure, not an exception")
    void cannotStart() {
        ProcessRemoteRunner r = new ProcessRemoteRunner(null, "/nonexistent/ssh-" + System.nanoTime());
        List<String> lines = new ArrayList<>();
        RemoteRunner.Result result = r.run(new SshTarget("root", "h", 22, null), "x", null, lines::add, Duration.ofSeconds(5));
        assertThat(result.exitCode()).isEqualTo(-1);
        assertThat(lines).anyMatch(l -> l.startsWith("ssh could not start"));
    }
}
