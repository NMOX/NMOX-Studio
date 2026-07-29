package org.nmox.studio.dbstudio.engine;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.nmox.studio.dbstudio.model.TableInfo;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Couch backend against a real in-JVM HTTP server — the transport
 * half its parser-only sibling deliberately leaves out: the welcome
 * probe (genuine, impostor, unreachable), database listing with the
 * system-db filter, the Mango console loop, basic-auth header
 * emission, and CouchDB error replies surfacing as friendly console
 * errors rather than exceptions.
 */
class CouchBackendFixtureTest {

    private static HttpServer server;
    private static int port;
    private static final AtomicReference<String> lastAuth = new AtomicReference<>();

    @BeforeAll
    static void serve() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            lastAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            String path = ex.getRequestURI().getPath();
            switch (path) {
                case "/" -> respond(ex, 200,
                        "{\"couchdb\":\"Welcome\",\"version\":\"3.4.2\"}");
                case "/_all_dbs" -> respond(ex, 200,
                        "[\"_users\",\"_replicator\",\"shop\",\"crm\"]");
                case "/shop/_find" -> respond(ex, 200, """
                        {"docs": [{"_id": "a1", "name": "ada"},
                                  {"_id": "b2", "name": "grace"}]}""");
                case "/broken/_find" -> respond(ex, 400,
                        "{\"error\":\"bad_request\",\"reason\":\"invalid selector\"}");
                default -> respond(ex, 404, "{\"error\":\"not_found\",\"reason\":\"missing\"}");
            }
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, int status,
            String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static CouchBackend backend(String database, String user, char[] password) {
        return new CouchBackend(new ConnectionSpec("id-fx", "fx", DbEngine.COUCHDB,
                "127.0.0.1", port, database, user, ""), password);
    }

    @Test
    @DisplayName("test/open recognize a real CouchDB and remember the verdict")
    void welcomeProbe() {
        CouchBackend backend = backend("shop", "", null);
        assertThat(backend.test()).isNull();
        assertThat(backend.open()).isNull();
        assertThat(backend.isOpen()).isTrue();
        backend.close();
        assertThat(backend.isOpen()).isFalse();
    }

    @Test
    @DisplayName("an unreachable server humanizes into the open() verdict")
    void unreachableServer() throws Exception {
        int closedPort;
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        CouchBackend backend = new CouchBackend(new ConnectionSpec("id-x", "x",
                DbEngine.COUCHDB, "127.0.0.1", closedPort, "shop", "", ""), null);
        assertThat(backend.open()).isNotBlank();
        assertThat(backend.isOpen()).isFalse();
    }

    @Test
    @DisplayName("listContainers keeps user databases and filters the system ones")
    void listContainersFiltersSystemDbs() {
        List<TableInfo> containers = backend("shop", "", null).listContainers();
        assertThat(containers).extracting(TableInfo::name)
                .containsExactly("shop", "crm");
    }

    @Test
    @DisplayName("columns shape the first _find documents; a null container is empty")
    void columnsFromFind() {
        CouchBackend backend = backend("shop", "", null);
        assertThat(backend.columns(new TableInfo("", "", "shop", "DATABASE")))
                .extracting(c -> c.name()).contains("_id", "name");
        assertThat(backend.columns(null)).isEmpty();
    }

    @Test
    @DisplayName("the _all_dbs console command returns the database grid")
    void allDbsConsole() {
        List<QueryResult> results = backend("shop", "", null).runConsole("_all_dbs", 50);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).error()).isNull();
        assertThat(results.get(0).rows()).hasSize(4);
    }

    @Test
    @DisplayName("a Mango query lands as a document grid")
    void mangoConsole() {
        List<QueryResult> results = backend("shop", "", null)
                .runConsole("{\"name\": {\"$exists\": true}}", 50);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).error()).isNull();
        assertThat(results.get(0).rowCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("a CouchDB error reply becomes a console error naming code and reason")
    void errorReplySurfaces() {
        List<QueryResult> results = backend("broken", "", null).runConsole("{}", 50);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).error())
                .contains("HTTP 400").contains("bad_request").contains("invalid selector");
    }

    @Test
    @DisplayName("credentials ride as a basic Authorization header when a user is set")
    void basicAuthHeaderSent() {
        lastAuth.set(null);
        backend("shop", "admin", "sekret".toCharArray()).test();
        String expected = "Basic " + java.util.Base64.getEncoder().encodeToString(
                "admin:sekret".getBytes(StandardCharsets.UTF_8));
        assertThat(lastAuth.get()).isEqualTo(expected);
    }

    @Test
    @DisplayName("without a user, no Authorization header is invented")
    void noAuthHeaderWithoutUser() {
        lastAuth.set(null);
        backend("shop", "", null).test();
        assertThat(lastAuth.get()).isNull();
    }
}
