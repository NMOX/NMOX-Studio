package org.nmox.studio.rack.devices;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.devices.ProjectInspector.ProjectKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The LEARN kind (v2.58.0): a learning space's marker file is a project
 * signal — the resort after the last resort — so SOLDER's manifest gate
 * lets the space's driver run. The precedence laws: a real manifest
 * wins, a root index.html (STATIC) wins, and a marker-less directory is
 * still NONE. Born from the walk that found every manifest-less run
 * space dead on RUN.
 */
class LearnKindTest {

    @Test
    @DisplayName("A marker-only space is LEARN and satisfies the manifest gate")
    void markerOnlyIsLearn(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("wit"));
        Files.writeString(dir.resolve("wit/world.wit"), "package a:b;\n");
        Files.writeString(dir.resolve(".nmox-learn"), "wit-component\n");
        assertThat(ProjectInspector.detectKind(dir.toFile())).isEqualTo(ProjectKind.LEARN);
        assertThat(ProjectInspector.hasProjectManifest(dir.toFile())).isTrue();
    }

    @Test
    @DisplayName("A real manifest beats the marker")
    void manifestBeatsMarker(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(".nmox-learn"), "react\n");
        Files.writeString(dir.resolve("package.json"), "{\"name\":\"x\"}\n");
        assertThat(ProjectInspector.detectKind(dir.toFile())).isEqualTo(ProjectKind.NODE);
    }

    @Test
    @DisplayName("A root index.html beats the marker — the space keeps serving")
    void staticBeatsMarker(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(".nmox-learn"), "jquery\n");
        Files.writeString(dir.resolve("index.html"), "<html></html>\n");
        assertThat(ProjectInspector.detectKind(dir.toFile())).isEqualTo(ProjectKind.STATIC);
    }

    @Test
    @DisplayName("No marker, no manifest: still NONE — the gate still refuses")
    void bareDirIsNone(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("hello.c"), "int main(){return 0;}\n");
        assertThat(ProjectInspector.detectKind(dir.toFile())).isEqualTo(ProjectKind.NONE);
        assertThat(ProjectInspector.hasProjectManifest(dir.toFile())).isFalse();
    }
}
