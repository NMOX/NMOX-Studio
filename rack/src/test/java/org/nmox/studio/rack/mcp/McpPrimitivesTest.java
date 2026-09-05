package org.nmox.studio.rack.mcp;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.mcp.McpTools.Tool;
import org.nmox.studio.rack.mcp.McpTools.ToolResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Resources and Prompts primitives (v2.56.0): resources are a
 * URI-view over tools' structured output (byte-identical, so the two
 * can't disagree), unknown URIs miss honestly; prompts fold live tool
 * TEXT into a user message. Built on a fixture roster so the laws are
 * pinned without the live rack.
 */
class McpPrimitivesTest {

    /** A tiny fixture roster mirroring two production tools' shapes. */
    private static McpTools fixture() {
        JSONObject ctx = new JSONObject()
                .put("project", "demo").put("serverCount", 1).put("runCount", 0)
                .put("activeFile", JSONObject.NULL)
                .put("lastFailureDevice", "VERITAS").put("diagnosticCount", 0);
        JSONObject fail = new JSONObject()
                .put("failed", true).put("device", "VERITAS")
                .put("command", "npm test").put("exitCode", 1)
                .put("errorLines", new JSONArray().put("Expected 2, got 3"));
        return new McpTools(List.of(
                tool("ide_context", ctx),
                tool("last_failure", fail),
                echoTool("outline", "file"),
                echoTool("search_text", "query"),
                echoTool("find_symbol", "query")));
    }

    /** A fake argument-taking tool: its structured answer echoes the argument it received. */
    private static Tool echoTool(String name, String arg) {
        return new Tool(name, name, name + " desc",
                McpTools.objectSchema(new JSONObject().put(arg, new JSONObject().put("type", "string"))),
                McpTools.objectSchema(new JSONObject()),
                args -> {
                    JSONObject structured = new JSONObject().put("echo", args.optString(arg, "(none)"));
                    return new ToolResult("echo " + args.optString(arg, "(none)"), structured);
                });
    }

    private static Tool tool(String name, JSONObject structured) {
        return new Tool(name, name, name + " desc",
                McpTools.objectSchema(new JSONObject()),
                McpTools.objectSchema(new JSONObject()),
                args -> new ToolResult(Texts.of(structured), structured));
    }

    // ---- resources ---------------------------------------------------------

    @Test
    @DisplayName("the production catalog binds nmox://runs to live_runs (v2.77.0)")
    void productionBindsRuns() {
        JSONArray resources = McpResources.list(McpTools.production()).getJSONArray("resources");
        java.util.List<String> uris = new java.util.ArrayList<>();
        for (int i = 0; i < resources.length(); i++) {
            uris.add(resources.getJSONObject(i).getString("uri"));
        }
        assertThat(uris).contains("nmox://runs", "nmox://editor", "nmox://history").hasSize(9);
    }

    @Test
    @DisplayName("resources/list offers a resource per bound tool with a URI")
    void resourcesList() {
        JSONArray resources = McpResources.list(fixture()).getJSONArray("resources");
        // only the two catalogued fixture tools are bound; the catalog skips the rest
        assertThat(resources.length()).isEqualTo(2);
        JSONObject first = resources.getJSONObject(0);
        assertThat(first.getString("uri")).isEqualTo("nmox://context");
        assertThat(first.getString("mimeType")).isEqualTo("application/json");
        assertThat(first.getString("name")).isEqualTo("ide_context");
    }

    @Test
    @DisplayName("resources/read returns the bound tool's structured JSON verbatim")
    void resourcesReadIsToolStructured() {
        McpTools tools = fixture();
        JSONObject read = McpResources.read("nmox://context", tools);
        assertThat(read).isNotNull();
        JSONObject content = read.getJSONArray("contents").getJSONObject(0);
        assertThat(content.getString("uri")).isEqualTo("nmox://context");
        assertThat(content.getString("mimeType")).isEqualTo("application/json");
        // byte-identical to the tool's own structured output — one source
        JSONObject fromResource = new JSONObject(content.getString("text"));
        JSONObject fromTool = tools.byName("ide_context")
                .handler().apply(new JSONObject()).structured();
        assertThat(fromResource.toString()).isEqualTo(fromTool.toString());
    }

    @Test
    @DisplayName("An unknown resource URI misses (null → caller's not-found)")
    void unknownResourceMisses() {
        assertThat(McpResources.read("nmox://nonesuch", fixture())).isNull();
        assertThat(McpResources.read("file:///etc/passwd", fixture())).isNull();
    }

