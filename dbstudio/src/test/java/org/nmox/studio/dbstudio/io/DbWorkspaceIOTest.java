package org.nmox.studio.dbstudio.io;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.dbstudio.engine.Passwords;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code .nmoxdb.json} persistence: every field of every engine
 * round-trips, secrets are structurally impossible in the file, and a
 * broken file degrades to an empty workspace instead of an exception.
 */
class DbWorkspaceIOTest {

    private static List<ConnectionSpec> richWorkspace() {
        return List.of(
                new ConnectionSpec("id-mysql", "prod primary", DbEngine.MYSQL,
                        "db.prod.example.com", 3307, "shop", "shop_rw", ""),
                new ConnectionSpec("id-pg", "analytics", DbEngine.POSTGRES,
                        "10.0.0.7", 5432, "warehouse", "analyst", ""),
                new ConnectionSpec("id-sqlite", "local cache", DbEngine.SQLITE,
                        "", -1, "", "", "/Users/dev/app/cache.db"));
    }

    @Test
    @DisplayName("Every field of every engine survives toJson/fromJson")
    void fullRoundTrip() {
        List<ConnectionSpec> back = DbWorkspaceIO.fromJson(DbWorkspaceIO.toJson(richWorkspace()));

        assertThat(back).hasSize(3);

        ConnectionSpec mysql = back.get(0);
        assertThat(mysql.id()).isEqualTo("id-mysql");
        assertThat(mysql.name()).isEqualTo("prod primary");
        assertThat(mysql.engine()).isEqualTo(DbEngine.MYSQL);
        assertThat(mysql.host()).isEqualTo("db.prod.example.com");
        assertThat(mysql.port()).isEqualTo(3307);
        assertThat(mysql.database()).isEqualTo("shop");
        assertThat(mysql.user()).isEqualTo("shop_rw");
        assertThat(mysql.filePath()).isEmpty();

        assertThat(back.get(1).engine()).isEqualTo(DbEngine.POSTGRES);
        assertThat(back.get(1).port()).isEqualTo(5432);

        ConnectionSpec sqlite = back.get(2);
        assertThat(sqlite.engine()).isEqualTo(DbEngine.SQLITE);
        assertThat(sqlite.filePath()).isEqualTo("/Users/dev/app/cache.db");
        assertThat(sqlite.port()).isEqualTo(-1);
    }

    @Test
    @DisplayName("save writes .nmoxdb.json in the project dir and load reads it back")
    void saveAndLoad(@TempDir Path dir) throws Exception {
        DbWorkspaceIO.save(dir.toFile(), richWorkspace());

        assertThat(dir.resolve(DbWorkspaceIO.FILENAME)).exists();

        List<ConnectionSpec> back = DbWorkspaceIO.load(dir.toFile());
        assertThat(back).hasSize(3);
        assertThat(back).extracting(ConnectionSpec::name)
                .containsExactly("prod primary", "analytics", "local cache");
    }

    @Test
    @DisplayName("The serialized file NEVER contains a password, even when one exists for the spec")
    void noPasswordEverWritten(@TempDir Path dir) throws Exception {
        String password = "hunter2-super-secret";
        // a password exists for this connection (in the keyring/fallback)...
        Passwords.save("id-mysql", password.toCharArray());
        try {
            DbWorkspaceIO.save(dir.toFile(), richWorkspace());

            String written = Files.readString(dir.resolve(DbWorkspaceIO.FILENAME),
                    StandardCharsets.UTF_8);
            // ...but the file has no secret and no field to put one in
            assertThat(written).doesNotContain(password);
            assertThat(written.toLowerCase()).doesNotContain("password");
        } finally {
            Passwords.delete("id-mysql");
        }
    }

    @Test
    @DisplayName("A missing file loads as an empty list")
    void missingFileLoadsEmpty(@TempDir Path dir) {
        assertThat(DbWorkspaceIO.load(dir.toFile())).isEmpty();
    }

    @Test
    @DisplayName("A malformed file loads as an empty list, never throws")
    void malformedFileLoadsEmpty(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(DbWorkspaceIO.FILENAME),
                "{ this is not json ]", StandardCharsets.UTF_8);

        assertThat(DbWorkspaceIO.load(dir.toFile())).isEmpty();
        assertThat(DbWorkspaceIO.fromJson("")).isEmpty();
        assertThat(DbWorkspaceIO.fromJson(null)).isEmpty();
        assertThat(DbWorkspaceIO.fromJson("[1,2,3]")).isEmpty();
    }

    @Test
    @DisplayName("An unknown engine from a newer NMOX skips that connection, keeps the rest")
    void unknownEngineSkipped() {
        String future = """
                {
                  "version": 1,
                  "connections": [
                    {"id": "a", "name": "keep me", "engine": "SQLITE", "filePath": "/tmp/x.db"},
                    {"id": "b", "name": "from the future", "engine": "COCKROACH", "host": "h"},
                    {"id": "c", "name": "keep me too", "engine": "POSTGRES", "host": "h", "port": 5432}
                  ]
                }
                """;
        List<ConnectionSpec> back = DbWorkspaceIO.fromJson(future);

        assertThat(back).extracting(ConnectionSpec::name)
                .containsExactly("keep me", "keep me too");
    }

    @Test
    @DisplayName("A hand-edited connection without an id gets a fresh UUID instead of an empty key")
    void missingIdHealed() {
        String edited = """
                {"version": 1, "connections": [{"name": "no id", "engine": "SQLITE"}]}
                """;
        List<ConnectionSpec> back = DbWorkspaceIO.fromJson(edited);

        assertThat(back).hasSize(1);
        assertThat(back.get(0).id()).isNotBlank();
    }

    @Test
    @DisplayName("The file is pretty-printed UTF-8 with a version stamp")
    void fileShape(@TempDir Path dir) throws Exception {
        DbWorkspaceIO.save(dir.toFile(), richWorkspace());

        String written = Files.readString(dir.resolve(DbWorkspaceIO.FILENAME),
                StandardCharsets.UTF_8);
        assertThat(written).contains("\"version\": 1");
        assertThat(written.lines().count()).as("pretty-printed, not one line").isGreaterThan(3);
    }

    @Test
    @DisplayName("An empty workspace round-trips to an empty workspace")
    void emptyRoundTrip() {
        assertThat(DbWorkspaceIO.fromJson(DbWorkspaceIO.toJson(List.of()))).isEmpty();
    }

    @Test
    @DisplayName("The document engines (MongoDB, CouchDB) round-trip like any other")
    void documentEnginesRoundTrip() {
        List<ConnectionSpec> specs = List.of(
                new ConnectionSpec("id-mongo", "docs cluster", DbEngine.MONGODB,
                        "mongo.internal", 27018, "appdb", "app_rw", ""),
                new ConnectionSpec("id-couch", "couch relax", DbEngine.COUCHDB,
                        "couch.internal", 5984, "shop", "admin", ""));

        List<ConnectionSpec> back = DbWorkspaceIO.fromJson(DbWorkspaceIO.toJson(specs));

        assertThat(back).hasSize(2);
        ConnectionSpec mongo = back.get(0);
        assertThat(mongo.engine()).isEqualTo(DbEngine.MONGODB);
        assertThat(mongo.host()).isEqualTo("mongo.internal");
        assertThat(mongo.port()).isEqualTo(27018);
        assertThat(mongo.database()).isEqualTo("appdb");
        ConnectionSpec couch = back.get(1);
        assertThat(couch.engine()).isEqualTo(DbEngine.COUCHDB);
        assertThat(couch.database()).isEqualTo("shop");
    }
}
