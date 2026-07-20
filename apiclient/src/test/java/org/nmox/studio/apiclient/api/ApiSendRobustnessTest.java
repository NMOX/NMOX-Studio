package org.nmox.studio.apiclient.api;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.apiclient.model.ApiModel.Request;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 52a+b, against a real in-JVM HTTP server: the response body
 * capture is BOUNDED (a runaway endpoint cannot OOM the IDE), the
 * truncation is flagged honestly, the declared charset is honored, and
 * an interrupt lands as the "cancelled" verdict, not a network error.
 */
class ApiSendRobustnessTest {

    private static HttpServer server;
    private static int port;

    @BeforeAll
    static void serve() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/huge", ex -> {
            byte[] chunk = new byte[64 * 1024];
            Arrays.fill(chunk, (byte) 'x');
            long total = ApiClient.MAX_BODY_BYTES + 1024L * 1024;
            ex.sendResponseHeaders(200, total);
            try (OutputStream out = ex.getResponseBody()) {
                for (long sent = 0; sent < total; sent += chunk.length) {
                    out.write(chunk, 0, (int) Math.min(chunk.length, total - sent));
                }
            } catch (java.io.IOException clientAborted) {
                // the capped reader closes early — expected
            }
        });
        server.createContext("/latin", ex -> {
            byte[] body = "café".getBytes(StandardCharsets.ISO_8859_1);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=ISO-8859-1");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/slow", ex -> {
            try {
                Thread.sleep(20_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            ex.sendResponseHeaders(204, -1);
            ex.close();
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    private static Request req(String path) {
        Request r = new Request();
        r.method = "GET";
        r.url = "http://127.0.0.1:" + port + path;
        return r;
    }

    @Test
    @DisplayName("A body past the cap is truncated at MAX_BODY_BYTES and flagged, never buffered whole")
    void hugeBodyIsCappedAndFlagged() {
        ApiResponse r = new ApiClient().send(req("/huge"), Map.of());

        assertThat(r.reached()).isTrue();
        assertThat(r.truncated()).as("the cap must be reported, not hidden").isTrue();
        assertThat(r.bytes()).isEqualTo(ApiClient.MAX_BODY_BYTES);
        assertThat(r.body()).hasSize(ApiClient.MAX_BODY_BYTES);
    }

    @Test
    @DisplayName("A small body arrives whole, untruncated, decoded with the declared charset")
    void smallBodyIsWholeAndCharsetHonored() {
        ApiResponse r = new ApiClient().send(req("/latin"), Map.of());

        assertThat(r.reached()).isTrue();
        assertThat(r.truncated()).isFalse();
        assertThat(r.body()).as("ISO-8859-1 bytes decoded per Content-Type").isEqualTo("café");
    }

    @Test
    @DisplayName("Interrupting the send thread yields the 'cancelled' verdict, not a network error")
    void interruptIsCancelledVerdict() throws Exception {
        AtomicReference<ApiResponse> out = new AtomicReference<>();
        Thread worker = new Thread(() -> out.set(new ApiClient().send(req("/slow"), Map.of())));
        worker.start();
        Thread.sleep(500); // let the exchange begin blocking
        worker.interrupt();
        worker.join(10_000);

        assertThat(worker.isAlive()).as("interrupt must unblock the send").isFalse();
        assertThat(out.get()).isNotNull();
        assertThat(out.get().reached()).isFalse();
        assertThat(out.get().error()).isEqualTo("cancelled");
    }

    @Test
    @DisplayName("charsetOf parses declared charsets and falls back to UTF-8 on absent/unknown")
    void charsetParsing() {
        assertThat(ApiClient.charsetOf("text/html; charset=ISO-8859-1"))
                .isEqualTo(StandardCharsets.ISO_8859_1);
        assertThat(ApiClient.charsetOf("application/json; charset=\"utf-8\"; boundary=x"))
                .isEqualTo(StandardCharsets.UTF_8);
        assertThat(ApiClient.charsetOf("application/json")).isEqualTo(StandardCharsets.UTF_8);
        assertThat(ApiClient.charsetOf("text/plain; charset=not-a-charset"))
                .isEqualTo(StandardCharsets.UTF_8);
        assertThat(ApiClient.charsetOf("")).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("prettyForDisplay survives a deeply-nested body (StackOverflowError) and skips huge ones")
    void prettyForDisplayIsGuarded() {
        String deep = "[".repeat(100_000) + "]".repeat(100_000);
        assertThat(WorkspaceIO.prettyForDisplay(deep))
                .as("recursion blowup degrades to raw, never kills the thread")
                .isEqualTo(deep);

        String huge = "{\"a\":\"" + "x".repeat(2_100_000) + "\"}";
        assertThat(WorkspaceIO.prettyForDisplay(huge))
                .as("past the size guard the body shows raw")
                .isSameAs(huge);

        assertThat(WorkspaceIO.prettyForDisplay("{\"a\":1,\"b\":2}"))
                .as("normal bodies still pretty")
                .contains("\n");
        assertThat(WorkspaceIO.prettyForDisplay(null)).isEmpty();
    }
}
