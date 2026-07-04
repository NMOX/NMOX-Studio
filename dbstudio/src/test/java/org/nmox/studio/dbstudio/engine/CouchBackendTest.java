package org.nmox.studio.dbstudio.engine;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.model.ColumnInfo;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * The CouchDB backend WITHOUT a server: every response parser is a
 * static String-input seam fed canned JSON (the DigitalOceanClient
 * idiom), plus the Mango body builder (selector wrapping, limit
 * injection), auth-header building, and the console failure paths
 * that must come back as friendly error results.
 */
class CouchBackendTest {

    private static ConnectionSpec couchSpec(String database) {
        return new ConnectionSpec("id-couch", "couch", DbEngine.COUCHDB,
                "couch.example.com", 5984, database, "admin", "");
    }

    // ---- parse seams --------------------------------------------------

    @Test
    @DisplayName("parseWelcome recognizes CouchDB's root document with its version")
    void parseWelcome() {
        assertThat(CouchBackend.parseWelcome(
                "{\"couchdb\":\"Welcome\",\"version\":\"3.4.2\",\"vendor\":{\"name\":\"Apache\"}}"))
                .isEqualTo("CouchDB 3.4.2");
        assertThat(CouchBackend.parseWelcome("{\"couchdb\":\"Welcome\"}"))
                .isEqualTo("CouchDB");
    }

    @Test
    @DisplayName("parseWelcome rejects non-CouchDB and non-JSON bodies with null, never a throw")
    void parseWelcomeRejectsImpostors() {
        assertThat(CouchBackend.parseWelcome("{\"message\":\"nginx default page\"}")).isNull();
        assertThat(CouchBackend.parseWelcome("<html>hello</html>")).isNull();
        assertThat(CouchBackend.parseWelcome("{broken")).isNull();
        assertThat(CouchBackend.parseWelcome(null)).isNull();
    }

    @Test
    @DisplayName("parseAllDbs turns the JSON array into names in order")
    void parseAllDbs() {
        assertThat(CouchBackend.parseAllDbs("[\"_users\",\"_replicator\",\"shop\",\"crm\"]"))
                .containsExactly("_users", "_replicator", "shop", "crm");
        assertThat(CouchBackend.parseAllDbs("[]")).isEmpty();
    }

    @Test
    @DisplayName("parseFindDocs extracts the docs array of a _find reply")
    void parseFindDocs() {
        List<JSONObject> docs = CouchBackend.parseFindDocs("""
                {"docs": [
                    {"_id": "a1", "name": "ada"},
                    {"_id": "b2", "name": "grace"}
                ], "bookmark": "g1AAAA"}
                """);

        assertThat(docs).hasSize(2);
        assertThat(docs.get(0).getString("_id")).isEqualTo("a1");
        assertThat(docs.get(1).getString("name")).isEqualTo("grace");
    }

    @Test
    @DisplayName("parseFindDocs surfaces a CouchDB error body as a throw with the server's reason")
    void parseFindDocsError() {
        assertThatIllegalStateException()
                .isThrownBy(() -> CouchBackend.parseFindDocs(
                        "{\"error\":\"not_found\",\"reason\":\"Database does not exist.\"}"))
                .withMessage("not_found: Database does not exist.");
    }

    @Test
    @DisplayName("parseFindDocs of a reply without docs yields an empty list")
    void parseFindDocsNoDocs() {
        assertThat(CouchBackend.parseFindDocs("{\"bookmark\": \"nil\"}")).isEmpty();
    }

    // ---- Mango body building -------------------------------------------

    @Test
    @DisplayName("a bare selector is wrapped into {selector: ...}")
    void bareSelectorWrapped() {
        JSONObject body = new JSONObject(CouchBackend.mangoBody("{\"name\": \"ada\"}", 50));

        assertThat(body.getJSONObject("selector").getString("name")).isEqualTo("ada");
        assertThat(body.getInt("limit")).isEqualTo(51);
    }

    @Test
    @DisplayName("a full Mango body with selector passes through, fields and all")
    void fullMangoBodyPassesThrough() {
        JSONObject body = new JSONObject(CouchBackend.mangoBody(
                "{\"selector\": {\"age\": {\"$gt\": 30}}, \"fields\": [\"name\"]}", 50));

        assertThat(body.getJSONObject("selector").getJSONObject("age").getInt("$gt")).isEqualTo(30);
        assertThat(body.getJSONArray("fields").getString(0)).isEqualTo("name");
        assertThat(body.getInt("limit")).as("injected as rowLimit + 1 probe").isEqualTo(51);
    }

    @Test
    @DisplayName("a user-set limit is respected — never overwritten")
    void userLimitRespected() {
        JSONObject body = new JSONObject(CouchBackend.mangoBody(
                "{\"selector\": {}, \"limit\": 7}", 50));

        assertThat(body.getInt("limit")).isEqualTo(7);
    }

    @Test
    @DisplayName("rowLimit <= 0 injects no limit at all")
    void noLimitInjectionWhenUnlimited() {
        JSONObject body = new JSONObject(CouchBackend.mangoBody("{\"selector\": {}}", 0));

        assertThat(body.has("limit")).isFalse();
    }

    @Test
    @DisplayName("malformed console text throws a JSONException for the caller to wrap")
    void malformedMangoThrows() {
        assertThatExceptionOfType(JSONException.class)
                .isThrownBy(() -> CouchBackend.mangoBody("{oops", 10));
    }

