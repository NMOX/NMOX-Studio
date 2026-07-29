package org.nmox.studio.core.process;

import java.io.File;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

class ToolLocatorTest {

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisplayName("Should resolve a standard tool to an absolute path")
    void shouldResolveStandardTool() {
        String resolved = ToolLocator.resolve("sh");
        assertThat(resolved).startsWith("/");
        assertThat(new File(resolved)).exists();
    }

    @Test
    @DisplayName("Should leave unknown tools unchanged for the OS to report")
    void shouldLeaveUnknownToolsAlone() {
        assertThat(ToolLocator.resolve("definitely-not-a-real-tool-xyz"))
                .isEqualTo("definitely-not-a-real-tool-xyz");
    }

    @Test
    @DisplayName("Should not touch commands that are already paths")
    void shouldNotTouchAbsolutePaths() {
        String path = File.separator + "opt" + File.separator + "thing";
        assertThat(ToolLocator.resolve(path)).isEqualTo(path);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisplayName("Should resolve only the executable of a command line")
    void shouldResolveCommandHead() {
        List<String> resolved = ToolLocator.resolveCommand(List.of("sh", "-c", "echo hi"));
        assertThat(resolved.get(0)).startsWith("/");
        assertThat(resolved.subList(1, 3)).containsExactly("-c", "echo hi");
    }

    @Test
    @DisplayName("Augmented PATH must include every existing PATH entry")
    void augmentedPathKeepsExistingEntries() {
        String envPath = System.getenv("PATH");
        if (envPath == null) {
            return;
        }
        String augmented = ToolLocator.augmentedPath();
        for (String dir : envPath.split(File.pathSeparator)) {
            if (!dir.isBlank() && new File(dir).isDirectory()) {
                assertThat(augmented).contains(dir);
            }
        }
    }

    @Test
    @DisplayName("An empty command list resolves to itself")
    void emptyCommandListUnchanged() {
        assertThat(ToolLocator.resolveCommand(java.util.List.of())).isEmpty();
    }

    @Test
    @DisplayName("Windows-style .exe/.cmd names resolve, and the newest version-manager dir wins")
    void resolvesPathextAndVersionDirs(@org.junit.jupiter.api.io.TempDir java.io.File home)
            throws Exception {
        // searchDirs reads user.home live: point it at a fixture home carrying
        // a volta bin (with .exe/.cmd tools) and an nvm versions tree where the
        // newest version has a bin dir and a newer-named empty one does not
        java.io.File volta = new java.io.File(home, ".volta/bin");
        assertThat(volta.mkdirs()).isTrue();
        assertThat(new java.io.File(volta, "faketool.exe").createNewFile()).isTrue();
        assertThat(new java.io.File(volta, "otherfake.cmd").createNewFile()).isTrue();
        java.io.File nvmWithBin = new java.io.File(home, ".nvm/versions/node/v20.1.0/bin");
        assertThat(nvmWithBin.mkdirs()).isTrue();
        // sorts AFTER v20.1.0 reversed (v9 > v2 lexicographically) but has no
        // bin dir, so the locator must fall through to the one that does
        assertThat(new java.io.File(home, ".nvm/versions/node/v9-empty").mkdirs()).isTrue();

        String realHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.getAbsolutePath());
            ToolLocator.reset();

            assertThat(ToolLocator.resolve("faketool"))
                    .isEqualTo(new java.io.File(volta, "faketool.exe").getAbsolutePath());
            assertThat(ToolLocator.resolve("otherfake"))
                    .isEqualTo(new java.io.File(volta, "otherfake.cmd").getAbsolutePath());
            assertThat(ToolLocator.augmentedPath())
                    .contains(nvmWithBin.getAbsolutePath());
        } finally {
            System.setProperty("user.home", realHome);
            ToolLocator.reset(); // forget every fixture-home lookup
        }
    }
}
