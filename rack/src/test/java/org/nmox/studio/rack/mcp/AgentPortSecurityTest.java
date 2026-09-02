package org.nmox.studio.rack.mcp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.mcp.McpTools.Tool;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The transport's security laws, proven against a REAL running port:
 * loopback bind witnessed, the bearer token demanded (401 without,
 * with wrong), any Origin header refused (403 — the DNS-rebinding
 * defense), GET refused (405), over-cap bodies refused (413), and the
 * happy path answering real JSON only with the right token.
 */
class AgentPortSecurityTest {

    private AgentPort port;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws Exception {
        McpTools tools = new McpTools(List.of(
                new Tool("project_state", "Project state", "the aimed project",
                        McpTools.objectSchema(new org.json.JSONObject()),
                        McpTools.objectSchema(new org.json.JSONObject()),
                        args -> new McpTools.ToolResult("Project: demo", null))));
        port = AgentPort.start(tools, "9.9.9");
    }

    @AfterEach
    void stop() {
        if (port != null) {
            port.stop();
        }
    }

    private HttpRequest.Builder req() {
        return HttpRequest.newBuilder(URI.create(port.url()));
    }

    private static final String PING =
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";

    @Test
    @DisplayName("The bind is loopback and the URL is 127.0.0.1")
    void loopbackBind() {
        assertThat(port.url()).startsWith("http://127.0.0.1:");
    }

    @Test
    @DisplayName("No token is 401; a wrong token is 401")
    void tokenDemanded() throws Exception {
        var noAuth = http.send(req().POST(HttpRequest.BodyPublishers.ofString(PING)).build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(noAuth.statusCode()).isEqualTo(401);
        var wrong = http.send(req().header("Authorization", "Bearer wrong")
                .POST(HttpRequest.BodyPublishers.ofString(PING)).build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(wrong.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("An Origin header is refused outright — the rebinding defense")
    void originRefused() throws Exception {
        var r = http.send(req()
                .header("Authorization", "Bearer " + port.token())
                .header("Origin", "http://evil.example")
                .POST(HttpRequest.BodyPublishers.ofString(PING)).build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(r.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("GET is 405 — only the stateless POST shape is served")
    void getRefused() throws Exception {
        var r = http.send(req().header("Authorization", "Bearer " + port.token())
                .GET().build(), HttpResponse.BodyHandlers.discarding());
        assertThat(r.statusCode()).isEqualTo(405);
    }

    @Test
    @DisplayName("The right token answers real JSON-RPC")
    void happyPath() throws Exception {
        var r = http.send(req().header("Authorization", "Bearer " + port.token())
                .POST(HttpRequest.BodyPublishers.ofString(PING)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(new JSONObject(r.body()).getJSONObject("result")).isNotNull();
        assertThat(new JSONObject(r.body()).get("id")).isEqualTo(1);
    }

    @Test
    @DisplayName("An over-cap body is refused, never clipped")
    void overCapRefused() throws Exception {
        String huge = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\",\"pad\":\""
                + "x".repeat(AgentPort.MAX_REQUEST_BYTES + 16) + "\"}";
        var r = http.send(req().header("Authorization", "Bearer " + port.token())
                .POST(HttpRequest.BodyPublishers.ofString(huge)).build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(r.statusCode()).isEqualTo(413);
    }
}
