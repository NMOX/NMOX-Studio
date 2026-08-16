package org.nmox.studio.editor.lsp;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VueServer's two pure resolutions (v2.14.0): the tsdk probe (the
 * project's own typescript/lib, judged by the FILE the 2.x server
 * loads — typescript.js, not ngserver's tsserverlibrary.js) and the
 * version ceiling (a local 3.x pin is the un-bridgeable line and must
 * decline, not spawn-and-silence).
 */
class VueServerResolutionTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("vueTsdk probes for typescript.js and returns the lib dir")
    void tsdkProbe() throws Exception {
        assertThat(LanguageServers.vueTsdk(dir.toFile()))
                .as("no install: no tsdk").isNull();
        Path lib = dir.resolve("node_modules/typescript/lib");
        Files.createDirectories(lib);
        assertThat(LanguageServers.vueTsdk(dir.toFile()))
                .as("a bare dir without typescript.js is not a tsdk —"
                        + " workspace hoisting leaves empty shells")
                .isNull();
        Files.writeString(lib.resolve("typescript.js"), "// ts");
        File found = LanguageServers.vueTsdk(dir.toFile());
        assertThat(found).isNotNull();
        assertThat(found.getAbsolutePath()).endsWith("typescript/lib".replace('/', File.separatorChar));
    }

    @Test
    @DisplayName("vueServerMajor reads the pinned line; 3.x is the ceiling")
    void versionCeiling() throws Exception {
        assertThat(LanguageServers.vueServerMajor(dir.toFile()))
                .as("no install reads as -1, never as a passing version").isEqualTo(-1);
        Path pkg = dir.resolve("node_modules/@vue/language-server");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("package.json"), "{\"version\":\"2.2.12\"}");
        assertThat(LanguageServers.vueServerMajor(dir.toFile())).isEqualTo(2);
        Files.writeString(pkg.resolve("package.json"), "{\"version\":\"3.3.10\"}");
        assertThat(LanguageServers.vueServerMajor(dir.toFile())).isEqualTo(3);
        Files.writeString(pkg.resolve("package.json"), "not json at all");
        assertThat(LanguageServers.vueServerMajor(dir.toFile())).isEqualTo(-1);
    }
}
