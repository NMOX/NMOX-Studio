package org.nmox.studio.editor.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v1.62.0 Tailwind gate: the language server spawns ONLY for
 * projects that actually use Tailwind — a config file, or the
 * tailwindcss dependency (v4's CSS-first setup ships no config file).
 * Every other project must pay nothing.
 */
class TailwindDetectionTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("bare project → no tailwind, no server")
    void bareProjectOptsOut() throws IOException {
        Files.writeString(dir.resolve("package.json"), "{}");
        assertThat(LanguageServers.usesTailwind(dir.toFile())).isFalse();
    }

    @Test
    @DisplayName("tailwind.config.js opts in")
    void configFileOptsIn() throws IOException {
        Files.writeString(dir.resolve("tailwind.config.js"), "module.exports = {}");
        assertThat(LanguageServers.usesTailwind(dir.toFile())).isTrue();
    }

    @Test
    @DisplayName("tailwind.config.ts opts in")
    void tsConfigOptsIn() throws IOException {
        Files.writeString(dir.resolve("tailwind.config.ts"), "export default {}");
        assertThat(LanguageServers.usesTailwind(dir.toFile())).isTrue();
    }

    @Test
    @DisplayName("v4 CSS-first: the tailwindcss dependency alone opts in")
    void v4DependencyOptsIn() throws IOException {
        Files.writeString(dir.resolve("package.json"),
                "{\"devDependencies\": {\"tailwindcss\": \"^4.0.0\"}}");
        assertThat(LanguageServers.usesTailwind(dir.toFile())).isTrue();
    }

    @Test
    @DisplayName("a malformed package.json is no opt-in (and no crash)")
    void malformedManifestOptsOut() throws IOException {
        Files.writeString(dir.resolve("package.json"), "{not json");
        assertThat(LanguageServers.usesTailwind(dir.toFile())).isFalse();
    }
}
