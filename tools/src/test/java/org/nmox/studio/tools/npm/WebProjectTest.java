package org.nmox.studio.tools.npm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.api.project.ProjectInformation;
import org.netbeans.spi.project.ActionProvider;
import org.netbeans.spi.project.ProjectState;
import org.netbeans.spi.project.ui.PrivilegedTemplates;
import org.netbeans.spi.project.ui.RecommendedTemplates;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.LocalFileSystem;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The WebProject value/identity surface: its name comes from the
 * directory, its display name prefers the package.json "name" field and
 * falls back to the directory, and its Lookup carries the collaborators
 * the platform relies on (action provider, recommended templates).
 */
class WebProjectTest {

    private static final ProjectState NO_OP_STATE = new ProjectState() {
        @Override
        public void markModified() {
        }

        @Override
        public void notifyDeleted() {
        }
    };

    /**
     * Returns a WebProject rooted at {@code dir} as a <em>named</em>
     * FileObject. The LocalFileSystem is mounted on the parent and the
     * child is resolved by name, because a filesystem root's FileObject
     * has an empty name — whereas a real project directory always has one.
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

    @Test
    @DisplayName("The project name is the mounted directory's name")
    void nameIsDirectoryName(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("package.json"), "{\"name\":\"pkg-name\"}");
        WebProject project = projectFor(dir);
        assertThat(project.getName()).isEqualTo(dir.toFile().getName());
    }

    @Test
    @DisplayName("The display name prefers the package.json name field")
    void displayNamePrefersPackageJsonName(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("package.json"), "{\"name\":\"my-cool-app\"}");
        ProjectInformation info = projectFor(dir).getLookup().lookup(ProjectInformation.class);

        assertThat(info).isNotNull();
        assertThat(info.getDisplayName()).isEqualTo("my-cool-app");
        assertThat(info.getName()).isEqualTo(dir.toFile().getName());
        assertThat(info.getProject()).isNotNull();
    }

    @Test
    @DisplayName("The display name falls back to the directory when package.json has no name")
    void displayNameFallsBackToDirectory(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("package.json"), "{\"version\":\"1.0.0\"}");
        ProjectInformation info = projectFor(dir).getLookup().lookup(ProjectInformation.class);

        assertThat(info.getDisplayName()).isEqualTo(dir.toFile().getName());
    }

    @Test
    @DisplayName("The display name falls back to the directory when there is no package.json at all")
    void displayNameFallsBackWithoutManifest(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("Cargo.toml"), "[package]\nname = \"rusty\"\n");
        ProjectInformation info = projectFor(dir).getLookup().lookup(ProjectInformation.class);

        // package.json drives the display name; a Cargo project has none,
        // so it falls back to the directory rather than reading Cargo.toml
        assertThat(info.getDisplayName()).isEqualTo(dir.toFile().getName());
    }

    @Test
    @DisplayName("The project directory is exactly the FileObject it was constructed with")
    void projectDirectoryIsTheConstructionRoot(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("package.json"), "{}");
        WebProject project = projectFor(dir);
        assertThat(project.getProjectDirectory()).isSameAs(
                project.getLookup().lookup(org.netbeans.api.project.Project.class)
                        .getProjectDirectory());
    }

    @Test
    @DisplayName("The Lookup wires up the action provider and the recommended-template scoping")
    void lookupCarriesPlatformCollaborators(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("package.json"), "{\"scripts\":{\"build\":\"x\"}}");
        WebProject project = projectFor(dir);

        ActionProvider actions = project.getLookup().lookup(ActionProvider.class);
        assertThat(actions).isNotNull();
        assertThat(actions.getSupportedActions()).contains(ActionProvider.COMMAND_BUILD);

        RecommendedTemplates templates = project.getLookup().lookup(RecommendedTemplates.class);
        assertThat(templates).isNotNull();
        assertThat(templates.getRecommendedTypes()).contains("javascript");

        // the same object also privileges the everyday web templates
        assertThat(project.getLookup().lookup(PrivilegedTemplates.class)).isNotNull();
    }
}
