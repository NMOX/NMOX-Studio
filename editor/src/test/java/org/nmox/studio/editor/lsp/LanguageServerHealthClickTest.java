package org.nmox.studio.editor.lsp;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.nmox.studio.editor.lsp.LanguageServerCatalog.Server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The zero-friction install click (Angular-top arc): a missing-server
 * notification RUNS the install when the catalog knows the exact argv
 * and its package manager is present, and falls back to click-to-copy
 * otherwise — the wrong branch either mangles nothing (copy where run
 * was possible: friction) or spawns nothing it shouldn't (the installer
 * itself stays trust-gated), but the detail text must always match
 * what the click will actually do.
 */
class LanguageServerHealthClickTest {

    @Test
    @DisplayName("an auto-installable server with its package manager present installs on click")
    void autoInstallableRuns() {
        assumeTrue(LanguageServerCatalog.isInstalled("npm"), "npm required");
        Server ng = LanguageServerCatalog.forBinary("ngserver");
        assertThat(ng).isNotNull();
        assertThat(LanguageServerHealth.clickInstalls(ng)).isTrue();
        assertThat(LanguageServerHealth.detail(ng, "ngserver", ng.install()))
                .startsWith("Click to install ngserver into the project")
                .contains(ng.install());
    }

    @Test
    @DisplayName("a manual server (or unknown binary) keeps click-to-copy")
    void manualCopies() {
        Server manual = new Server("X", "xls", "get it somewhere", List.of(), false);
        assertThat(LanguageServerHealth.clickInstalls(manual)).isFalse();
        assertThat(LanguageServerHealth.clickInstalls(null)).isFalse();
        assertThat(LanguageServerHealth.detail(manual, "xls", "get it somewhere"))
                .contains("click to copy")
                .doesNotContain("Click to install");
        assertThat(LanguageServerHealth.detail(null, "yls", "hint"))
                .contains("click to copy");
    }

    @Test
    @DisplayName("a runnable command whose package manager is absent falls back to copy")
    void absentToolchainCopies() {
        Server ghost = new Server("Y", "yserver", "definitely-not-a-tool install y",
                List.of("definitely-not-a-tool-9x7", "install", "y"), false);
        assertThat(LanguageServerHealth.clickInstalls(ghost))
                .as("a doomed install must not be offered as a click").isFalse();
        assertThat(LanguageServerHealth.detail(ghost, "yserver", ghost.install()))
                .contains("click to copy");
    }
}
