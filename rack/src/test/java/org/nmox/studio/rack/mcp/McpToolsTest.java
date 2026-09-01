package org.nmox.studio.rack.mcp;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.engine.DiagnosticsBus;
import org.nmox.studio.rack.engine.OracleClient.FailureContext;
import org.nmox.studio.rack.service.ServingRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tool disclosures, now STRUCTURED (v2.55.0): each builder returns
 * a typed object that is the single source of truth, the text is
 * rendered from it, an over-full listing is bounded, the diagnostics
 * filter narrows by file substring, and ide_context folds the whole
 * snapshot into one call.
 */
class McpToolsTest {

    @Test
    @DisplayName("project_state is structured and the text is derived from it")
    void projectState() {
        JSONObject none = McpTools.projectState(() -> null);
        assertThat(none.isNull("project")).isTrue();
        assertThat(Texts.of(none)).isEqualTo("No project is aimed.");

        JSONObject s = McpTools.projectState(() -> new File("/tmp/nope-not-a-repo-xyz"));
        assertThat(s.getString("project")).isEqualTo("nope-not-a-repo-xyz");
        assertThat(s.isNull("gitBranch")).isTrue();
        assertThat(Texts.of(s)).contains("Project: nope-not-a-repo-xyz")
                .contains("not a git repository");
    }

    @Test
    @DisplayName("live_servers structures each serving with url and kind")
    void liveServers() {
        assertThat(Texts.of(McpTools.liveServers(List.of())))
                .isEqualTo("Nothing is serving.");
        JSONObject s = McpTools.liveServers(List.of(
                new ServingRegistry.Serving("dev", "Vite", "http://localhost:5173",
                        ServingRegistry.Kind.WEB, new File("/tmp/demo"))));
        JSONObject first = s.getJSONArray("servers").getJSONObject(0);
        assertThat(first.getString("url")).isEqualTo("http://localhost:5173");
        assertThat(first.getString("kind")).isEqualTo("WEB");
        assertThat(Texts.of(s)).isEqualTo("Vite — http://localhost:5173");
    }

    @Test
    @DisplayName("last_failure structures the FailureContext with its error lines")
    void lastFailure() {
        assertThat(McpTools.lastFailure(Optional.empty()).getBoolean("failed")).isFalse();
        var ctx = new FailureContext("VERITAS", "npm test", 1,
                List.of("FAIL src/a.test.js", "Expected 2, got 3"), "demo", 120L);
        JSONObject s = McpTools.lastFailure(Optional.of(ctx));
        assertThat(s.getBoolean("failed")).isTrue();
        assertThat(s.getString("device")).isEqualTo("VERITAS");
        assertThat(s.getInt("exitCode")).isEqualTo(1);
        assertThat(s.getJSONArray("errorLines").length()).isEqualTo(2);
        assertThat(Texts.of(s)).contains("Command: npm test").contains("Expected 2, got 3");
    }

    @Test
    @DisplayName("diagnostics counts per tool, caps at five, and totals")
    void diagnostics() {
        assertThat(Texts.of(McpTools.diagnostics(Map.of(), null)))
                .contains("every tool that has run is clean");
        List<DiagnosticsBus.Problem> many = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            many.add(new DiagnosticsBus.Problem(new File("a" + i + ".js"), i,
                    "issue " + i, true));
        }
        JSONObject s = McpTools.diagnostics(Map.of("eslint", many), null);
        assertThat(s.getInt("totalFindings")).isEqualTo(8);
        JSONObject tool = s.getJSONArray("tools").getJSONObject(0);
        assertThat(tool.getInt("count")).isEqualTo(8);
        // bounded: five shown, the rest are a file-read away
        assertThat(tool.getJSONArray("findings").length()).isEqualTo(5);
    }

    @Test
    @DisplayName("The diagnostics file filter narrows by path substring")
    void diagnosticsFilter() {
        var cart = new DiagnosticsBus.Problem(new File("src/cart.js"), 4, "x", true);
        var user = new DiagnosticsBus.Problem(new File("src/user.js"), 9, "y", false);
        Map<String, List<DiagnosticsBus.Problem>> byTool =
                Map.of("eslint", List.of(cart, user));
        JSONObject filtered = McpTools.diagnostics(byTool, "cart");
        assertThat(filtered.getInt("totalFindings")).isEqualTo(1);
        assertThat(filtered.getJSONArray("tools").getJSONObject(0)
                .getJSONArray("findings").getJSONObject(0).getString("file"))
                .isEqualTo("cart.js");
        // a filter matching nothing is an honest empty, distinct from "clean"
        JSONObject none = McpTools.diagnostics(byTool, "nowhere");
        assertThat(none.getInt("totalFindings")).isZero();
        assertThat(Texts.of(none)).isEqualTo("No findings match that filter.");
    }

    @Test
    @DisplayName("ide_context folds project, servers, failure, and diagnostics into one")
    void ideContext() {
        JSONObject s = McpTools.ideContext(
                () -> new File("/tmp/proj"),
                List.of(new ServingRegistry.Serving("d", "Vite", "http://x",
                        ServingRegistry.Kind.WEB, new File("/tmp/proj"))),
                Optional.of(new FailureContext("VERITAS", "npm test", 1,
                        List.of("boom"), "proj", 1L)),
                Map.of("eslint", List.of(new DiagnosticsBus.Problem(
                        new File("a.js"), 1, "x", true))));
        assertThat(s.getString("project")).isEqualTo("proj");
        assertThat(s.getInt("serverCount")).isEqualTo(1);
        assertThat(s.getString("lastFailureDevice")).isEqualTo("VERITAS");
        assertThat(s.getInt("diagnosticCount")).isEqualTo(1);
        assertThat(Texts.of(s)).contains("Project: proj")
                .contains("Serving: 1 server").contains("on VERITAS");
    }

    @Test
    @DisplayName("The production roster is the six read-only tools, each fully described")
    void productionRoster() {
        McpTools tools = McpTools.production();
        assertThat(tools.all()).extracting(McpTools.Tool::name)
                .containsExactly("ide_context", "project_state", "live_servers",
                        "last_failure", "diagnostics", "rack_devices");
        // every tool carries a title, a real input schema, and an output schema
        tools.all().forEach(t -> {
            assertThat(t.title()).isNotBlank();
            assertThat(t.inputSchema().getString("type")).isEqualTo("object");
            assertThat(t.outputSchema().getString("type")).isEqualTo("object");
        });
        // diagnostics declares its file argument
        assertThat(tools.byName("diagnostics").inputSchema()
                .getJSONObject("properties").has("file")).isTrue();
        assertThat(tools.byName("delete_everything")).isNull();
    }
}
