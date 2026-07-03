package org.nmox.studio.tools.npm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.spi.project.ProjectState;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.LocalFileSystem;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Manifest-based project recognition: any supported manifest at the
 * directory root makes a project, and nothing else does.
 *
 * <p>FileObjects are minted through a {@link LocalFileSystem} mounted
 * on the temp dir, because {@code FileUtil.toFileObject} needs masterfs,
 * which is (rightly) not on the unit-test classpath.</p>
 */
class WebProjectFactoryTest {

    private final WebProjectFactory factory = new WebProjectFactory();

    private static final ProjectState NO_OP_STATE = new ProjectState() {
        @Override
        public void markModified() {
        }

        @Override
        public void notifyDeleted() {
        }
    };

    /** Mounts the already-populated directory and returns its root FileObject. */
    private static FileObject mount(Path dir) throws IOException {
        LocalFileSystem fs = new LocalFileSystem();
        try {
            fs.setRootDirectory(dir.toFile());
        } catch (java.beans.PropertyVetoException ex) {
            throw new IOException(ex);
        }
        FileObject root = fs.getRoot();
        assertThat(root).as("FileObject for " + dir).isNotNull();
        return root;
    }

    @Test
    @DisplayName("package.json at the root is recognized as a project")
    void packageJsonMakesAProject(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("package.json"), "{\"name\":\"app\"}");
        assertThat(factory.isProject(mount(dir))).isTrue();
    }

    @Test
    @DisplayName("Cargo.toml and go.mod are first-class manifests too")
    void polyglotManifestsMakeProjects(@TempDir Path rust, @TempDir Path go)
            throws IOException {
        Files.writeString(rust.resolve("Cargo.toml"), "[package]\nname = \"app\"\n");
        assertThat(factory.isProject(mount(rust))).as("Cargo.toml").isTrue();

        Files.writeString(go.resolve("go.mod"), "module example.com/app\n");
        assertThat(factory.isProject(mount(go))).as("go.mod").isTrue();
    }

    @Test
    @DisplayName("A directory with no manifest is not a project, whatever else it holds")
    void manifestlessDirectoryIsNoProject(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("README.md"), "# not a manifest");
        Files.writeString(dir.resolve("notes.txt"), "still not one");
        assertThat(factory.isProject(mount(dir))).isFalse();
    }

    @Test
    @DisplayName("A manifest buried in a subdirectory does not promote the parent to a project")
    void nestedManifestDoesNotPromoteParent(@TempDir Path dir) throws IOException {
        Path nested = Files.createDirectory(dir.resolve("frontend"));
        Files.writeString(nested.resolve("package.json"), "{\"name\":\"app\"}");

        FileObject root = mount(dir);
        assertThat(factory.isProject(root)).isFalse();
        FileObject frontend = root.getFileObject("frontend");
        assertThat(frontend).isNotNull();
        assertThat(factory.isProject(frontend)).as("the subdirectory itself qualifies").isTrue();
    }

    @Test
    @DisplayName("loadProject on a manifest-less directory returns null rather than a project")
    void loadProjectRefusesNonProjects(@TempDir Path dir) throws IOException {
        assertThat(factory.loadProject(mount(dir), NO_OP_STATE)).isNull();
    }

    @Test
    @DisplayName("loadProject on a real manifest builds a WebProject rooted at that directory")
    void loadProjectBuildsWebProject(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("package.json"), "{\"name\":\"loadable\"}");
        // mount the parent and resolve the named child: a filesystem root's
        // FileObject has an empty name, unlike a real project directory.
        FileObject projectDir = mount(dir.getParent()).getFileObject(dir.toFile().getName());
        assertThat(projectDir).isNotNull();

        var project = factory.loadProject(projectDir, NO_OP_STATE);
        assertThat(project).isInstanceOf(WebProject.class);
        assertThat(project.getProjectDirectory()).isSameAs(projectDir);
        assertThat(((WebProject) project).getName()).isEqualTo(dir.toFile().getName());
    }

    @Test
    @DisplayName("Bun and Deno manifests are recognized as projects")
    void bunAndDenoManifestsMakeProjects(@TempDir Path bun, @TempDir Path deno) throws IOException {
        Files.writeString(bun.resolve("bunfig.toml"), "[install]\n");
        assertThat(factory.isProject(mount(bun))).as("bunfig.toml").isTrue();

        Files.writeString(deno.resolve("deno.json"), "{}");
        assertThat(factory.isProject(mount(deno))).as("deno.json").isTrue();
    }
}
