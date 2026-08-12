package org.nmox.studio.editor.lsp;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The marker walk is BOUNDED — v1.354.0, from the v1.349–v1.353 arc
 * review. Both the LSP authority checks (denoRootAbove /
 * angularRootAbove) and Run Focused Test's runner selection climb
 * ancestors looking for deno.json / angular.json, and an UNBOUNDED
 * climb reaches $HOME: a stray ~/deno.json (a real pattern — deno's
 * own config discovery reads ancestor directories) would flip every
 * project's TypeScript server to deno lsp and every JS/TS focused
 * test to {@code deno test}. The law: a marker above the repo's
 * {@code .git} belongs to someone else.
 */
class RootAboveBoundaryTest {

    @TempDir
    Path home;

    @Test
    @DisplayName("a marker above the repo's .git is never found — the repo boundary stops the climb")
    void gitBoundaryStopsTheClimb() throws Exception {
        // ~/deno.json above ~/repo/.git, file inside the repo
        Files.writeString(home.resolve("deno.json"), "{}");
        Path repo = Files.createDirectories(home.resolve("repo"));
        Files.createDirectories(repo.resolve(".git"));
        Path src = Files.createDirectories(repo.resolve("src"));

        assertThat(LanguageServers.rootAbove(src.toFile(), "deno.json", "deno.jsonc"))
                .as("the ~/deno.json must not hijack a plain repo below it")
                .isNull();
    }

    @Test
    @DisplayName("a marker AT the repo root still wins — markers check before the boundary")
    void markerAtRepoRootWins() throws Exception {
        Path repo = Files.createDirectories(home.resolve("repo"));
        Files.createDirectories(repo.resolve(".git"));
        Files.writeString(repo.resolve("deno.json"), "{}");
        Path src = Files.createDirectories(repo.resolve("src"));

        assertThat(LanguageServers.rootAbove(src.toFile(), "deno.json", "deno.jsonc"))
                .isEqualTo(repo.toFile());
    }

    @Test
    @DisplayName("the climb is depth-capped even without a .git anywhere")
    void depthCapHolds() throws Exception {
        Files.writeString(home.resolve("angular.json"), "{}");
        Path deep = home;
        for (int i = 0; i < 10; i++) {
            deep = Files.createDirectories(deep.resolve("d" + i));
        }
        assertThat(LanguageServers.rootAbove(deep.toFile(), "angular.json"))
                .as("10 levels down: the 8-level cap stops short of the marker")
                .isNull();
        File shallow = home.resolve("d0/d1/d2").toFile();
        assertThat(LanguageServers.rootAbove(shallow, "angular.json"))
                .as("3 levels down: the marker is inside the bound")
                .isEqualTo(home.toFile());
    }
}
