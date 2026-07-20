package org.nmox.studio.dbstudio.engine;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H1 (v1.101.0): the CouchDB backend's HTTP read is BOUNDED. The Watch-
 * equivalent here is a {@code _find}/{@code _all_docs} against a large
 * collection or a hostile endpoint — {@code ofString()} would have
 * buffered gigabytes into heap before the grid ever applied a row cap.
 * Driven against a real in-JVM server.
 */
class CouchBackendCapTest {

    private static HttpServer server;
    private static int port;

    @BeforeAll
    static void serve() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/huge/", ex -> {
            byte[] chunk = new byte[64 * 1024];
            Arrays.fill(chunk, (byte) 'x');
            long total = CouchBackend.MAX_RESPONSE_BYTES + 1024L * 1024;
            ex.sendResponseHeaders(200, total);
            try (OutputStream out = ex.getResponseBody()) {
                for (long sent = 0; sent < total; sent += chunk.length) {
                    out.write(chunk, 0, (int) Math.min(chunk.length, total - sent));
                }
            } catch (IOException aborted) {
                // the capped reader closes early — expected
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
    @DisplayName("An oversize CouchDB response is refused, never buffered whole")
    void oversizeResponseRefused() {
        ConnectionSpec spec = new ConnectionSpec("id", "c", DbEngine.COUCHDB,
                "127.0.0.1", port, "huge", "", "");
        CouchBackend backend = new CouchBackend(spec, new char[0]);

        // any console op that reads a body trips the cap; runConsole
        // wraps failures into a friendly error QueryResult rather than
        // throwing, so assert on that
        List<QueryResult> out = backend.runConsole("{\"selector\":{}}", 0);
        assertThat(out).isNotEmpty();
        assertThat(out.get(0).isError()).isTrue();
        assertThat(out.get(0).error()).contains("8MB");
    }
}
