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
                .contains("npm install -g typescript-language-server");
        assertThat(LanguageServerCatalog.forBinary("typescript-language-server").command())
                .as("a named version replaces the one installed: no install here takes a machine's TypeScript 7 down to 5")
                .noneMatch(arg -> arg.startsWith("typescript@") || arg.equals("typescript"));
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

    @Test
    @DisplayName("no install names a TypeScript version into a project: the project's own is the project's decision")
    void noInstallNamesTypeScript() {
        // measured 3.10.0: 'npm i -D typescript@5' rewrote an Angular 22 project's "~6.0.3" to "^5.9.3"
        // (Angular 22 wants >=6.0 <6.1), and a bare 'typescript' gave a Vue project TypeScript 7,
        // which has no lib/typescript.js for Vue's server to load
        for (Server s : LanguageServerCatalog.all()) {
            assertThat(s.command()).as(s.binary() + " installs").noneMatch(arg -> arg.equals("typescript") || arg.startsWith("typescript@"));
            assertThat(s.install()).as(s.binary() + " says").doesNotContainPattern("(^| )typescript(@\\S+)?( |$)");
        }
    }

    @Test
    @DisplayName("a project that names no TypeScript is offered 5 by name; one that names its own, or cannot be read, is not")
    void typescriptIsNamedOnlyIntoAProjectWithNone(@org.junit.jupiter.api.io.TempDir java.nio.file.Path project) throws Exception {
        java.io.File dir = project.toFile();
        assertThat(LanguageServerCatalog.declaresTypeScript(dir)).as("no manifest at all").isFalse();
        java.nio.file.Files.writeString(project.resolve("package.json"), "{\"name\":\"shop\",\"dependencies\":{\"vue\":\"^3.5.0\"}}");
        assertThat(LanguageServerCatalog.declaresTypeScript(dir)).isFalse();
        java.nio.file.Files.writeString(project.resolve("package.json"),
                "{\"name\":\"shop\",\"devDependencies\":{\"typescript\":\"~5.8.3\"}}");
        assertThat(LanguageServerCatalog.declaresTypeScript(dir)).isTrue();
        java.nio.file.Files.writeString(project.resolve("package.json"), "{\"peerDependencies\":{\"typescript\":\">=5\"}}");
        assertThat(LanguageServerCatalog.declaresTypeScript(dir)).isTrue();
        java.nio.file.Files.writeString(project.resolve("package.json"), "{ not json");
        assertThat(LanguageServerCatalog.declaresTypeScript(dir))
                .as("a version is named only into a project KNOWN to have none").isTrue();
        java.nio.file.Files.writeString(project.resolve("package.json"),
                "{\"x\":\"" + "y".repeat((int) LanguageServerCatalog.MANIFEST_READ_CAP) + "\"}");
        assertThat(LanguageServerCatalog.declaresTypeScript(dir)).as("too large to read").isTrue();

        Server vue = LanguageServerCatalog.forBinary("vue-language-server");
        Server withFive = LanguageServerCatalog.withTypeScript5(vue);
        assertThat(withFive.command()).containsExactly("npm", "install", "--save-dev", "@vue/language-server@2", "typescript@5");
        assertThat(withFive.install()).isEqualTo("npm i -D @vue/language-server@2 typescript@5");
        assertThat(withFive.projectLocal()).isTrue();
        assertThat(vue.command()).as("the catalog's own entry is untouched").doesNotContain("typescript@5");
    }

    @Test
    @DisplayName("wiring: Vue's missing-TypeScript notice chooses its install by what the project declares")
    void vueDoorChoosesByTheProject() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java")).replace("\r\n", "\n");
        assertThat(src).contains("LanguageServerCatalog.declaresTypeScript(dir) ? catalogued\n"
                + "                                : LanguageServerCatalog.withTypeScript5(catalogued));");
        assertThat(src).as("a machine with nothing that serves TypeScript is offered TypeScript, which serves itself")
                .contains("LanguageServerHealth.reportMissing(\"typescript\", LanguageServerHealth.OWN_TYPESCRIPT);");
    }
}
