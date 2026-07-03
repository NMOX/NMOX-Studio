package org.nmox.studio.apiclient.api;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code {{var}}} substitution and referencing at the edges: null and
 * varless input, spaced tokens, repeated names, and the deliberate
 * choice to leave an unknown variable visible rather than blank it.
 */
class VariablesTest {

    @Test
    @DisplayName("Null text resolves to empty; a null var map leaves text untouched")
    void nullHandling() {
        assertThat(Variables.resolve(null, Map.of("a", "1"))).isEmpty();
        assertThat(Variables.resolve("{{a}}", null)).isEqualTo("{{a}}");
        assertThat(Variables.resolve(null, null)).isEmpty();
    }

    @Test
    @DisplayName("Text with no token passes through unchanged")
    void noToken() {
        assertThat(Variables.resolve("https://x.dev/path", Map.of("a", "1")))
                .isEqualTo("https://x.dev/path");
    }

    @Test
    @DisplayName("Whitespace inside a token is tolerated: {{ name }} resolves")
    void spacedToken() {
        assertThat(Variables.resolve("{{ base }}/x", Map.of("base", "https://x.dev")))
                .isEqualTo("https://x.dev/x");
    }

    @Test
    @DisplayName("A known token resolves; an unknown one is left verbatim, not blanked")
    void knownAndUnknown() {
        Map<String, String> vars = Map.of("known", "yes");
        assertThat(Variables.resolve("{{known}} {{unknown}}", vars))
                .isEqualTo("yes {{unknown}}");
    }

    @Test
    @DisplayName("A replacement value that itself looks like a token is inserted literally")
    void replacementIsQuoted() {
        assertThat(Variables.resolve("{{a}}", Map.of("a", "$1 {{b}}")))
                .as("no cascading or regex-group interpretation")
                .isEqualTo("$1 {{b}}");
    }

    @Test
    @DisplayName("referenced lists each name once, in first-seen order; null yields empty")
    void referenced() {
        assertThat(Variables.referenced("{{a}}/{{b}}/{{a}}/{{c}}"))
                .containsExactly("a", "b", "c");
        assertThat(Variables.referenced("no tokens here")).isEmpty();
        assertThat(Variables.referenced(null)).isEmpty();
    }
}
