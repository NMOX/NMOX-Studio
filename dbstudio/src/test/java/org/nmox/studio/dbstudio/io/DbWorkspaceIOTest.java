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
    @DisplayName("A corrupt file is copied to .bak BEFORE the empty fallback")
    void corruptFileIsBackedUpBeforeEmptyFallback(@TempDir Path dir) throws Exception {
        String corrupt = "{ the saved queries were in here ]";
        Files.writeString(dir.resolve(DbWorkspaceIO.FILENAME), corrupt, StandardCharsets.UTF_8);

        DbWorkspaceIO.LoadOutcome outcome = DbWorkspaceIO.loadWorkspaceGuarded(dir.toFile());

        assertThat(outcome.workspace()).isEqualTo(DbWorkspaceIO.Workspace.empty());
        assertThat(outcome.backup()).isNotNull();
        assertThat(outcome.backup().getName()).isEqualTo(DbWorkspaceIO.FILENAME + ".bak");
        assertThat(Files.readString(outcome.backup().toPath(), StandardCharsets.UTF_8))
                .as("the backup carries the original bytes").isEqualTo(corrupt);
    }

    @Test
    @DisplayName("Guarded load: missing and clean files make no backup")
    void guardedLoadMakesNoBackupWithoutCorruption(@TempDir Path dir) throws Exception {
        DbWorkspaceIO.LoadOutcome missing = DbWorkspaceIO.loadWorkspaceGuarded(dir.toFile());
        assertThat(missing.workspace()).isEqualTo(DbWorkspaceIO.Workspace.empty());
        assertThat(missing.backup()).isNull();
        assertThat(dir.resolve(DbWorkspaceIO.FILENAME + ".bak")).doesNotExist();

        DbWorkspaceIO.save(dir.toFile(), richWorkspace());
        DbWorkspaceIO.LoadOutcome clean = DbWorkspaceIO.loadWorkspaceGuarded(dir.toFile());
        assertThat(clean.workspace().connections()).hasSize(3);
        assertThat(clean.backup()).isNull();
        assertThat(dir.resolve(DbWorkspaceIO.FILENAME + ".bak")).doesNotExist();
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

    // ---- the workspace schema: history + saved queries ----------------

    private static DbWorkspaceIO.Workspace richWorkspaceState() {
        return new DbWorkspaceIO.Workspace(
                richWorkspace(),
                List.of(
                        new DbWorkspaceIO.HistoryEntry("SELECT * FROM users;", "MySQL", 2000L),
                        new DbWorkspaceIO.HistoryEntry("SELECT 1;", "PostgreSQL", 1000L)),
                List.of(
                        new DbWorkspaceIO.SavedQuery("daily orders",
                                "SELECT * FROM orders WHERE created_at > now();", "PostgreSQL"),
                        new DbWorkspaceIO.SavedQuery("user count",
                                "SELECT COUNT(*) FROM users;", "MySQL")));
    }

    @Test
    @DisplayName("A full workspace — connections, history, saved — round-trips")
    void workspaceRoundTrip() {
        DbWorkspaceIO.Workspace back = DbWorkspaceIO.workspaceFromJson(
                DbWorkspaceIO.toJson(richWorkspaceState()));

        assertThat(back.connections()).hasSize(3);
        assertThat(back.history()).containsExactly(
                new DbWorkspaceIO.HistoryEntry("SELECT * FROM users;", "MySQL", 2000L),
                new DbWorkspaceIO.HistoryEntry("SELECT 1;", "PostgreSQL", 1000L));
        assertThat(back.saved()).containsExactly(
                new DbWorkspaceIO.SavedQuery("daily orders",
                        "SELECT * FROM orders WHERE created_at > now();", "PostgreSQL"),
                new DbWorkspaceIO.SavedQuery("user count",
                        "SELECT COUNT(*) FROM users;", "MySQL"));
    }

    @Test
    @DisplayName("BACKWARD tolerance: a pre-history file (v1.31 shape) loads with empty lists")
    void oldFileLoadsWithEmptyHistoryAndSaved() {
        String v131 = """
                {
                  "version": 1,
                  "connections": [
                    {"id": "a", "name": "old", "engine": "SQLITE", "filePath": "/tmp/x.db"}
                  ]
                }
                """;
        DbWorkspaceIO.Workspace back = DbWorkspaceIO.workspaceFromJson(v131);

        assertThat(back.connections()).hasSize(1);
        assertThat(back.history()).isEmpty();
        assertThat(back.saved()).isEmpty();
    }

    @Test
    @DisplayName("FORWARD tolerance: keys from a newer NMOX are ignored, known keys load")
    void futureKeysIgnored() {
        String future = """
                {
                  "version": 1,
                  "connections": [{"id": "a", "name": "keep", "engine": "SQLITE"}],
                  "history": [{"text": "SELECT 1;", "engine": "SQLite", "at": 5,
                               "futureField": true}],
                  "saved": [{"name": "q", "text": "SELECT 2;", "engine": "SQLite",
                             "tags": ["new"]}],
                  "pinnedDashboards": [{"whatever": "a v1.40 idea"}]
                }
                """;
        DbWorkspaceIO.Workspace back = DbWorkspaceIO.workspaceFromJson(future);

        assertThat(back.connections()).hasSize(1);
        assertThat(back.history()).containsExactly(
                new DbWorkspaceIO.HistoryEntry("SELECT 1;", "SQLite", 5L));
        assertThat(back.saved()).containsExactly(
                new DbWorkspaceIO.SavedQuery("q", "SELECT 2;", "SQLite"));
    }

    @Test
    @DisplayName("The legacy fromJson still reads connections out of a full workspace file")
    void legacyFromJsonReadsWorkspaceFiles() {
        assertThat(DbWorkspaceIO.fromJson(DbWorkspaceIO.toJson(richWorkspaceState())))
                .hasSize(3);
    }

    @Test
    @DisplayName("History is capped at the 50 newest on write AND on load")
    void historyCap() {
        List<DbWorkspaceIO.HistoryEntry> sixty = new java.util.ArrayList<>();
        for (int i = 0; i < 60; i++) {
            // newest first, as the console's history model keeps them
            sixty.add(new DbWorkspaceIO.HistoryEntry("SELECT " + i + ";", "MySQL", 60L - i));
        }
        DbWorkspaceIO.Workspace big = new DbWorkspaceIO.Workspace(List.of(), sixty, List.of());

        DbWorkspaceIO.Workspace back = DbWorkspaceIO.workspaceFromJson(DbWorkspaceIO.toJson(big));

        assertThat(back.history()).hasSize(DbWorkspaceIO.HISTORY_CAP);
        assertThat(back.history().get(0).text()).isEqualTo("SELECT 0;");
        assertThat(back.history().get(49).text()).isEqualTo("SELECT 49;");

        // a hand-fattened file is trimmed on load too
        StringBuilder handEdited = new StringBuilder("{\"version\":1,\"history\":[");
        for (int i = 0; i < 70; i++) {
            handEdited.append(i > 0 ? "," : "")
                    .append("{\"text\":\"SELECT ").append(i).append(";\",\"at\":1}");
        }
        handEdited.append("]}");
        assertThat(DbWorkspaceIO.workspaceFromJson(handEdited.toString()).history())
                .hasSize(DbWorkspaceIO.HISTORY_CAP);
    }

    @Test
    @DisplayName("Saved-query names are unique: the last duplicate wins, in the first's place")
    void savedNamesUnique() {
        DbWorkspaceIO.Workspace withDupes = new DbWorkspaceIO.Workspace(List.of(), List.of(),
                List.of(
                        new DbWorkspaceIO.SavedQuery("report", "SELECT 1;", "MySQL"),
                        new DbWorkspaceIO.SavedQuery("other", "SELECT 2;", "MySQL"),
                        new DbWorkspaceIO.SavedQuery("report", "SELECT 3;", "MySQL")));

        DbWorkspaceIO.Workspace back = DbWorkspaceIO.workspaceFromJson(
                DbWorkspaceIO.toJson(withDupes));

        assertThat(back.saved()).containsExactly(
                new DbWorkspaceIO.SavedQuery("report", "SELECT 3;", "MySQL"),
                new DbWorkspaceIO.SavedQuery("other", "SELECT 2;", "MySQL"));
    }

    @Test
    @DisplayName("Entries missing their essential field are skipped, the rest kept")
    void degenerateEntriesSkipped() {
        String scruffy = """
                {
                  "version": 1,
                  "history": [
                    {"engine": "MySQL", "at": 1},
                    {"text": "", "engine": "MySQL", "at": 2},
                    {"text": "SELECT 1;"}
                  ],
                  "saved": [
                    {"text": "SELECT 2;", "engine": "MySQL"},
                    {"name": "", "text": "SELECT 3;"},
                    {"name": "kept"}
                  ]
                }
                """;
        DbWorkspaceIO.Workspace back = DbWorkspaceIO.workspaceFromJson(scruffy);

        assertThat(back.history()).containsExactly(
                new DbWorkspaceIO.HistoryEntry("SELECT 1;", "", 0L));
        assertThat(back.saved()).containsExactly(
                new DbWorkspaceIO.SavedQuery("kept", "", ""));
    }

    @Test
    @DisplayName("Malformed and blank input load as the empty workspace, never throw")
    void malformedWorkspaceLoadsEmpty() {
        assertThat(DbWorkspaceIO.workspaceFromJson("{ nope ]").connections()).isEmpty();
        assertThat(DbWorkspaceIO.workspaceFromJson(null).history()).isEmpty();
        assertThat(DbWorkspaceIO.workspaceFromJson("").saved()).isEmpty();
    }

    @Test
    @DisplayName("The whole workspace saves to and loads from .nmoxdb.json")
    void workspaceSaveAndLoad(@TempDir Path dir) throws Exception {
        DbWorkspaceIO.save(dir.toFile(), richWorkspaceState());

        DbWorkspaceIO.Workspace back = DbWorkspaceIO.loadWorkspace(dir.toFile());

        assertThat(back.connections()).hasSize(3);
        assertThat(back.history()).hasSize(2);
        assertThat(back.saved()).hasSize(2);
        // a missing file is the empty workspace
        assertThat(DbWorkspaceIO.loadWorkspace(dir.resolve("nowhere").toFile()).connections())
                .isEmpty();
    }

    @Test
    @DisplayName("The connections-only save PRESERVES the file's history and saved queries")
    void connectionsOnlySavePreservesHistory(@TempDir Path dir) throws Exception {
        DbWorkspaceIO.save(dir.toFile(), richWorkspaceState());

        // the legacy call path: UI saves just the connection list
        DbWorkspaceIO.save(dir.toFile(), List.of(richWorkspace().get(0)));

        DbWorkspaceIO.Workspace back = DbWorkspaceIO.loadWorkspace(dir.toFile());
        assertThat(back.connections()).hasSize(1);
        assertThat(back.history()).as("history survives a specs-only save").hasSize(2);
        assertThat(back.saved()).as("saved queries survive a specs-only save").hasSize(2);
    }

    @Test
    @DisplayName("A workspace file with history/saved still never contains a password")
    void workspaceFileStillHasNoPasswords(@TempDir Path dir) throws Exception {
        DbWorkspaceIO.save(dir.toFile(), richWorkspaceState());

        String written = Files.readString(dir.resolve(DbWorkspaceIO.FILENAME),
                StandardCharsets.UTF_8);
        assertThat(written.toLowerCase()).doesNotContain("password");
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
