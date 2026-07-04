package org.nmox.studio.dbstudio.engine;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.dbstudio.model.ColumnInfo;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.nmox.studio.dbstudio.model.TableInfo;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The end-to-end engine test, made REAL by the bundled SQLite driver: a
 * genuine database file in a temp dir, genuine DDL/DML/queries through
 * the exact code path the UI will call — no mocks anywhere.
 */
class DbClientSqliteTest {

    private static ConnectionSpec sqliteSpec(Path dbFile) {
        return new ConnectionSpec(UUID.randomUUID().toString(), "test db",
                DbEngine.SQLITE, "", -1, "", "", dbFile.toString());
    }

    @Test
    @DisplayName("open/isOpen/close lifecycle behaves and never throws")
    void lifecycle(@TempDir Path dir) {
        DbClient client = new DbClient(sqliteSpec(dir.resolve("life.db")), null);

        assertThat(client.isOpen()).isFalse();
        assertThat(client.open()).as("open on a fresh temp file").isNull();
        assertThat(client.isOpen()).isTrue();
        assertThat(client.open()).as("re-open is a no-op success").isNull();
        client.close();
        assertThat(client.isOpen()).isFalse();
        client.close(); // double close is safe
        assertThat(client.isOpen()).isFalse();
    }

    @Test
    @DisplayName("A CREATE + INSERTs + SELECT script reports update counts, columns and rows")
    void createInsertSelect(@TempDir Path dir) {
        try (DbClient client = new DbClient(sqliteSpec(dir.resolve("crud.db")), null)) {
            List<QueryResult> results = client.runScript("""
                    CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT NOT NULL, note TEXT);
                    INSERT INTO users (name, note) VALUES ('ada', 'first');
                    INSERT INTO users (name, note) VALUES ('grace', NULL);
                    SELECT id, name, note FROM users ORDER BY id;
                    """, 100);

            assertThat(results).hasSize(4);

            QueryResult create = results.get(0);
            assertThat(create.error()).isNull();
            assertThat(create.updateCount()).isZero();
            assertThat(create.isResultSet()).isFalse();

            assertThat(results.get(1).updateCount()).isEqualTo(1);
            assertThat(results.get(2).updateCount()).isEqualTo(1);

            QueryResult select = results.get(3);
            assertThat(select.error()).isNull();
            assertThat(select.isResultSet()).isTrue();
            assertThat(select.updateCount()).isEqualTo(-1);
            assertThat(select.columnNames()).containsExactly("id", "name", "note");
            assertThat(select.rowCount()).isEqualTo(2);
            assertThat(select.rows()).containsExactly(
                    List.of("1", "ada", "first"),
                    List.of("2", "grace", "NULL")); // SQL NULL stringifies as "NULL"
            assertThat(select.truncated()).isFalse();
            assertThat(select.statement()).startsWith("SELECT id, name, note");

            for (QueryResult r : results) {
                assertThat(r.elapsedMs()).isGreaterThanOrEqualTo(0);
            }
        }
    }

    @Test
    @DisplayName("rowLimit truncates: 20 rows with limit 5 fetches 5 and flags truncated")
    void rowLimitTruncation(@TempDir Path dir) {
        try (DbClient client = new DbClient(sqliteSpec(dir.resolve("limit.db")), null)) {
            StringBuilder script = new StringBuilder("CREATE TABLE n (v INTEGER);");
            for (int i = 1; i <= 20; i++) {
                script.append("INSERT INTO n VALUES (").append(i).append(");");
            }
            script.append("SELECT v FROM n ORDER BY v;");

            List<QueryResult> results = client.runScript(script.toString(), 5);
            QueryResult select = results.get(results.size() - 1);

            assertThat(select.error()).isNull();
            assertThat(select.rowCount()).isEqualTo(5);
            assertThat(select.rows()).hasSize(5);
            assertThat(select.rows().get(4)).containsExactly("5");
            assertThat(select.truncated()).isTrue();

            // and an exact-fit result is NOT flagged truncated
            QueryResult exact = client.runScript("SELECT v FROM n WHERE v <= 5;", 5).get(0);
            assertThat(exact.rowCount()).isEqualTo(5);
            assertThat(exact.truncated()).isFalse();
        }
    }

    @Test
    @DisplayName("An error mid-script lands in its own result and the rest still runs")
    void errorMidScriptContinues(@TempDir Path dir) {
        try (DbClient client = new DbClient(sqliteSpec(dir.resolve("err.db")), null)) {
            List<QueryResult> results = client.runScript("""
                    CREATE TABLE a (x INTEGER);
                    INSERT INTO no_such_table VALUES (1);
                    INSERT INTO a VALUES (42);
                    SELECT x FROM a;
                    """, 100);

            assertThat(results).hasSize(4);
            assertThat(results.get(0).error()).isNull();

            QueryResult failed = results.get(1);
            assertThat(failed.isError()).isTrue();
            assertThat(failed.error()).contains("no_such_table");
            assertThat(failed.statement()).contains("INSERT INTO no_such_table");

            assertThat(results.get(2).error()).as("the statement AFTER the failure ran").isNull();
            assertThat(results.get(2).updateCount()).isEqualTo(1);
            assertThat(results.get(3).rows()).containsExactly(List.of("42"));
        }
    }

