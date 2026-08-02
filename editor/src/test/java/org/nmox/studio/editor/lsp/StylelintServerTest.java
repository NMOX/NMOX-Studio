package org.nmox.studio.editor.lsp;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v1.232.0: stylelint findings arrive on file-open — the v1.213.0
 * eslint arrangement, told for stylesheets. The platform's own CSS
 * grammar predates modern syntax and its false positives cannot be
 * silenced externally (ledger 71), so the linter that DOES understand
 * modern CSS rides beside it. These tests pin the registration shape,
 * the trust-and-config gates, and the catalog/Doctor rows.
 */
class StylelintServerTest {

    private static String source(String rel) throws IOException {
        return Files.readString(Path.of(rel), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("stylelint is registered on the whole css family, both mime spellings")
    void registeredOnCssFamily() throws IOException {
        String src = source("src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java");
        int at = src.indexOf("class StylelintServer");
        assertThat(at).as("StylelintServer exists").isGreaterThan(0);
        String block = src.substring(Math.max(0, at - 700), at);
        // the css-prep mimes real files resolve to AND the x- pair that
        // reaches .sass — the v1.230.0 mime lesson applied on day one
        assertThat(block).contains("text/css").contains("text/scss")
                .contains("text/less").contains("text/x-scss").contains("text/x-less");
    }

    @Test
    @DisplayName("trust-gated AND config-gated, through the shared launcher")
    void gatesAndLauncher() throws IOException {
        String src = source("src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java");
        int at = src.indexOf("class StylelintServer");
        String body = src.substring(at, src.indexOf("static boolean hasStylelintConfig", at));
        // a stylelint config is executable JS resolved from the project's
        // node_modules — the v1.216.0 payload law, not just the binary gate
        assertThat(body).contains("WorkspaceTrust.isTrusted");
        assertThat(body).contains("hasStylelintConfig");
        assertThat(body).contains("launchNpm(");
        assertThat(body).contains("stylelint-lsp");
    }

    @Test
    @DisplayName("every config spelling opts in; a bare project does not")
    void configSpellings(@TempDir File dir) throws IOException {
        assertThat(LanguageServers.hasStylelintConfig(dir)).isFalse();
        for (String name : new String[]{
            ".stylelintrc", ".stylelintrc.json", ".stylelintrc.js",
            "stylelint.config.mjs"}) {
            File f = new File(dir, name);
            assertThat(f.createNewFile()).isTrue();
            assertThat(LanguageServers.hasStylelintConfig(dir))
                    .as(name + " opts in").isTrue();
            assertThat(f.delete()).isTrue();
        }
        // package.json carrying a stylelint key opts in too
        Files.writeString(new File(dir, "package.json").toPath(),
                "{\"stylelint\": {\"rules\": {}}}");
        assertThat(LanguageServers.hasStylelintConfig(dir)).isTrue();
    }

    @Test
    @DisplayName("the catalog knows stylelint, naming both packages the install needs")
    void catalogEntry() {
        LanguageServerCatalog.Server s = LanguageServerCatalog.forBinary("stylelint-lsp");
        assertThat(s).as("a missing binary must be explainable").isNotNull();
        assertThat(s.language()).containsIgnoringCase("stylelint");
        // the lsp wrapper and the linter are separate packages; a hint
        // naming only one leaves a broken install
        assertThat(s.install()).contains("stylelint-lsp").contains("stylelint");
    }

    @Test
    @DisplayName("Environment Doctor probes the stylelint server")
    void doctorProbe() throws IOException {
        String doctor = source(
                "../rack/src/main/java/org/nmox/studio/rack/projectstudio/EnvironmentDoctor.java");
        assertThat(doctor).contains("stylelint-lsp");
    }
}
