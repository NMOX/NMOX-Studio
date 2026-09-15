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
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The platform's Debug ▸ Debug File row (⇧⌘F5) on a web project (v2.157.0):
 * {@code debug.single} is supported, enabled for exactly one supported file
 * inside the project, and invoking it hands that file to the editor's
 * debugger through the {@link DebugLauncher} facade — which owns the trust
 * prompt and the lane, so this door adds no spawn and no gate of its own.
 */
class DebugSingleDoorTest {

    private static final ProjectState NO_OP_STATE = new ProjectState() {
        @Override public void markModified() { }
        @Override public void notifyDeleted() { }
    };

    /** Records what it was asked; supports .js only. */
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
        FileObject projectDir = FileUtil.toFileObject(dir.toFile());
        return new WebProjectActionProvider(new WebProject(projectDir, NO_OP_STATE));
    }

    private static Lookup contextOf(Path... files) {
        Object[] fos = new Object[files.length];
        for (int i = 0; i < files.length; i++) {
            fos[i] = FileUtil.toFileObject(files[i].toFile());
        }
        return Lookups.fixed(fos);
    }

    @Test
    @DisplayName("debug.single is a supported command, so the platform's Debug File row appears for a web project")
    void shouldSupportDebugSingle() {
        assertThat(new WebProjectActionProvider(null).getSupportedActions())
                .contains(ActionProvider.COMMAND_DEBUG_SINGLE);
    }

    @Test
    @DisplayName("enabled for exactly one supported file inside the project; not for two, a stranger, or an unsupported type")
    void shouldEnableForOneSupportedProjectFile(@TempDir Path tmp) throws Exception {
        Path project = Files.createDirectories(tmp.resolve("app"));
        Files.writeString(project.resolve("package.json"), "{\"name\":\"app\"}");
        Path app = Files.writeString(project.resolve("app.js"), "console.log(1)");
        Path other = Files.writeString(project.resolve("other.js"), "console.log(2)");
        Path readme = Files.writeString(project.resolve("README.md"), "# app");
        Path stranger = Files.writeString(tmp.resolve("stranger.js"), "console.log(3)");
        FakeLauncher launcher = new FakeLauncher();
        WebProjectActionProvider.debugLauncher = () -> launcher;
        WebProjectActionProvider provider = providerFor(project);

        assertThat(provider.isActionEnabled(ActionProvider.COMMAND_DEBUG_SINGLE, contextOf(app)))
                .as("one supported file in the project").isTrue();
        assertThat(provider.isActionEnabled(ActionProvider.COMMAND_DEBUG_SINGLE, contextOf(app, other)))
                .as("two files: Debug File debugs one program").isFalse();
        assertThat(provider.isActionEnabled(ActionProvider.COMMAND_DEBUG_SINGLE, contextOf(readme)))
                .as("a type the debugger does not support").isFalse();
        assertThat(provider.isActionEnabled(ActionProvider.COMMAND_DEBUG_SINGLE, contextOf(stranger)))
                .as("a file outside the project").isFalse();
        assertThat(provider.isActionEnabled(ActionProvider.COMMAND_DEBUG_SINGLE, Lookup.EMPTY))
                .as("nothing selected").isFalse();
    }

    @Test
    @DisplayName("invoking debug.single hands the selected file to the debugger, and nothing else")
    void shouldHandTheFileToTheDebugger(@TempDir Path tmp) throws Exception {
        Path project = Files.createDirectories(tmp.resolve("app"));
        Files.writeString(project.resolve("package.json"), "{\"name\":\"app\"}");
        Path app = Files.writeString(project.resolve("app.js"), "console.log(1)");
        Path readme = Files.writeString(project.resolve("README.md"), "# app");
        FakeLauncher launcher = new FakeLauncher();
        WebProjectActionProvider.debugLauncher = () -> launcher;
        WebProjectActionProvider provider = providerFor(project);

        provider.invokeAction(ActionProvider.COMMAND_DEBUG_SINGLE, contextOf(app));
        provider.invokeAction(ActionProvider.COMMAND_DEBUG_SINGLE, contextOf(readme));

        assertThat(launcher.debugged).hasSize(1);
        assertThat(launcher.debugged.get(0).getCanonicalFile()).isEqualTo(app.toRealPath().toFile());
    }

    @Test
    @DisplayName("without the editor (no launcher in Lookup) the row is disabled and invoking does nothing")
    void shouldDisableWithoutTheEditor(@TempDir Path tmp) throws Exception {
        Path project = Files.createDirectories(tmp.resolve("app"));
        Files.writeString(project.resolve("package.json"), "{\"name\":\"app\"}");
        Path app = Files.writeString(project.resolve("app.js"), "console.log(1)");
        WebProjectActionProvider.debugLauncher = () -> null;
        WebProjectActionProvider provider = providerFor(project);

        assertThat(provider.isActionEnabled(ActionProvider.COMMAND_DEBUG_SINGLE, contextOf(app))).isFalse();
        provider.invokeAction(ActionProvider.COMMAND_DEBUG_SINGLE, contextOf(app)); // must not throw
    }
}
