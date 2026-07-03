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
}
