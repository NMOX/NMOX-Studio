package org.nmox.studio.dbstudio.engine;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.api.db.explorer.ConnectionManager;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.netbeans.api.db.explorer.DatabaseException;
import org.netbeans.api.db.explorer.JDBCDriver;
import org.nmox.studio.dbstudio.model.ColumnInfo;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.nmox.studio.dbstudio.model.TableInfo;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Services bridge, tested through the REAL NetBeans Database
 * Explorer API: a {@link DatabaseConnection} built via
 * {@code DatabaseConnection.create} with a registered SQLite
 * {@link JDBCDriver}, connected by {@code ConnectionManager.connect}
 * inside {@link ServicesBackend#open()} — headless, no window system.
 * This proves the adapter seam itself (open → console → metadata →
 * no-op close); the SQL behavior underneath is {@link JdbcCore}'s,
 * pinned by {@link DbClientSqliteTest} and {@link JdbcCoreTest}.
 */
class ServicesBackendTest {

    private static DatabaseConnection sqliteServicesConnection(Path dbFile) {
        URL driverJar = org.sqlite.JDBC.class.getProtectionDomain()
                .getCodeSource().getLocation();
        JDBCDriver driver = JDBCDriver.create("sqlite", "SQLite (test)",
                "org.sqlite.JDBC", new URL[]{driverJar});
        // a user is required: ConnectionManager.connect() returns false for
        // user-less connections (the SQLite driver itself ignores it) — a
        // user-less real-world entry still works once connected in Services,
        // because open() short-circuits on the live getJDBCConnection()
        return DatabaseConnection.create(driver, "jdbc:sqlite:" + dbFile,
                "dbstudio", null, "", false);
    }

    @Test
    @DisplayName("open connects through ConnectionManager; console, tree and peek data flow")
    void adapterEndToEnd(@TempDir Path dir) throws DatabaseException {
        DatabaseConnection dbconn = sqliteServicesConnection(dir.resolve("nb.db"));
        // register it, exactly like a connection created in the Services
        // window — production only ever wraps ConnectionManager.getConnections()
        // entries, and connect() refuses unregistered connections
        ConnectionManager.getDefault().addConnection(dbconn);
        try {
            ServicesBackend backend = new ServicesBackend(dbconn);

            assertThat(backend.isOpen()).isFalse();
            assertThat(backend.open()).as("NB-managed connect succeeds").isNull();
            assertThat(backend.isOpen()).isTrue();
            assertThat(backend.open()).as("re-open is a no-op success").isNull();

            List<QueryResult> results = backend.runConsole("""
                    CREATE TABLE books (id INTEGER PRIMARY KEY, title TEXT NOT NULL);
                    INSERT INTO books (title) VALUES ('dune');
                    SELECT id, title FROM books;
                    """, 10);
            assertThat(results).hasSize(3);
            assertThat(results.get(1).updateCount()).isEqualTo(1);
            assertThat(results.get(2).columnNames()).containsExactly("id", "title");
            assertThat(results.get(2).rows()).containsExactly(List.of("1", "dune"));

            List<TableInfo> containers = backend.listContainers();
            assertThat(containers).extracting(TableInfo::name).contains("books");
            TableInfo books = containers.stream()
                    .filter(t -> t.name().equals("books")).findFirst().orElseThrow();
            List<ColumnInfo> columns = backend.columns(books);
            assertThat(columns).extracting(ColumnInfo::name).containsExactly("id", "title");
            assertThat(columns.get(0).primaryKey()).isTrue();

            // close() is a documented reference-drop no-op: the Services window
            // owns the shared connection, so it must survive our close
            backend.close();
            assertThat(backend.isOpen())
                    .as("NB's shared connection stays up after our close()").isTrue();
            assertThat(backend.runConsole("SELECT title FROM books;", 10).get(0).rows())
                    .containsExactly(List.of("dune"));
        } finally {
            ConnectionManager.getDefault().removeConnection(dbconn);
        }
    }

    @Test
    @DisplayName("open on an unregistered connection fails softly, pointing at the Services window")
    void openFailureNamesTheServicesWindow(@TempDir Path dir) {
        // never added to the ConnectionManager: NB refuses to connect it —
        // the same soft-failure shape as a missing stored password
        DatabaseConnection dbconn = sqliteServicesConnection(dir.resolve("orphan.db"));
        ServicesBackend backend = new ServicesBackend(dbconn);

        String error = backend.open();
        assertThat(error).as("a human message, not a throw").isNotBlank();
        assertThat(error).contains("Services window");
        assertThat(backend.isOpen()).isFalse();

        // and the console surfaces it as a single error result
        List<QueryResult> results = backend.runConsole("SELECT 1;", 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).isError()).isTrue();
        assertThat(results.get(0).error()).startsWith("Could not open connection:");
        // metadata calls follow the empty-list contract
        assertThat(backend.listContainers()).isEmpty();
        assertThat(backend.columns(null)).isEmpty();
        backend.cancel(); // idle cancel stays a no-op
    }

    @Test
    @DisplayName("spec() is a display identity: nb:-prefixed id, inferred engine, no secrets")
    void specSynthesis(@TempDir Path dir) {
        DatabaseConnection dbconn = sqliteServicesConnection(dir.resolve("id.db"));
        ServicesBackend backend = new ServicesBackend(dbconn);

        ConnectionSpec spec = backend.spec();
        assertThat(spec.id()).isEqualTo(ServicesBackend.ID_PREFIX + dbconn.getName());
        assertThat(spec.name()).isEqualTo(dbconn.getName());
        assertThat(spec.engine()).isEqualTo(DbEngine.SQLITE);
        assertThat(spec.filePath()).isNull();
        // JDBC by definition, even when the engine dialect is unmodeled
        assertThat(backend.kind()).isEqualTo(DbEngine.Kind.SQL);
    }

    @Test
    @DisplayName("URL anatomy is best-effort: host, port and database from server URLs, blanks elsewhere")
    void urlAnatomy() {
        String derby = "jdbc:derby://dbhost:1527/sample;create=true";
        assertThat(ServicesBackend.hostOf(derby)).isEqualTo("dbhost");
        assertThat(ServicesBackend.portOf(derby)).isEqualTo(1527);
        assertThat(ServicesBackend.databaseOf(derby)).isEqualTo("sample");

        String mysql = "jdbc:mysql://db.example.com:3306/shop?useSSL=false";
        assertThat(ServicesBackend.hostOf(mysql)).isEqualTo("db.example.com");
        assertThat(ServicesBackend.portOf(mysql)).isEqualTo(3306);
        assertThat(ServicesBackend.databaseOf(mysql)).isEqualTo("shop");

        String noPort = "jdbc:postgresql://localhost/app";
        assertThat(ServicesBackend.hostOf(noPort)).isEqualTo("localhost");
        assertThat(ServicesBackend.portOf(noPort)).isEqualTo(-1);
        assertThat(ServicesBackend.databaseOf(noPort)).isEqualTo("app");

        // no authority at all (SQLite, H2 mem, Oracle TNS): everything blank
        String sqlite = "jdbc:sqlite:/tmp/x.db";
        assertThat(ServicesBackend.hostOf(sqlite)).isEmpty();
        assertThat(ServicesBackend.portOf(sqlite)).isEqualTo(-1);
        assertThat(ServicesBackend.databaseOf(sqlite)).isEmpty();

        assertThat(ServicesBackend.hostOf(null)).isEmpty();
        assertThat(ServicesBackend.portOf("jdbc:derby://h:notaport/d")).isEqualTo(-1);
        assertThat(ServicesBackend.databaseOf(null)).isEmpty();
    }
}
