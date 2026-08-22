package org.nmox.studio.tools.npm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The caret-to-script boundary rules (v2.33.0). The named mutants:
 * dropping the top-level-depth check lets a nested "scripts" key in
 * some dependency config qualify; dropping the inside-braces check
 * runs a DEPENDENCY named like a script.
 */
class NpmScriptsTest {

    private static final String PKG = "{\n"
            + "  \"name\": \"demo\",\n"
            + "  \"scripts\": {\n"
            + "    \"dev\": \"vite\",\n"
            + "    \"test\": \"vitest run\"\n"
            + "  },\n"
            + "  \"dependencies\": {\n"
            + "    \"dev\": \"1.0.0\"\n"
            + "  }\n"
            + "}\n";

    @Test
    @DisplayName("caret on a script line names the script")
    void caretOnScript() {
        assertThat(NpmScripts.scriptAt(PKG, PKG.indexOf("\"vite\"")))
                .isEqualTo("dev");
        assertThat(NpmScripts.scriptAt(PKG, PKG.indexOf("vitest")))
                .isEqualTo("test");
    }

    @Test
    @DisplayName("outside the scripts object: null — a dependency named 'dev' never runs")
    void outsideScripts() {
        assertThat(NpmScripts.scriptAt(PKG, PKG.indexOf("\"1.0.0\""))).isNull();
        assertThat(NpmScripts.scriptAt(PKG, PKG.indexOf("\"demo\""))).isNull();
        assertThat(NpmScripts.scriptAt(PKG, 0)).isNull();
    }

    @Test
    @DisplayName("a nested scripts key in some other object does not qualify")
    void nestedScriptsRefused() {
        String nested = "{\n  \"config\": { \"scripts\": { \"evil\": \"rm -rf\" } }\n}\n";
        assertThat(NpmScripts.scriptAt(nested, nested.indexOf("rm"))).isNull();
    }

    @Test
    @DisplayName("no scripts object at all: null")
    void noScripts() {
        assertThat(NpmScripts.scriptAt("{\"name\":\"x\"}", 5)).isNull();
    }
}
