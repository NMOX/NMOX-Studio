package org.nmox.studio.dbstudio.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * {@link DbBackend#create} — the one place engine dispatch happens:
 * every SQL engine gets the JDBC client, each document engine its
 * native backend, and construction never touches the network.
 */
class DbBackendFactoryTest {

    private static ConnectionSpec spec(DbEngine engine) {
        return new ConnectionSpec("id-" + engine.name(), "conn", engine,
                "db.example.com", 0, "appdb", "app", "/tmp/x.db");
    }

    @Test
    @DisplayName("SQL engines dispatch to the JDBC DbClient")
    void sqlEnginesGetDbClient() {
        for (DbEngine engine : DbEngine.values()) {
            if (engine.kind() == DbEngine.Kind.SQL) {
                try (DbBackend backend = DbBackend.create(spec(engine), "pw".toCharArray())) {
                    assertThat(backend).as(engine.name()).isInstanceOf(DbClient.class);
                    assertThat(backend.spec().engine()).isEqualTo(engine);
                    assertThat(backend.kind()).isEqualTo(DbEngine.Kind.SQL);
                }
            }
        }
    }

    @Test
    @DisplayName("MongoDB dispatches to MongoBackend")
    void mongoGetsMongoBackend() {
        try (DbBackend backend = DbBackend.create(spec(DbEngine.MONGODB), null)) {
            assertThat(backend).isInstanceOf(MongoBackend.class);
            assertThat(backend.kind()).isEqualTo(DbEngine.Kind.DOCUMENT);
            assertThat(backend.isOpen()).as("construction is lazy").isFalse();
        }
    }

    @Test
    @DisplayName("CouchDB dispatches to CouchBackend")
    void couchGetsCouchBackend() {
        try (DbBackend backend = DbBackend.create(spec(DbEngine.COUCHDB), null)) {
            assertThat(backend).isInstanceOf(CouchBackend.class);
            assertThat(backend.kind()).isEqualTo(DbEngine.Kind.DOCUMENT);
            assertThat(backend.isOpen()).as("construction is lazy").isFalse();
        }
    }

    @Test
    @DisplayName("every backend carries its spec and closes safely when never opened")
    void specCarriedAndCloseSafe() {
        for (DbEngine engine : DbEngine.values()) {
            DbBackend backend = DbBackend.create(spec(engine), null);
            assertThat(backend.spec()).isEqualTo(spec(engine));
            backend.close();
            backend.close(); // double close is safe on all backends
            assertThat(backend.isOpen()).isFalse();
        }
    }

    @Test
    @DisplayName("a null spec is rejected eagerly; the wrong backend for an engine is impossible")
    void nullSpecRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> DbBackend.create(null, null));
        // and the JDBC client refuses to be built for a document engine directly
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DbClient(spec(DbEngine.MONGODB), null))
                .withMessageContaining("JDBC only");
    }
}
