package org.nmox.studio.dbstudio.engine;

import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.model.ColumnInfo;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * The MongoDB backend WITHOUT a server: the failure paths that must
 * come back as friendly error results (parse errors, missing
 * database), and the static seams — connection-string building with
 * credential encoding, reply-to-grid mapping from canned Extended
 * JSON, the one-document shape sample.
 */
class MongoBackendTest {

    private static ConnectionSpec mongoSpec(String database) {
        return new ConnectionSpec("id-mongo", "docs", DbEngine.MONGODB,
                "mongo.example.com", 27017, database, "app", "");
    }

    // ---- console failure paths (no server touched) ------------------

    @Test
    @DisplayName("unparseable console text yields an error result, not a throw — before any connection")
    void parseErrorBecomesErrorResult() {
        MongoBackend backend = new MongoBackend(mongoSpec("appdb"), null);

        List<QueryResult> results = backend.runConsole("{this is not json]", 50);

        assertThat(results).hasSize(1);
        QueryResult result = results.get(0);
        assertThat(result.isError()).isTrue();
        assertThat(result.error()).startsWith("Not a MongoDB command document:");
        assertThat(result.statement()).isEqualTo("{this is not json]");
        assertThat(backend.isOpen()).as("nothing was opened").isFalse();
    }

    @Test
    @DisplayName("a blank database yields the friendly error result — before any connection")
    void blankDatabaseFriendlyError() {
        MongoBackend backend = new MongoBackend(mongoSpec(""), null);

        List<QueryResult> results = backend.runConsole("{\"find\": \"users\"}", 50);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isError()).isTrue();
        assertThat(results.get(0).error())
                .contains("No database set")
                .contains("connection settings");
        assertThat(backend.isOpen()).isFalse();
    }

    @Test
    @DisplayName("open() on a blank database returns the same friendly message")
    void openWithBlankDatabase() {
        assertThat(new MongoBackend(mongoSpec("   "), null).open())
                .contains("No database set");
    }

    @Test
    @DisplayName("blank console text executes nothing")
    void blankTextRunsNothing() {
        MongoBackend backend = new MongoBackend(mongoSpec("appdb"), null);
        assertThat(backend.runConsole(null, 10)).isEmpty();
        assertThat(backend.runConsole("   ", 10)).isEmpty();
    }

    @Test
    @DisplayName("cancel() is a safe no-op and close() when never opened is safe")
    void cancelAndCloseAreSafe() {
        MongoBackend backend = new MongoBackend(mongoSpec("appdb"), null);
        backend.cancel();
        backend.close();
        assertThat(backend.isOpen()).isFalse();
    }

    @Test
    @DisplayName("the constructor refuses non-MongoDB specs")
    void constructorRefusesWrongEngine() {
        ConnectionSpec couch = new ConnectionSpec("id", "n", DbEngine.COUCHDB,
                "h", 5984, "db", "u", "");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MongoBackend(couch, null))
                .withMessageContaining("CouchDB");
    }

    // ---- connection-string seam --------------------------------------

    @Test
    @DisplayName("connection string: credentials are URL-encoded, host:port and timeouts appended")
    void connectionStringEncodesCredentials() {
        String url = MongoBackend.connectionString(
                "user@corp", "p:a/s s@".toCharArray(), "db.example.com", 27017);

        assertThat(url).isEqualTo("mongodb://user%40corp:p%3Aa%2Fs%20s%40@db.example.com:27017"
                + "/?connectTimeoutMS=5000&serverSelectionTimeoutMS=5000");
    }

    @Test
    @DisplayName("connection string: no user means no credentials block at all")
    void connectionStringWithoutUser() {
        assertThat(MongoBackend.connectionString(null, null, "localhost", 27017))
                .startsWith("mongodb://localhost:27017/?");
        assertThat(MongoBackend.connectionString("  ", "x".toCharArray(), "localhost", 27017))
                .doesNotContain("@");
    }

    @Test
    @DisplayName("connection string: a user with no password gets no colon")
    void connectionStringUserWithoutPassword() {
        assertThat(MongoBackend.connectionString("bob", null, "h", 27017))
                .startsWith("mongodb://bob@h:27017");
    }

    // ---- reply mapping seam (canned Extended JSON, no server) --------

    @Test
    @DisplayName("a cursor.firstBatch reply flattens the batch into a grid, _id first")
    void cursorReplyFlattensBatch() {
        Document reply = Document.parse("""
                {"cursor": {"id": 0, "ns": "appdb.users", "firstBatch": [
                    {"_id": {"$oid": "507f1f77bcf86cd799439011"}, "name": "ada", "age": 36},
                    {"_id": {"$oid": "507f1f77bcf86cd799439012"}, "name": "grace"}
                ]}, "ok": 1.0}
                """);

        QueryResult result = MongoBackend.toResult(reply, 50, 7, "{\"find\":\"users\"}");

        assertThat(result.isError()).isFalse();
        assertThat(result.isResultSet()).isTrue();
        assertThat(result.columnNames()).containsExactly("_id", "name", "age");
        assertThat(result.rowCount()).isEqualTo(2);
        assertThat(result.rows().get(0))
                .containsExactly("507f1f77bcf86cd799439011", "ada", "36");
        assertThat(result.rows().get(1))
                .containsExactly("507f1f77bcf86cd799439012", "grace", "");
        assertThat(result.truncated()).isFalse();
        assertThat(result.elapsedMs()).isEqualTo(7);
        assertThat(result.statement()).isEqualTo("{\"find\":\"users\"}");
    }

    @Test
    @DisplayName("rowLimit caps the batch and flags truncated")
    void cursorReplyTruncation() {
        Document reply = Document.parse("""
                {"cursor": {"firstBatch": [
                    {"n": 1}, {"n": 2}, {"n": 3}, {"n": 4}
                ]}, "ok": 1.0}
                """);

        QueryResult capped = MongoBackend.toResult(reply, 2, 0, "cmd");
        assertThat(capped.rowCount()).isEqualTo(2);
        assertThat(capped.rows()).containsExactly(List.of("1"), List.of("2"));
        assertThat(capped.truncated()).isTrue();

        QueryResult exact = MongoBackend.toResult(reply, 4, 0, "cmd");
        assertThat(exact.truncated()).isFalse();
    }

    @Test
    @DisplayName("a non-cursor reply renders as one flattened row (the reply document itself)")
    void plainReplyRendersAsOneRow() {
        Document reply = Document.parse("{\"ns\": \"appdb.users\", \"count\": 12, \"ok\": 1.0}");

        QueryResult result = MongoBackend.toResult(reply, 50, 0, "{\"count\":\"users\"}");

        assertThat(result.isError()).isFalse();
        assertThat(result.columnNames()).containsExactly("ns", "count", "ok");
        assertThat(result.rows()).containsExactly(List.of("appdb.users", "12", "1.0"));
        assertThat(result.truncated()).isFalse();
    }

    @Test
    @DisplayName("a malformed cursor (firstBatch not documents) falls back to the raw-reply row")
    void malformedCursorFallsBack() {
        Document reply = Document.parse("{\"cursor\": {\"firstBatch\": [1, 2, 3]}, \"ok\": 1.0}");

        QueryResult result = MongoBackend.toResult(reply, 50, 0, "cmd");

        assertThat(result.columnNames()).containsExactly("cursor", "ok");
        assertThat(result.rows().get(0).get(0)).contains("firstBatch");
    }

    // ---- shape-sample seam -------------------------------------------

    @Test
    @DisplayName("shapeSample reports one document's fields with BSON type names, _id as the key")
    void shapeSampleReportsBsonTypes() {
        Document doc = Document.parse("""
                {"_id": {"$oid": "507f1f77bcf86cd799439011"},
                 "name": "ada", "age": 36, "score": 3.14, "big": 9999999999,
                 "active": true, "addr": {"city": "Oslo"}, "tags": ["x"],
                 "note": null}
                """);

        List<ColumnInfo> columns = MongoBackend.shapeSample(doc);

        assertThat(columns).extracting(ColumnInfo::name).containsExactly(
                "_id", "name", "age", "score", "big", "active", "addr", "tags", "note");
        assertThat(columns).extracting(ColumnInfo::typeName).containsExactly(
                "objectId", "string", "int", "double", "long", "bool", "object", "array", "null");
        assertThat(columns).allSatisfy(c -> {
            assertThat(c.size()).isZero();
            assertThat(c.nullable()).isTrue();
        });
        assertThat(columns.get(0).primaryKey()).as("_id is the key").isTrue();
        assertThat(columns.stream().filter(ColumnInfo::primaryKey)).hasSize(1);
    }

    @Test
    @DisplayName("shapeSample of an empty collection (null document) reports no columns")
    void shapeSampleEmptyCollection() {
        assertThat(MongoBackend.shapeSample(null)).isEmpty();
    }

    @Test
    @DisplayName("bsonTypeName covers the driver's decoded types, including date and decimal")
    void bsonTypeNames() {
        Document doc = Document.parse("""
                {"when": {"$date": "2026-01-01T00:00:00Z"},
                 "price": {"$numberDecimal": "19.99"},
                 "raw": {"$binary": {"base64": "AAE=", "subType": "00"}}}
                """);
        assertThat(MongoBackend.bsonTypeName(doc.get("when"))).isEqualTo("date");
        assertThat(MongoBackend.bsonTypeName(doc.get("price"))).isEqualTo("decimal");
        assertThat(MongoBackend.bsonTypeName(doc.get("raw"))).isEqualTo("binData");
    }
}
