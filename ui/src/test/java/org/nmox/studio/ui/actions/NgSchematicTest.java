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

    /** A platform without symlinks has nothing to prove in the walk below. */
    private static boolean linked(Path from, Path to) {
        try {
            Files.createSymbolicLink(from, to);
            return true;
        } catch (UnsupportedOperationException | java.io.IOException noSymlinks) {
            return false;
        }
    }

    @Test
    @DisplayName("the folder field cannot escape the workspace")
    void traversalGuard(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("src/app"));
        File r = root.toFile();
        // the CANONICAL directory (ledger 111): this becomes the cwd of a
        // trust-gated spawn and ng writes relative to its cwd, so the
        // answer is the directory the guard judged, not the one typed
        assertThat(NgSchematic.targetFolder(r, "src/app"))
                .isEqualTo(new File(r, "src/app").getCanonicalFile());
        assertThat(NgSchematic.targetFolder(r, "")).isEqualTo(r);
        assertThat(NgSchematic.targetFolder(r, "../../etc"))
                .as("a typed traversal must die before any spawn").isNull();
        assertThat(NgSchematic.targetFolder(r, "src/app/../../.."))
                .isNull();
        assertThat(NgSchematic.targetFolder(r, "does/not/exist"))
                .as("ng cannot run in a folder that isn't there").isNull();
    }

    @Test
    @DisplayName("the field's own ways of saying \"here\" mean the workspace; a spelling that only looks like it does not")
    void theWorkspaceItself(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("src"));
        File r = root.toFile();
        // an empty field is the common case — ng generate at the workspace
        // root — and "." is the same wish typed out. Neither asks a
        // containment question: the workspace is itself by definition.
        assertThat(NgSchematic.targetFolder(r, "")).isEqualTo(r);
        assertThat(NgSchematic.targetFolder(r, "   ")).isEqualTo(r);
        assertThat(NgSchematic.targetFolder(r, ".")).isEqualTo(r);
        assertThat(NgSchematic.targetFolder(r, "./")).isEqualTo(r);
        assertThat(NgSchematic.targetFolder(r, null)).isEqualTo(r);
        // "src/.." only LOOKS like the root: if src were a link it would
        // name somewhere else, so it is a real question and the guard's
        // decided policy answers it — the root is not a file inside itself
        assertThat(NgSchematic.targetFolder(r, "src/.."))
                .as("a traversal that lands on the root is the guard's question, not the field's")
                .isNull();
    }

    @Test
    @DisplayName("a symlinked folder: one leaving the workspace is refused, one staying inside answers the directory it judged")
    void symlinkedFolder(@TempDir Path root, @TempDir Path elsewhere) throws Exception {
        File r = root.toFile();
        Files.createDirectories(elsewhere.resolve("victim"));
        if (!linked(root.resolve("escape"), elsewhere.resolve("victim"))) {
            return;
        }
        assertThat(NgSchematic.targetFolder(r, "escape"))
                .as("ng must not be given a cwd outside the workspace to generate into")
                .isNull();

        Files.createDirectories(root.resolve("real"));
        if (!linked(root.resolve("inside"), root.resolve("real"))) {
            return;
        }
        File target = NgSchematic.targetFolder(r, "inside");
        assertThat(target).isNotNull();
        assertThat(target.getPath())
                .as("the spawn's cwd must be the directory the check looked at")
                .doesNotContain("inside")
                .contains("real");
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
