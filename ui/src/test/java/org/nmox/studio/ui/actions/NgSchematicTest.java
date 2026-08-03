package org.nmox.studio.ui.actions;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pure half of New Angular Schematic… — root detection, the
 * traversal guard, name validation, and the exact argv, all without
 * a dialog or a spawn.
 */
class NgSchematicTest {

    @Test
    @DisplayName("only an angular.json aim is an Angular workspace")
    void rootDetection(@TempDir Path ng, @TempDir Path plain) throws Exception {
        Files.writeString(ng.resolve("angular.json"), "{}");
        assertThat(NgSchematic.angularRoot(ng.toFile())).isEqualTo(ng.toFile());
        assertThat(NgSchematic.angularRoot(plain.toFile())).isNull();
        assertThat(NgSchematic.angularRoot(null)).isNull();
    }

    @Test
    @DisplayName("the folder field cannot escape the workspace")
    void traversalGuard(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("src/app"));
        File r = root.toFile();
        assertThat(NgSchematic.targetFolder(r, "src/app"))
                .isEqualTo(new File(r, "src/app"));
        assertThat(NgSchematic.targetFolder(r, "")).isEqualTo(r);
        assertThat(NgSchematic.targetFolder(r, "../../etc"))
                .as("a typed traversal must die before any spawn").isNull();
        assertThat(NgSchematic.targetFolder(r, "src/app/../../.."))
                .isNull();
        assertThat(NgSchematic.targetFolder(r, "does/not/exist"))
                .as("ng cannot run in a folder that isn't there").isNull();
    }

    @Test
    @DisplayName("names are single identifiers; flags and paths are refused")
    void nameValidation() {
        assertThat(NgSchematic.validName("user-card")).isTrue();
        assertThat(NgSchematic.validName("  widget ")).isTrue();
        assertThat(NgSchematic.validName(null)).isFalse();
        assertThat(NgSchematic.validName("  ")).isFalse();
        assertThat(NgSchematic.validName("a b")).isFalse();
        assertThat(NgSchematic.validName("a/b")).isFalse();
        assertThat(NgSchematic.validName("--force")).isFalse();
    }

    @Test
    @DisplayName("the argv is exactly the terminal habit, and the vocabulary is HALO's")
    void argvAndVocabulary() {
        assertThat(NgSchematic.argv("component", " widget "))
                .containsExactly("npx", "ng", "generate", "component", "widget");
        // one schematic vocabulary across both surfaces (HALO's knob list)
        assertThat(NgSchematic.SCHEMATICS).containsExactly(
                "component", "service", "directive", "pipe", "guard",
                "interceptor", "resolver", "class");
    }
}
