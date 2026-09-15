package org.nmox.studio.tools.npm;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.spi.project.ActionProvider;
import org.netbeans.spi.project.ProjectState;
import org.nmox.studio.core.spi.DebugLauncher;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The platform's Debug ▸ Debug Main Project row and the toolbar's bug
 * button on a web project (v2.158.0): {@code debug} is supported, enabled
 * when the project's toolchain names an entry the debugger takes, and
 * invoking it hands exactly that entry to the editor's debugger through
 * the {@link DebugLauncher} facade. The entry rules themselves live in
 * {@link DebugEntriesTest}; this test is the door.
 */
class DebugMainProjectDoorTest {

    private static final ProjectState NO_OP_STATE = new ProjectState() {
        @Override public void markModified() { }
        @Override public void notifyDeleted() { }
    };

    private static final class FakeLauncher implements DebugLauncher {
        final List<File> debugged = new ArrayList<>();
        @Override public boolean supports(File file) { return file.getName().endsWith(".js"); }
        @Override public void debug(File file) { debugged.add(file); }
    }

    private final Supplier<DebugLauncher> original = WebProjectActionProvider.debugLauncher;

    @AfterEach
    void restoreSeam() {
        WebProjectActionProvider.debugLauncher = original;
    }

    private static WebProjectActionProvider providerFor(Path dir) {
        return new WebProjectActionProvider(new WebProject(FileUtil.toFileObject(dir.toFile()), NO_OP_STATE));
    }

    @Test
    @DisplayName("debug is a supported command, so the platform's Debug Main Project row and toolbar button appear")
    void shouldSupportDebug() {
        assertThat(new WebProjectActionProvider(null).getSupportedActions())
                .contains(ActionProvider.COMMAND_DEBUG);
    }

    @Test
    @DisplayName("enabled when the project's start script names a file the debugger takes; the entry is what gets debugged")
    void shouldDebugTheProjectsEntry(@TempDir Path tmp) throws Exception {
        Path project = Files.createDirectories(tmp.resolve("app"));
        Files.writeString(project.resolve("package.json"),
                "{\"name\":\"app\",\"scripts\":{\"start\":\"node server.js\"}}");
        Path server = Files.writeString(project.resolve("server.js"), "console.log(1)");
        FakeLauncher launcher = new FakeLauncher();
        WebProjectActionProvider.debugLauncher = () -> launcher;
        WebProjectActionProvider provider = providerFor(project);

        assertThat(provider.isActionEnabled(ActionProvider.COMMAND_DEBUG, Lookup.EMPTY)).isTrue();
        provider.invokeAction(ActionProvider.COMMAND_DEBUG, Lookup.EMPTY);

        assertThat(launcher.debugged).hasSize(1);
        assertThat(launcher.debugged.get(0).getCanonicalFile()).isEqualTo(server.toRealPath().toFile());
    }

    @Test
    @DisplayName("disabled and inert when the toolchain names no entry, the debugger refuses it, or the editor is absent")
    void shouldStayDisabledWithoutAnEntry(@TempDir Path tmp) throws Exception {
        Path noEntry = Files.createDirectories(tmp.resolve("scripts-only"));
        Files.writeString(noEntry.resolve("package.json"), "{\"name\":\"a\",\"scripts\":{\"dev\":\"vite\"}}");
        Path tsEntry = Files.createDirectories(tmp.resolve("ts"));
        Files.writeString(tsEntry.resolve("package.json"), "{\"name\":\"b\",\"main\":\"index.ts\"}");
        Files.writeString(tsEntry.resolve("index.ts"), "export {}");
        Path plain = Files.createDirectories(tmp.resolve("plain"));
        Files.writeString(plain.resolve("package.json"), "{\"name\":\"c\",\"main\":\"index.js\"}");
        Files.writeString(plain.resolve("index.js"), "1");
        FakeLauncher launcher = new FakeLauncher(); // .js only

        WebProjectActionProvider.debugLauncher = () -> launcher;
        assertThat(providerFor(noEntry).isActionEnabled(ActionProvider.COMMAND_DEBUG, Lookup.EMPTY))
                .as("a dev server is not a program to debug").isFalse();
        assertThat(providerFor(tsEntry).isActionEnabled(ActionProvider.COMMAND_DEBUG, Lookup.EMPTY))
                .as("an entry this launcher does not take").isFalse();
        providerFor(noEntry).invokeAction(ActionProvider.COMMAND_DEBUG, Lookup.EMPTY);
        assertThat(launcher.debugged).isEmpty();

        WebProjectActionProvider.debugLauncher = () -> null;
        assertThat(providerFor(plain).isActionEnabled(ActionProvider.COMMAND_DEBUG, Lookup.EMPTY))
                .as("no editor, no debugger").isFalse();
        providerFor(plain).invokeAction(ActionProvider.COMMAND_DEBUG, Lookup.EMPTY); // must not throw
    }
}
