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
    @DisplayName("the workspace's typescript wins: 5 with a tsserver serves, 7 without one is the wall")
    void workspaceTypescript(@TempDir Path project, @TempDir Path prefix) throws Exception {
        typescript(project.resolve("node_modules/typescript"), "5.9.2", true);
        File bin = prefix.resolve("bin/typescript-language-server").toFile();
        assertThat(TsServerPrecheck.check(project.toFile(), bin).kind()).isEqualTo(TsServerPrecheck.Kind.SERVEABLE);
        typescript(project.resolve("node_modules/typescript"), "7.0.2", false);
        TsServerPrecheck.Verdict v = TsServerPrecheck.check(project.toFile(), bin);
        assertThat(v.kind()).isEqualTo(TsServerPrecheck.Kind.NO_TSSERVER);
        assertThat(v.version()).isEqualTo("7.0.2");
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
        typescript(prefix.resolve("lib/node_modules/typescript"), "5.9.2", true);
        assertThat(TsServerPrecheck.check(project.toFile(), bin).kind()).isEqualTo(TsServerPrecheck.Kind.SERVEABLE);
        assertThat(TsServerPrecheck.check(null, null).kind()).isEqualTo(TsServerPrecheck.Kind.NOT_FOUND);
    }

    @Test
    @DisplayName("launchNpm consults the precheck for the TypeScript server and refuses with the door (wiring)")
    void wiring() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java"));
        assertThat(src).contains("TsServerPrecheck.check(").contains("LanguageServerHealth.reportNoTsserver(");
        String health = Files.readString(Path.of("src/main/java/org/nmox/studio/editor/lsp/LanguageServerHealth.java"));
        assertThat(health).contains("typescript-language-server:no-tsserver").contains("ships no tsserver");
    }
}
