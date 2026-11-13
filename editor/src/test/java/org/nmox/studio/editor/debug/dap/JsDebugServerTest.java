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

    // ---- the Node floor (js-debug v1.140.1: the bundle does not load under Node 18) ----

    @Test
    @DisplayName("the major is read from what node --version prints, and only from a version")
    void nodeMajorIsReadFromAVersion() {
        assertThat(JsDebugServer.nodeMajor("v18.20.8\n")).isEqualTo(18);
        assertThat(JsDebugServer.nodeMajor("v22.9.0")).isEqualTo(22);
        assertThat(JsDebugServer.nodeMajor("  v20.0.0-nightly2023\n")).isEqualTo(20);
        assertThat(JsDebugServer.nodeMajor("24.4.1")).isEqualTo(24);
        assertThat(JsDebugServer.nodeMajor("")).isEqualTo(-1);
        assertThat(JsDebugServer.nodeMajor(null)).isEqualTo(-1);
        assertThat(JsDebugServer.nodeMajor("node: command not found")).isEqualTo(-1);
        assertThat(JsDebugServer.nodeMajor("Welcome v18")).isEqualTo(-1);
    }

    /**
     * A stand-in for the user's {@code node}: answers {@code --version}
     * with {@code version}, and anything else — which is to say, being
     * asked to run the adapter — by leaving {@code marker} and exiting 3.
     */
    private File standInNode(String version, Path marker) throws IOException {
        Path node = dir.resolve("node-" + version.replaceAll("[^A-Za-z0-9.]", "_"));
        Files.writeString(node, "#!/bin/sh\n"
                + "if [ \"$1\" = \"--version\" ]; then echo '" + version + "'; exit 0; fi\n"
                + "echo ran > '" + marker.toAbsolutePath() + "'\n"
                + "exit 3\n", StandardCharsets.UTF_8);
        assumeTrue(node.toFile().setExecutable(true), "cannot mark a stand-in executable here");
        return node.toFile();
    }

    private static boolean posix() {
        return !System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    @Test
    @DisplayName("a Node older than the adapter loads under is refused by name, before the adapter is started")
    void oldNodeIsRefusedByNameBeforeAnySpawn() throws Exception {
        assumeTrue(posix(), "the stand-in node is a shell script");
        Path marker = dir.resolve("adapter-was-started");
        File node = standInNode("v18.20.8", marker);
        Path adapter = dir.resolve("adapter.js");
        Files.writeString(adapter, "// never run", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> JsDebugServer.start(adapter.toFile(), node.getAbsolutePath()))
                .isInstanceOfSatisfying(JsDebugServer.NodeTooOld.class, old -> {
                    assertThat(old.found()).isEqualTo("v18.20.8");
                    assertThat(old.needed()).isEqualTo(JsDebugServer.MIN_NODE_MAJOR);
                    assertThat(old.getMessage()).contains("v18.20.8").contains("Node.js 20");
                });
        assertThat(marker).as("the adapter must not be started under a Node it cannot load under")
                .doesNotExist();
    }

    @Test
    @DisplayName("a Node at the floor is not refused: the same command goes on to start the adapter")
    void nodeAtTheFloorStartsTheAdapter() throws Exception {
        assumeTrue(posix(), "the stand-in node is a shell script");
        Path marker = dir.resolve("adapter-was-started");
        File node = standInNode("v" + JsDebugServer.MIN_NODE_MAJOR + ".0.0", marker);
        Path adapter = dir.resolve("adapter.js");
        Files.writeString(adapter, "// the stand-in exits 3 instead of running this", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> JsDebugServer.start(adapter.toFile(), node.getAbsolutePath()))
                .isInstanceOf(IOException.class)
                .isNotInstanceOf(JsDebugServer.NodeTooOld.class)
                .hasMessageContaining("exited immediately");
        assertThat(marker).as("the launch went ahead").exists();
    }

    @Test
    @DisplayName("a node that does not say a version is not refused on a guess")
    void unreadableVersionIsNotARefusal() throws Exception {
        assumeTrue(posix(), "the stand-in node is a shell script");
        Path marker = dir.resolve("adapter-was-started");
        File node = standInNode("custom build, no number", marker);
        Path adapter = dir.resolve("adapter.js");
        Files.writeString(adapter, "// the stand-in exits 3 instead of running this", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> JsDebugServer.start(adapter.toFile(), node.getAbsolutePath()))
                .isNotInstanceOf(JsDebugServer.NodeTooOld.class);
        assertThat(marker).as("left to the launch, which reports its own failures").exists();
    }
}
