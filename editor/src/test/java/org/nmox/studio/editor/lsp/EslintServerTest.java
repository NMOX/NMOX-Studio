package org.nmox.studio.editor.lsp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v1.213.0: eslint findings arrive on file-open like every other
 * diagnostic, instead of only when a PURITY device is mounted and
 * clicked.
 *
 * <p>The load-bearing premise was verified by decompiling the shipped
 * {@code org-netbeans-modules-lsp-client.jar} rather than assumed:
 * {@code LSPBindings} collects providers with
 * {@code MimeLookup.getLookup(mime).lookupAll(LanguageServerProvider.class)}
 * — a Collection — so registering eslint on a mime that already has
 * typescript-language-server ADDS a server rather than replacing one.
 * These tests pin the consequences of that: both servers on both mimes,
 * and the catalog/Doctor rows that make a missing binary say so.
 */
class EslintServerTest {

    private static String source(String rel) throws IOException {
        return Files.readString(Path.of(rel), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("eslint is registered on BOTH JS and TS mimes")
    void registeredOnBothMimes() throws IOException {
        String src = source("src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java");
        int eslintAt = src.indexOf("class EslintServer");
        assertThat(eslintAt).as("EslintServer exists").isGreaterThan(0);
        // the annotation block immediately above the class
        String block = src.substring(Math.max(0, eslintAt - 400), eslintAt);
        assertThat(block).contains("text/javascript");
        assertThat(block).contains("text/typescript");
    }

    @Test
    @DisplayName("eslint does NOT displace typescript-language-server")
    void tsserverStillRegisteredOnBothMimes() throws IOException {
        String src = source("src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java");
        // since the ledger-81 suppression the two mimes ride SEPARATE
        // registrations (TS yields to ngserver in Angular workspaces,
        // JS never does) — both must still exist
        int tsAt = src.indexOf("class TypeScriptServer");
        assertThat(tsAt).isGreaterThan(0);
        assertThat(src.substring(Math.max(0, tsAt - 400), tsAt))
                .as("types keep arriving; eslint is additive, not a swap")
                .contains("text/typescript");
        int jsAt = src.indexOf("class JavaScriptTsServer");
        assertThat(jsAt).isGreaterThan(0);
        assertThat(src.substring(Math.max(0, jsAt - 400), jsAt))
                .contains("text/javascript");
    }

    @Test
    @DisplayName("eslint runs through launchNpm, so the trust law applies unchanged")
    void ridesTheTrustGatedLauncher() throws IOException {
        String src = source("src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java");
        int at = src.indexOf("class EslintServer");
        // slice to the next member, not a fixed width — the v1.216.0
        // trust gate sits above the launch and grew the body
        String body = src.substring(at, src.indexOf("static boolean hasEslintConfig", at));
        // launchNpm is where v1.102.0 lives: a committed
        // node_modules/.bin binary is only used in a TRUSTED workspace,
        // else the user's own global tool. Reimplementing the spawn here
        // would silently opt out of that.
        assertThat(body).contains("launchNpm(");
        assertThat(body).contains("vscode-eslint-language-server");
    }

    @Test
    @DisplayName("the catalog knows eslint, with an install hint naming the real package")
    void catalogEntry() {
        LanguageServerCatalog.Server s =
                LanguageServerCatalog.forBinary("vscode-eslint-language-server");
        assertThat(s).as("a missing binary must be explainable").isNotNull();
        assertThat(s.language()).containsIgnoringCase("eslint");
        // the binary and the npm package differ — the hint must name the
        // package you actually install, or the advice fails
        assertThat(s.install()).contains("vscode-langservers-extracted");
        assertThat(s.command()).contains("vscode-langservers-extracted");
    }

    @Test
    @DisplayName("Environment Doctor probes the eslint server")
    void doctorProbe() throws IOException {
        String doctor = source(
                "../rack/src/main/java/org/nmox/studio/rack/projectstudio/EnvironmentDoctor.java");
        assertThat(doctor).contains("vscode-eslint-language-server");
    }

    @Test
    @DisplayName("the Web Pipeline preset lints (it never did before)")
    void webPipelineHasLint() throws IOException {
        String presets = source(
                "../rack/src/main/java/org/nmox/studio/rack/projectstudio/RackPresets.java");
        int at = presets.indexOf("WEB_PIPELINE");
        assertThat(at).isGreaterThan(0);
        String block = presets.substring(at, presets.indexOf("},", at));
        assertThat(block)
                .as("the preset named Web Pipeline ran a web pipeline with no linting")
                .contains("DeviceType.LINT");
    }
}
