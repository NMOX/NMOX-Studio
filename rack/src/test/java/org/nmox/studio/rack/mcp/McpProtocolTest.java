package org.nmox.studio.rack.mcp;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.mcp.McpTools.Tool;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Agent Port's protocol laws: initialize advertises exactly the
 * read-only tool capability, tools/list mirrors the roster, tools/call
 * runs the named handler and wraps its text, a handler throw becomes
 * an isError RESULT (never a protocol error), unknown methods and bad
 * params get their JSON-RPC codes, and a notification answers null.
 */
class McpProtocolTest {

    private static McpTools twoTools(AtomicInteger calls) {
        return new McpTools(List.of(
                new Tool("project_state", "the aimed project",
                        () -> { calls.incrementAndGet(); return "Project: demo"; }),
                new Tool("boom", "always fails",
                        () -> { throw new IllegalStateException("kaboom"); })));
    }

    private static JSONObject handle(String req, McpTools tools) {
        return new JSONObject(McpProtocol.handle(req, tools, "9.9.9"));
    }

    @Test
    @DisplayName("initialize advertises the tools capability and product version")
    void initialize() {
        JSONObject r = handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}",
                twoTools(new AtomicInteger()));
        JSONObject result = r.getJSONObject("result");
        assertThat(result.getString("protocolVersion"))
                .isEqualTo(McpProtocol.PROTOCOL_VERSION);
        assertThat(result.getJSONObject("capabilities").has("tools")).isTrue();
        assertThat(result.getJSONObject("serverInfo").getString("version"))
                .isEqualTo("9.9.9");
    }

    @Test
    @DisplayName("tools/list mirrors the roster with object input schemas")
    void toolsList() {
        JSONObject r = handle("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}",
                twoTools(new AtomicInteger()));
        var defs = r.getJSONObject("result").getJSONArray("tools");
        assertThat(defs.length()).isEqualTo(2);
        assertThat(defs.getJSONObject(0).getString("name")).isEqualTo("project_state");
        assertThat(defs.getJSONObject(0).getJSONObject("inputSchema")
                .getString("type")).isEqualTo("object");
    }

    @Test
    @DisplayName("tools/call runs the named handler and wraps its text")
    void toolsCall() {
        AtomicInteger calls = new AtomicInteger();
        JSONObject r = handle("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"project_state\"}}", twoTools(calls));
        JSONObject result = r.getJSONObject("result");
        assertThat(result.getBoolean("isError")).isFalse();
        assertThat(result.getJSONArray("content").getJSONObject(0).getString("text"))
                .isEqualTo("Project: demo");
        assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("A handler throw is an isError RESULT, not a protocol error")
    void handlerThrowIsResult() {
        JSONObject r = handle("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"boom\"}}", twoTools(new AtomicInteger()));
        assertThat(r.has("error")).isFalse();
        assertThat(r.getJSONObject("result").getBoolean("isError")).isTrue();
        assertThat(r.getJSONObject("result").getJSONArray("content")
                .getJSONObject(0).getString("text")).contains("kaboom");
    }

    @Test
    @DisplayName("An unknown tool is -32602, an unknown method -32601")
    void refusals() {
        JSONObject badTool = handle("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"ghost\"}}", twoTools(new AtomicInteger()));
        assertThat(badTool.getJSONObject("error").getInt("code")).isEqualTo(-32602);
        JSONObject badMethod = handle("{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"do/it\"}",
                twoTools(new AtomicInteger()));
        assertThat(badMethod.getJSONObject("error").getInt("code")).isEqualTo(-32601);
    }

    @Test
    @DisplayName("Unparseable input is -32700")
    void parseError() {
        JSONObject r = handle("{not json", twoTools(new AtomicInteger()));
        assertThat(r.getJSONObject("error").getInt("code")).isEqualTo(-32700);
    }

    @Test
    @DisplayName("A notification (no id) answers null — nothing sent back")
    void notificationIsSilent() {
        assertThat(McpProtocol.handle(
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",
                twoTools(new AtomicInteger()), "9.9.9")).isNull();
        // an unknown notification is also silent, never an error object
        assertThat(McpProtocol.handle(
                "{\"jsonrpc\":\"2.0\",\"method\":\"whatever\"}",
                twoTools(new AtomicInteger()), "9.9.9")).isNull();
    }
}
