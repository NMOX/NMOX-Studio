package org.nmox.studio.tools.npm;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.devices.ProjectInspector.ProjectKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What "Debug Main Project" debugs, read from the toolchain's own contract
 * and never guessed past it (v2.158.0): for a Node project the file its
 * {@code start} script runs under {@code node}, else its {@code main},
 * else Node's own default {@code index.js}; for a Go project the package
 * directory's {@code main.go} (delve debugs the directory, so any root
 * source names it); nothing for the kinds whose Run names no program.
 */
class DebugEntriesTest {

    private static Path node(Path tmp, String packageJson, String... files) throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("p" + packageJson.hashCode()));
        Files.writeString(dir.resolve("package.json"), packageJson);
        for (String f : files) {
            Files.writeString(dir.resolve(f), "// " + f);
        }
        return dir;
    }

    @Test
    @DisplayName("the start script's node target wins, flags and a relative prefix tolerated")
    void shouldReadTheStartScript(@TempDir Path tmp) throws Exception {
        Path a = node(tmp, "{\"scripts\":{\"start\":\"node server.js\"},\"main\":\"index.js\"}", "server.js", "index.js");
        assertThat(DebugEntries.mainEntry(a.toFile(), ProjectKind.NODE)).isEqualTo(new File(a.toFile(), "server.js"));
        Path b = node(tmp, "{\"scripts\":{\"start\":\"node --enable-source-maps ./src/app.mjs\"}}");
        Files.createDirectories(b.resolve("src"));
        Files.writeString(b.resolve("src/app.mjs"), "1");
        assertThat(DebugEntries.mainEntry(b.toFile(), ProjectKind.NODE)).isEqualTo(new File(b.toFile(), "src/app.mjs"));
    }

    @Test
    @DisplayName("a start script that is not a node file (a dev server, nodemon, a shell pipeline) names nothing; main and index.js follow")
    void shouldFallBackFromStartToMainToIndex(@TempDir Path tmp) throws Exception {
        Path dev = node(tmp, "{\"scripts\":{\"start\":\"vite\"},\"main\":\"lib.js\"}", "lib.js");
        assertThat(DebugEntries.mainEntry(dev.toFile(), ProjectKind.NODE))
                .as("start is not node: fall through to main").isEqualTo(new File(dev.toFile(), "lib.js"));
        Path nodemon = node(tmp, "{\"scripts\":{\"start\":\"nodemon server.js\"}}", "server.js", "index.js");
        assertThat(DebugEntries.mainEntry(nodemon.toFile(), ProjectKind.NODE))
                .as("nodemon is not node; no main; Node's default index.js").isEqualTo(new File(nodemon.toFile(), "index.js"));
        Path pipeline = node(tmp, "{\"scripts\":{\"start\":\"node build.js && node server.js\"}}", "build.js", "server.js");
        assertThat(DebugEntries.mainEntry(pipeline.toFile(), ProjectKind.NODE))
                .as("a shell pipeline is not one program").isNull();
        Path none = node(tmp, "{\"name\":\"x\"}", "app.js");
        assertThat(DebugEntries.mainEntry(none.toFile(), ProjectKind.NODE)).as("no contract, no guess").isNull();
    }

    @Test
    @DisplayName("an entry that does not exist on disk is refused, and a path outside the project is refused")
    void shouldRefuseMissingAndEscapingEntries(@TempDir Path tmp) throws Exception {
        Path missing = node(tmp, "{\"main\":\"gone.js\"}");
        assertThat(DebugEntries.mainEntry(missing.toFile(), ProjectKind.NODE)).isNull();
        // the file EXISTS one level above the project, so only containment can refuse it
        Path escaping = node(tmp, "{\"scripts\":{\"start\":\"node ../outside.js\"}}");
        Files.writeString(tmp.resolve("outside.js"), "1");
        assertThat(DebugEntries.mainEntry(escaping.toFile(), ProjectKind.NODE)).isNull();
    }

    @Test
    @DisplayName("Go: main.go, else the first root source; other kinds name nothing")
    void shouldHandleGoAndTheRest(@TempDir Path tmp) throws Exception {
        Path go = Files.createDirectories(tmp.resolve("go"));
        Files.writeString(go.resolve("go.mod"), "module x");
        Files.writeString(go.resolve("util.go"), "package main");
        Files.writeString(go.resolve("main.go"), "package main");
        assertThat(DebugEntries.mainEntry(go.toFile(), ProjectKind.GO)).isEqualTo(new File(go.toFile(), "main.go"));
        Files.delete(go.resolve("main.go"));
        assertThat(DebugEntries.mainEntry(go.toFile(), ProjectKind.GO)).isEqualTo(new File(go.toFile(), "util.go"));
        Path py = Files.createDirectories(tmp.resolve("py"));
        Files.writeString(py.resolve("main.py"), "print(1)");
        assertThat(DebugEntries.mainEntry(py.toFile(), ProjectKind.PYTHON)).as("Run names no program for Python; neither does Debug").isNull();
        assertThat(DebugEntries.mainEntry(py.toFile(), ProjectKind.RUST)).isNull();
        assertThat(DebugEntries.mainEntry(py.toFile(), null)).isNull();
    }
}