    @Test
    @DisplayName("listTables sees created tables and views with their JDBC types")
    void listTablesSeesTablesAndViews(@TempDir Path dir) {
        try (DbClient client = new DbClient(sqliteSpec(dir.resolve("meta.db")), null)) {
            client.runScript("""
                    CREATE TABLE invoices (id INTEGER PRIMARY KEY, total REAL);
                    CREATE VIEW big_invoices AS SELECT * FROM invoices WHERE total > 100;
                    """, 10);

            List<TableInfo> tables = client.listTables();

            assertThat(tables).extracting(TableInfo::name)
                    .contains("invoices", "big_invoices");
            TableInfo table = tables.stream().filter(t -> t.name().equals("invoices")).findFirst().orElseThrow();
            TableInfo view = tables.stream().filter(t -> t.name().equals("big_invoices")).findFirst().orElseThrow();
            assertThat(table.type()).isEqualTo("TABLE");
            assertThat(table.isView()).isFalse();
            assertThat(view.type()).isEqualTo("VIEW");
            assertThat(view.isView()).isTrue();
        }
    }

    @Test
    @DisplayName("columns() reports names, types, nullability and the primary key")
    void columnsReportTypesAndPrimaryKey(@TempDir Path dir) {
        try (DbClient client = new DbClient(sqliteSpec(dir.resolve("cols.db")), null)) {
            client.runScript(
                    "CREATE TABLE people (id INTEGER PRIMARY KEY, name TEXT NOT NULL, age INTEGER);", 10);
            TableInfo people = client.listTables().stream()
                    .filter(t -> t.name().equals("people")).findFirst().orElseThrow();

            List<ColumnInfo> columns = client.columns(people);

            assertThat(columns).extracting(ColumnInfo::name)
                    .containsExactly("id", "name", "age");
            ColumnInfo id = columns.get(0);
            ColumnInfo name = columns.get(1);
            ColumnInfo age = columns.get(2);

            assertThat(id.typeName()).isEqualTo("INTEGER");
            assertThat(id.primaryKey()).isTrue();
            assertThat(name.typeName()).isEqualTo("TEXT");
            assertThat(name.nullable()).as("NOT NULL column").isFalse();
            assertThat(name.primaryKey()).isFalse();
            assertThat(age.nullable()).isTrue();
        }
    }

    @Test
    @DisplayName("test() returns null for a good spec and a human message for a bad path")
    void testProbe(@TempDir Path dir) {
        assertThat(new DbClient(sqliteSpec(dir.resolve("good.db")), null).test()).isNull();

        ConnectionSpec bad = sqliteSpec(dir.resolve("no/such/subdir/bad.db"));
        String error = new DbClient(bad, null).test();
        assertThat(error).as("a human-readable reason, not an exception").isNotBlank();
    }

    @Test
    @DisplayName("runScript auto-opens a fresh client; an unopenable spec yields one error result")
    void runScriptAutoOpensAndSurfacesOpenFailure(@TempDir Path dir) {
        // fresh client, no explicit open()
        try (DbClient client = new DbClient(sqliteSpec(dir.resolve("auto.db")), null)) {
            List<QueryResult> results = client.runScript("SELECT 1 AS one;", 10);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).columnNames()).containsExactly("one");
            assertThat(client.isOpen()).isTrue();
        }

        // unopenable database: the open failure is the (single) result
        DbClient broken = new DbClient(sqliteSpec(dir.resolve("no/such/subdir/x.db")), null);
        List<QueryResult> results = broken.runScript("SELECT 1; SELECT 2;", 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).isError()).isTrue();
        assertThat(results.get(0).error()).startsWith("Could not open connection:");
    }

    @Test
    @DisplayName("An empty or comment-only script executes nothing")
    void emptyScriptRunsNothing(@TempDir Path dir) {
        try (DbClient client = new DbClient(sqliteSpec(dir.resolve("empty.db")), null)) {
            assertThat(client.runScript("", 10)).isEmpty();
            assertThat(client.runScript("-- nothing to see\n/* move along */", 10)).isEmpty();
        }
    }

    @Test
    @DisplayName("cancel() with nothing in flight is a harmless no-op")
    void cancelIdleIsNoOp(@TempDir Path dir) {
        DbClient client = new DbClient(sqliteSpec(dir.resolve("cancel.db")), null);
        client.cancel(); // before open
        client.open();
        client.cancel(); // idle but open
        client.close();
        client.cancel(); // after close
        assertThat(client.isOpen()).isFalse();
    }
}
