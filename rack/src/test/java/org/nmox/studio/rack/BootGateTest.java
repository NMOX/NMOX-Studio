package org.nmox.studio.rack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v1.38.0 startup audit: open-at-startup TopComponents get constructed and
 * componentOpened during window-system load, hidden or not — so any work in
 * the constructor runs on every boot, and constructor + componentOpened
 * pairs run it twice. These pins keep the rack module's two default-open
 * surfaces honest about what boot may cost.
 */
class BootGateTest {

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertThat(start).as(signature + " exists").isGreaterThan(0);
        int end = source.indexOf("\n    }", start);
        return source.substring(start, end);
    }

    @Test
    @DisplayName("Project Studio syncs once per boot — componentOpened owns it, not the constructor")
    void projectStudioConstructorDoesNotSync() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/rack/projectstudio/ProjectStudioTopComponent.java"),
                StandardCharsets.UTF_8);

        assertThat(method(source, "public ProjectStudioTopComponent()"))
                .as("constructor sync = the directory list + FileWatcher spin-up runs twice per boot")
                .doesNotContain("syncToRack()");
        assertThat(method(source, "public void componentOpened()"))
                .contains("syncToRack()");
    }

    @Test
    @DisplayName("Docker panel generates dockerize previews on first show, not at boot")
    void dockerizeWalkDefersToShowing() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/rack/docker/DockerPanelTopComponent.java"),
                StandardCharsets.UTF_8);

        assertThat(method(source, "private Component buildDockerizeTab()"))
                .as("construction happens at boot behind the selected tab — no detect walk there")
                .doesNotContain("regenerateDockerize();")
                .contains("dockerizePending = true");
        assertThat(method(source, "protected void componentShowing()"))
                .as("first show serves the deferred preview generation")
                .contains("dockerizePending")
                .contains("regenerateDockerize()");
    }
}
