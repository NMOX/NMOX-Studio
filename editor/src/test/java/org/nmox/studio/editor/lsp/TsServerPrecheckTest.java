package org.nmox.studio.editor.lsp;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/** The TypeScript wall (v2.85.0): a typescript without lib/tsserver.js — TypeScript 7 — is named before the spawn. */
class TsServerPrecheckTest {

    private static void typescript(Path dir, String version, boolean tsserver) throws Exception {
        Files.createDirectories(dir.resolve("lib"));
        Files.writeString(dir.resolve("package.json"), "{\"name\":\"typescript\",\"version\":\"" + version + "\"}");
        if (tsserver) {
            Files.writeString(dir.resolve("lib/tsserver.js"), "// tsserver");
        } else {
            Files.deleteIfExists(dir.resolve("lib/tsserver.js"));
        }
    }

    @Test
    @org.junit.jupiter.api.DisplayName("the version read is bounded: a package.json whose version sits past the cap costs the cap, not the heap")
    void versionReadIsBounded(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path normal = dir.resolve("normal.json");
        java.nio.file.Files.writeString(normal, "{\n  \"name\": \"typescript\",\n  \"version\": \"5.9.2\"\n}\n");
        assertThat(TsServerPrecheck.version(normal.toFile())).isEqualTo("5.9.2");
        java.nio.file.Path padded = dir.resolve("padded.json");
        String pad = " ".repeat(TsServerPrecheck.VERSION_READ_CAP + 1024);
        java.nio.file.Files.writeString(padded, "{" + pad + "\"version\": \"9.9.9\"}");
        assertThat(TsServerPrecheck.version(padded.toFile()))
                .as("past the cap is unread — the bounded-read law for a file under someone else's node_modules")
                .isEqualTo("?");
    }

    @Test
    @DisplayName("the workspace's typescript wins: 5 with a tsserver serves, 7 without one is the wall")
    void workspaceTypescript(@TempDir Path project, @TempDir Path prefix) throws Exception {
        typescript(project.resolve("node_modules/typescript"), "5.9.2", true);
        File bin = prefix.resolve("bin/typescript-language-server").toFile();
        assertThat(TsServerPrecheck.check(project.toFile(), bin).kind()).isEqualTo(TsServerPrecheck.Kind.SERVEABLE);
        typescript(project.resolve("node_modules/typescript"), "7.0.2", false);
        TsServerPrecheck.Verdict v = TsServerPrecheck.check(project.toFile(), bin);
        assertThat(v.kind()).isEqualTo(TsServerPrecheck.Kind.NO_TSSERVER);
        assertThat(v.version()).isEqualTo("7.0.2");
        assertThat(v.workspace()).as("it is the project's: what it carries waits for trust").isTrue();
        assertThat(v.typescriptDir()).isEqualTo(project.resolve("node_modules/typescript").toFile());
    }

    @Test
    @DisplayName("no workspace typescript: the global sibling beside the server binary (the npm prefix layout) decides")
    void globalSibling(@TempDir Path project, @TempDir Path prefix) throws Exception {
        File bin = prefix.resolve("bin/typescript-language-server").toFile();
        assertThat(TsServerPrecheck.check(project.toFile(), bin).kind()).as("nothing anywhere").isEqualTo(TsServerPrecheck.Kind.NOT_FOUND);
        typescript(prefix.resolve("lib/node_modules/typescript"), "7.0.2", false);
        TsServerPrecheck.Verdict v = TsServerPrecheck.check(project.toFile(), bin);
        assertThat(v.kind()).as("this machine's exact shape: nvm prefix, typescript 7 beside the server").isEqualTo(TsServerPrecheck.Kind.NO_TSSERVER);
        assertThat(v.version()).isEqualTo("7.0.2");
        assertThat(v.workspace()).as("the user's own install").isFalse();
        typescript(prefix.resolve("lib/node_modules/typescript"), "5.9.2", true);
        assertThat(TsServerPrecheck.check(project.toFile(), bin).kind()).isEqualTo(TsServerPrecheck.Kind.SERVEABLE);
        assertThat(TsServerPrecheck.check(null, null).kind()).isEqualTo(TsServerPrecheck.Kind.NOT_FOUND);
    }

    @Test
    @DisplayName("npm's Windows prefix layout — the typescript beside the .cmd shim, no lib/ level — is a candidate too")
    void windowsPrefix(@TempDir Path project, @TempDir Path prefix) throws Exception {
        File bin = prefix.resolve("typescript-language-server.cmd").toFile();
        typescript(prefix.resolve("node_modules/typescript"), "7.0.2", false);
        assertThat(TsServerPrecheck.check(project.toFile(), bin).kind()).isEqualTo(TsServerPrecheck.Kind.NO_TSSERVER);
    }

    @Test
    @DisplayName("launchNpm consults the precheck, and a TypeScript without a tsserver goes to its own server, not to a notice (wiring)")
    void wiring() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java")).replace("\r\n", "\n");
        int branch = src.indexOf("if (v.kind() == TsServerPrecheck.Kind.NO_TSSERVER) {\n                return nativeTypeScript(lookup, dir, v);");
        assertThat(branch).as("TypeScript 7 is served by TypeScript 7 (3.10.0)").isPositive();
        assertThat(src.indexOf("TsServerPrecheck.check(")).isPositive().isLessThan(branch);
        String health = Files.readString(Path.of("src/main/java/org/nmox/studio/editor/lsp/LanguageServerHealth.java"));
        assertThat(health).as("the notice that is left is about a missing compiler, and offers the user's own TypeScript")
                .contains("typescript:no-compiler").contains("has no compiler binary for this machine")
                .contains("\"npm\", \"install\", \"-g\", \"typescript\")")
                .as("the refusal speaks in the log too").contains("was not started");
        assertThat(health).as("TypeScript 5 is no longer what a TypeScript 7 project is told to install")
                .doesNotContain("reportNoTsserver");
    }
}
