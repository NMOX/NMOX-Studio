package org.nmox.studio.editor.standards;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The EditorConfig standard, held to its spec: glob semantics, root
 * stopping, closer-file precedence, and both directions of
 * insert_final_newline.
 */
class EditorConfigTest {

    @Test
    @DisplayName("Globs: *, **, ?, [seq], {alt}, {n..m}, and no-slash means any directory")
    void globSemantics() {
        assertThat(EditorConfig.glob("*.js").matches("src/deep/app.js")).isTrue();
        assertThat(EditorConfig.glob("*.js").matches("app.jsx")).isFalse();
        assertThat(EditorConfig.glob("src/*.js").matches("src/app.js")).isTrue();
        assertThat(EditorConfig.glob("src/*.js").matches("src/deep/app.js")).isFalse();
        assertThat(EditorConfig.glob("src/**.js").matches("src/deep/app.js")).isTrue();
        assertThat(EditorConfig.glob("?.md").matches("a.md")).isTrue();
        assertThat(EditorConfig.glob("?.md").matches("ab.md")).isFalse();
        assertThat(EditorConfig.glob("*.{js,ts}").matches("x/a.ts")).isTrue();
        assertThat(EditorConfig.glob("*.{js,ts}").matches("x/a.rs")).isFalse();
        assertThat(EditorConfig.glob("[ch]").matches("c")).isTrue();
        assertThat(EditorConfig.glob("v{1..3}.txt").matches("v2.txt")).isTrue();
        assertThat(EditorConfig.glob("v{1..3}.txt").matches("v4.txt")).isFalse();
    }

    @Test
    @DisplayName("Glob edges: negated classes, anchored slash, and unclosed brackets stay literal")
    void globEdges() {
        // [!seq] negates the class
        assertThat(EditorConfig.glob("[!c]").matches("h")).isTrue();
        assertThat(EditorConfig.glob("[!c]").matches("c")).isFalse();
        // a leading slash anchors to the .editorconfig's own directory
        assertThat(EditorConfig.glob("/root.js").matches("root.js")).isTrue();
        assertThat(EditorConfig.glob("/root.js").matches("sub/root.js")).isFalse();
        // unclosed [ and { are literal characters, not malformed regex
        assertThat(EditorConfig.glob("a[b").matches("a[b")).isTrue();
        assertThat(EditorConfig.glob("a{b").matches("a{b")).isTrue();
        assertThat(EditorConfig.glob("a{b").matches("x/a{b")).isTrue();
        assertThat(EditorConfig.glob("a{b").matches("a-b")).isFalse();
    }

    @Test
    @DisplayName("Closer .editorconfig wins; root=true stops the upward walk")
    void precedenceAndRoot(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve(".editorconfig"), """
                root = true
                [*]
                indent_size = 2
                trim_trailing_whitespace = true
                """);
        Path sub = Files.createDirectories(tmp.resolve("sub"));
        Files.writeString(sub.resolve(".editorconfig"), """
                [*.md]
                indent_size = 4
                """);
        File mdFile = sub.resolve("notes.md").toFile();
        Map<String, String> props = EditorConfig.propertiesFor(mdFile);
        assertThat(props).containsEntry("indent_size", "4")
                .containsEntry("trim_trailing_whitespace", "true");

        File jsFile = sub.resolve("app.js").toFile();
        assertThat(EditorConfig.propertiesFor(jsFile)).containsEntry("indent_size", "2");
    }

    @Test
    @DisplayName("Later sections in the same file override earlier ones")
    void sectionOrder(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve(".editorconfig"), """
                root = true
                [*]
                insert_final_newline = true
                [*.log]
                insert_final_newline = false
                """);
        assertThat(EditorConfig.propertiesFor(tmp.resolve("a.txt").toFile()))
                .containsEntry("insert_final_newline", "true");
        assertThat(EditorConfig.propertiesFor(tmp.resolve("a.log").toFile()))
                .containsEntry("insert_final_newline", "false");
    }

    @Test
    @DisplayName("Save transforms: trim trailing whitespace, final newline both directions")
    void saveTransforms() {
        assertThat(EditorConfig.applyOnSave("a  \nb\t\n", Map.of("trim_trailing_whitespace", "true")))
                .isEqualTo("a\nb\n");
        assertThat(EditorConfig.applyOnSave("x", Map.of("insert_final_newline", "true")))
                .isEqualTo("x\n");
        assertThat(EditorConfig.applyOnSave("x\n", Map.of("insert_final_newline", "true")))
                .isEqualTo("x\n");
        assertThat(EditorConfig.applyOnSave("x\n\n\n", Map.of("insert_final_newline", "false")))
                .isEqualTo("x");
        assertThat(EditorConfig.applyOnSave("", Map.of("insert_final_newline", "true")))
                .as("an empty file stays empty").isEmpty();
        assertThat(EditorConfig.applyOnSave("keep  me\n", Map.of()))
                .as("no matching properties: untouched").isEqualTo("keep  me\n");
    }

    @Test
    @DisplayName("CRLF documents keep their line endings through the trim")
    void crlfPreserved() {
        assertThat(EditorConfig.applyOnSave("a  \r\nb\r\n", Map.of("trim_trailing_whitespace", "true")))
                .isEqualTo("a\r\nb\r\n");
    }
}
