package org.nmox.studio.project;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v1.38.0 startup audit: this tab is open-at-startup, so the constructor
 * and componentOpened both fire during window-system load — and both ran
 * refresh(), which fans out the per-project toolchain-detect walk. One
 * boot, two walks. componentOpened owns the refresh (its comment explains
 * why: aims that landed while closed must show on reopen); the constructor
 * stays passive.
 */
class ProjectExplorerBootGateTest {

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertThat(start).as(signature + " exists").isGreaterThan(0);
        int end = source.indexOf("\n    }", start);
        return source.substring(start, end);
    }

    @Test
    @DisplayName("the toolchain-detect walk runs once per boot — componentOpened owns it")
    void constructorDoesNotRefresh() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/project/ProjectExplorerTopComponent.java"),
                StandardCharsets.UTF_8);

        assertThat(method(source, "public ProjectExplorerTopComponent()"))
                .as("constructor refresh doubles the detect walk on every boot")
                .doesNotContain("refresh();");
        assertThat(method(source, "public void componentOpened()"))
                .contains("refresh();");
    }
}
