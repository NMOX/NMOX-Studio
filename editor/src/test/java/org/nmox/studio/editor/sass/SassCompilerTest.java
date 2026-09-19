package org.nmox.studio.editor.sass;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Sass compile engine's rules (v1.230.0): the sibling-output
 * derivation, the partial refusal, the pinned argv (no source map,
 * absolute in/out), and the failure path carrying sass's own first
 * error line — all through the Runner seam, no real sass needed.
 */
class SassCompilerTest {

    @TempDir
    File dir;

    @Test
    @DisplayName("output is the sibling .css: extension swapped, directory kept")
    void outputSibling() {
        assertThat(SassCompiler.outputFor(new File(dir, "style.scss")))
                .isEqualTo(new File(dir, "style.css"));
        assertThat(SassCompiler.outputFor(new File(dir, "main.sass")))
                .isEqualTo(new File(dir, "main.css"));
        assertThat(SassCompiler.outputFor(new File(dir, "no-extension")))
                .isEqualTo(new File(dir, "no-extension.css"));
    }

    @Test
    @DisplayName("partials are refused before any spawn: they are imports, not entry points")
    void partialsRefused() {
        List<List<String>> spawns = new ArrayList<>();
        SassCompiler compiler = new SassCompiler((cmd, wd) -> {
            spawns.add(cmd);
            return new SassCompiler.Exec(0, "");
        });
        SassCompiler.Result result = compiler.compile(new File(dir, "_mixins.scss"));
        assertThat(result.outcome()).isEqualTo(SassCompiler.Outcome.PARTIAL);
        assertThat(spawns).isEmpty();
    }

    /**
     * The argv this test is named for is asserted on EVERY machine.
     *
     * <p>Until v2.186.0 the assertions sat inside an
     * {@code if (outcome == COMPILED)} because the binary was resolved
     * before the injected runner, so on any box without dart-sass —
     * which is all three CI lanes — this test proved only that the
     * compiler did nothing. The resolver is a seam now (ledger 115).
     */
    @Test
    @DisplayName("the argv is pinned: sass --no-source-map <in> <out>, in the file's dir")
    void argvPinned() throws Exception {
        File scss = new File(dir, "style.scss");
        List<List<String>> spawns = new ArrayList<>();
        List<File> workDirs = new ArrayList<>();
        SassCompiler compiler = new SassCompiler((cmd, wd) -> {
            spawns.add(cmd);
            workDirs.add(wd);
            return new SassCompiler.Exec(0, "");
        }, startDir -> "/opt/sass/bin/sass");
        SassCompiler.Result result = compiler.compile(scss);
        assertThat(result.outcome()).isEqualTo(SassCompiler.Outcome.COMPILED);
        assertThat(spawns).hasSize(1);
        assertThat(spawns.get(0)).containsExactly(
                "/opt/sass/bin/sass",
                "--no-source-map",
                scss.getAbsolutePath(),
                new File(dir, "style.css").getAbsolutePath());
        assertThat(workDirs.get(0)).isEqualTo(dir);
        assertThat(result.output()).isEqualTo(new File(dir, "style.css"));
    }

    @Test
    @DisplayName("no sass anywhere refuses honestly, before any spawn")
    void noSassRefusesBeforeSpawn() {
        List<List<String>> spawns = new ArrayList<>();
        SassCompiler compiler = new SassCompiler((cmd, wd) -> {
            spawns.add(cmd);
            return new SassCompiler.Exec(0, "");
        }, startDir -> null);
        SassCompiler.Result result = compiler.compile(new File(dir, "style.scss"));
        assertThat(result.outcome()).isEqualTo(SassCompiler.Outcome.NO_SASS);
        assertThat(result.output()).isNull();
        assertThat(spawns).isEmpty();
    }

