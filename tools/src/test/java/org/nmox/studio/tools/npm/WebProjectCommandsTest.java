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
}
