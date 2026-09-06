package org.nmox.studio.rack.mcp;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.nmox.studio.core.spi.LiveRuns;
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
    /** A seam fake that answers only search — outline refuses (the seam grew a second method in v2.79.0). */
    private interface Searcher {
        org.nmox.studio.core.spi.SymbolIndex.Answer search(File root, String q, int limit);
    }

    private static org.nmox.studio.core.spi.SymbolIndex searchOnly(Searcher s) {
        return new org.nmox.studio.core.spi.SymbolIndex() {
            @Override public Answer search(File root, String q, int limit) { return s.search(root, q, limit); }
            @Override public Outline outline(File root, String file) { return new Outline(List.of(), "no such file: " + file); }
        };
    }


    @AfterEach
    void drainRuns() {
        LiveRuns.stopAll();
    }

    @Test
    @DisplayName("find_symbol answers hits over the seam, says unavailable without one, and bounds the limit (v2.78.0)")
    void findSymbol() {
        org.nmox.studio.core.spi.SymbolIndex fake = searchOnly((root, q, limit) -> new org.nmox.studio.core.spi.SymbolIndex.Answer(
                List.of(new org.nmox.studio.core.spi.SymbolIndex.Hit("checkout", "FUNCTION", "src/cart.js", 12)).subList(0, Math.min(1, limit)),
                true));
        JSONObject s = McpTools.findSymbol(fake, new File("/tmp/proj"), " check ", 20);
        assertThat(s.getBoolean("available")).isTrue();
        assertThat(s.getString("query")).isEqualTo("check");
        assertThat(s.getJSONArray("hits").getJSONObject(0).getInt("line")).isEqualTo(12);
        assertThat(s.getBoolean("truncated")).isTrue();
        assertThat(Texts.of(s)).startsWith("checkout (function) \u2014 src/cart.js:12").contains("partial");
        assertThat(Texts.of(McpTools.findSymbol(null, new File("/tmp/proj"), "x", 5)))
                .isEqualTo("No symbol index: aim a project first.");
        assertThat(Texts.of(McpTools.findSymbol(fake, null, "x", 5))).contains("aim a project");
        assertThat(Texts.of(McpTools.findSymbol(fake, new File("/tmp/proj"), "", 5))).isEqualTo("Pass a name to look for.");
        int[] seen = {0};
        org.nmox.studio.core.spi.SymbolIndex counting = searchOnly((root, q, limit) -> { seen[0] = limit; return new org.nmox.studio.core.spi.SymbolIndex.Answer(List.of(), false); });
        McpTools.findSymbol(counting, new File("/tmp/proj"), "x", 500);
        assertThat(seen[0]).as("the limit is capped").isEqualTo(100);
        assertThat(Texts.of(McpTools.findSymbol(counting, new File("/tmp/proj"), "nonesuch", 5)))
                .isEqualTo("No symbol matches \"nonesuch\".");
    }

    @Test
    @DisplayName("outline carries the seam's nodes or its refusal; search_text bounds and says no-aim (v2.79.0)")
    void outlineAndSearch() throws Exception {
        org.nmox.studio.core.spi.SymbolIndex fake = new org.nmox.studio.core.spi.SymbolIndex() {
            @Override public Answer search(File root, String q, int limit) { return new Answer(List.of(), false); }
            @Override public Outline outline(File root, String file) {
                return file.equals("a.js")
                        ? new Outline(List.of(new Node("Cart", "CLASS", "", 2, 0), new Node("total", "METHOD", "()", 3, 1)), null)
                        : new Outline(List.of(), "no such file: " + file);
            }
        };
        JSONObject ok = McpTools.outline(fake, new File("/tmp/proj"), " a.js ");
        assertThat(ok.getBoolean("available")).isTrue();
        assertThat(ok.getJSONArray("items").getJSONObject(1).getInt("depth")).isEqualTo(1);
        assertThat(Texts.of(ok)).isEqualTo("Cart (class) :2\n  total (method) :3");
        JSONObject secret = McpTools.outline(fake, new File("/tmp/proj"), "config/.npmrc");
        assertThat(secret.getBoolean("available")).isFalse();
        assertThat(secret.getString("refusal")).contains("secret-bearing").contains("config/.npmrc");
        JSONObject refused = McpTools.outline(fake, new File("/tmp/proj"), "zzz");
        assertThat(refused.getBoolean("available")).isFalse();
        assertThat(Texts.of(refused)).isEqualTo("No outline: no such file: zzz.");
        assertThat(Texts.of(McpTools.outline(null, new File("/tmp/proj"), "a.js"))).contains("aim a project");
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("mcp-search");
        try {
            java.nio.file.Files.writeString(dir.resolve("a.js"), "let total = 0;\nfunction Checkout() {}\n");
            JSONObject s = McpTools.searchText(dir.toFile(), "checkout", 20);
            assertThat(s.getBoolean("available")).isTrue();
            assertThat(s.getJSONArray("matches").getJSONObject(0).getInt("line")).isEqualTo(2);
            assertThat(Texts.of(s)).isEqualTo("a.js:2 function Checkout() {}");
            assertThat(Texts.of(McpTools.searchText(dir.toFile(), "nonesuch", 20))).startsWith("No line contains \"nonesuch\"");
            assertThat(Texts.of(McpTools.searchText(null, "x", 20))).isEqualTo("No project is aimed.");
        } finally {
            java.nio.file.Files.deleteIfExists(dir.resolve("a.js"));
            java.nio.file.Files.deleteIfExists(dir);
        }
    }

    @Test
    @DisplayName("project_state carries the detected kind and the Node package manager; run_history lists launches and exits newest first (v2.81.0)")
    void kindAndHistory() {
        JSONObject s = McpTools.projectState(() -> new File("/tmp/proj"), d -> "NODE", d -> "pnpm");
        assertThat(s.getString("kind")).isEqualTo("NODE");
        assertThat(s.getString("packageManager")).isEqualTo("pnpm");
        assertThat(Texts.of(s)).contains("Kind: NODE (pnpm)");
        JSONObject bare = McpTools.projectState(() -> new File("/tmp/proj"), d -> null, d -> null);
        assertThat(bare.isNull("kind")).isTrue();
        assertThat(Texts.of(bare)).contains("Kind: unknown");
        assertThat(McpTools.projectState(() -> null, d -> "NODE", d -> "npm").isNull("kind")).isTrue();
        List<org.nmox.studio.rack.engine.FlightRecorder.Event> tl = List.of(
                new org.nmox.studio.rack.engine.FlightRecorder.Event(1_000L, "VERITAS", org.nmox.studio.rack.engine.FlightRecorder.Kind.LAUNCH, "npm test", -1),
                new org.nmox.studio.rack.engine.FlightRecorder.Event(1_500L, "VERITAS", org.nmox.studio.rack.engine.FlightRecorder.Kind.ERROR, "boom", -1),
                new org.nmox.studio.rack.engine.FlightRecorder.Event(3_100L, "VERITAS", org.nmox.studio.rack.engine.FlightRecorder.Kind.EXIT_FAIL, "[exit 1]", 2_100),
                new org.nmox.studio.rack.engine.FlightRecorder.Event(4_000L, "FORGE", org.nmox.studio.rack.engine.FlightRecorder.Kind.LAUNCH, "npm run build", -1),
                new org.nmox.studio.rack.engine.FlightRecorder.Event(9_000L, "FORGE", org.nmox.studio.rack.engine.FlightRecorder.Kind.EXIT_OK, "[exit 0]", 5_000),
                new org.nmox.studio.rack.engine.FlightRecorder.Event(10_000L, "SOLDER", org.nmox.studio.rack.engine.FlightRecorder.Kind.LAUNCH, "npm run dev", -1),
                new org.nmox.studio.rack.engine.FlightRecorder.Event(12_000L, "SOLDER", org.nmox.studio.rack.engine.FlightRecorder.Kind.STOPPED, "exit 143", 2_000));
        JSONObject h = McpTools.runHistory(tl, 20);
        assertThat(h.getJSONArray("events").length()).as("ERROR lines are not history").isEqualTo(6);
        JSONObject newest = h.getJSONArray("events").getJSONObject(0);
        assertThat(newest.getString("kind")).as("a user's stop is stopped, not failed (v2.84.0)").isEqualTo("stopped");
        assertThat(newest.getInt("exitCode")).isEqualTo(143);
        assertThat(Texts.of(h)).contains("SOLDER stopped [143] npm run dev (2.0 s)");
        JSONObject ok = h.getJSONArray("events").getJSONObject(2);
        assertThat(ok.getString("kind")).isEqualTo("ok");
        assertThat(ok.getInt("exitCode")).isEqualTo(0);
        assertThat(ok.getLong("durationMs")).isEqualTo(5_000);
        assertThat(h.getJSONArray("events").getJSONObject(5).isNull("exitCode")).as("a launch has no exit").isTrue();
        assertThat(h.getBoolean("truncated")).isFalse();
        assertThat(Texts.of(h)).contains("FORGE ok [0] npm run build (5.0 s)").contains("VERITAS failed [1] npm test (2.1 s)");
        JSONObject two = McpTools.runHistory(tl, 2);
        assertThat(two.getJSONArray("events").length()).isEqualTo(2);
        assertThat(two.getBoolean("truncated")).isTrue();
        assertThat(Texts.of(two)).endsWith("(older events not shown)");
        assertThat(Texts.of(McpTools.runHistory(List.of(), 5))).isEqualTo("Nothing has run yet.");
    }

    @Test
    @DisplayName("the file being edited is the focused editor tab, else the editor area's selected tab (v2.84.0)")
    void activeEditorRule() {
        org.openide.windows.TopComponent welcome = new org.openide.windows.TopComponent();
        org.openide.windows.TopComponent app = new org.openide.windows.TopComponent();
        org.openide.windows.TopComponent shown = new org.openide.windows.TopComponent();
        assertThat(EditorState.activeEditor(app, true, shown)).as("a focused editor tab wins").isSameAs(app);
        assertThat(EditorState.activeEditor(welcome, false, shown)).as("focus elsewhere: the tab showing in the editor area").isSameAs(shown);
        assertThat(EditorState.activeEditor(null, false, shown)).isSameAs(shown);
        assertThat(EditorState.activeEditor(welcome, false, null)).as("nothing in the editor area: nothing").isNull();
    }

    @Test
    @DisplayName("editor_state structures the open tabs with the active one and unsaved flags (v2.78.0)")
    void editorState() {
        JSONObject s = EditorState.editorState("/p/a.js", List.of(
                new EditorState.OpenFile("/p/a.js", true, true),
                new EditorState.OpenFile("/p/b.css", false, false)), null);
        assertThat(s.getString("activeFile")).isEqualTo("/p/a.js");
        assertThat(s.getInt("openCount")).isEqualTo(2);
        assertThat(Texts.of(s)).isEqualTo("* /p/a.js  (unsaved changes)\n  /p/b.css");
        assertThat(Texts.of(EditorState.editorState(null, List.of(), null))).isEqualTo("No editor is open.");
        assertThat(Texts.of(EditorState.editorState(null, List.of(), "editor state unavailable: x")))
                .startsWith("editor state unavailable");
    }

    @Test
    @DisplayName("live_runs structures each live run with its label and since-when (v2.77.0)")
    void liveRuns() {
        LiveRuns.add(new LiveRuns.Run("ide-run:/tmp/mcp#1", "Run \u2014 shop", () -> { }));
        JSONObject s = McpTools.liveRuns(LiveRuns.live());
        JSONObject run = s.getJSONArray("runs").getJSONObject(0);
        assertThat(run.getString("label")).isEqualTo("Run \u2014 shop");
        assertThat(run.getString("since")).startsWith("since ");
        assertThat(run.getLong("startedAt")).isPositive();
        assertThat(Texts.of(s)).startsWith("Run \u2014 shop (since ");
        assertThat(Texts.of(McpTools.liveRuns(List.of()))).isEqualTo("Nothing is running.");
    }

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
                List.of(new LiveRuns.Run("ide-run:/tmp/proj#1", "Run \u2014 proj", () -> { })),
                "/tmp/proj/src/app.js",
                Optional.of(new FailureContext("VERITAS", "npm test", 1,
                        List.of("boom"), "proj", 1L)),
                Map.of("eslint", List.of(new DiagnosticsBus.Problem(
                        new File("a.js"), 1, "x", true))));
        assertThat(s.getString("project")).isEqualTo("proj");
        assertThat(s.getInt("serverCount")).isEqualTo(1);
        assertThat(s.getInt("runCount")).isEqualTo(1);
        assertThat(s.getString("activeFile")).isEqualTo("/tmp/proj/src/app.js");
        assertThat(s.getString("lastFailureDevice")).isEqualTo("VERITAS");
        assertThat(s.getInt("diagnosticCount")).isEqualTo(1);
        assertThat(Texts.of(s)).contains("Project: proj")
                .contains("Serving: 1 server").contains("Running: 1 command")
                .contains("Editing: /tmp/proj/src/app.js").contains("on VERITAS");
    }

    @Test
    @DisplayName("initialize's instructions name every tool the roster offers (v2.84.0 — an agent reads them first)")
    void instructionsNameEveryTool() {
        String out = McpProtocol.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}", McpTools.production(), "2.84.0");
        String instructions = new JSONObject(out).getJSONObject("result").getString("instructions");
        for (McpTools.Tool t : McpTools.production().all()) {
            assertThat(instructions).as("instructions name " + t.name()).contains(t.name());
        }
        assertThat(instructions).contains("Nothing here executes");
    }

    @Test
    @DisplayName("The production roster is the twelve read-only tools, each fully described")
    void productionRoster() {
        McpTools tools = McpTools.production();
        assertThat(tools.all()).extracting(McpTools.Tool::name)
                .containsExactly("ide_context", "project_state", "run_history", "live_servers",
                        "live_runs", "last_failure", "diagnostics", "find_symbol",
                        "outline", "search_text", "editor_state", "rack_devices");
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
