package org.nmox.studio.editor.fullstack;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A route lookup says whether it read the whole project (after 3.2.0): past
 * {@link Routes#MAX_FILES} a miss is a miss among the files read, not proof
 * that no route registers the path — the jump's refusal must not claim it.
 */
class RoutesCensusTest {

    @Test
    @DisplayName("a small project's miss is complete: no route registers the path")
    void smallMissIsComplete(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("server.js"), "app.get('/api/users', h);\n");
        Routes.Lookup l = Routes.lookup(dir.toFile(), "/nope");
        assertThat(l.route()).isNull();
        assertThat(l.complete()).isTrue();
        assertThat(Routes.lookup(dir.toFile(), "/api/users").route()).isNotNull();
    }

    @Test
    @DisplayName("past the cap the lookup says it stopped, and a route beyond it is not claimed absent")
    void pastTheCapIsIncomplete(@TempDir Path dir) throws Exception {
        for (int i = 0; i < Routes.MAX_FILES + 5; i++) {
            Files.writeString(dir.resolve("m" + i + ".js"), "export const x" + i + " = " + i + ";\n");
        }
        Routes.Lookup l = Routes.lookup(dir.toFile(), "/api/orders");
        assertThat(l.route()).isNull();
        assertThat(l.complete()).as("the census stopped at the cap").isFalse();
    }

    @Test
    @DisplayName("the jump's refusal distinguishes a complete census from one that stopped at its cap")
    void refusalReadsCompleteness() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/editor/fullstack/FetchRouteHyperlink.java"));
        assertThat(src).contains("Routes.lookup(").contains("found.complete()")
                .contains("FetchRouteHyperlink_noRouteCapped(path, Routes.MAX_FILES)")
                .doesNotContain("Routes.findRoute(");
    }
}
