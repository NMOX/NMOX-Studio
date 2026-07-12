package org.nmox.studio.editor.debug.dap;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;

import org.nmox.studio.core.process.ProcessSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Lifecycle tests against fake "servers" (tiny node scripts), so they run
 * wherever node does — CI included — without touching the real adapter.
 */
@Timeout(60)
class JsDebugServerTest {

    /**
     * A self-managed temp dir instead of {@code @TempDir}: these tests spawn
     * node, and on Windows the OS can lag releasing the script/cwd file
     * handle for a moment after the process is confirmed dead — JUnit's
     * @TempDir cleanup throws on that lag and reddens the build (ledger 37/38
     * class). We delete best-effort with a short retry, so a lingering handle
     * is waited out, not fatal.
     */
    private Path dir;

    @BeforeEach
    void makeDir() throws IOException {
        dir = Files.createTempDirectory("nmox-jsdebug-test");
    }

    @AfterEach
    void removeDir() {
        if (dir == null) {
            return;
        }
        for (int attempt = 0; attempt < 20; attempt++) {
            try {
                if (!Files.exists(dir)) {
                    return;
                }
                try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> p.toFile().delete());
                }
                if (!Files.exists(dir)) {
                    return;
                }
            } catch (IOException retryable) {
                // a still-releasing Windows handle — wait and try again
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static boolean nodePresent() {
        try {
            return ProcessSupport.runBounded(
                    java.util.List.of("node", "--version"), null,
                    java.time.Duration.ofSeconds(10)).ok();
        } catch (IOException ex) {
            return false;
        }
    }

    @Test
    @DisplayName("start returns once the READY line appears; stop kills the tree")
    void shouldStartAndStop() throws Exception {
        assumeTrue(nodePresent(), "node not installed");
        // mimics dapDebugServer: prints READY, then serves forever
        Path fake = dir.resolve("fake-server.js");
        Files.writeString(fake, """
                console.log('Debug server listening at 127.0.0.1:' + process.argv[2]);
                setInterval(() => {}, 1 << 30);
                """, StandardCharsets.UTF_8);

        JsDebugServer server = JsDebugServer.start(fake.toFile());
        assertThat(server.port()).isPositive();
        server.stop();
        // stop() is a confirmed-dead handshake, not a fire-and-forget kill:
        // on Windows a still-dying node keeps fake-server.js and the cwd
        // locked, and @TempDir cleanup (the very next thing) would fail.
        assertThat(server.processAlive())
                .as("adapter process dead before stop() returns")
                .isFalse();
        server.stop(); // idempotent
    }

    @Test
    @DisplayName("a server that exits immediately fails fast with an honest message")
    void shouldFailFastOnEarlyExit() throws Exception {
        assumeTrue(nodePresent(), "node not installed");
        Path fake = dir.resolve("dies.js");
        Files.writeString(fake, "process.exit(3);", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> JsDebugServer.start(fake.toFile()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("exited immediately");
    }

    @Test
    @DisplayName("a missing script fails fast rather than hanging out the full timeout")
    void shouldFailFastOnMissingScript() {
        assumeTrue(nodePresent(), "node not installed");
        File missing = dir.resolve("nope.js").toFile();
        long start = System.nanoTime();
        assertThatThrownBy(() -> JsDebugServer.start(missing))
                .isInstanceOf(IOException.class);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsedMs).isLessThan(9_000);
    }
}
