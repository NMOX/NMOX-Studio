package org.nmox.studio.tools.npm;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.spi.project.ActionProvider;
import org.nmox.studio.rack.devices.ProjectInspector.ProjectKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The platform Run/Build/Test/Clean actions are only as good as the
 * command they map to. This pins that mapping down per toolchain without
 * starting a single process.
 */
class WebProjectCommandsTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("Node maps to the package.json script that exists")
    void nodeUsesScripts() throws Exception {
        Files.writeString(dir.resolve("package.json"), """
                {"scripts":{"dev":"vite","build":"vite build","test":"vitest run"}}
                """);
        File d = dir.toFile();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.NODE, ActionProvider.COMMAND_RUN))
                .containsExactly("npm", "run", "dev");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.NODE, ActionProvider.COMMAND_BUILD))
                .containsExactly("npm", "run", "build");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.NODE, ActionProvider.COMMAND_TEST))
                .containsExactly("npm", "test");
        // no clean script -> the action is honestly unavailable
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.NODE, ActionProvider.COMMAND_CLEAN))
                .isNull();
    }

    @Test
    @DisplayName("Node actions speak the project's own manager — yarn.lock and corepack pnpm pin (v1.60.0)")
    void nodeManagerTruth() throws Exception {
        File yarnRepo = Files.createDirectory(dir.resolve("yarn-repo")).toFile();
        Files.writeString(yarnRepo.toPath().resolve("package.json"),
                "{\"scripts\":{\"dev\":\"vite\",\"test\":\"vitest\"}}");
        Files.writeString(yarnRepo.toPath().resolve("yarn.lock"), "# yarn lockfile v1");
        assertThat(WebProjectCommands.commandFor(yarnRepo, ProjectKind.NODE, ActionProvider.COMMAND_RUN))
                .containsExactly("yarn", "run", "dev");
        assertThat(WebProjectCommands.commandFor(yarnRepo, ProjectKind.NODE, ActionProvider.COMMAND_TEST))
                .containsExactly("yarn", "test");

        File pnpmPinned = Files.createDirectory(dir.resolve("pnpm-pinned")).toFile();
        Files.writeString(pnpmPinned.toPath().resolve("package.json"),
                "{\"packageManager\":\"pnpm@9.1.0\",\"scripts\":{\"build\":\"tsc\"}}");
        assertThat(WebProjectCommands.commandFor(pnpmPinned, ProjectKind.NODE, ActionProvider.COMMAND_BUILD))
                .containsExactly("pnpm", "run", "build");
    }

    @Test
    @DisplayName("Move actions follow the dialect — Sui by default, Aptos when Move.toml names AptosFramework (v1.142.0)")
    void moveDialectTruth() throws Exception {
        File suiRepo = Files.createDirectory(dir.resolve("sui-repo")).toFile();
        Files.writeString(suiRepo.toPath().resolve("Move.toml"),
                "[package]\nname = \"counter\"\nedition = \"2024.beta\"\n");
        assertThat(WebProjectCommands.commandFor(suiRepo, ProjectKind.MOVE, ActionProvider.COMMAND_BUILD))
                .containsExactly("sui", "move", "build");
        assertThat(WebProjectCommands.commandFor(suiRepo, ProjectKind.MOVE, ActionProvider.COMMAND_TEST))
                .containsExactly("sui", "move", "test");

        File aptosRepo = Files.createDirectory(dir.resolve("aptos-repo")).toFile();
        Files.writeString(aptosRepo.toPath().resolve("Move.toml"), """
                [package]
                name = "counter"

                [dependencies.AptosFramework]
                git = "https://github.com/aptos-labs/aptos-framework.git"
                rev = "mainnet"
                """);
        assertThat(WebProjectCommands.commandFor(aptosRepo, ProjectKind.MOVE, ActionProvider.COMMAND_BUILD))
                .containsExactly("aptos", "move", "compile");
        assertThat(WebProjectCommands.commandFor(aptosRepo, ProjectKind.MOVE, ActionProvider.COMMAND_TEST))
                .containsExactly("aptos", "move", "test");
    }

    @Test
    @DisplayName("Node RUN falls back start -> serve, and is null when none exist")
    void nodeRunFallback() throws Exception {
        // distinct dirs: ProjectInspector caches package.json per path, so
        // rewriting one file in a single mtime tick would read stale.
        File withStart = Files.createDirectory(dir.resolve("with-start")).toFile();
        Files.writeString(withStart.toPath().resolve("package.json"), "{\"scripts\":{\"start\":\"node .\"}}");
        assertThat(WebProjectCommands.commandFor(withStart, ProjectKind.NODE, ActionProvider.COMMAND_RUN))
                .containsExactly("npm", "start");

        File noScripts = Files.createDirectory(dir.resolve("no-scripts")).toFile();
        Files.writeString(noScripts.toPath().resolve("package.json"), "{\"scripts\":{}}");
        assertThat(WebProjectCommands.commandFor(noScripts, ProjectKind.NODE, ActionProvider.COMMAND_RUN))
                .isNull();
    }

    @Test
    @DisplayName("Compiled toolchains get their canonical commands")
    void fixedToolchains() {
        File d = dir.toFile();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.RUST, ActionProvider.COMMAND_TEST))
                .containsExactly("cargo", "test");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.GO, ActionProvider.COMMAND_BUILD))
                .containsExactly("go", "build", "./...");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.MAVEN, ActionProvider.COMMAND_CLEAN))
                .containsExactly("mvn", "clean");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.ELIXIR, ActionProvider.COMMAND_RUN))
                .containsExactly("mix", "run");
    }

    @Test
    @DisplayName("Actions a toolchain can't express return null, not a bogus command")
    void unsupportedIsNull() {
        File d = dir.toFile();
        // Maven has no single 'run'; Python only tests
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.MAVEN, ActionProvider.COMMAND_RUN)).isNull();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.PYTHON, ActionProvider.COMMAND_BUILD)).isNull();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.PYTHON, ActionProvider.COMMAND_TEST))
                .containsExactly("python3", "-m", "pytest");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.NONE, ActionProvider.COMMAND_BUILD)).isNull();
    }

    @Test
    @DisplayName("Node RUN prefers dev, then start, then serve")
    void nodeRunPrefersServeLast() throws Exception {
        // only 'serve' exists — RUN must fall all the way through to it
        File onlyServe = Files.createDirectory(dir.resolve("only-serve")).toFile();
        Files.writeString(onlyServe.toPath().resolve("package.json"),
                "{\"scripts\":{\"serve\":\"http-server\"}}");
        assertThat(WebProjectCommands.commandFor(onlyServe, ProjectKind.NODE, ActionProvider.COMMAND_RUN))
                .containsExactly("npm", "run", "serve");
    }

    @Test
    @DisplayName("Node CLEAN maps to the clean script when it exists")
    void nodeCleanUsesScript() throws Exception {
        File withClean = Files.createDirectory(dir.resolve("with-clean")).toFile();
        Files.writeString(withClean.toPath().resolve("package.json"),
                "{\"scripts\":{\"clean\":\"rimraf dist\"}}");
        assertThat(WebProjectCommands.commandFor(withClean, ProjectKind.NODE, ActionProvider.COMMAND_CLEAN))
                .containsExactly("npm", "run", "clean");
    }

    @Test
    @DisplayName("An unrecognized action on Node returns null rather than guessing")
    void nodeUnknownActionIsNull() throws Exception {
        File d = Files.createDirectory(dir.resolve("node-unknown")).toFile();
        Files.writeString(d.toPath().resolve("package.json"),
                "{\"scripts\":{\"dev\":\"vite\"}}");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.NODE, "nonsense-action")).isNull();
    }

    @Test
    @DisplayName("Gradle maps every action to its wrapper command")
    void gradleFullMapping() {
        File d = dir.toFile();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.GRADLE, ActionProvider.COMMAND_RUN))
                .containsExactly("gradle", "run");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.GRADLE, ActionProvider.COMMAND_BUILD))
                .containsExactly("gradle", "build");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.GRADLE, ActionProvider.COMMAND_TEST))
                .containsExactly("gradle", "test");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.GRADLE, ActionProvider.COMMAND_CLEAN))
                .containsExactly("gradle", "clean");
    }

    @Test
    @DisplayName("Swift and Zig can run/build/test but have no clean action")
    void swiftAndZigLackClean() {
        File d = dir.toFile();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.SWIFT, ActionProvider.COMMAND_RUN))
                .containsExactly("swift", "run");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.SWIFT, ActionProvider.COMMAND_CLEAN)).isNull();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.ZIG, ActionProvider.COMMAND_BUILD))
                .containsExactly("zig", "build");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.ZIG, ActionProvider.COMMAND_TEST))
                .containsExactly("zig", "build", "test");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.ZIG, ActionProvider.COMMAND_CLEAN)).isNull();
    }

    @Test
    @DisplayName("V runs/builds/tests via the v CLI but has no clean action (v1.72.0)")
    void vlangRunsBuildsTests() {
        File d = dir.toFile();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.VLANG, ActionProvider.COMMAND_RUN))
                .containsExactly("v", "run", ".");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.VLANG, ActionProvider.COMMAND_BUILD))
                .containsExactly("v", ".");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.VLANG, ActionProvider.COMMAND_TEST))
                .containsExactly("v", "test", ".");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.VLANG, ActionProvider.COMMAND_CLEAN)).isNull();
    }

    @Test
    @DisplayName("Fortran runs/builds/tests via fpm but has no clean action (v1.73.0)")
    void fortranRunsBuildsTests() {
        File d = dir.toFile();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.FORTRAN, ActionProvider.COMMAND_RUN))
                .containsExactly("fpm", "run");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.FORTRAN, ActionProvider.COMMAND_BUILD))
                .containsExactly("fpm", "build");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.FORTRAN, ActionProvider.COMMAND_TEST))
                .containsExactly("fpm", "test");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.FORTRAN, ActionProvider.COMMAND_CLEAN)).isNull();
    }

    @Test
    @DisplayName("Ada runs/builds via alr; test and clean are null (v1.75.0)")
    void adaRunsAndBuilds() {
        File d = dir.toFile();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.ADA, ActionProvider.COMMAND_RUN))
                .containsExactly("alr", "run");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.ADA, ActionProvider.COMMAND_BUILD))
                .containsExactly("alr", "build");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.ADA, ActionProvider.COMMAND_TEST)).isNull();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.ADA, ActionProvider.COMMAND_CLEAN)).isNull();
    }

    @Test
    @DisplayName("Dart runs and tests but has neither a build nor a clean action")
    void dartRunsAndTestsOnly() {
        File d = dir.toFile();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.DART, ActionProvider.COMMAND_RUN))
                .containsExactly("dart", "run");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.DART, ActionProvider.COMMAND_TEST))
                .containsExactly("dart", "test");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.DART, ActionProvider.COMMAND_BUILD)).isNull();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.DART, ActionProvider.COMMAND_CLEAN)).isNull();
    }

    @Test
    @DisplayName("Make drives run/build/test/clean through the Makefile")
    void makeFullMapping() {
        File d = dir.toFile();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.MAKE, ActionProvider.COMMAND_RUN))
                .containsExactly("make", "run");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.MAKE, ActionProvider.COMMAND_BUILD))
                .containsExactly("make");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.MAKE, ActionProvider.COMMAND_TEST))
                .containsExactly("make", "test");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.MAKE, ActionProvider.COMMAND_CLEAN))
                .containsExactly("make", "clean");
    }

    @Test
    @DisplayName("Foundry builds/tests/cleans with forge; run is honestly absent")
    void foundryMapsToForge() {
        File d = dir.toFile();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.FOUNDRY, ActionProvider.COMMAND_BUILD))
                .containsExactly("forge", "build");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.FOUNDRY, ActionProvider.COMMAND_TEST))
                .containsExactly("forge", "test");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.FOUNDRY, ActionProvider.COMMAND_CLEAN))
                .containsExactly("forge", "clean");
        // deploys are forge scripts, not a single 'run' - the menu greys out
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.FOUNDRY, ActionProvider.COMMAND_RUN))
                .isNull();
    }

    @Test
    @DisplayName("Ruby only exposes a test action, via rake")
    void rubyTestsOnly() {
        File d = dir.toFile();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.RUBY, ActionProvider.COMMAND_TEST))
                .containsExactly("rake", "test");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.RUBY, ActionProvider.COMMAND_RUN)).isNull();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.RUBY, ActionProvider.COMMAND_BUILD)).isNull();
    }

    @Test
    @DisplayName("Webpack builds for production and runs its dev server; test/clean honestly absent")
    void webpackBuildsAndServes() {
        File d = dir.toFile();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.WEBPACK, ActionProvider.COMMAND_BUILD))
                .containsExactly("npx", "webpack", "--mode", "production");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.WEBPACK, ActionProvider.COMMAND_RUN))
                .containsExactly("npx", "webpack", "serve", "--mode", "development");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.WEBPACK, ActionProvider.COMMAND_TEST)).isNull();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.WEBPACK, ActionProvider.COMMAND_CLEAN)).isNull();
    }

    @Test
    @DisplayName("Grunt and Gulp build their default task; run/test/clean grey out")
    void taskRunnersBuildOnly() {
        File d = dir.toFile();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.GRUNT, ActionProvider.COMMAND_BUILD))
                .containsExactly("npx", "grunt");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.GRUNT, ActionProvider.COMMAND_RUN)).isNull();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.GRUNT, ActionProvider.COMMAND_TEST)).isNull();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.GULP, ActionProvider.COMMAND_BUILD))
                .containsExactly("npx", "gulp");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.GULP, ActionProvider.COMMAND_RUN)).isNull();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.GULP, ActionProvider.COMMAND_CLEAN)).isNull();
    }

    @Test
    @DisplayName("Bower is a package manager, not a build system: every action is null")
    void bowerHasNoActions() {
        File d = dir.toFile();
        for (String action : new String[]{ActionProvider.COMMAND_RUN, ActionProvider.COMMAND_BUILD,
            ActionProvider.COMMAND_TEST, ActionProvider.COMMAND_CLEAN}) {
            assertThat(WebProjectCommands.commandFor(d, ProjectKind.BOWER, action))
                    .as("bower " + action).isNull();
        }
    }

    @Test
    @DisplayName("A static site runs by serving the folder — the exact command the rack's lane uses")
    void staticServesTheFolder() {
        File d = dir.toFile();
        // -u is load-bearing: piped python block-buffers the "Serving
        // HTTP on" banner without it (v1.37.0, relearned v1.216.0), and
        // the banner is what triggers the serving announce.
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.STATIC, ActionProvider.COMMAND_RUN))
                .satisfies(cmd -> {
                    // probed port since v1.320.0: shape + range, not a literal
                    java.util.List<String> c = new java.util.ArrayList<>(cmd);
                    org.assertj.core.api.Assertions.assertThat(c.subList(0, 4))
                            .containsExactly("python3", "-u", "-m", "http.server");
                    org.assertj.core.api.Assertions.assertThat(
                            Integer.parseInt(c.get(4))).isBetween(8000, 8019);
                });
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.STATIC, ActionProvider.COMMAND_BUILD)).isNull();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.STATIC, ActionProvider.COMMAND_TEST)).isNull();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.STATIC, ActionProvider.COMMAND_CLEAN)).isNull();
    }

    @Test
    @DisplayName("Indie stacks speak their own toolchains on IDE actions (v1.69.0)")
    void indieStackActions() {
        assertThat(WebProjectCommands.commandFor(dir.toFile(), ProjectKind.JULIA, ActionProvider.COMMAND_TEST))
                .containsExactly("julia", "--project=.", "-e", "using Pkg; Pkg.test()");
        // julia has no standard run: the action greys out honestly
        assertThat(WebProjectCommands.commandFor(dir.toFile(), ProjectKind.JULIA, ActionProvider.COMMAND_RUN))
                .isNull();
        assertThat(WebProjectCommands.commandFor(dir.toFile(), ProjectKind.NIM, ActionProvider.COMMAND_RUN))
                .containsExactly("nimble", "run");
        assertThat(WebProjectCommands.commandFor(dir.toFile(), ProjectKind.DLANG, ActionProvider.COMMAND_BUILD))
                .containsExactly("dub", "build");
        assertThat(WebProjectCommands.commandFor(dir.toFile(), ProjectKind.RACKET, ActionProvider.COMMAND_TEST))
                .containsExactly("raco", "test", ".");
    }

    @Test
    @DisplayName("Functional web IDE actions: elm reactor/make/test, rescript build, spago run (v1.70.0)")
    void functionalWebActions() {
        assertThat(WebProjectCommands.commandFor(dir.toFile(), ProjectKind.ELM, ActionProvider.COMMAND_RUN))
                .containsExactly("npx", "elm", "reactor");
        assertThat(WebProjectCommands.commandFor(dir.toFile(), ProjectKind.RESCRIPT, ActionProvider.COMMAND_BUILD))
                .containsExactly("npx", "rescript", "build");
        // rescript has no standard test runner: greys out honestly
        assertThat(WebProjectCommands.commandFor(dir.toFile(), ProjectKind.RESCRIPT, ActionProvider.COMMAND_TEST))
                .isNull();
        assertThat(WebProjectCommands.commandFor(dir.toFile(), ProjectKind.PURESCRIPT, ActionProvider.COMMAND_RUN))
                .containsExactly("spago", "run");
    }

    @Test
    @DisplayName("Gleam speaks gleam on all four actions (v1.59.0 expansion)")
    void gleamAllFourActions() {
        java.io.File d = new java.io.File(".");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.GLEAM, ActionProvider.COMMAND_RUN))
                .containsExactly("gleam", "run");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.GLEAM, ActionProvider.COMMAND_BUILD))
                .containsExactly("gleam", "build");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.GLEAM, ActionProvider.COMMAND_TEST))
                .containsExactly("gleam", "test");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.GLEAM, ActionProvider.COMMAND_CLEAN))
                .containsExactly("gleam", "clean");
    }

    @Test
    @DisplayName("Aiken: build and run both compile, test runs aiken check (v1.161.0)")
    void aikenCommands() {
        File d = dir.toFile();
        // validators have no run verb — build is the honest "make my code"
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.AIKEN, ActionProvider.COMMAND_RUN))
                .containsExactly("aiken", "build");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.AIKEN, ActionProvider.COMMAND_BUILD))
                .containsExactly("aiken", "build");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.AIKEN, ActionProvider.COMMAND_TEST))
                .containsExactly("aiken", "check");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.AIKEN, ActionProvider.COMMAND_CLEAN))
                .isNull();
    }

    @Test
    @DisplayName("Clarity: build is clarinet check, tests ride the npm harness (v1.161.0)")
    void clarityCommands() throws Exception {
        Files.writeString(dir.resolve("Clarinet.toml"), "[project]\n");
        Files.writeString(dir.resolve("package.json"),
                "{\"scripts\":{\"test\":\"vitest run\"}}");
        File d = dir.toFile();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.CLARITY, ActionProvider.COMMAND_RUN))
                .isNull();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.CLARITY, ActionProvider.COMMAND_BUILD))
                .containsExactly("clarinet", "check");
        // the vitest/simnet harness, in the project's own package manager
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.CLARITY, ActionProvider.COMMAND_TEST))
                .containsExactly("npm", "test");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.CLARITY, ActionProvider.COMMAND_CLEAN))
                .isNull();
    }

    @Test
    @DisplayName(".NET speaks dotnet on all four actions (v1.233.0 — it greyed everything before)")
    void dotnetCommands() {
        File d = dir.toFile();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.DOTNET, ActionProvider.COMMAND_RUN))
                .containsExactly("dotnet", "run");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.DOTNET, ActionProvider.COMMAND_BUILD))
                .containsExactly("dotnet", "build");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.DOTNET, ActionProvider.COMMAND_TEST))
                .containsExactly("dotnet", "test");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.DOTNET, ActionProvider.COMMAND_CLEAN))
                .containsExactly("dotnet", "clean");
    }

    @Test
    @DisplayName("Tact rides the project's own npm scripts, like the kit that ships it")
    void tactCommands() throws Exception {
        Files.writeString(dir.resolve("package.json"),
                "{\"scripts\":{\"build\":\"tact --config tact.config.json\",\"test\":\"jest\"}}");
        File d = dir.toFile();
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.TACT, ActionProvider.COMMAND_BUILD))
                .containsExactly("npm", "run", "build");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.TACT, ActionProvider.COMMAND_TEST))
                .containsExactly("npm", "test");
    }

    @Test
    @DisplayName("CMake plans only what exists: configure without build/, real verbs with it")
    void cmakeCommands() throws Exception {
        File d = dir.toFile();
        // no build/ yet: Build offers the configure step, nothing else guesses
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.CMAKE, ActionProvider.COMMAND_BUILD))
                .containsExactly("cmake", "-B", "build");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.CMAKE, ActionProvider.COMMAND_TEST))
                .isNull();
        Files.createDirectory(dir.resolve("build"));
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.CMAKE, ActionProvider.COMMAND_BUILD))
                .containsExactly("cmake", "--build", "build");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.CMAKE, ActionProvider.COMMAND_TEST))
                .containsExactly("ctest", "--test-dir", "build");
        assertThat(WebProjectCommands.commandFor(d, ProjectKind.CMAKE, ActionProvider.COMMAND_CLEAN))
                .containsExactly("cmake", "--build", "build", "--target", "clean");
    }

    /**
     * Kinds with NO IDE lanes at all, each with its reason. A new kind
     * landing here silently is exactly the v1.163.0 default-null bug —
     * so it can't: the completeness gate below fails the build instead.
     */
    private static final java.util.Map<ProjectKind, String> BLESSED_NO_LANES = java.util.Map.of(
            ProjectKind.BOWER, "a package manager, not a build system — CRATE installs",
            ProjectKind.NONE, "no project, no lanes");

    /** Kinds whose toolchain honestly has no test verb, each with the reason. */
    private static final java.util.Map<ProjectKind, String> BLESSED_NO_TEST = java.util.Map.of(
            ProjectKind.ADA, "alr has no test verb (v1.155.0-era recon)",
            ProjectKind.RESCRIPT, "build-only compiler; tests ride the host npm project",
            ProjectKind.WEBPACK, "a bundler, not a test runner",
            ProjectKind.GRUNT, "a task runner; tasks are project-defined",
            ProjectKind.GULP, "a task runner; tasks are project-defined",
            ProjectKind.STATIC, "a bare index.html has nothing to test");

    @Test
    @DisplayName("EVERY kind either tests through the IDE or is blessed here by name (v1.233.0)")
    void everyKindTestsOrIsBlessed() throws Exception {
        // a fixture rich enough that conditional lanes (scripts, build/,
        // harnesses) take their real branch instead of failing on absence
        Files.writeString(dir.resolve("package.json"),
                "{\"scripts\":{\"dev\":\"x\",\"build\":\"x\",\"test\":\"x\",\"clean\":\"x\"}}");
        Files.createDirectory(dir.resolve("build"));
        File d = dir.toFile();
        for (ProjectKind kind : ProjectKind.values()) {
            if (BLESSED_NO_LANES.containsKey(kind)) {
                for (String action : new String[]{ActionProvider.COMMAND_RUN,
                    ActionProvider.COMMAND_BUILD, ActionProvider.COMMAND_TEST,
                    ActionProvider.COMMAND_CLEAN}) {
                    assertThat(WebProjectCommands.commandFor(d, kind, action))
                            .as(kind + " is blessed laneless: " + BLESSED_NO_LANES.get(kind))
                            .isNull();
                }
                continue;
            }
            // every unblessed kind must offer SOMETHING…
            boolean any = false;
            for (String action : new String[]{ActionProvider.COMMAND_RUN,
                ActionProvider.COMMAND_BUILD, ActionProvider.COMMAND_TEST,
                ActionProvider.COMMAND_CLEAN}) {
                any |= WebProjectCommands.commandFor(d, kind, action) != null;
            }
            assertThat(any)
                    .as(kind + " has no IDE lane at all — wire it or bless it "
                            + "IN THIS TEST with a written reason (the v1.163.0 "
                            + "default-null class, made structural)")
                    .isTrue();
            // …and TESTING specifically runs through the system unless the
            // toolchain honestly lacks a test verb
            if (!BLESSED_NO_TEST.containsKey(kind)) {
                assertThat(WebProjectCommands.commandFor(d, kind, ActionProvider.COMMAND_TEST))
                        .as(kind + " cannot test through the IDE — wire the test "
                                + "lane or bless it with the reason the toolchain "
                                + "has no test verb")
                        .isNotNull();
            }
        }
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("the static lane's probe and the announce's banner-read stay wired (v1.321.0)")
    void probedPortStaysWired() throws Exception {
        // The night-arc review's find: the seam tests (FreePortsTest,
        // BannerPortTest) are green even when the SPAWN site reverts to the
        // pinned constant — on any machine where 8000 is free, probe and
        // constant agree, so only a wiring gate diverges. Pin both halves.
        String commands = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/org/nmox/studio/tools/npm/WebProjectCommands.java"));
        org.assertj.core.api.Assertions.assertThat(commands)
                .as("the IDE Run button's static lane probes for a free port"
                        + " — python's http.server has no upward scan")
                .contains("firstFreeFrom");
        String provider = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/org/nmox/studio/tools/npm/WebProjectActionProvider.java"));
        org.assertj.core.api.Assertions.assertThat(provider)
                .as("the announce reads the port the banner NAMES, so a"
                        + " shifted port registers truthfully")
                .contains("bannerPort");
    }
}