    @Test
    @DisplayName("resources/templates/list offers a template per argument-taking tool; a templated read fills the argument, percent-decoded (v2.80.0)")
    void resourceTemplates() {
        JSONArray templates = McpResources.templates(fixture()).getJSONArray("resourceTemplates");
        assertThat(templates.length()).isEqualTo(2);
        assertThat(templates.getJSONObject(0).getString("uriTemplate")).isEqualTo("nmox://outline/{file}");
        assertThat(templates.getJSONObject(1).getString("name")).isEqualTo("search_text");
        JSONObject read = McpResources.read("nmox://outline/src/my%20app.js", fixture());
        assertThat(read).isNotNull();
        assertThat(read.getJSONArray("contents").getJSONObject(0).getString("text")).contains("\"echo\":\"src/my app.js\"");
        assertThat(McpResources.read("nmox://search/is%20live", fixture()).getJSONArray("contents")
                .getJSONObject(0).getString("text")).contains("is live");
        assertThat(McpResources.read("nmox://outline/lib/a+b.js", fixture()).getJSONArray("contents")
                .getJSONObject(0).getString("text")).as("a URI path's + is literal, not a form-encoded space (v2.83.0)")
                .contains("\"echo\":\"lib/a+b.js\"");
        assertThat(McpResources.read("nmox://outline/", fixture())).as("an empty tail names nothing").isNull();
        assertThat(McpResources.read("nmox://outline/%zz", fixture())).as("a broken escape names nothing").isNull();
        // a template whose tool is absent is not offered
        assertThat(McpResources.templates(new McpTools(List.of())).getJSONArray("resourceTemplates")).isEmpty();
    }

    // ---- prompts -----------------------------------------------------------

    @Test
    @DisplayName("prompts/list offers the templates with no arguments")
    void promptsList() {
        JSONArray prompts = McpPrompts.list().getJSONArray("prompts");
        assertThat(prompts.length()).isEqualTo(3);
        List<String> names = List.of(
                prompts.getJSONObject(0).getString("name"),
                prompts.getJSONObject(1).getString("name"),
                prompts.getJSONObject(2).getString("name"));
        assertThat(names).containsExactly("diagnose_failure", "review_setup", "where_is");
        assertThat(prompts.getJSONObject(0).getJSONArray("arguments")).isEmpty();
        JSONObject arg = prompts.getJSONObject(2).getJSONArray("arguments").getJSONObject(0);
        assertThat(arg.getString("name")).isEqualTo("name");
        assertThat(arg.getBoolean("required")).isTrue();
    }

    @Test
    @DisplayName("prompts/get folds the live tool text into a user message")
    void promptsGetFoldsState() {
        JSONObject got = McpPrompts.get("diagnose_failure", fixture());
        assertThat(got).isNotNull();
        String text = got.getJSONArray("messages").getJSONObject(0)
                .getJSONObject("content").getString("text");
        // the last_failure text is folded in — the command and error line
        assertThat(text).contains("npm test").contains("Expected 2, got 3")
                .contains("next step to fix it");
        assertThat(got.getJSONArray("messages").getJSONObject(0)
                .getString("role")).isEqualTo("user");
    }

