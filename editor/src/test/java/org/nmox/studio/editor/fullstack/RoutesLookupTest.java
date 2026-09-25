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
class RoutesLookupTest {

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
                .contains("!found.allPackages()")
                .doesNotContain("Routes.findRoute(");
    }

    private static void write(Path p, String text) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
    }

    /** web fetches; api declares express and serves; lib has a route-shaped line but no server framework. */
    private static Path monorepo(Path dir) throws Exception {
        Files.createDirectories(dir.resolve(".git"));
        write(dir.resolve("package.json"), "{\"private\":true,\"workspaces\":[\"packages/*\"]}");
        write(dir.resolve("packages/web/package.json"), "{\"name\":\"web\"}");
        write(dir.resolve("packages/web/src/app.js"), "fetch('/api/users');\napp.get('/api/:thing', h);\n");
        write(dir.resolve("packages/api/package.json"), "{\"name\":\"api\",\"dependencies\":{\"express\":\"^5\"}}");
        write(dir.resolve("packages/api/server.js"), "app.get('/api/users', list);\n");
        write(dir.resolve("packages/lib/package.json"), "{\"name\":\"lib\"}");
        write(dir.resolve("packages/lib/mock.js"), "app.get('/orders', h);\n");
        return dir.resolve("packages/web");
    }

    @Test
    @DisplayName("in a monorepo the route is found in the package that declares a server framework, exact beating a :param at home")
    void serverPackage(@TempDir Path dir) throws Exception {
        Path web = monorepo(dir);
        Routes.Lookup l = Routes.lookup(web.toFile(), "/api/users");
        assertThat(l.route()).isNotNull();
        assertThat(l.route().file().getName()).isEqualTo("server.js");
        assertThat(l.allPackages()).isTrue();
    }

    @Test
    @DisplayName("a sibling that declares no server framework is not read")
    void notAServer(@TempDir Path dir) throws Exception {
        Path web = monorepo(dir);
        Routes.Lookup l = Routes.lookup(web.toFile(), "/orders");
        assertThat(l.route()).isNull();
        assertThat(l.complete()).isTrue();
    }

    @Test
    @DisplayName("a workspace with more packages than are enumerated says the miss is about the ones read")
    void tooManyPackages(@TempDir Path dir) throws Exception {
        Path web = monorepo(dir);
        for (int i = 0; i < org.nmox.studio.editor.WorkspaceDependencies.MAX_WORKSPACE_PACKAGES; i++) {
            write(dir.resolve("packages/p" + i + "/package.json"), "{\"name\":\"p" + i + "\"}");
        }
        Routes.Lookup l = Routes.lookup(web.toFile(), "/nowhere");
        assertThat(l.route()).isNull();
        assertThat(l.allPackages()).isFalse();
    }
}
