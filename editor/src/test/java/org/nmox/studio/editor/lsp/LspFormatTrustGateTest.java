package org.nmox.studio.editor.lsp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v1.102.0: launching a project's committed {@code node_modules/.bin/}
 * binary is running a stranger's code — RCE on file-open (LSP) and on
 * save (Prettier). The debug actions gate every project-code spawn
 * behind Workspace Trust; these two auto-firing paths must too. The
 * gate is the SILENT {@code WorkspaceTrust.isTrusted} check (never a
 * prompt — the LSP client and format-on-save fire constantly): untrusted
 * → fall back to the user's own global tool, never the committed local
 * binary.
 */
class LspFormatTrustGateTest {

    private static String read(String rel) throws Exception {
        return Files.readString(Path.of(rel), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("launchNpm only prefers the project-local server binary when the workspace is trusted")
    void lspGatesLocalBinaryOnTrust() throws Exception {
        String src = read("src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java");
        int m = src.indexOf("launchNpm(");
        assertThat(m).as("launchNpm exists").isPositive();
        String body = src.substring(m, src.indexOf("\n    }", m));
        assertThat(body)
                .as("the local node_modules/.bin binary is used only when trusted")
                .contains("WorkspaceTrust.isTrusted(dir)");
        // the trust check must gate the local-vs-global choice, not sit unused
        assertThat(body)
                .contains("useLocal")
                .contains("node_modules/.bin/");
    }

    @Test
    @DisplayName("Prettier resolveBinary only uses the project-local binary when the workspace is trusted")
    void prettierGatesLocalBinaryOnTrust() throws Exception {
        String src = read("src/main/java/org/nmox/studio/editor/format/PrettierFormatter.java");
        int m = src.indexOf("static String resolveBinary(");
        assertThat(m).as("resolveBinary exists").isPositive();
        String body = src.substring(m, src.indexOf("\n    }", m));
        assertThat(body)
                .as("findLocalBinary is reached only inside the trust check")
                .contains("WorkspaceTrust.isTrusted(startDir)");
        int trust = body.indexOf("WorkspaceTrust.isTrusted(");
        int local = body.indexOf("findLocalBinary(");
        assertThat(local).as("the local lookup happens AFTER the trust gate")
                .isGreaterThan(trust);
    }
}
