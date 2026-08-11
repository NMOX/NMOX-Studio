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

    @Test
    @DisplayName("the primary created file is the first non-spec .ts, byte-counts stripped")
    void primaryCreated() {
        // the exact receipt shape Angular 21 prints (type:component pin)
        assertThat(NgSchematic.primaryCreated(java.util.List.of(
                "CREATE src/app/widget/widget.component.html (21 bytes)",
                "CREATE src/app/widget/widget.component.spec.ts (601 bytes)",
                "CREATE src/app/widget/widget.component.ts (245 bytes)",
                "UPDATE src/app/app.module.ts (412 bytes)")))
                .isEqualTo("src/app/widget/widget.component.ts");
        // a schematic with no .ts (unlikely, but the fallback is honest):
        // the FIRST created file
        assertThat(NgSchematic.primaryCreated(java.util.List.of(
                "CREATE src/styles/theme.scss (10 bytes)",
                "CREATE src/styles/vars.scss (12 bytes)")))
                .isEqualTo("src/styles/theme.scss");
        // nothing created (UPDATE-only or failure) → null, nothing opens
        assertThat(NgSchematic.primaryCreated(java.util.List.of(
                "UPDATE angular.json (2999 bytes)", "error: something")))
                .isNull();
        assertThat(NgSchematic.primaryCreated(java.util.List.of())).isNull();
    }

    @Test
    @DisplayName("the action actually collects lines and opens the primary (two-proof wiring)")
    void openWiring() throws Exception {
        String src = java.nio.file.Files.readString(new java.io.File(
                "src/main/java/org/nmox/studio/ui/actions/NgSchematicAction.java").toPath());
        assertThat(src)
                .as("a lines::add consumer plus a primaryCreated resolve at exit —"
                        + " without both, generate ends with the dev hunting the tree")
                .contains("lines::add")
                .contains("NgSchematic.primaryCreated(lines)")
                .contains("new File(root, created)")
                .contains("openInEditor(createdFile)")
                // the first live proof's two finds, pinned: refresh before
                // resolve (external creation), and status says "opened"
                // only when a tab REALLY opened
                .contains("FileUtil.refreshFor(")
                .contains("boolean opened = createdFile != null");
    }
}
