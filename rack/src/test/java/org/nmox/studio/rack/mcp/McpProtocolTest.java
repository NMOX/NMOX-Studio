package org.nmox.studio.rack.mcp;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.mcp.McpTools.Tool;
import org.nmox.studio.rack.mcp.McpTools.ToolResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Agent Port's protocol laws: initialize advertises the read-only
 * tool capability; tools/list mirrors the roster WITH input/output
 * schemas and the readOnlyHint annotation; tools/call passes arguments,
 * runs the handler, and returns both a text block AND structuredContent;
 * a handler throw is an isError RESULT; unknown methods/params get their
 * codes; a notification answers null.
 */
class McpProtocolTest {

    private static McpTools tools(AtomicReference<JSONObject> sawArgs) {
        JSONObject schema = McpTools.objectSchema(new JSONObject()
                .put("file", new JSONObject().put("type", "string")));
        return new McpTools(List.of(
                new Tool("echo", "Echo", "echoes args back", schema, schema,
                        args -> {
                            sawArgs.set(args);
                            return new ToolResult("ok",
                                    new JSONObject().put("got", args.optString("file", "")));
                        }),
                new Tool("boom", "Boom", "always fails",
                        McpTools.objectSchema(new JSONObject()),
                        McpTools.objectSchema(new JSONObject()),
                        args -> { throw new IllegalStateException("kaboom"); })));
    }

    private static JSONObject handle(String req, McpTools tools) {
        return new JSONObject(McpProtocol.handle(req, tools, "9.9.9"));
    }

    @Test
    @DisplayName("initialize advertises the tools capability and product version")
    void initialize() {
        JSONObject r = handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}",
                tools(new AtomicReference<>()));
        JSONObject result = r.getJSONObject("result");
        assertThat(result.getString("protocolVersion"))
                .isEqualTo(McpProtocol.PROTOCOL_VERSION);
        assertThat(result.getJSONObject("capabilities").has("tools")).isTrue();
        assertThat(result.getJSONObject("serverInfo").getString("version"))
                .isEqualTo("9.9.9");
    }

    @Test
    @DisplayName("tools/list carries schemas and the readOnlyHint annotation")
    void toolsListAnnotated() {
        JSONObject r = handle("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}",
                tools(new AtomicReference<>()));
        JSONObject echo = r.getJSONObject("result").getJSONArray("tools").getJSONObject(0);
        assertThat(echo.getString("name")).isEqualTo("echo");
        assertThat(echo.getJSONObject("inputSchema").getString("type")).isEqualTo("object");
        assertThat(echo.has("outputSchema")).isTrue();
        // the read-only guarantee lives IN the protocol
        JSONObject ann = echo.getJSONObject("annotations");
        assertThat(ann.getBoolean("readOnlyHint")).isTrue();
        assertThat(ann.getBoolean("destructiveHint")).isFalse();
    }

    @Test
    @DisplayName("tools/call passes arguments and returns text AND structuredContent")
    void toolsCallStructured() {
        AtomicReference<JSONObject> sawArgs = new AtomicReference<>();
        JSONObject r = handle("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"echo\",\"arguments\":{\"file\":\"cart.js\"}}}",
                tools(sawArgs));
        // the handler received the arguments
        assertThat(sawArgs.get().getString("file")).isEqualTo("cart.js");
        JSONObject result = r.getJSONObject("result");
        assertThat(result.getBoolean("isError")).isFalse();
        assertThat(result.getJSONArray("content").getJSONObject(0).getString("text"))
                .isEqualTo("ok");
        // the programmatic face: structuredContent, typed
        assertThat(result.getJSONObject("structuredContent").getString("got"))
                .isEqualTo("cart.js");
    }

    @Test
    @DisplayName("Missing arguments default to an empty object, never null")
    void missingArgumentsAreEmpty() {
        AtomicReference<JSONObject> sawArgs = new AtomicReference<>();
        handle("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"echo\"}}", tools(sawArgs));
        assertThat(sawArgs.get()).isNotNull();
        assertThat(sawArgs.get().optString("file", "")).isEmpty();
    }

    @Test
    @DisplayName("A handler throw is an isError RESULT, not a protocol error")
    void handlerThrowIsResult() {
        JSONObject r = handle("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"boom\"}}", tools(new AtomicReference<>()));
        assertThat(r.has("error")).isFalse();
        assertThat(r.getJSONObject("result").getBoolean("isError")).isTrue();
        assertThat(r.getJSONObject("result").getJSONArray("content")
                .getJSONObject(0).getString("text")).contains("kaboom");
    }

    @Test
    @DisplayName("An unknown tool is -32602, an unknown method -32601, bad JSON -32700")
    void refusals() {
        JSONObject badTool = handle("{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"ghost\"}}", tools(new AtomicReference<>()));
        assertThat(badTool.getJSONObject("error").getInt("code")).isEqualTo(-32602);
        JSONObject badMethod = handle("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"do/it\"}",
                tools(new AtomicReference<>()));
        assertThat(badMethod.getJSONObject("error").getInt("code")).isEqualTo(-32601);
        JSONObject parse = handle("{not json", tools(new AtomicReference<>()));
        assertThat(parse.getJSONObject("error").getInt("code")).isEqualTo(-32700);
    }

    @Test
    @DisplayName("A notification (no id) answers null — nothing sent back")
    void notificationIsSilent() {
        assertThat(McpProtocol.handle(
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",
                tools(new AtomicReference<>()), "9.9.9")).isNull();
        assertThat(McpProtocol.handle(
                "{\"jsonrpc\":\"2.0\",\"method\":\"whatever\"}",
                tools(new AtomicReference<>()), "9.9.9")).isNull();
    }
}
