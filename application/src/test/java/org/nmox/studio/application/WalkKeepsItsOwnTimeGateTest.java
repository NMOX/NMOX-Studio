package org.nmox.studio.application;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A walk that does not end, ends itself (3.5.4).
 *
 * <p>{@code scripts/platform-walk.sh} leashed the app with {@code timeout(1)},
 * which a stock Mac does not have, and without it ran "unleashed, the job's
 * own timeout is the leash". The installed 3.5.2 app then stopped at its
 * Browser tab on a macOS runner: the job was cancelled at 25 minutes and what
 * came back was nine pictures and no log, so nobody could say where it had
 * stopped. This runs the script against a stand-in app that never exits, with
 * the script's own leash forced, and holds three things: it comes back, it
 * says 124, and nothing it started is still running, its children's children
 * included.
 */
@DisabledOnOs(OS.WINDOWS) // the script's own leash is for systems without timeout(1); Git Bash has one
class WalkKeepsItsOwnTimeGateTest {

    @Test
    @DisplayName("a stand-in app that never exits is stopped at the limit, with its descendants, and the walk says 124")
    void theWalkEndsItself(@TempDir Path tmp) throws Exception {
        Path bin = Files.createDirectories(tmp.resolve("app/bin"));
        Path pids = tmp.resolve("pids");
        // the launcher starts a child that starts a grandchild, as the real one
        // starts nbexec, which starts the JVM
        Path launcher = bin.resolve("nmoxstudio");
        Files.writeString(launcher, String.join("\n",
                "#!/bin/sh",
                "sh -c 'sleep 300 & echo $! >> \"" + pids + "\"; wait' &",
                "echo $! >> \"" + pids + "\"",
                "echo $$ >> \"" + pids + "\"",
                "wait",
                ""), StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(launcher, PosixFilePermissions.fromString("rwxr-xr-x"));
        Path out = tmp.resolve("out");

        ProcessBuilder pb = new ProcessBuilder("sh", "scripts/platform-walk.sh", out.toString())
                .directory(new File("..").getCanonicalFile())
                .redirectErrorStream(true)
                .redirectOutput(tmp.resolve("script-output.txt").toFile());
        pb.environment().put("NMOX_WALK_APP", tmp.resolve("app").toString());
        pb.environment().put("NMOX_WALK_TIMEOUT", "2");
        pb.environment().put("NMOX_WALK_OWN_LEASH", "1");
        pb.environment().remove("JAVA_HOME");
        long started = System.nanoTime();
        Process script = pb.start();
        try {
            check(tmp, pids, out, started, script);
        } finally {
            // whatever the verdict, this test leaves nothing running
            script.descendants().forEach(ProcessHandle::destroyForcibly);
            script.destroyForcibly();
            if (Files.exists(pids)) {
                for (String pid : read(pids).strip().split("\\s+")) {
                    if (!pid.isBlank()) {
                        ProcessHandle.of(Long.parseLong(pid)).ifPresent(ProcessHandle::destroyForcibly);
                    }
                }
            }
        }
    }

    private static void check(Path tmp, Path pids, Path out, long started, Process script) throws Exception {
        assertThat(script.waitFor(60, TimeUnit.SECONDS))
                .as("the walk comes back by itself: " + read(tmp.resolve("script-output.txt"))).isTrue();
        assertThat(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started))
                .as("soon after its limit of two seconds").isLessThan(40);
        assertThat(script.exitValue()).as("and does not call a walk that was stopped a success").isNotZero();
        assertThat(read(out.resolve("walk.txt"))).contains("exit code: 124");
        assertThat(read(tmp.resolve("script-output.txt"))).contains("still running after 2s");

        for (String pid : read(pids).strip().split("\\s+")) {
            assertThat(ProcessHandle.of(Long.parseLong(pid)).map(ProcessHandle::isAlive).orElse(false))
                    .as("process " + pid + " of the stand-in's tree is gone").isFalse();
        }
        assertThat(read(pids).strip().split("\\s+")).as("launcher, child, grandchild").hasSize(3);
    }

    private static String read(Path file) throws Exception {
        return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "(no " + file.getFileName() + ")";
    }
}
