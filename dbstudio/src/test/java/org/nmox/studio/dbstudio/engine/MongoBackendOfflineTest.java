package org.nmox.studio.dbstudio.engine;

import java.net.ServerSocket;
import org.bson.BsonRegularExpression;
import org.bson.BsonTimestamp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Mongo backend without a mongod: the connection-refused paths
 * must come back as human sentences (never driver stack traces), the
 * lifecycle flags must tell the truth around a failed open, and the
 * BSON display-name table must know its rarer types. The happy paths
 * against a live server belong to the app-level gauntlets — the
 * driver's 5s server-selection budget makes each refused call cost
 * real seconds, so this class keeps them to a minimum.
 */
class MongoBackendOfflineTest {

    private static ConnectionSpec unreachableSpec() throws Exception {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        return new ConnectionSpec("id-mg", "mg", DbEngine.MONGODB,
                "127.0.0.1", closedPort, "appdb", "", "");
    }

    @Test
    @DisplayName("test and open against a closed port fail with a sentence, and the flags stay honest")
    @Timeout(40)
    void refusedConnectionIsHumanized() throws Exception {
        MongoBackend backend = new MongoBackend(unreachableSpec(), "pw".toCharArray());
        try {
            assertThat(backend.test()).as("test() reports, never throws").isNotBlank();
            assertThat(backend.open()).as("open() reports the same failure").isNotBlank();
            assertThat(backend.isOpen()).isFalse();
            assertThat(backend.columns(null)).isEmpty();
        } finally {
            backend.close();
        }
    }

    @Test
    @DisplayName("the connection string carries encoded credentials and the timeout budget")
    void connectionStringShape() {
        assertThat(MongoBackend.connectionString("ad min", "p@ss".toCharArray(),
                "db.example.com", 27017))
                .startsWith("mongodb://ad%20min:p%40ss@db.example.com:27017/")
                .contains("serverSelectionTimeoutMS=");
        assertThat(MongoBackend.connectionString("", null, null, 27017))
                .as("no user means no credentials block, a null host stays empty")
                .startsWith("mongodb://:27017/");
    }

    @Test
    @DisplayName("the BSON display-name table knows timestamps, regexes, and falls back to the class name")
    void bsonTypeNames() {
        assertThat(MongoBackend.bsonTypeName(new BsonTimestamp(1, 2))).isEqualTo("timestamp");
        assertThat(MongoBackend.bsonTypeName(new BsonRegularExpression("^a"))).isEqualTo("regex");
        assertThat(MongoBackend.bsonTypeName(new StringBuilder())).isEqualTo("StringBuilder");
    }
}
