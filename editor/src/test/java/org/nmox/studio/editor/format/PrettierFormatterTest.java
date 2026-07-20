package org.nmox.studio.editor.format;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The formatter's promise is "the project's prettier, or nothing":
 * these tests pin the opt-in walk, the local-binary preference, and
 * every failure mode collapsing to null (save proceeds untouched).
 */
class PrettierFormatterTest {

    @TempDir
    Path root;

    // ---- opt-in detection --------------------------------------------------

    @Test
    @DisplayName("A .prettierrc anywhere up the tree opts the project in")
    void configFileOptsIn() throws IOException {
        Files.createFile(root.resolve(".prettierrc"));
        Path nested = Files.createDirectories(root.resolve("src/components"));

        assertThat(PrettierFormatter.projectOptedIn(nested.toFile())).isTrue();
    }

    @Test
    @DisplayName("prettier.config.mjs counts too — every documented config name does")
    void modernConfigNamesCount() throws IOException {
        for (String name : PrettierFormatter.CONFIG_FILES) {
            Path dir = Files.createDirectories(root.resolve("case-" + name.replace('.', '_')));
            Files.createFile(dir.resolve(name));
            assertThat(PrettierFormatter.projectOptedIn(dir.toFile()))
                    .as("config file %s should opt the project in", name)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("package.json mentioning prettier (dep or options key) opts in")
    void packageJsonOptsIn() throws IOException {
        Files.writeString(root.resolve("package.json"),
                "{ \"devDependencies\": { \"prettier\": \"^3.3.0\" } }");

        assertThat(PrettierFormatter.projectOptedIn(root.toFile())).isTrue();
    }

    @Test
    @DisplayName("A nested package.json without prettier does not stop the walk to a root config")
    void monorepoWalksPastSilentPackageJson() throws IOException {
        Files.createFile(root.resolve(".prettierrc.json"));
        Path pkg = Files.createDirectories(root.resolve("packages/web"));
        Files.writeString(pkg.resolve("package.json"), "{ \"name\": \"web\" }");

        assertThat(PrettierFormatter.projectOptedIn(pkg.toFile())).isTrue();
    }

    @Test
    @DisplayName("No config and no prettier mention anywhere: not opted in")
    void bareProjectIsNotOptedIn() throws IOException {
        Path dir = Files.createDirectories(root.resolve("plain"));
        Files.writeString(dir.resolve("package.json"), "{ \"name\": \"plain\" }");

        // the walk continues above the temp dir; a stray config there would be
        // a test-environment accident, not something this repo can create
        assertThat(PrettierFormatter.projectOptedIn(dir.toFile())).isFalse();
    }

    @Test
    @DisplayName("A config above the repository boundary (.git) does not opt the project in")
    void configAboveRepoBoundaryDoesNotCount() throws IOException {
        Files.createFile(root.resolve(".prettierrc")); // e.g. a personal ~/.prettierrc
        Path repo = Files.createDirectories(root.resolve("repo"));
        Files.createDirectories(repo.resolve(".git"));
        Path src = Files.createDirectories(repo.resolve("src"));

        assertThat(PrettierFormatter.projectOptedIn(src.toFile())).isFalse();
    }

    @Test
    @DisplayName("A config at the repository root itself opts in — the boundary is inclusive")
    void configAtRepoRootCounts() throws IOException {
        Path repo = Files.createDirectories(root.resolve("repo"));
        Files.createDirectories(repo.resolve(".git"));
        Files.createFile(repo.resolve(".prettierrc"));
        Path src = Files.createDirectories(repo.resolve("src"));

        assertThat(PrettierFormatter.projectOptedIn(src.toFile())).isTrue();
    }

    @Test
    @DisplayName("A .git file (worktree/submodule) bounds the walk just like a .git directory")
    void gitFileBoundsWalkToo() throws IOException {
        Files.createFile(root.resolve(".prettierrc"));
        Path worktree = Files.createDirectories(root.resolve("wt"));
        Files.writeString(worktree.resolve(".git"), "gitdir: ../repo/.git/worktrees/wt\n");

        assertThat(PrettierFormatter.projectOptedIn(worktree.toFile())).isFalse();
    }

    // ---- binary resolution -------------------------------------------------

    @Test
    @DisplayName("A node_modules above the repository boundary is not the project's binary")
    void localBinaryStopsAtRepoBoundary() throws IOException {
        Path bin = Files.createDirectories(root.resolve("node_modules/.bin"));
        Path stray = Files.createFile(bin.resolve("prettier"));
        assertThat(stray.toFile().setExecutable(true)).isTrue();
        Path repo = Files.createDirectories(root.resolve("repo"));
        Files.createDirectories(repo.resolve(".git"));

        assertThat(PrettierFormatter.findLocalBinary(repo.toFile())).isNull();
    }

    @Test
    @DisplayName("The nearest node_modules/.bin/prettier wins, found from a nested dir")
    void localBinaryPreferred() throws IOException {
        Path bin = Files.createDirectories(root.resolve("node_modules/.bin"));
        Path prettier = Files.createFile(bin.resolve("prettier"));
        assertThat(prettier.toFile().setExecutable(true)).isTrue();
        Path nested = Files.createDirectories(root.resolve("src/deep"));

        assertThat(PrettierFormatter.findLocalBinary(nested.toFile()))
                .isEqualTo(prettier.toFile().getAbsolutePath());
    }

    @Test
    @DisplayName("No local install anywhere up the tree: findLocalBinary is null")
    void noLocalBinary() {
        assertThat(PrettierFormatter.findLocalBinary(root.toFile())).isNull();
    }

    // ---- the format run ----------------------------------------------------

    @org.junit.jupiter.api.AfterEach
    void clearTrust() {
        org.nmox.studio.rack.service.WorkspaceTrust.clearForTest();
    }

    private PrettierFormatter withStub(List<List<String>> commands, PrettierFormatter.Result result)
            throws IOException {
        Files.createFile(root.resolve(".prettierrc"));
        Path bin = Files.createDirectories(root.resolve("node_modules/.bin"));
        File prettier = bin.resolve("prettier").toFile();
        Files.createFile(prettier.toPath());
        assertThat(prettier.setExecutable(true)).isTrue();
        // using the project-LOCAL binary now requires the workspace to be
        // trusted (v1.102.0 RCE gate); a real user opted in, so does the test
        org.nmox.studio.rack.service.WorkspaceTrust.trust(root.toFile());
        return new PrettierFormatter((command, workDir, stdin) -> {
            commands.add(command);
            return result;
        });
    }

    @Test
    @DisplayName("Happy path: formatted text comes back, invoked as prettier --stdin-filepath <file>")
    void formatsThroughStdin() throws IOException {
        List<List<String>> commands = new ArrayList<>();
        PrettierFormatter formatter =
                withStub(commands, new PrettierFormatter.Result(0, "const x = 1;\n"));
        File file = root.resolve("index.js").toFile();

        String out = formatter.format("const x=1", file);

        assertThat(out).isEqualTo("const x = 1;\n");
        assertThat(commands).hasSize(1);
        assertThat(commands.get(0).get(0)).endsWith("prettier");
        assertThat(commands.get(0)).containsSequence("--stdin-filepath", file.getAbsolutePath());
    }

    @Test
    @DisplayName("Already-formatted text returns null so the save writes nothing new")
    void unchangedTextIsNull() throws IOException {
        PrettierFormatter formatter =
                withStub(new ArrayList<>(), new PrettierFormatter.Result(0, "const x = 1;\n"));

        assertThat(formatter.format("const x = 1;\n", root.resolve("a.js").toFile())).isNull();
    }

    @Test
    @DisplayName("A non-zero exit (syntax error mid-edit) is a quiet no-op")
    void syntaxErrorIsQuiet() throws IOException {
        PrettierFormatter formatter =
                withStub(new ArrayList<>(), new PrettierFormatter.Result(2, ""));

        assertThat(formatter.format("const const", root.resolve("a.js").toFile())).isNull();
    }

    @Test
    @DisplayName("A runner that throws IOException degrades to null, not an exception")
    void ioFailureIsQuiet() throws IOException {
        Files.createFile(root.resolve(".prettierrc"));
        Path bin = Files.createDirectories(root.resolve("node_modules/.bin"));
        File prettier = bin.resolve("prettier").toFile();
        Files.createFile(prettier.toPath());
        assertThat(prettier.setExecutable(true)).isTrue();
        PrettierFormatter formatter = new PrettierFormatter((command, workDir, stdin) -> {
            throw new IOException("boom");
        });

        assertThat(formatter.format("x", root.resolve("a.js").toFile())).isNull();
    }

    @Test
    @DisplayName("A project that never opted in never spawns a process")
    void noOptInNoProcess() {
        AtomicInteger runs = new AtomicInteger();
        PrettierFormatter formatter = new PrettierFormatter((command, workDir, stdin) -> {
            runs.incrementAndGet();
            return new PrettierFormatter.Result(0, "formatted");
        });

        assertThat(formatter.format("x", root.resolve("a.js").toFile())).isNull();
        assertThat(runs).hasValue(0);
    }

    @Test
    @DisplayName("The real process runner feeds stdin and drains stdout (fake prettier script)")
    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void realRunnerRoundTrip() throws IOException {
        Files.createFile(root.resolve(".prettierrc"));
        Path bin = Files.createDirectories(root.resolve("node_modules/.bin"));
        Path script = bin.resolve("prettier");
        Files.writeString(script, "#!/bin/sh\ntr 'a-z' 'A-Z'\n");
        assertThat(script.toFile().setExecutable(true)).isTrue();
        // the local binary path is trust-gated now (v1.102.0)
        org.nmox.studio.rack.service.WorkspaceTrust.trust(root.toFile());

        String out = new PrettierFormatter().format("shout", root.resolve("a.js").toFile());

        assertThat(out).isEqualTo("SHOUT");
    }

    @Test
    @DisplayName("Oversized documents skip formatting rather than stall the save")
    void oversizedDocumentSkips() throws IOException {
        AtomicInteger runs = new AtomicInteger();
        Files.createFile(root.resolve(".prettierrc"));
        PrettierFormatter formatter = new PrettierFormatter((command, workDir, stdin) -> {
            runs.incrementAndGet();
            return new PrettierFormatter.Result(0, "formatted");
        });

        String huge = "x".repeat(PrettierFormatter.MAX_CHARS + 1);
        assertThat(formatter.format(huge, root.resolve("a.js").toFile())).isNull();
        assertThat(runs).hasValue(0);
    }
}
