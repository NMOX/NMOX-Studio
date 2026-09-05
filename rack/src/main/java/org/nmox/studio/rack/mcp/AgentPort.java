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
import java.util.ArrayList;
import java.util.List;
import org.nmox.studio.core.http.HttpBodies;
import org.nmox.studio.core.spi.LiveRuns;
import org.nmox.studio.core.spi.LiveServings;

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
    /** Open GET streams past this are refused (503) — one agent needs one;
     *  an unbounded count is an unbounded set of sockets and buffers (v2.84.0 review). */
    static final int MAX_STREAMS = 8;

    // a process-lifetime CSPRNG, seeded once and reused for every port
    // start (a per-call new SecureRandom is used-only-once — sharing it
    // is both the lint fix and the correct shape)
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private final HttpServer server;
    private final String token;
    private final String productVersion;
    private final McpTools tools;
    private final McpSubscriptions subs = new McpSubscriptions();
    private final List<Runnable> unwatch = new ArrayList<>();
    // the completion/complete resolver (v2.84.0): the symbol index and the
    // aim, looked up lazily per request — nothing at construction
    private final McpCompletions completions = McpCompletions.production();

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
        port.watch();
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
        for (Runnable r : unwatch) {
            r.run();
        }
        unwatch.clear();
        subs.close();
        server.stop(0);
    }

    /** The port's subscriptions — tests read them. */
    McpSubscriptions subscriptions() {
        return subs;
    }

    /**
     * The registries whose changes become {@code resources/updated} frames
     * (v2.84.0), each with the URIs it moves; listener symmetry — every
     * add here has its remove in {@link #stop}. Absent registries (no
     * rack in the session) are simply not watched.
     */
    private void watch() {
        Runnable runs = () -> subs.updated("nmox://runs", "nmox://context");
        LiveRuns.addListener(runs);
        unwatch.add(() -> LiveRuns.removeListener(runs));
        LiveServings servings = LiveServings.find();
        if (servings != null) {
            LiveServings.Listener l = () -> subs.updated("nmox://servers", "nmox://context");
            servings.addListener(l);
            unwatch.add(() -> servings.removeListener(l));
        }
        // the editor: what the user looks at (v2.84.0) — the window registry
        // fires on the EDT; the frame write rides the push daemon, so the EDT
        // never waits on a socket
        java.beans.PropertyChangeListener editor = evt -> {
            String p = evt.getPropertyName();
            if (org.openide.windows.TopComponent.Registry.PROP_ACTIVATED.equals(p)
                    || org.openide.windows.TopComponent.Registry.PROP_OPENED.equals(p)
                    || org.openide.windows.TopComponent.Registry.PROP_TC_OPENED.equals(p)
                    || org.openide.windows.TopComponent.Registry.PROP_TC_CLOSED.equals(p)) {
                subs.updated("nmox://editor", "nmox://context");
            }
        };
        org.openide.windows.TopComponent.getRegistry().addPropertyChangeListener(editor);
        unwatch.add(() -> org.openide.windows.TopComponent.getRegistry().removePropertyChangeListener(editor));
        try {
            org.nmox.studio.rack.engine.DiagnosticsBus.Listener d =
                    (tool, problems) -> subs.updated("nmox://diagnostics", "nmox://context");
            org.nmox.studio.rack.engine.DiagnosticsBus.addListener(d);
            unwatch.add(() -> org.nmox.studio.rack.engine.DiagnosticsBus.removeListener(d));
            Runnable rec = () -> subs.updated("nmox://history", "nmox://last-failure", "nmox://context");
            org.nmox.studio.rack.engine.FlightRecorder.getDefault().addChangeListener(rec);
            unwatch.add(() -> org.nmox.studio.rack.engine.FlightRecorder.getDefault().removeChangeListener(rec));
        } catch (RuntimeException | LinkageError absent) {
            // a session without the rack's engine: those resources never change
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        // the GET stream stays open past this method — it must not sit
        // inside the try-with-resources that closes every other exchange
        if ("GET".equals(exchange.getRequestMethod())
                && exchange.getRequestHeaders().getFirst("Origin") == null
                && authorized(exchange)
                && acceptsEventStream(exchange)) {
            openStream(exchange);
            return;
        }
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
                // a plain GET is not a page (405); the SSE GET was served above
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
            String response;
            try {
                response = McpProtocol.handle(body, tools, productVersion, subs, completions);
            } catch (RuntimeException ex) {
                // defense in depth under the every-refusal-speaks law: an
                // uncaught throw here would make httpserver DROP the
                // connection — silence, the one thing this port must
                // never answer with. Every protocol path guards its own
                // handlers; this net is for whatever a future path forgets.
                response = "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,"
                        + "\"message\":\"Internal error\"}}";
            }
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

    private static boolean acceptsEventStream(HttpExchange exchange) {
        String accept = exchange.getRequestHeaders().getFirst("Accept");
        return accept != null && accept.contains("text/event-stream");
    }

    /**
     * The Streamable HTTP GET stream (v2.84.0): server-to-client frames —
     * here only {@code notifications/resources/updated} for subscribed
     * URIs. Chunked, kept open until the client leaves or the port stops.
     */
    private void openStream(HttpExchange exchange) throws IOException {
        if (subs.attachedCount() >= MAX_STREAMS) {
            // refused out loud, with the reason a client can read
            try (exchange) {
                exchange.sendResponseHeaders(503, -1);
            }
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, 0);
        OutputStream out = exchange.getResponseBody();
        out.write(": connected\n\n".getBytes(StandardCharsets.UTF_8));
        out.flush();
        subs.attach(out, exchange::close);
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
