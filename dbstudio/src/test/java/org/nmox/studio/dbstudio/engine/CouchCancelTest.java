package org.nmox.studio.dbstudio.engine;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * 3.4, "when something goes wrong": a CouchDB server that sends its headers
 * and then stops sending the body held the console forever, and Cancel was
 * a documented no-op ("the timeout is the cancellation" — but the request
 * timeout ends at the headers). Cancel now closes the body being read, and
 * the run answers "Cancelled".
 */
class CouchCancelTest {

    private HttpServer server;
    private final CountDownLatch bodyStarted = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void serve() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/stall/", ex -> {
            ex.sendResponseHeaders(200, 10_000);
            OutputStream out = ex.getResponseBody();
            out.write("{\"docs\":[".getBytes(StandardCharsets.UTF_8));
            out.flush();
            bodyStarted.countDown();
            try {
                release.await(60, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            ex.close();
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    @AfterEach
    void stop() {
        release.countDown();
        server.stop(0);
    }

    @Test
    @DisplayName("Cancel frees a console run whose server stalled mid-body, and says Cancelled")
    void cancelFreesAStalledBody() throws Exception {
        ConnectionSpec spec = new ConnectionSpec("id", "c", DbEngine.COUCHDB,
                "127.0.0.1", server.getAddress().getPort(), "stall", "", "");
        CouchBackend backend = new CouchBackend(spec, new char[0]);

        CompletableFuture<List<QueryResult>> run = CompletableFuture.supplyAsync(
                () -> backend.runConsole("{\"selector\":{}}", 10));
        assertThat(bodyStarted.await(10, TimeUnit.SECONDS))
                .as("the fixture sent its headers and a first chunk").isTrue();

        // the run is now blocked reading a body that will not come; Cancel
        // is the gesture — it may land a beat before the read begins, so it
        // is repeated until the run answers (each press is idempotent)
        List<QueryResult> out = assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            while (!run.isDone()) {
                backend.cancel();
                try {
                    return run.get(200, TimeUnit.MILLISECONDS);
                } catch (java.util.concurrent.TimeoutException notYet) {
                    // press again
                }
            }
            return run.get();
        });
        assertThat(out).hasSize(1);
        assertThat(out.get(0).isError()).isTrue();
        assertThat(out.get(0).error()).startsWith("Cancelled");
    }

    @Test
    @DisplayName("A Cancel pressed with no console run does not poison the next one")
    void idleCancelIsForgotten() {
        ConnectionSpec spec = new ConnectionSpec("id", "c", DbEngine.COUCHDB,
                "127.0.0.1", server.getAddress().getPort(), "stall", "", "");
        CouchBackend backend = new CouchBackend(spec, new char[0]);
        backend.cancel(); // nothing running
        release.countDown(); // the fixture answers (a short body → a named break, not "Cancelled")
        List<QueryResult> out = assertTimeoutPreemptively(Duration.ofSeconds(60),
                () -> backend.runConsole("{\"selector\":{}}", 10));
        assertThat(out.get(0).error()).doesNotStartWith("Cancelled");
    }
}
