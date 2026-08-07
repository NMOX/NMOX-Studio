package org.nmox.studio.editor.outline;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The outline sees your routes (v1.292.0, the editor persona walk).
 *
 * <p>Opening an Express server file — the most-opened file in the
 * ecosystem this IDE is built for — showed "No structure to show". The
 * extractor was right: such a file declares no named functions and no
 * classes, because every handler is an inline callback. It was also
 * useless, because the structure of that file IS its route table.
 *
 * <p>The rule is deliberately narrow, since a wrong outline entry is
 * worse than a missing one: an app-or-router receiver, a real HTTP
 * verb, and a string path. These tests pin both halves — the routes it
 * must find, and the look-alikes it must leave alone.
 */
class OutlineRouteTest {

    private static List<OutlineModel.Item> js(String src) {
        return OutlineModel.extract("text/javascript", src);
    }

    private static List<String> names(String src) {
        return js(src).stream().map(OutlineModel.Item::name).toList();
    }

    @Test
    @DisplayName("an Express server file outlines as its route table")
    void expressServerOutlinesRoutes() {
        // the shape that produced ZERO items before this release
        List<OutlineModel.Item> items = js("""
                import express from 'express';
                const app = express();
                app.use(express.json());
                app.get('/', (req, res) => {
                  res.json({ status: 'ok' });
                });
                app.get('/health', (req, res) => res.json({ up: true }));
                app.post('/invoices', async (req, res) => {});
                app.listen(port, () => {});
                """);

        assertThat(items.stream().map(OutlineModel.Item::name))
                .containsExactly("GET /", "GET /health", "POST /invoices");
        assertThat(items).allSatisfy(i ->
                assertThat(i.kind()).isEqualTo(OutlineKind.TARGET));
        assertThat(items.get(1).line())
                .as("clicking a route must land on the line that declares it")
                .isEqualTo(6);
    }

    @Test
    @DisplayName("routers and every common verb are read the same way")
    void routersAndVerbs() {
        assertThat(names("""
                const router = express.Router();
                router.put('/a', h);
                router.patch('/b', h);
                router.delete('/c', h);
                router.head('/d', h);
                router.options('/e', h);
                router.all('/f', h);
                """))
                .containsExactly("PUT /a", "PATCH /b", "DELETE /c",
                        "HEAD /d", "OPTIONS /e", "ALL /f");

        assertThat(names("apiRouter.get('/v1/users', h);\n"))
                .as("a prefixed router name is still a router")
                .containsExactly("GET /v1/users");
    }

    @Test
    @DisplayName("look-alikes stay out — a wrong entry is worse than none")
    void lookAlikesAreNotRoutes() {
        assertThat(names("app.use(express.json());\n"))
                .as("use() has no path and is not a route")
                .isEmpty();
        assertThat(names("res.json({ ok: true });\n"))
                .as("json is not an HTTP verb and res is not a router")
                .isEmpty();
        assertThat(names("cache.get('some-key');\n"))
                .as("a cache get is a real method call, not a route")
                .isEmpty();
        assertThat(names("app.get('port');\n"))
                .as("Express's own one-argument config getter still reads as"
                        + " a route by shape — accepted, and pinned here so"
                        + " the limit is written down rather than discovered")
                .containsExactly("GET port");
    }

    @Test
    @DisplayName("a route inside a comment or template literal is not a route")
    void commentsAndTemplatesAreNotCode() {
        assertThat(names("""
                // app.get('/old', h);
                /* app.post('/older', h); */
                const doc = `
                app.get('/documented', h);
                `;
                app.get('/real', h);
                """))
                .as("stripNonCode already guards the other patterns; routes"
                        + " ride the same guard")
                .containsExactly("GET /real");
    }

    @Test
    @DisplayName("named functions and classes still win where they exist")
    void classicShapesUnaffected() {
        assertThat(names("""
                export function handler(req, res) {}
                class Service {}
                const add = (a, b) => a + b;
                """))
                .as("the route rule must not shadow what already worked")
                .containsExactly("handler", "Service", "add");
    }
}
