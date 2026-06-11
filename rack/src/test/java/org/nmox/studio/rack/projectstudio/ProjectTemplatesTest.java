package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackIO;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every template must produce a complete project: parseable
 * package.json with the right name, the standard housekeeping files,
 * and a rack patch that actually mounts devices and cables.
 */
class ProjectTemplatesTest {

    @TempDir
    Path parent;

    @Test
    @DisplayName("Should generate a complete wired project from every template")
    void shouldGenerateEveryTemplate() throws Exception {
        for (ProjectTemplates template : ProjectTemplates.values()) {
            File dir = parent.resolve(template.name().toLowerCase()).toFile();

            template.generate(dir, "demo-app");

            // package.json parses and carries the chosen name
            Path pkg = dir.toPath().resolve("package.json");
            assertThat(pkg).as(template + " package.json").exists();
            JSONObject json = new JSONObject(Files.readString(pkg));
            assertThat(json.getString("name")).isEqualTo("demo-app");
            assertThat(json.getJSONObject("scripts").keySet()).isNotEmpty();

            // housekeeping
            assertThat(dir.toPath().resolve(".gitignore")).exists();
            assertThat(dir.toPath().resolve("README.md")).exists();

            // the infra patch mounts: devices present, cables patched
            Path patch = dir.toPath().resolve(RackIO.DEFAULT_FILENAME);
            assertThat(patch).as(template + " rack patch").exists();
            Rack rack = new Rack();
            rack.setProjectDir(dir);
            try {
                RackIO.fromJson(rack, new JSONObject(Files.readString(patch)));
                assertThat(rack.getDevices()).as(template + " devices").isNotEmpty();
                assertThat(rack.getCables()).as(template + " cables").isNotEmpty();
            } finally {
                rack.shutdown();
            }
        }
    }

    @Test
    @DisplayName("Should refuse to generate into a non-empty directory")
    void shouldRefuseNonEmptyDirectory() throws Exception {
        File dir = parent.resolve("occupied").toFile();
        assertThat(dir.mkdirs()).isTrue();
        Files.writeString(dir.toPath().resolve("existing.txt"), "data");

        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class,
                () -> ProjectTemplates.VANILLA.generate(dir, "demo"));
    }
}
