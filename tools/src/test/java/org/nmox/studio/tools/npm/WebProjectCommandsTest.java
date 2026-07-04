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
}