    // ---- auth header -----------------------------------------------------

    @Test
    @DisplayName("basicAuth builds the standard header; blank user means no header")
    void basicAuthHeader() {
        String header = CouchBackend.basicAuth("admin", "s3cret".toCharArray());

        assertThat(header).startsWith("Basic ");
        String decoded = new String(Base64.getDecoder().decode(header.substring(6)),
                StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo("admin:s3cret");

        assertThat(CouchBackend.basicAuth(null, "x".toCharArray())).isNull();
        assertThat(CouchBackend.basicAuth("  ", "x".toCharArray())).isNull();
        String noPassword = CouchBackend.basicAuth("admin", null);
        assertThat(new String(Base64.getDecoder().decode(noPassword.substring(6)),
                StandardCharsets.UTF_8)).isEqualTo("admin:");
    }

    // ---- console failure paths and conveniences (no server touched) ------

    @Test
    @DisplayName("a blank database yields the friendly error result pointing at _all_dbs")
    void blankDatabaseFriendlyError() {
        CouchBackend backend = new CouchBackend(couchSpec(""), null);

        List<QueryResult> results = backend.runConsole("{\"selector\": {}}", 50);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isError()).isTrue();
        assertThat(results.get(0).error())
                .contains("No database set")
                .contains("_all_dbs");
    }

    @Test
    @DisplayName("malformed console text yields an error result, not a throw")
    void malformedTextBecomesErrorResult() {
        CouchBackend backend = new CouchBackend(couchSpec("shop"), null);

        List<QueryResult> results = backend.runConsole("{oops", 50);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isError()).isTrue();
        assertThat(results.get(0).error()).startsWith("Not a Mango query");
    }

    @Test
    @DisplayName("blank console text executes nothing")
    void blankTextRunsNothing() {
        CouchBackend backend = new CouchBackend(couchSpec("shop"), null);
        assertThat(backend.runConsole(null, 10)).isEmpty();
        assertThat(backend.runConsole("  ", 10)).isEmpty();
    }

    @Test
    @DisplayName("the _all_dbs convenience grid is one database column, in server order")
    void allDbsConvenienceGrid() {
        QueryResult result = CouchBackend.allDbsResult(List.of("shop", "crm"), 3, "_all_dbs");

        assertThat(result.isError()).isFalse();
        assertThat(result.isResultSet()).isTrue();
        assertThat(result.columnNames()).containsExactly("database");
        assertThat(result.rows()).containsExactly(List.of("shop"), List.of("crm"));
        assertThat(result.elapsedMs()).isEqualTo(3);
        assertThat(result.statement()).isEqualTo("_all_dbs");
    }

    @Test
    @DisplayName("open/isOpen/close reflect the stateless-HTTP contract without a server")
    void statelessLifecycle() {
        CouchBackend backend = new CouchBackend(couchSpec("shop"), null);
        assertThat(backend.isOpen()).isFalse();
        backend.close(); // close before open is safe
        assertThat(backend.isOpen()).isFalse();
        backend.cancel(); // documented no-op
    }

    @Test
    @DisplayName("the constructor refuses non-CouchDB specs")
    void constructorRefusesWrongEngine() {
        ConnectionSpec mongo = new ConnectionSpec("id", "n", DbEngine.MONGODB,
                "h", 27017, "db", "u", "");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CouchBackend(mongo, null))
                .withMessageContaining("MongoDB");
    }

    // ---- shape sample -----------------------------------------------------

    @Test
    @DisplayName("shapeSample reports the first doc's fields with JSON type names, _id as the key")
    void shapeSampleReportsJsonTypes() {
        List<JSONObject> docs = CouchBackend.parseFindDocs("""
                {"docs": [{
                    "_id": "a1", "_rev": "1-abc", "name": "ada", "age": 36,
                    "active": true, "addr": {"city": "Oslo"}, "tags": ["x"], "note": null
                }]}
                """);

        List<ColumnInfo> columns = CouchBackend.shapeSample(docs);

        assertThat(columns).extracting(ColumnInfo::name).containsExactlyInAnyOrder(
                "_id", "_rev", "name", "age", "active", "addr", "tags", "note");
        ColumnInfo id = columns.stream().filter(c -> c.name().equals("_id")).findFirst().orElseThrow();
        assertThat(id.typeName()).isEqualTo("string");
        assertThat(id.primaryKey()).isTrue();
        assertThat(columns.stream().filter(ColumnInfo::primaryKey)).hasSize(1);
        assertThat(typeOf(columns, "age")).isEqualTo("number");
        assertThat(typeOf(columns, "active")).isEqualTo("boolean");
        assertThat(typeOf(columns, "addr")).isEqualTo("object");
        assertThat(typeOf(columns, "tags")).isEqualTo("array");
        assertThat(typeOf(columns, "note")).isEqualTo("null");
        assertThat(columns).allSatisfy(c -> {
            assertThat(c.size()).isZero();
            assertThat(c.nullable()).isTrue();
        });
    }

    @Test
    @DisplayName("shapeSample of an empty database reports no columns")
    void shapeSampleEmpty() {
        assertThat(CouchBackend.shapeSample(List.of())).isEmpty();
        assertThat(CouchBackend.shapeSample(null)).isEmpty();
    }

    private static String typeOf(List<ColumnInfo> columns, String name) {
        return columns.stream().filter(c -> c.name().equals(name))
                .findFirst().orElseThrow().typeName();
    }
}
