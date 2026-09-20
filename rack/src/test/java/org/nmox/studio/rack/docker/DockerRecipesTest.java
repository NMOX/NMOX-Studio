package org.nmox.studio.rack.docker;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dockerize recipes (v1.301.0, the fourth drop-in surface, the last
 * seam the plan.md direction named): a JSON file in
 * {@code ~/.nmox/dockerize.d} joins the Dockerize tab beside the
 * detected-toolchain generator. The path law is the SAME public
 * implementation the template drop-ins use, and the writer itself
 * carries a resolved-path guard so no future producer can reopen the
 * hole.
 */
class DockerRecipesTest {

    @Test
    @DisplayName("a valid recipe parses; {{name}} materializes in paths and content")
    void parsesAndMaterializes(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("corp.json"), """
                { "name": "Corp Node baseline", "files": {
                    "Dockerfile": "FROM node:24-alpine\\nLABEL app={{name}}\\n",
                    "compose.yaml": "services:\\n  {{name}}:\\n    build: .\\n" } }
                """, StandardCharsets.UTF_8);
        DockerRecipes.Loaded loaded = DockerRecipes.loadFrom(tmp.toFile());

        assertThat(loaded.skipped()).isEmpty();
        assertThat(loaded.recipes()).hasSize(1);
        Map<String, String> files =
                DockerRecipes.materialize(loaded.recipes().get(0), "billing-svc");
        assertThat(files.get("Dockerfile")).contains("LABEL app=billing-svc");
        assertThat(files.get("compose.yaml")).contains("  billing-svc:");
    }

    @Test
    @DisplayName("an unsafe path disqualifies the WHOLE recipe — same law, same home")
    void unsafePathRefusesWholeRecipe(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("evil.json"), """
                { "name": "Evil", "files": {
                    "Dockerfile": "FROM scratch",
                    "../outside": "escape" } }
                """, StandardCharsets.UTF_8);
        DockerRecipes.Loaded loaded = DockerRecipes.loadFrom(tmp.toFile());

        assertThat(loaded.recipes())
                .as("the innocent Dockerfile must not survive while the escape"
                        + " is quietly dropped")
                .isEmpty();
        assertThat(loaded.skipped()).hasSize(1);
        assertThat(loaded.skipped().get(0).reason()).contains("..");
    }

    @Test
    @DisplayName("a malformed drop-in is skipped with a note; the good ones load")
    void malformedSkipped(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("a-broken.json"), "{ nope", StandardCharsets.UTF_8);
        Files.writeString(tmp.resolve("b-good.json"),
                "{ \"name\": \"Good\", \"files\": { \"Dockerfile\": \"FROM scratch\" } }",
                StandardCharsets.UTF_8);
        DockerRecipes.Loaded loaded = DockerRecipes.loadFrom(tmp.toFile());
        assertThat(loaded.recipes()).extracting(DockerRecipes.Recipe::name)
                .containsExactly("Good");
        assertThat(loaded.skipped()).extracting(DockerRecipes.Skipped::file)
                .containsExactly("a-broken.json");
    }

    @Test
    @DisplayName("resolveInside refuses escapes BEHAVIORALLY — not by prose")
    void resolveInsideRefusesEscapes(@TempDir Path tmp) throws Exception {
        java.io.File dir = tmp.toFile();
        // the happy path resolves under the project. The answer is the
        // CANONICAL path now (ledger 111's symlink law), so the root it
        // must sit under is the canonical one — on macOS @TempDir hands
        // out /var/... while the canonical form is /private/var/...
        Path canonicalRoot = dir.getCanonicalFile().toPath();
        assertThat(DockerRecipes.resolveInside(dir, "docker/nginx.conf"))
                .satisfies(p -> assertThat(p.startsWith(canonicalRoot)).isTrue());
        // the divergent inputs: a mutant that skips the check RETURNS a
        // path outside tmp instead of throwing — behavior, not a string
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> DockerRecipes.resolveInside(dir, "../outside"))
                .isInstanceOf(java.io.IOException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> DockerRecipes.resolveInside(dir, "a/../../b"))
                .isInstanceOf(java.io.IOException.class);
        // the project ROOT is not a file inside the project: this used
        // to be accepted and handed on to a bare "Is a directory"
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> DockerRecipes.resolveInside(dir, ""))
                .isInstanceOf(java.io.IOException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> DockerRecipes.resolveInside(dir, "sub/.."))
                .isInstanceOf(java.io.IOException.class);
    }

    @Test
    @DisplayName("THE WRITE WALK: a symlinked segment cannot carry the writer out of the project")
    void writerRefusesASymlinkOutOfTheProject(@TempDir Path tmp) throws Exception {
        // the guard here was normalize() ONLY until ledger 111 — no
        // canonicalization at all, on a WRITE path — so a link inside
        // the project pointed anywhere on disk and the writer followed
        // it. This walks the writer's own loop, not a string.
        java.io.File dir = Files.createDirectories(tmp.resolve("project")).toFile();
        Path outside = Files.createDirectories(tmp.resolve("elsewhere"));
        Files.writeString(outside.resolve("nginx.conf"), "the user's own file");
        try {
            Files.createSymbolicLink(dir.toPath().resolve("docker"), outside);
        } catch (UnsupportedOperationException | java.io.IOException noSymlinks) {
            return; // a platform without symlinks has nothing to prove here
        }

        java.util.Map<String, String> files = new java.util.LinkedHashMap<>();
        files.put("Dockerfile", "FROM scratch");
        files.put("docker/nginx.conf", "server { }");

        java.util.List<String> written = new java.util.ArrayList<>();
        String refusal = null;
        try {
            // exactly what DockerPanelTopComponent.writeDockerizeFiles does
            for (var e : files.entrySet()) {
                Path target = DockerRecipes.resolveInside(dir, e.getKey());
                Files.createDirectories(target.getParent());
                Files.writeString(target, e.getValue(), StandardCharsets.UTF_8);
                written.add(e.getKey());
            }
        } catch (java.io.IOException refused) {
            refusal = refused.getMessage();
        }

        assertThat(refusal)
                .as("the writer must refuse, in its own words")
                .isEqualTo("Refusing to write outside the project: docker/nginx.conf");
        assertThat(written).containsExactly("Dockerfile");
        assertThat(Files.readString(outside.resolve("nginx.conf")))
                .as("the user's file outside the project must be untouched")
                .isEqualTo("the user's own file");
    }

    @Test
    @DisplayName("Regenerate resolves the selection against the FRESH load")
    void regenerateUsesFreshRecipe(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("corp.json"),
                "{ \"name\": \"Corp\", \"files\": { \"Dockerfile\": \"FROM a\" } }",
                StandardCharsets.UTF_8);
        DockerRecipes.Recipe stale =
                DockerRecipes.loadFrom(tmp.toFile()).recipes().get(0);

        // the recipe is EDITED on disk; the fresh load must win
        Files.writeString(tmp.resolve("corp.json"),
                "{ \"name\": \"Corp\", \"files\": { \"Dockerfile\": \"FROM b\" } }",
                StandardCharsets.UTF_8);
        var fresh = DockerRecipes.findByName(
                DockerRecipes.loadFrom(tmp.toFile()).recipes(), stale.name());
        assertThat(fresh).isPresent();
        assertThat(fresh.get().files().get("Dockerfile"))
                .as("editing a recipe then pressing Regenerate must show the"
                        + " edit — the stale combo object must not win")
                .isEqualTo("FROM b");

        // the recipe is DELETED; the selection must resolve to empty so the
        // panel falls back to the detected generator, never ghost-writes
        Files.delete(tmp.resolve("corp.json"));
        assertThat(DockerRecipes.findByName(
                DockerRecipes.loadFrom(tmp.toFile()).recipes(), stale.name()))
                .isEmpty();
    }

    @Test
    @DisplayName("the panel wires the combo, the shared law, and the writer guard")
    void panelWiring() throws Exception {
        // CRLF checkouts (the windows lane) — normalize before asserting
        String src = Files.readString(Path.of("src", "main", "java", "org",
                "nmox", "studio", "rack", "docker", "DockerPanelTopComponent.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(src)
                .as("recipes must reach the tab, or the drop-in dir is dead")
                .contains("DockerRecipes.load()");
        assertThat(src)
                .as("a selected recipe must preview through materialize —"
                        + " the same bytes Write will write")
                .contains("DockerRecipes.materialize(fresh.get(), image)");
        assertThat(src)
                .as("the selection resolves by NAME against the fresh load")
                .contains("DockerRecipes.findByName(loaded.recipes(), recipe.name())");
        assertThat(src)
                .as("the writer must route every file through the ONE"
                        + " behaviorally-tested resolver below")
                .contains("DockerRecipes.resolveInside(dir, e.getKey())");

        String recipes = Files.readString(Path.of("src", "main", "java", "org",
                "nmox", "studio", "rack", "docker", "DockerRecipes.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(recipes)
                .as("ONE path-law implementation — the template drop-ins' —"
                        + " not a drifting copy")
                .contains("UserTemplates.pathProblem(key)");
    }
}
