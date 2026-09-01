package org.nmox.studio.rack.mcp;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.engine.DiagnosticsBus;
import org.nmox.studio.rack.engine.OracleClient.FailureContext;
import org.nmox.studio.rack.service.ServingRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tool disclosures: each read is honest when empty, bounded when
 * full, and never leaks more than the state it names. The failure tool
 * is ORACLE's own FailureContext — the shape a consent dialog already
 * describes — so nothing new leaves the machine.
 */
class McpToolsTest {

    @Test
    @DisplayName("project_state names the project, dir, and branch; honest when unaimed")
    void projectState() {
        assertThat(McpTools.projectState(() -> null)).isEqualTo("No project is aimed.");
        String s = McpTools.projectState(() -> new File("/tmp/nope-not-a-repo-xyz"));
        assertThat(s).contains("Project: nope-not-a-repo-xyz")
                .contains("not a git repository");
    }

    @Test
    @DisplayName("live_servers lists URLs, and says so when nothing serves")
    void liveServers() {
        assertThat(McpTools.liveServers(List.of())).isEqualTo("Nothing is serving.");
        var s = McpTools.liveServers(List.of(
                new ServingRegistry.Serving("dev", "Vite", "http://localhost:5173",
                        ServingRegistry.Kind.WEB, new File("/tmp/demo"))));
        assertThat(s).isEqualTo("Vite — http://localhost:5173");
    }

    @Test
    @DisplayName("last_failure mirrors the FailureContext; honest when nothing failed")
    void lastFailure() {
        assertThat(McpTools.lastFailure(Optional.empty()))
                .contains("Nothing has failed");
        var ctx = new FailureContext("VERITAS", "npm test", 1,
                List.of("FAIL src/a.test.js", "Expected 2, got 3"), "demo", 120L);
        String s = McpTools.lastFailure(Optional.of(ctx));
        assertThat(s).contains("Device: VERITAS").contains("Command: npm test")
                .contains("Exit code: 1").contains("Expected 2, got 3");
    }

    @Test
    @DisplayName("diagnostics counts per tool and caps the listing at five")
    void diagnostics() {
        assertThat(McpTools.diagnostics(Map.of())).contains("every tool that has run is clean");
        List<DiagnosticsBus.Problem> many = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            many.add(new DiagnosticsBus.Problem(new File("a" + i + ".js"), i,
                    "issue " + i, true));
        }
        String s = McpTools.diagnostics(Map.of("eslint", many));
        assertThat(s).contains("[eslint] 8 findings");
        // bounded: five shown, the rest are a file-read away
        assertThat(s).contains("a0.js").contains("a4.js").doesNotContain("a5.js");
    }

    @Test
    @DisplayName("The registry answers by name and null for the unknown")
    void byName() {
        McpTools tools = McpTools.production();
        assertThat(tools.byName("project_state")).isNotNull();
        assertThat(tools.byName("delete_everything")).isNull();
        // the production roster is exactly the five read-only tools
        assertThat(tools.all()).extracting(McpTools.Tool::name)
                .containsExactly("project_state", "live_servers",
                        "last_failure", "diagnostics", "rack_devices");
    }
}
