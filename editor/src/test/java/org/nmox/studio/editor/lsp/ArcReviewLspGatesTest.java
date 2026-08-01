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
 * The v1.216.0 arc-review fixes on the two new LSP providers, pinned.
 *
 * <p>Both v1.213/v1.214 providers honored the v1.102.0 trust law for the
 * server BINARY and missed that the binary's PAYLOAD is workspace code:
 * eslint's flat configs are plain JavaScript the server evaluates, and
 * ngserver {@code require()}s typescript and the Angular compiler from
 * the probe locations — the project's own {@code node_modules}. Opening
 * one file in a hostile clone executed repo-committed JS. The gates here
 * are source-gated the same way the launchNpm gate is.
 */
class ArcReviewLspGatesTest {

    private static String source() throws IOException {
        return Files.readString(
                Path.of("src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java"),
                StandardCharsets.UTF_8);
    }

    private static String body(String src, String from, String to) {
        int a = src.indexOf(from);
        assertThat(a).as(from + " exists").isGreaterThan(0);
        return src.substring(a, src.indexOf(to, a));
    }

    @Test
    @DisplayName("AngularServer gates on workspace trust before any launch")
    void angularTrustGate() throws IOException {
        String b = body(source(), "class AngularServer", "static File angularProbeDir");
        // the gate must sit before the launch in the SAME method — the
        // probe locations are repo code ngserver executes
        int gate = b.indexOf("WorkspaceTrust.isTrusted");
        int launch = b.indexOf("launch(lookup");
        assertThat(gate).as("trust gate present").isGreaterThan(0);
        assertThat(launch).as("launch present").isGreaterThan(0);
        assertThat(gate).as("gate precedes launch").isLessThan(launch);
    }

    @Test
    @DisplayName("EslintServer gates on workspace trust — configs are executable JS")
    void eslintTrustGate() throws IOException {
        String b = body(source(), "class EslintServer", "static boolean hasEslintConfig");
        assertThat(b).contains("WorkspaceTrust.isTrusted");
        // and only starts where eslint is actually configured
        assertThat(b).contains("hasEslintConfig(dir)");
    }

    @Test
    @DisplayName("eslint config detection: every spelling, and absence")
    void eslintConfigSpellings(@TempDir File dir) throws IOException {
        assertThat(LanguageServers.hasEslintConfig(dir))
                .as("bare project has no config").isFalse();

        for (String name : new String[]{"eslint.config.js", ".eslintrc.json", ".eslintrc"}) {
            File f = new File(dir, name);
            Files.writeString(f.toPath(), "{}");
            assertThat(LanguageServers.hasEslintConfig(dir)).as(name).isTrue();
            assertThat(f.delete()).isTrue();
        }

        Files.writeString(new File(dir, "package.json").toPath(),
                "{\"name\":\"x\",\"eslintConfig\":{}}");
        assertThat(LanguageServers.hasEslintConfig(dir))
                .as("package.json eslintConfig key").isTrue();
    }

    @Test
    @DisplayName("probe dir survives workspace hoisting — the FILE decides, not the directory")
    void probeDirSurvivesHoisting(@TempDir File root) throws IOException {
        // npm-workspaces shape: the app's node_modules exists but holds
        // only .bin links; typescript is hoisted to the repo root. The
        // old isDirectory() test picked the half-empty nested dir and
        // declined a perfectly good install.
        File appModules = new File(root, "node_modules");
        assertThat(new File(appModules, ".bin").mkdirs()).isTrue();
        File hoistedTs = new File(root, "node_modules/typescript/lib");
        assertThat(hoistedTs.mkdirs()).isTrue();
        Files.writeString(new File(hoistedTs, "tsserverlibrary.js").toPath(), "// ts5");

        File chosen = LanguageServers.angularProbeDir(root);
        assertThat(new File(chosen, "typescript/lib/tsserverlibrary.js"))
                .as("the chosen probe dir actually carries the library")
                .isFile();
    }

    @Test
    @DisplayName("no usable TypeScript anywhere still returns a dir for the honest decline")
    void noTypescriptStillReportsHonestly(@TempDir File root) {
        assertThat(new File(root, "node_modules").mkdirs()).isTrue();
        // an install exists but has no tsserverlibrary (TS 7): the caller
        // needs a non-null dir so its TS-7 check produces the catalog
        // message instead of silence
        assertThat(LanguageServers.angularProbeDir(root)).isNotNull();
    }

    @Test
    @DisplayName("AngularServer resolves the project-local ngserver the catalog installs")
    void angularResolvesProjectLocalBinary() throws IOException {
        String b = body(source(), "class AngularServer", "static File angularProbeDir");
        // the catalog's documented install is npm i -D — without this
        // resolution the flagship feature's own instructions produced a
        // server the IDE could never find (v1.216.0)
        assertThat(b).contains(".bin/ngserver");
    }

    @Test
    @DisplayName("the Angular catalog entry is marked project-local; the globals are not")
    void catalogProjectLocalFlags() {
        assertThat(LanguageServerCatalog.forBinary("ngserver").projectLocal()).isTrue();
        assertThat(LanguageServerCatalog.forBinary("vscode-eslint-language-server")
                .projectLocal()).isFalse();
        assertThat(LanguageServerCatalog.forBinary("pyright-langserver")
                .projectLocal()).isFalse();
    }

    @Test
    @DisplayName("a project-local install refuses without a project instead of polluting $HOME")
    void projectLocalInstallNeedsProject() throws IOException {
        // Source-gated: the installer must consult ProjectAim for
        // project-local entries and refuse with NEEDS_PROJECT when there
        // is none — running npm install --save-dev in $HOME created
        // ~/package.json and ~/node_modules while reporting INSTALLED.
        String src = Files.readString(
                Path.of("src/main/java/org/nmox/studio/editor/lsp/LanguageServerInstaller.java"),
                StandardCharsets.UTF_8);
        assertThat(src).contains("server.projectLocal()");
        assertThat(src).contains("Result.NEEDS_PROJECT");
        assertThat(src).contains("ProjectAim.find()");
    }
}