    @Test
    @DisplayName("where_is folds the argument into the tool call and the frame; without it, it refuses by name (v2.80.0)")
    void promptWithArgument() {
        JSONObject got = McpPrompts.get("where_is", fixture(), new JSONObject().put("name", "checkout"));
        String text = got.getJSONArray("messages").getJSONObject(0).getJSONObject("content").getString("text");
        assertThat(text).contains("\"checkout\" is declared").contains("echo checkout");
        assertThatThrownBy(() -> McpPrompts.get("where_is", fixture(), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("arguments.name");
        assertThatThrownBy(() -> McpPrompts.get("where_is", fixture(), new JSONObject().put("name", "  ")))
                .isInstanceOf(IllegalArgumentException.class);
        // the argument-less prompts ignore arguments
        assertThat(McpPrompts.get("review_setup", fixture(), new JSONObject().put("name", "x"))).isNotNull();
    }

    @Test
    @DisplayName("resources/subscribe tracks a catalogued URI, refuses an unknown one, needs params.uri; initialize declares subscribe (v2.84.0)")
    void subscriptions() {
        McpSubscriptions subs = new McpSubscriptions();
        String ok = McpProtocol.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/subscribe\",\"params\":{\"uri\":\"nmox://context\"}}", fixture(), "2.84.0", subs);
        assertThat(ok).contains("\"result\":{}").doesNotContain("error");
        assertThat(subs.isSubscribed("nmox://context")).isTrue();
        String unknown = McpProtocol.handle("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"resources/subscribe\",\"params\":{\"uri\":\"nmox://nonesuch\"}}", fixture(), "2.84.0", subs);
        assertThat(unknown).contains("-32002");
        String missing = McpProtocol.handle("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"resources/subscribe\"}", fixture(), "2.84.0", subs);
        assertThat(missing).contains("-32602").contains("params.uri");
        String un = McpProtocol.handle("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"resources/unsubscribe\",\"params\":{\"uri\":\"nmox://context\"}}", fixture(), "2.84.0", subs);
        assertThat(un).contains("\"result\":{}");
        assertThat(subs.isSubscribed("nmox://context")).isFalse();
        String init = McpProtocol.handle("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"initialize\"}", fixture(), "2.84.0");
        assertThat(new JSONObject(init).getJSONObject("result").getJSONObject("capabilities")
                .getJSONObject("resources").getBoolean("subscribe")).isTrue();
        // the three-arg form still answers, keeping nothing
        assertThat(McpProtocol.handle("{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"resources/subscribe\",\"params\":{\"uri\":\"nmox://context\"}}", fixture(), "2.84.0")).contains("\"result\":{}");
    }

    @Test
    @DisplayName("through the protocol: resources/templates/list answers and a where_is without its argument is -32602 (v2.80.0)")
    void protocolTemplatesAndArguments() {
        String list = McpProtocol.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/templates/list\"}", fixture(), "2.80.0");
        assertThat(list).contains("nmox://outline/{file}").contains("nmox://search/{query}");
        String missing = McpProtocol.handle("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"prompts/get\",\"params\":{\"name\":\"where_is\"}}", fixture(), "2.80.0");
        assertThat(missing).contains("-32602").contains("arguments.name");
        String ok = McpProtocol.handle("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"prompts/get\",\"params\":{\"name\":\"where_is\",\"arguments\":{\"name\":\"checkout\"}}}", fixture(), "2.80.0");
        assertThat(ok).contains("echo checkout").doesNotContain("-32602");
    }

    @Test
    @DisplayName("An unknown prompt name misses")
    void unknownPromptMisses() {
        assertThat(McpPrompts.get("nonesuch", fixture())).isNull();
    }

    // ---- capability wiring through the real protocol -----------------------

    @Test
    @DisplayName("initialize declares all three read-only primitives")
    void initializeDeclaresAllPrimitives() {
        String out = McpProtocol.handle(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}",
                fixture(), "2.56.0");
        JSONObject caps = new JSONObject(out).getJSONObject("result")
                .getJSONObject("capabilities");
        assertThat(caps.has("tools")).isTrue();
        assertThat(caps.has("resources")).isTrue();
        assertThat(caps.has("prompts")).isTrue();
    }

    /** A roster whose only tool throws — the live-state failure shape. */
    private static McpTools throwing() {
        return new McpTools(List.of(new Tool("ide_context", "ctx", "throws",
                McpTools.objectSchema(new JSONObject()),
                McpTools.objectSchema(new JSONObject()),
                args -> {
                    throw new IllegalStateException("rack not ready");
                })));
    }

    @Test
    @DisplayName("A throwing handler behind resources/read answers -32603, never escapes")
    void resourcesReadGuardsThrow() {
        String out = McpProtocol.handle(
                "{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"resources/read\","
                + "\"params\":{\"uri\":\"nmox://context\"}}", throwing(), "2.56.1");
        JSONObject err = new JSONObject(out).getJSONObject("error");
        assertThat(err.getInt("code")).isEqualTo(-32603);
        assertThat(err.getString("message")).contains("rack not ready");
    }

    @Test
    @DisplayName("A throwing handler behind prompts/get answers -32603, never escapes")
    void promptsGetGuardsThrow() {
        String out = McpProtocol.handle(
                "{\"jsonrpc\":\"2.0\",\"id\":12,\"method\":\"prompts/get\","
                + "\"params\":{\"name\":\"review_setup\"}}", throwing(), "2.56.1");
        assertThat(new JSONObject(out).getJSONObject("error").getInt("code"))
                .isEqualTo(-32603);
    }

    @Test
    @DisplayName("resources/read for an unknown URI is the spec's -32002")
    void protocolUnknownResourceCode() {
        String out = McpProtocol.handle(
                "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"resources/read\","
                + "\"params\":{\"uri\":\"nmox://nope\"}}", fixture(), "2.56.0");
        assertThat(new JSONObject(out).getJSONObject("error").getInt("code"))
                .isEqualTo(-32002);
    }
}
