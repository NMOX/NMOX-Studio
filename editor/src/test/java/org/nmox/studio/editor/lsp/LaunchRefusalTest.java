package org.nmox.studio.editor.lsp;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The launch() refusal predicate (v1.218.0). The bug this pins: the old
 * bare-name-only check tested {@code new File(pathDir, absolutePath)}
 * for every PATH entry — never true — so every ABSOLUTE command was
 * refused, and the project-local {@code .bin/ngserver} the v1.216.0
 * install fix resolves could never launch. Found live: trusted Angular
 * workspace, correct probe dirs, correct binary — and no server process,
 * silently.
 */
class LaunchRefusalTest {

    @Test
    @DisplayName("an absolute executable path launches — the ngserver case")
    void absoluteExecutableAccepted(@TempDir File dir) throws Exception {
        File bin = new File(dir, "ngserver");
        Files.writeString(bin.toPath(), "#!/bin/sh\n");
        assertThat(bin.setExecutable(true)).isTrue();
        assertThat(LanguageServers.refusesCommand(
                bin.getAbsolutePath(), bin.getAbsolutePath())).isFalse();
    }

    @Test
    @DisplayName("an absolute path that does not exist is refused")
    void absoluteMissingRefused(@TempDir File dir) {
        String ghost = new File(dir, "no-such-server").getAbsolutePath();
        assertThat(LanguageServers.refusesCommand(ghost, ghost)).isTrue();
    }

    @Test
    @DisplayName("a bare name nowhere on PATH is refused")
    void bareMissingNameRefused() {
        assertThat(LanguageServers.refusesCommand(
                "definitely-not-a-real-language-server-binary",
                "definitely-not-a-real-language-server-binary")).isTrue();
    }

    @Test
    @DisplayName("a name the locator resolved elsewhere is accepted")
    void resolvedNameAccepted(@TempDir File dir) throws Exception {
        File bin = new File(dir, "tool");
        Files.writeString(bin.toPath(), "#!/bin/sh\n");
        assertThat(bin.setExecutable(true)).isTrue();
        // resolved != original means ToolLocator found it — no refusal
        assertThat(LanguageServers.refusesCommand(
                bin.getAbsolutePath(), "tool")).isFalse();
    }
}
