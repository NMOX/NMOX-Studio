package org.nmox.studio.rack.devices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.devices.ProjectInspector.ProjectKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The glob-detection filter (v1.234.0 review): extension-detected
 * kinds (DOTNET by *.csproj/*.sln, NIM by *.nimble) must match plain
 * FILES only, never directories and never dotfiles — nimble's package
 * cache is a DIRECTORY named {@code ~/.nimble}, and before the filter
 * a rack aimed at $HOME detected NIM (the same bug's WebProjectFactory
 * half made $HOME a platform project, re-arming the v1.33.1 TCC-storm
 * class).
 */
class GlobManifestFilterTest {

    @Test
    @DisplayName("~/.nimble (a dot-DIRECTORY) does not make $HOME a Nim project")
    void dotDirectoryIsNotAManifest(@TempDir Path home) throws IOException {
        Files.createDirectory(home.resolve(".nimble"));
        assertThat(ProjectInspector.detectKind(home.toFile()))
                .as("nimble's cache dir is not a manifest")
                .isNotEqualTo(ProjectKind.NIM);
    }

    @Test
    @DisplayName("a plain directory whose NAME carries the suffix is not a manifest either")
    void suffixNamedDirectoryIsNotAManifest(@TempDir Path dir) throws IOException {
        Files.createDirectory(dir.resolve("gallery.sln"));
        assertThat(ProjectInspector.detectKind(dir.toFile()))
                .isNotEqualTo(ProjectKind.DOTNET);
    }

    @Test
    @DisplayName("real glob manifests still detect — the filter removes noise, not signal")
    void realGlobManifestsStillDetect(@TempDir Path nim, @TempDir Path net)
            throws IOException {
        Files.writeString(nim.resolve("tool.nimble"), "version = \"0.1\"\n");
        assertThat(ProjectInspector.detectKind(nim.toFile())).isEqualTo(ProjectKind.NIM);
        Files.writeString(net.resolve("app.csproj"), "<Project/>");
        assertThat(ProjectInspector.detectKind(net.toFile())).isEqualTo(ProjectKind.DOTNET);
    }
}
