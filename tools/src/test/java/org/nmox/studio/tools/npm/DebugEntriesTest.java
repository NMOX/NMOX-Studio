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

    /**
     * The entry as the guard answers it: the CANONICAL file (ledger 111 —
     * the check and the launch must name the same file), which is what
     * these assertions compare against. They used to compare the path as
     * spelled, which is exactly the property the symlink law removed.
     */
    private static File entry(Path dir, String rel) throws Exception {
        return new File(dir.toFile(), rel).getCanonicalFile();
    }

    @Test
    @DisplayName("the start script's node target wins, flags and a relative prefix tolerated")
    void shouldReadTheStartScript(@TempDir Path tmp) throws Exception {
        Path a = node(tmp, "{\"scripts\":{\"start\":\"node server.js\"},\"main\":\"index.js\"}", "server.js", "index.js");
        assertThat(DebugEntries.mainEntry(a.toFile(), ProjectKind.NODE)).isEqualTo(entry(a, "server.js"));
        Path b = node(tmp, "{\"scripts\":{\"start\":\"node --enable-source-maps ./src/app.mjs\"}}");
        Files.createDirectories(b.resolve("src"));
        Files.writeString(b.resolve("src/app.mjs"), "1");
        assertThat(DebugEntries.mainEntry(b.toFile(), ProjectKind.NODE)).isEqualTo(entry(b, "src/app.mjs"));
    }

    @Test
    @DisplayName("a start script that is not a node file (a dev server, nodemon, a shell pipeline) names nothing; main and index.js follow")
    void shouldFallBackFromStartToMainToIndex(@TempDir Path tmp) throws Exception {
        Path dev = node(tmp, "{\"scripts\":{\"start\":\"vite\"},\"main\":\"lib.js\"}", "lib.js");
        assertThat(DebugEntries.mainEntry(dev.toFile(), ProjectKind.NODE))
                .as("start is not node: fall through to main").isEqualTo(entry(dev, "lib.js"));
        Path nodemon = node(tmp, "{\"scripts\":{\"start\":\"nodemon server.js\"}}", "server.js", "index.js");
        assertThat(DebugEntries.mainEntry(nodemon.toFile(), ProjectKind.NODE))
                .as("nodemon is not node; no main; Node's default index.js").isEqualTo(entry(nodemon, "index.js"));
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

    /** A platform without symlinks has nothing to prove in the walk below. */
    private static boolean linked(Path from, Path to) {
        try {
            Files.createSymbolicLink(from, to);
            return true;
        } catch (UnsupportedOperationException | java.io.IOException noSymlinks) {
            return false;
        }
    }

    @Test
    @DisplayName("a symlinked entry: one leaving the project is refused, one staying inside answers the file it judged")
    void shouldAnswerTheFileItJudged(@TempDir Path tmp) throws Exception {
        // a debug launch is a SPAWN, and the file the guard judged must be
        // the file the debugger opens (ledger 111): this used to judge the
        // canonical path and hand back the path as spelled
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        Files.writeString(outside.resolve("secret.js"), "1");

        Path escaping = node(tmp, "{\"main\":\"entry.js\"}");
        if (!linked(escaping.resolve("entry.js"), outside.resolve("secret.js"))) {
            return;
        }
        assertThat(DebugEntries.mainEntry(escaping.toFile(), ProjectKind.NODE))
                .as("a repo cannot point the debugger outside itself through a link")
                .isNull();

        Path staying = node(tmp, "{\"scripts\":{\"start\":\"node link/app.js\"}}");
        Files.createDirectories(staying.resolve("real"));
        Files.writeString(staying.resolve("real/app.js"), "1");
        if (!linked(staying.resolve("link"), staying.resolve("real"))) {
            return;
        }
        File answered = DebugEntries.mainEntry(staying.toFile(), ProjectKind.NODE);
        assertThat(answered).isNotNull();
        assertThat(answered.getPath())
                .as("the launcher must be handed the file the check looked at")
                .doesNotContain("link")
                .contains("real");
        assertThat(answered).isEqualTo(entry(staying, "real/app.js"));
    }

    @Test
    @DisplayName("an absolute entry is JOINED under the project, never honored as a way around the guard")
    void shouldJoinAnAbsoluteEntry(@TempDir Path tmp) throws Exception {
        Path proj = node(tmp, "{\"main\":\"/etc/passwd\"}");
        assertThat(DebugEntries.mainEntry(proj.toFile(), ProjectKind.NODE))
                .as("joined to <project>/etc/passwd, which does not exist")
                .isNull();
        // and when the project really holds that path, it is an ordinary
        // contained file — npm's own spec makes main relative to the root
        Files.createDirectories(proj.resolve("etc"));
        Files.writeString(proj.resolve("etc/passwd"), "mine");
        assertThat(DebugEntries.mainEntry(proj.toFile(), ProjectKind.NODE))
                .isEqualTo(entry(proj, "etc/passwd"));
    }

    @Test
    @DisplayName("Go: main.go, else the first root source; other kinds name nothing")
    void shouldHandleGoAndTheRest(@TempDir Path tmp) throws Exception {
        Path go = Files.createDirectories(tmp.resolve("go"));
        Files.writeString(go.resolve("go.mod"), "module x");
        Files.writeString(go.resolve("util.go"), "package main");
        Files.writeString(go.resolve("main.go"), "package main");
        // Go's entry is found by listing the package dir, not by resolving a
        // caller's string, so it is not a containment question and keeps the
        // File the listing named
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
