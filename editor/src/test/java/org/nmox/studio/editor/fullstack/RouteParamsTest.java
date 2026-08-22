package org.nmox.studio.editor.fullstack;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Param-aware route matching (v2.33.0, granting the v2.31.0 recorded
 * limit). The named mutants: dropping the segment-count guard makes a
 * prefix serve everything; dropping the sawParam requirement lets the
 * pattern pass shadow exact-miss reporting semantics.
 */
class RouteParamsTest {

    @Test
    @DisplayName("a concrete path matches its :param route, segment-exact")
    void paramMatching() {
        assertThat(Routes.servesViaParams("/api/users/:id", "/api/users/123")).isTrue();
        assertThat(Routes.servesViaParams("/api/users/:id/posts/:pid",
                "/api/users/7/posts/42")).isTrue();
        assertThat(Routes.servesViaParams("/api/users/:id", "/api/users/123?page=2"))
                .as("query strings are not part of the route").isTrue();
    }

    @Test
    @DisplayName("segment count guards the match — a prefix never serves a longer path")
    void segmentCountGuards() {
        assertThat(Routes.servesViaParams("/api/users/:id", "/api/users")).isFalse();
        assertThat(Routes.servesViaParams("/api/users", "/api/users/123")).isFalse();
        assertThat(Routes.servesViaParams("/api/users/:id", "/api/users/")).isFalse();
    }

    @Test
    @DisplayName("no params means no pattern match — exact equality owns that case")
    void exactCaseStaysExact() {
        assertThat(Routes.servesViaParams("/api/users", "/api/users")).isFalse();
    }

    @Test
    @DisplayName("findRoute: exact wins over an earlier pattern match")
    void exactWins(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.js"),
                "app.get('/api/users/:id', h)\n");
        Files.writeString(dir.resolve("b.js"),
                "app.get('/api/users/me', h)\n");
        File root = dir.toFile();
        Routes.Route r = Routes.findRoute(root, "/api/users/me");
        assertThat(r.path()).isEqualTo("/api/users/me");
        assertThat(Routes.findRoute(root, "/api/users/123").path())
                .isEqualTo("/api/users/:id");
    }
}
