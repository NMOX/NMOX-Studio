package org.nmox.studio.rack.mcp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.nmox.studio.core.http.HttpBodies;

/**
 * The Agent Port's transport (futures-2031 F4): a loopback-witnessed
 * MCP Streamable-HTTP endpoint over the JDK's httpserver — the
 * SiteServer recipe with the trust laws rewritten for a caller that is
 * not the user at the keyboard. OFF by default and started only by an
 * explicit gesture; binds loopback and WITNESSES it; demands the
 * per-start bearer token on every request (constant-time compare, 401
 * otherwise); REFUSES any request carrying an Origin header outright —
 * a browser page rebinding to localhost sends one, a native MCP client
 * does not (the spec's own DNS-rebinding defense, made total); caps
 * the request body; and answers only POST (the stateless shape — GET's
 * SSE listening channel is honestly 405, not half-implemented).
 */
public final class AgentPort {

    /** Request bodies past this are refused — no MCP message is 1 MB. */
    static final int MAX_REQUEST_BYTES = 1024 * 1024;

    // a process-lifetime CSPRNG, seeded once and reused for every port
    // start (a per-call new SecureRandom is used-only-once — sharing it
    // is both the lint fix and the correct shape)
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private final HttpServer server;
    private final String token;
    private final String productVersion;
    private final McpTools tools;

    private AgentPort(HttpServer server, String token, McpTools tools,
            String productVersion) {
        this.server = server;
        this.token = token;
        this.tools = tools;
        this.productVersion = productVersion;
    }

    /**
     * Binds loopback on an ephemeral port with a fresh SecureRandom
     * token and starts serving. The caller shows the user the port and
     * token ONCE; the token is never logged and never persisted.
     */
    public static AgentPort start(McpTools tools, String productVersion)
            throws IOException {
        byte[] raw = new byte[32];
        TOKEN_RANDOM.nextBytes(raw);
        String token = HexFormat.of().formatHex(raw);
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        if (!server.getAddress().getAddress().isLoopbackAddress()) {
            // the witness: never trust the request, verify the bind
            server.stop(0);
            throw new IOException("Refused to serve: bind is not loopback.");
        }
        AgentPort port = new AgentPort(server, token, tools, productVersion);
        server.createContext("/mcp", port::handle);
        server.start();
        return port;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String token() {
        return token;
    }

    public String url() {
        return "http://127.0.0.1:" + port() + "/mcp";
    }

    public void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            // a browser-originated request carries Origin; no native MCP
            // client does — refuse the whole class before anything else
            if (exchange.getRequestHeaders().getFirst("Origin") != null) {
                refuse(exchange, 403);
                return;
            }
            if (!authorized(exchange)) {
                refuse(exchange, 401);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                refuse(exchange, 405);
                return;
            }
            String body;
            try (InputStream in = exchange.getRequestBody()) {
                HttpBodies.Capped capped = HttpBodies.readUtf8(in, MAX_REQUEST_BYTES);
                if (capped.truncated()) {
                    refuse(exchange, 413); // over-cap is refused, never clipped
                    return;
                }
                body = capped.text();
            }
            String response = McpProtocol.handle(body, tools, productVersion);
            if (response == null) {
                // a notification: acknowledged with 202 and no body
                exchange.sendResponseHeaders(202, -1);
                return;
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    private boolean authorized(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return false;
        }
        // constant-time: a timing oracle on a secret is still an oracle
        return MessageDigest.isEqual(
                header.substring("Bearer ".length()).getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    private static void refuse(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
    }
}
