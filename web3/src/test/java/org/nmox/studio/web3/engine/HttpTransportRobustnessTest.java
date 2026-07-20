package org.nmox.studio.web3.engine;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The production transport against a real in-JVM HTTP server: the
 * response read is BOUNDED (the Watch pane polls arbitrary user-added
 * endpoints every 2s — an oversize body must be refused, not
 * buffered), and every failure message stays redacted, including the
 * pre-request URI parse.
 */
class HttpTransportRobustnessTest {

    private static HttpServer server;
    private static int port;

    @BeforeAll
    static void serve() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/huge", ex -> {
            byte[] chunk = new byte[64 * 1024];
            Arrays.fill(chunk, (byte) 'x');
            long total = JsonRpcClient.MAX_RESPONSE_BYTES + 1024L * 1024;
            ex.sendResponseHeaders(200, total);
            try (OutputStream out = ex.getResponseBody()) {
                for (long sent = 0; sent < total; sent += chunk.length) {
                    out.write(chunk, 0, (int) Math.min(chunk.length, total - sent));
                }
            } catch (IOException clientAborted) {
                // the capped reader closes early — expected
            }
        });
        server.createContext("/ok", ex -> {
            byte[] body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0x1\"}"
                    .getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    @Test
    @DisplayName("An oversize RPC response is refused with a redacted message, never buffered")
    void oversizeBodyRefused() {
        String url = "http://127.0.0.1:" + port + "/huge?apikey=SECRET_TOKEN";
        assertThatThrownBy(() -> JsonRpcClient.httpTransport().post(url, "{}"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Response over 8MB")
                .satisfies(e -> assertThat(e.getMessage())
                        .as("the endpoint's key must never surface")
                        .doesNotContain("SECRET_TOKEN")
                        .doesNotContain("apikey"));
    }

    @Test
    @DisplayName("A normal RPC body arrives whole through the capped reader")
    void normalBodyArrives() throws Exception {
        String body = JsonRpcClient.httpTransport()
                .post("http://127.0.0.1:" + port + "/ok", "{}");
        assertThat(body).contains("\"result\":\"0x1\"");
    }

    @Test
    @DisplayName("An unparseable URL fails redacted — URI.create's message echoes the full input")
    void unparseableUrlRedacted() {
        String garbage = "http://[bad host/rpc?apikey=SECRET_TOKEN";
        assertThatThrownBy(() -> JsonRpcClient.httpTransport().post(garbage, "{}"))
                .isInstanceOf(IOException.class)
                .satisfies(e -> assertThat(e.getMessage())
                        .as("the raw URL (and its key) must not leak through the parse error")
                        .doesNotContain("SECRET_TOKEN"));
    }

    @Test
    @DisplayName("loopback() recognizes the devnet hosts and refuses everything else")
    void loopbackClassification() {
        assertThat(JsonRpcClient.loopback("http://127.0.0.1:8545")).isTrue();
        assertThat(JsonRpcClient.loopback("http://localhost:8545/rpc")).isTrue();
        assertThat(JsonRpcClient.loopback("https://LOCALHOST:8545")).isTrue();
        assertThat(JsonRpcClient.loopback("http://[::1]:8545")).isTrue();
        assertThat(JsonRpcClient.loopback("http://127.9.9.9:8545")).isTrue();

        assertThat(JsonRpcClient.loopback("https://mainnet.example.io/v3/key")).isFalse();
        assertThat(JsonRpcClient.loopback("http://192.168.1.10:8545")).isFalse();
        assertThat(JsonRpcClient.loopback("http://10.0.0.5:8545")).isFalse();
        assertThat(JsonRpcClient.loopback("not a url")).isFalse();
        assertThat(JsonRpcClient.loopback("")).isFalse();
    }
}