    /**
     * The v1.102.0 law at this binary: a committed
     * {@code node_modules/.bin/sass} is a cloned repo's code, so it is
     * used only when the workspace is TRUSTED. An untrusted workspace
     * falls through to the user's own global tool — and on a box with
     * no global sass that is an honest null, never the project's.
     */
    @Test
    @DisplayName("the project-local sass is used only when the workspace is trusted")
    void localBinaryNeedsTrust() throws Exception {
        org.nmox.studio.rack.service.WorkspaceTrust.clearForTest();
        File bin = new File(dir, "node_modules/.bin");
        assertThat(bin.mkdirs()).isTrue();
        File sass = new File(bin, "sass");
        assertThat(sass.createNewFile()).isTrue();
        assertThat(sass.setExecutable(true)).isTrue();

        String untrusted = SassCompiler.resolveBinary(dir);
        assertThat(untrusted)
                .as("an untrusted workspace never runs its own committed sass")
                .isNotEqualTo(sass.getAbsolutePath());

        org.nmox.studio.rack.service.WorkspaceTrust.trust(dir);
        assertThat(SassCompiler.resolveBinary(dir))
                .as("a trusted workspace prefers the project's own sass")
                .isEqualTo(sass.getAbsolutePath());
        org.nmox.studio.rack.service.WorkspaceTrust.clearForTest();
    }

    @Test
    @DisplayName("the real runner captures stderr and the exit code, cross-platform")
    void realRunnerCapturesStderr() throws Exception {
        String javaExe = new File(new File(System.getProperty("java.home"), "bin"),
                System.getProperty("os.name").toLowerCase().contains("win")
                        ? "java.exe" : "java").getAbsolutePath();
        // java -version prints its banner to STDERR — exactly the stream
        // sass reports errors on, so this exercises the capture for real
        SassCompiler.Exec exec = SassCompiler.exec(
                java.util.List.of(javaExe, "-version"), dir);
        assertThat(exec.exitCode()).isZero();
        assertThat(exec.stderr()).containsIgnoringCase("version");
    }

    @Test
    @DisplayName("the project-local binary is found inside the repo, never past .git")
    void localBinaryBounded() throws Exception {
        File bin = new File(dir, "node_modules/.bin");
        assertThat(bin.mkdirs()).isTrue();
        File cmd = new File(bin, "sass.cmd");
        assertThat(cmd.createNewFile()).isTrue();
        assertThat(SassCompiler.findLocalBinary(dir)).isEqualTo(cmd.getAbsolutePath());
        // a child checkout with its own .git never inherits the parent's sass
        File child = new File(dir, "child");
        assertThat(new File(child, ".git").mkdirs()).isTrue();
        assertThat(SassCompiler.findLocalBinary(child)).isNull();
    }

    @Test
    @DisplayName("failure carries sass's first stderr line, capped for a status bar")
    void failureFirstLine() {
        assertThat(SassCompiler.firstLine("Error: Undefined variable.\n  ╷\n3 │ color: $x"))
                .isEqualTo("Error: Undefined variable.");
        assertThat(SassCompiler.firstLine("")).isEqualTo("sass failed");
        assertThat(SassCompiler.firstLine(null)).isEqualTo("sass failed");
        assertThat(SassCompiler.firstLine("x".repeat(500)))
                .hasSize(SassCompiler.ERROR_SNIPPET_CHARS);
    }
    @Test
    @DisplayName("a crashing sass's stack-path head yields to the Error line")
    void firstLinePrefersTheErrorLine() {
        String crash = "/Users/x/lib/node_modules/sass/sass.js:315\n"
                + "undefined\n"
                + "             ^\n"
                + "\n"
                + "Error [ERR_REQUIRE_ESM]: require() of ES Module chokidar not supported.\n"
                + "    at TracingChannel.traceSync (node:diagnostics_channel:315:14)";
        assertThat(SassCompiler.firstLine(crash))
                .startsWith("Error [ERR_REQUIRE_ESM]");
        // an ordinary sass error still reads its own first line
        assertThat(SassCompiler.firstLine("Error: expected \":\".\n  ╷\n3 │ x\n"))
                .startsWith("Error: expected");
        // no Error line anywhere: the literal head, never silence
        assertThat(SassCompiler.firstLine("something odd\nmore"))
                .isEqualTo("something odd");
    }

}
