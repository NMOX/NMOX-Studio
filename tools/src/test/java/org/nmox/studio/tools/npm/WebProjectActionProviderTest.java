package org.nmox.studio.tools.npm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.spi.project.ActionProvider;
import org.netbeans.spi.project.ProjectState;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.LocalFileSystem;
import org.openide.util.Lookup;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The native Run/Build/Test/Clean provider only advertises an action as
 * enabled when the project's toolchain can actually express it. This
 * pins the supported-action set and the enablement routing down without
 * ever invoking a command (which would spawn a real process).
 *
 * <p>Projects are minted through a {@link LocalFileSystem} mounted on a
 * temp dir so {@code FileUtil.toFile} resolves back to a real directory —
 * masterfs (needed by {@code FileUtil.toFileObject}) is not on the
 * unit-test classpath.</p>
 */
class WebProjectActionProviderTest {

    private static final ProjectState NO_OP_STATE = new ProjectState() {
        @Override
        public void markModified() {
        }

        @Override
        public void notifyDeleted() {
        }
    };

    /**
     * Mounts a populated directory (as a named child of its parent) and
     * returns a WebProject over it, so {@code FileUtil.toFile} resolves
     * back to the real directory the toolchain detection reads.
     */
    private static WebProject projectFor(Path dir) throws IOException {
        LocalFileSystem fs = new LocalFileSystem();
        try {
            fs.setRootDirectory(dir.getParent().toFile());
        } catch (java.beans.PropertyVetoException ex) {
            throw new IOException(ex);
        }
        FileObject projectDir = fs.getRoot().getFileObject(dir.toFile().getName());
        assertThat(projectDir).as("named FileObject for " + dir).isNotNull();
        return new WebProject(projectDir, NO_OP_STATE);
    }

    private static WebProjectActionProvider providerFor(Path dir) throws IOException {
        return new WebProjectActionProvider(projectFor(dir));
    }

    @Test
    @DisplayName("Exactly Run, Build, Test and Clean are advertised as supported actions")
    void advertisesTheFourStandardActions(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("package.json"), "{\"scripts\":{}}");
        String[] supported = providerFor(dir).getSupportedActions();

        assertThat(supported).containsExactlyInAnyOrder(
                ActionProvider.COMMAND_RUN,
                ActionProvider.COMMAND_BUILD,
                ActionProvider.COMMAND_TEST,
                ActionProvider.COMMAND_CLEAN);
    }

    @Test
    @DisplayName("getSupportedActions hands back a fresh copy each call, not the shared array")
    void supportedActionsAreDefensivelyCopied(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("package.json"), "{\"scripts\":{}}");
        WebProjectActionProvider provider = providerFor(dir);

        String[] first = provider.getSupportedActions();
        first[0] = "mutated";
        assertThat(provider.getSupportedActions()).doesNotContain("mutated");
    }

    @Test
    @DisplayName("A Node project enables exactly the actions its package.json scripts back")
    void nodeEnablementFollowsScripts(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("package.json"),
                "{\"scripts\":{\"dev\":\"vite\",\"build\":\"vite build\",\"test\":\"vitest\"}}");
        WebProjectActionProvider provider = providerFor(dir);

        assertThat(provider.isActionEnabled(ActionProvider.COMMAND_RUN, Lookup.EMPTY)).isTrue();
        assertThat(provider.isActionEnabled(ActionProvider.COMMAND_BUILD, Lookup.EMPTY)).isTrue();
        assertThat(provider.isActionEnabled(ActionProvider.COMMAND_TEST, Lookup.EMPTY)).isTrue();
        // no clean script declared -> the action is honestly disabled
        assertThat(provider.isActionEnabled(ActionProvider.COMMAND_CLEAN, Lookup.EMPTY)).isFalse();
    }

    @Test
    @DisplayName("A Rust project enables all four actions from its fixed cargo mapping")
    void rustEnablesEveryAction(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("Cargo.toml"), "[package]\nname = \"app\"\n");
        WebProjectActionProvider provider = providerFor(dir);

        assertThat(provider.isActionEnabled(ActionProvider.COMMAND_RUN, Lookup.EMPTY)).isTrue();
        assertThat(provider.isActionEnabled(ActionProvider.COMMAND_BUILD, Lookup.EMPTY)).isTrue();
        assertThat(provider.isActionEnabled(ActionProvider.COMMAND_TEST, Lookup.EMPTY)).isTrue();
        assertThat(provider.isActionEnabled(ActionProvider.COMMAND_CLEAN, Lookup.EMPTY)).isTrue();
    }

    @Test
    @DisplayName("A Maven project has no single Run but does have Build, Test and Clean")
    void mavenHasNoRunAction(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("pom.xml"),
                "<project><artifactId>app</artifactId></project>");
        WebProjectActionProvider provider = providerFor(dir);

        assertThat(provider.isActionEnabled(ActionProvider.COMMAND_RUN, Lookup.EMPTY)).isFalse();
        assertThat(provider.isActionEnabled(ActionProvider.COMMAND_BUILD, Lookup.EMPTY)).isTrue();
        assertThat(provider.isActionEnabled(ActionProvider.COMMAND_TEST, Lookup.EMPTY)).isTrue();
        assertThat(provider.isActionEnabled(ActionProvider.COMMAND_CLEAN, Lookup.EMPTY)).isTrue();
    }

    @Test
    @DisplayName("An unrecognized action name is never enabled")
    void unknownActionIsNeverEnabled(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("Cargo.toml"), "[package]\nname = \"app\"\n");
        assertThat(providerFor(dir).isActionEnabled("compile-the-universe", Lookup.EMPTY)).isFalse();
    }
}
