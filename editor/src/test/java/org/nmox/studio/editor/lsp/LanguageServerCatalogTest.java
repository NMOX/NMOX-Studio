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
        // 'sh' is on every unix PATH the IDE augments, and Git Bash puts
        // sh.exe on the Windows runners' PATH; the nonsense name is on none
        assertThat(LanguageServerCatalog.isInstalled("sh")).isTrue();
        assertThat(LanguageServerCatalog.isInstalled("nmox-no-such-binary-zzz")).isFalse();
    }

    @Test
    @DisplayName("PATH-entry probing sees Windows spellings — .exe and npm's .cmd shims")
    void windowsSpellingsDetected(@org.junit.jupiter.api.io.TempDir java.io.File dir) throws Exception {
        // Pure file probing, so the Windows-shaped names are testable on any
        // OS. Before this, no language server was EVER detected as installed
        // on Windows: nothing there is executable under its bare name.
        new java.io.File(dir, "native-server.exe").createNewFile();
        new java.io.File(dir, "npm-server.cmd").createNewFile();

        assertThat(LanguageServerCatalog.foundIn(dir, "native-server")).isTrue();
        assertThat(LanguageServerCatalog.foundIn(dir, "npm-server")).isTrue();
        assertThat(LanguageServerCatalog.foundIn(dir, "absent-server")).isFalse();
    }

    @Test
    @DisplayName("Auto-installable servers carry a runnable argv; manual ones are flagged, never faked")
    void installCommands() {
        Server go = LanguageServerCatalog.forBinary("gopls");
        assertThat(go.autoInstallable()).isTrue();
        assertThat(go.command()).containsExactly("go", "install", "golang.org/x/tools/gopls@latest");
        assertThat(go.installer()).isEqualTo("go");

        // Swift ships with the toolchain — there is no one-command install, so no fake button
        Server swift = LanguageServerCatalog.forBinary("sourcekit-lsp");
        assertThat(swift.autoInstallable()).isFalse();
        assertThat(swift.command()).isEmpty();
        assertThat(swift.installer()).isEmpty();
    }
}
