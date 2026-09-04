package org.nmox.studio.rack.service;

import java.io.File;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.service.ServingRegistry.Kind;
import org.nmox.studio.rack.service.ServingRegistry.Serving;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The status line's serving chip: what it says for zero, one, and many
 * servings — the model logic behind the label, tested without Swing.
 */
class RackStatusLineChipTest {

    private static Serving serving(String title, String url) {
        return new Serving(title + "@1", title, url, Kind.WEB, new File("/tmp/p"));
    }

    @Test
    @DisplayName("no servings: no chip")
    void emptyIsNull() {
        assertThat(RackStatusLine.chipText(List.of())).isNull();
        assertThat(RackStatusLine.chipTooltip(List.of())).isNull();
    }

    @Test
    @DisplayName("one serving: the URL, no +N")
    void single() {
        assertThat(RackStatusLine.chipText(List.of(
                serving("SURGE", "http://localhost:5173"))))
                .isEqualTo("⇄ serving: http://localhost:5173");
    }

    @Test
    @DisplayName("many servings: first URL +N, tooltip lists them all")
    void many() {
        List<Serving> servings = List.of(
                serving("SURGE", "http://localhost:5173"),
                serving("ARTISAN", "http://127.0.0.1:8000"),
                serving("ANVIL", "http://127.0.0.1:8545"));
        assertThat(RackStatusLine.chipText(servings))
                .isEqualTo("⇄ serving: http://localhost:5173 +2");
        assertThat(RackStatusLine.chipTooltip(servings))
                .contains("SURGE — http://localhost:5173")
                .contains("ARTISAN — http://127.0.0.1:8000")
                .contains("ANVIL — http://127.0.0.1:8545");
    }

    @Test
    @DisplayName("A pick from the ⇄ chip opens in the in-app Browser; the system browser only when none is wired or it declines (v2.69.19)")
    void picksOpenInApp() {
        java.util.List<String> inApp = new java.util.ArrayList<>();
        java.util.List<String> system = new java.util.ArrayList<>();
        RackStatusLine.openInBrowser("http://localhost:8081/", url -> { inApp.add(url); return true; }, system::add);
        assertThat(inApp).containsExactly("http://localhost:8081/");
        assertThat(system).as("the in-app Browser took it").isEmpty();
        RackStatusLine.openInBrowser("http://localhost:8082/", null, system::add);
        assertThat(system).as("no in-app Browser wired: the system browser").containsExactly("http://localhost:8082/");
        RackStatusLine.openInBrowser("http://localhost:8083/", url -> false, system::add);
        assertThat(system).as("the in-app Browser declined: the system browser").containsExactly("http://localhost:8082/", "http://localhost:8083/");
    }
}
