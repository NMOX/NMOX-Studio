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
        assertThat(EditorConfig.globToRegex("*.js").matcher("src/deep/app.js").matches()).isTrue();
        assertThat(EditorConfig.globToRegex("*.js").matcher("app.jsx").matches()).isFalse();
        assertThat(EditorConfig.globToRegex("src/*.js").matcher("src/app.js").matches()).isTrue();
        assertThat(EditorConfig.globToRegex("src/*.js").matcher("src/deep/app.js").matches()).isFalse();
        assertThat(EditorConfig.globToRegex("src/**.js").matcher("src/deep/app.js").matches()).isTrue();
        assertThat(EditorConfig.globToRegex("?.md").matcher("a.md").matches()).isTrue();
        assertThat(EditorConfig.globToRegex("?.md").matcher("ab.md").matches()).isFalse();
        assertThat(EditorConfig.globToRegex("*.{js,ts}").matcher("x/a.ts").matches()).isTrue();
        assertThat(EditorConfig.globToRegex("*.{js,ts}").matcher("x/a.rs").matches()).isFalse();
        assertThat(EditorConfig.globToRegex("[ch]").matcher("c").matches()).isTrue();
        assertThat(EditorConfig.globToRegex("v{1..3}.txt").matcher("v2.txt").matches()).isTrue();
        assertThat(EditorConfig.globToRegex("v{1..3}.txt").matcher("v4.txt").matches()).isFalse();
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
