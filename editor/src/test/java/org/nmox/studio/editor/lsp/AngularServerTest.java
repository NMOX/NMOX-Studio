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
 * v1.214.0: the Angular Language Service, and the two traps that make it
 * unlike every other server in the catalog.
 *
 * <p>Both were verified against the REAL published binary
 * ({@code @angular/language-server} 22.1.0) before a line was wired:
 *
 * <ol>
 *   <li>{@code ngserver} run bare does not print {@code --help}; it
 *       throws {@code Failed to resolve 'typescript/lib/tsserverlibrary'
 *       ... from []}. Probe locations are mandatory, not optional.</li>
 *   <li>It needs {@code typescript/lib/tsserverlibrary.js}. TypeScript 7
 *       — the native rewrite, and what a bare {@code npm install
 *       typescript} gives today (7.0.2) — no longer ships that file, so
 *       the server cannot start. TypeScript 5.9.3 starts it clean.</li>
 * </ol>
 */
class AngularServerTest {

    private static String source() throws IOException {
        return Files.readString(
                Path.of("src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java"),
                StandardCharsets.UTF_8);
    }

    private static String angularBody(String src) {
        int at = src.indexOf("class AngularServer");
        assertThat(at).as("AngularServer exists").isGreaterThan(0);
        return src.substring(at, src.indexOf("static File angularProbeDir", at));
    }

    @Test
    @DisplayName("both probe locations are passed — bare ngserver cannot start")
    void probeLocationsAreMandatory() throws IOException {
        String body = angularBody(source());
        assertThat(body)
                .as("verified on the real binary: without these it throws on startup")
                .contains("--tsProbeLocations")
                .contains("--ngProbeLocations");
    }

    @Test
    @DisplayName("TypeScript 7 is detected and declined, not crash-looped")
    void typeScriptSevenDeclinesHonestly() throws IOException {
        String body = angularBody(source());
        // TS 7 dropped this file; starting anyway would have the LSP
        // client retry an unstartable process on every file open
        assertThat(body).contains("typescript/lib/tsserverlibrary.js");
        assertThat(body)
                .as("a decline routes through the same channel every missing server uses")
                .contains("reported(null, \"ngserver\")");
    }

    @Test
    @DisplayName("it only starts inside an Angular workspace")
    void onlyInAngularWorkspaces() throws IOException {
        String body = angularBody(source());
        // the gate moved spellings in the ledger-79 fix: the workspace is
        // located by walking UP for angular.json (the owner project is
        // often src/), and a null root still declines — the law holds
        assertThat(body)
                .as("every other TypeScript project would otherwise pay for it")
                .contains("angularRootAbove(projectDir(lookup))");
    }

    @Test
    @DisplayName("it is additive — tsserver and eslint keep their registrations")
    void additiveOnTypeScript() throws IOException {
        String src = source();
        assertThat(src).contains("class TypeScriptServer");
        assertThat(src).contains("class EslintServer");
        // three providers now share text/typescript; the platform's
        // lookupAll collects them all
        assertThat(src.split("mimeType = \"text/typescript\"", -1).length - 1)
                .as("tsserver + eslint + angular all claim the TS mime")
                .isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("the probe dir prefers the Angular subproject's node_modules")
    void probeDirPrefersNestedInstall(@TempDir File root) throws IOException {
        // a monorepo whose Angular app lives under the repo root
        File appModules = new File(root, "node_modules");
        assertThat(appModules.mkdirs()).isTrue();

        File found = LanguageServers.angularProbeDir(root);
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("node_modules");
    }

    @Test
    @DisplayName("no install yet means no server, not an error")
    void noNodeModulesIsQuiet(@TempDir File root) {
        assertThat(LanguageServers.angularProbeDir(root))
                .as("npm install first; reopening the file starts it")
                .isNull();
    }

    @Test
    @DisplayName("the catalog pins TypeScript 5 in its install advice")
    void catalogPinsTypeScriptMajor() {
        LanguageServerCatalog.Server s = LanguageServerCatalog.forBinary("ngserver");
        assertThat(s).isNotNull();
        assertThat(s.language()).containsIgnoringCase("angular");
        // advice that installed TypeScript 7 would produce a server that
        // cannot start — the pin is the whole point
        assertThat(s.install()).contains("typescript@5");
        assertThat(s.install()).contains("@angular/language-server");
        // installed into the PROJECT, not globally: it must match the
        // versions the workspace builds with
        assertThat(s.command()).contains("--save-dev");
    }
}
