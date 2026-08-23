package org.nmox.studio.apiclient.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The route-line rule (v2.34.0) — the v1.292.0 narrowness carried
 * over: receiver-shaped, real verb, leading-slash string path. The
 * named mutants: dropping the verb list admits app.use; dropping the
 * leading-slash requirement admits app.get('port').
 */
class RouteLineTest {

    @Test
    @DisplayName("route lines parse: verb uppercased, path verbatim")
    void parses() {
        assertThat(RouteLine.parse("app.get('/api/users', handler)"))
                .containsExactly("GET", "/api/users");
        assertThat(RouteLine.parse("  router.post(\"/login\", h)"))
                .containsExactly("POST", "/login");
        assertThat(RouteLine.parse("module.exports = api.delete(`/items/:id`, h)"))
                .containsExactly("DELETE", "/items/:id");
    }

    @Test
    @DisplayName("non-route lines refuse: app.use, config getters, prose")
    void refuses() {
        assertThat(RouteLine.parse("app.use('/static', express.static('pub'))")).isNull();
        assertThat(RouteLine.parse("app.get('port')")).isNull();
        assertThat(RouteLine.parse("cache.get('/api/users')")).isNull();
        assertThat(RouteLine.parse("const x = 1;")).isNull();
        assertThat(RouteLine.parse(null)).isNull();
    }
}
