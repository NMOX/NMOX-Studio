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
                .put("project", "demo").put("serverCount", 1)
                .put("lastFailureDevice", "VERITAS").put("diagnosticCount", 0);
        JSONObject fail = new JSONObject()
                .put("failed", true).put("device", "VERITAS")
                .put("command", "npm test").put("exitCode", 1)
                .put("errorLines", new JSONArray().put("Expected 2, got 3"));
        return new McpTools(List.of(
                tool("ide_context", ctx),
                tool("last_failure", fail)));
    }

    private static Tool tool(String name, JSONObject structured) {
        return new Tool(name, name, name + " desc",
                McpTools.objectSchema(new JSONObject()),
                McpTools.objectSchema(new JSONObject()),
                args -> new ToolResult(Texts.of(structured), structured));
    }

    // ---- resources ---------------------------------------------------------

    @Test
    @DisplayName("resources/list offers a resource per bound tool with a URI")
    void resourcesList() {
        JSONArray resources = McpResources.list(fixture()).getJSONArray("resources");
        // only the two fixture tools are bound; the catalog skips the rest
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

    // ---- prompts -----------------------------------------------------------

    @Test
    @DisplayName("prompts/list offers the templates with no arguments")
    void promptsList() {
        JSONArray prompts = McpPrompts.list().getJSONArray("prompts");
        assertThat(prompts.length()).isEqualTo(2);
        List<String> names = List.of(
                prompts.getJSONObject(0).getString("name"),
                prompts.getJSONObject(1).getString("name"));
        assertThat(names).containsExactly("diagnose_failure", "review_setup");
        assertThat(prompts.getJSONObject(0).getJSONArray("arguments")).isEmpty();
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
