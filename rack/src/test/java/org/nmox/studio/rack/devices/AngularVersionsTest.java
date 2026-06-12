package org.nmox.studio.rack.devices;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class AngularVersionsTest {

    @TempDir
    Path projectDir;

    @Test
    @DisplayName("Cleans range operators and reads the installed constraint")
    void readsInstalledVersion() throws Exception {
        Files.writeString(projectDir.resolve("package.json"), """
            {"dependencies": {"@angular/core": "^21.2.3"}}
            """);
        assertThat(AngularVersions.installed(projectDir.toFile())).isEqualTo("21.2.3");
        assertThat(AngularVersions.clean("~22.0.1")).isEqualTo("22.0.1");
        assertThat(AngularVersions.installed(new File(projectDir.toFile(), "missing"))).isNull();
    }

    @Test
    @DisplayName("Outdated and major-behind comparisons tell the truth")
    void comparesVersions() {
        assertThat(AngularVersions.isOutdated("22.0.0", "22.0.1")).isTrue();
        assertThat(AngularVersions.isOutdated("22.0.1", "22.0.1")).isFalse();
        assertThat(AngularVersions.isOutdated("22.1.0", "22.0.9")).isFalse();
        assertThat(AngularVersions.isOutdated("^21.0.0", "22.0.1")).isTrue();
        assertThat(AngularVersions.isOutdated(null, "22.0.1")).isFalse();

        assertThat(AngularVersions.isMajorBehind("21.9.9", "22.0.0")).isTrue();
        assertThat(AngularVersions.isMajorBehind("22.0.0", "22.9.0")).isFalse();
    }

    @Test
    @DisplayName("Angular workspace detection finds angular.json at root and in Node subdirs")
    void detectsAngularWorkspace() throws Exception {
        assertThat(ProjectInspector.hasAngular(projectDir.toFile())).isFalse();

        Files.writeString(projectDir.resolve("angular.json"), "{}");
        assertThat(ProjectInspector.hasAngular(projectDir.toFile())).isTrue();
    }
}
