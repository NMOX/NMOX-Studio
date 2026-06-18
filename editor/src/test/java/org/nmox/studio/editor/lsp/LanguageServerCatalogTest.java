package org.nmox.studio.editor.lsp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.lsp.LanguageServerCatalog.Server;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The catalog is what turns a missing language server into an answer
 * instead of silence, so the install hints have to be there and right.
 */
class LanguageServerCatalogTest {

    @Test
    @DisplayName("The headline web-stack languages map to the right binary and a real install hint")
    void headlineLanguages() {
        assertThat(LanguageServerCatalog.forBinary("gopls").language()).isEqualTo("Go");
        assertThat(LanguageServerCatalog.forBinary("gopls").install()).contains("gopls");
        assertThat(LanguageServerCatalog.forBinary("rust-analyzer").install()).contains("rustup");
        assertThat(LanguageServerCatalog.forBinary("typescript-language-server").install())
                .contains("npm install");
        assertThat(LanguageServerCatalog.forBinary("pyright-langserver")).isNotNull();
    }

    @Test
    @DisplayName("An uncatalogued binary returns null (caller falls back to a generic message)")
    void unknownBinaryIsNull() {
        assertThat(LanguageServerCatalog.forBinary("totally-not-a-server")).isNull();
    }

    @Test
    @DisplayName("Every entry is fully populated — language, binary, and a non-empty install hint")
    void everyEntryComplete() {
        for (Server s : LanguageServerCatalog.all()) {
            assertThat(s.language()).isNotBlank();
            assertThat(s.binary()).isNotBlank();
            assertThat(s.install()).isNotBlank();
        }
        assertThat(LanguageServerCatalog.all()).hasSizeGreaterThanOrEqualTo(20);
    }

    @Test
    @DisplayName("Install detection: a ubiquitous tool reads as present, a nonsense one as absent")
    void detection() {
        // 'sh' is on every unix PATH the IDE augments; the nonsense name is on none
        assertThat(LanguageServerCatalog.isInstalled("sh")).isTrue();
        assertThat(LanguageServerCatalog.isInstalled("nmox-no-such-binary-zzz")).isFalse();
    }
}
