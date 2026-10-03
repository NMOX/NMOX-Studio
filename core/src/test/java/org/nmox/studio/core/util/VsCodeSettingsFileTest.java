package org.nmox.studio.core.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which .vscode/settings.json speaks for a place — the nearest inside the
 * repository, never one above its root — and what it excludes there.
 */
class VsCodeSettingsFileTest {

    @TempDir
    Path tmp;

    @BeforeEach
    void forget() {
        VsCodeSettingsFile.forgetForTest();
    }

    private static void settings(Path folder, String json) throws Exception {
        Files.createDirectories(folder.resolve(".vscode"));
        Files.writeString(folder.resolve(".vscode/settings.json"), json);
    }

    @Test
    @DisplayName("the nearest settings.json at or above the place, and none above the repository's root")
    void nearestInsideTheRepository() throws Exception {
        Path repo = Files.createDirectories(tmp.resolve("repo"));
        Files.createDirectories(repo.resolve(".git"));
        Path deep = Files.createDirectories(repo.resolve("packages/web/src"));
        File file = deep.resolve("app.js").toFile();
        assertThat(VsCodeSettingsFile.nearest(file)).isNull();
        assertThat(VsCodeSettingsFile.nearestForFolder(deep.toFile())).isNull();

        settings(tmp, "{}");
        assertThat(VsCodeSettingsFile.nearest(file)).as("above .git is somebody else's").isNull();

        settings(repo, "{}");
        assertThat(VsCodeSettingsFile.nearest(file)).isEqualTo(repo.resolve(".vscode/settings.json").toFile());
        assertThat(VsCodeSettingsFile.nearestForFolder(repo.toFile()))
                .as("a folder's own .vscode speaks for what it holds")
                .isEqualTo(repo.resolve(".vscode/settings.json").toFile());

        settings(repo.resolve("packages/web"), "{}");
        assertThat(VsCodeSettingsFile.nearest(file)).as("the nearest wins")
                .isEqualTo(repo.resolve("packages/web/.vscode/settings.json").toFile());
        assertThat(VsCodeSettingsFile.nearestForFolder(repo.resolve("packages").toFile()))
                .isEqualTo(repo.resolve(".vscode/settings.json").toFile());
        assertThat(VsCodeSettingsFile.nearest(null)).isNull();
        assertThat(VsCodeSettingsFile.nearestForFolder(null)).isNull();
    }

    @Test
    @DisplayName("outside any repository nothing is read")
    void outsideARepository() throws Exception {
        settings(tmp, "{\"files.exclude\": {\"**/dist\": true}}");
        Path loose = Files.createDirectories(tmp.resolve("loose"));
        assertThat(VsCodeSettingsFile.nearestForFolder(tmp.toFile())).isNull();
        assertThat(VsCodeSettingsFile.excludesFor(loose.toFile())).isSameAs(VsCodeExcludes.NONE);
        assertThat(VsCodeSettingsFile.excludesFor(null)).isSameAs(VsCodeExcludes.NONE);
    }

    @Test
    @DisplayName("the home folder's settings are a person's, not a project's")
    void neverTheHomeFolder() throws Exception {
        String home = System.getProperty("user.home");
        Path fake = Files.createDirectories(tmp.resolve("home"));
        Files.createDirectories(fake.resolve(".git"));
        settings(fake, "{\"files.exclude\": {\"**/dist\": true}}");
        Path project = Files.createDirectories(fake.resolve("work/app"));
        try {
            System.setProperty("user.home", fake.toFile().getAbsolutePath());
            assertThat(VsCodeSettingsFile.nearestForFolder(project.toFile())).isNull();
            assertThat(VsCodeSettingsFile.nearestForFolder(fake.toFile())).isNull();
        } finally {
            System.setProperty("user.home", home);
        }
        assertThat(VsCodeSettingsFile.nearestForFolder(project.toFile())).as("the control: found once it is not home")
                .isNotNull();
    }

    @Test
    @DisplayName("a folder beneath the settings' own asks with its own relative paths")
    void excludesForAPackage() throws Exception {
        Files.createDirectories(tmp.resolve(".git"));
        settings(tmp, "{\"files.exclude\": {\"packages/*/dist\": true, \"*.log\": true}}");
        Path web = Files.createDirectories(tmp.resolve("packages/web"));
        VsCodeExcludes atRoot = VsCodeSettingsFile.excludesFor(tmp.toFile());
        VsCodeExcludes atWeb = VsCodeSettingsFile.excludesFor(web.toFile());
        assertThat(atRoot.hides("packages/web/dist")).isTrue();
        assertThat(atRoot.hides("x.log")).isTrue();
        assertThat(atWeb.hides("dist")).isTrue();
        assertThat(atWeb.hides("x.log")).isFalse();
        assertThat(atWeb).isNotEqualTo(atRoot);
    }

    @Test
    @DisplayName("an edit is seen by the next question; an untouched file is parsed once")
    void followsEdits() throws Exception {
        Files.createDirectories(tmp.resolve(".git"));
        settings(tmp, "{\"files.exclude\": {\"**/dist\": true}}");
        Path file = tmp.resolve(".vscode/settings.json");
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000_000_000L));
        VsCodeExcludes first = VsCodeSettingsFile.excludesFor(tmp.toFile());
        assertThat(first.hides("dist")).isTrue();
        assertThat(VsCodeSettingsFile.excludesFor(tmp.toFile())).as("the same parse").isSameAs(first);

        Files.writeString(file, "{\"files.exclude\": {\"**/build\": true}}");
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000_005_000L));
        VsCodeExcludes second = VsCodeSettingsFile.excludesFor(tmp.toFile());
        assertThat(second.hides("dist")).isFalse();
        assertThat(second.hides("build")).isTrue();

        Files.delete(file);
        assertThat(VsCodeSettingsFile.excludesFor(tmp.toFile())).isSameAs(VsCodeExcludes.NONE);
    }

    @Test
    @DisplayName("a settings file past the size cap is not read: it excludes nothing")
    void oversizeIsNotRead() throws Exception {
        Files.createDirectories(tmp.resolve(".git"));
        String head = "{\"files.exclude\": {\"**/dist\": true}, \"x\": \"";
        settings(tmp, head + "a".repeat((int) VsCodeSettingsFile.MAX_BYTES) + "\"}");
        assertThat(VsCodeSettingsFile.excludesFor(tmp.toFile())).isSameAs(VsCodeExcludes.NONE);
        settings(tmp, head + "\"}");
        assertThat(VsCodeSettingsFile.excludesFor(tmp.toFile()).hides("dist")).as("the control").isTrue();
    }

    @Test
    @DisplayName("a file that says only things this cannot honour excludes nothing")
    void onlyUnhonoured() throws Exception {
        Files.createDirectories(tmp.resolve(".git"));
        settings(tmp, "{\"files.exclude\": {\"**/*.js\": {\"when\": \"$(basename).ts\"}}}");
        assertThat(VsCodeSettingsFile.excludesFor(tmp.toFile())).isSameAs(VsCodeExcludes.NONE);
    }
}
