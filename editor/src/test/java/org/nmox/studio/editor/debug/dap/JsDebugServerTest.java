package org.nmox.studio.editor.debug.dap;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

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
    void shouldStartAndStop(@TempDir Path dir) throws Exception {
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
        server.stop(); // idempotent
    }

    @Test
    @DisplayName("a server that exits immediately fails fast with an honest message")
    void shouldFailFastOnEarlyExit(@TempDir Path dir) throws Exception {
        assumeTrue(nodePresent(), "node not installed");
        Path fake = dir.resolve("dies.js");
        Files.writeString(fake, "process.exit(3);", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> JsDebugServer.start(fake.toFile()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("exited immediately");
    }

    @Test
    @DisplayName("a missing script fails fast rather than hanging out the full timeout")
    void shouldFailFastOnMissingScript(@TempDir Path dir) {
        assumeTrue(nodePresent(), "node not installed");
        File missing = dir.resolve("nope.js").toFile();
        long start = System.nanoTime();
        assertThatThrownBy(() -> JsDebugServer.start(missing))
                .isInstanceOf(IOException.class);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsedMs).isLessThan(9_000);
    }
}
