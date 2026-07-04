package org.nmox.studio.dbstudio.engine;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.dbstudio.model.ColumnInfo;
import org.nmox.studio.dbstudio.model.TableInfo;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the extraction seam directly: JdbcCore working on a plain
 * {@code java.sql.Connection} it did not open — exactly the shape
 * {@link ServicesBackend} hands it (a connection owned by someone
 * else). The full behavioral surface stays pinned by
 * {@link DbClientSqliteTest}, which now exercises the same code through
 * DbClient.
 */
class JdbcCoreTest {

    @Test
    @DisplayName("Script, tables and columns all work on a borrowed Connection")
    void coreWorksOnABorrowedConnection(@TempDir Path dir) throws SQLException {
        try (Connection borrowed = DriverManager.getConnection(
                "jdbc:sqlite:" + dir.resolve("core.db"))) {
            JdbcCore.CancelHook hook = new JdbcCore.CancelHook();
            hook.cancel(); // idle cancel is a harmless no-op

            List<QueryResult> results = JdbcCore.runStatements(borrowed,
                    SqlSplitter.split("""
                            CREATE TABLE books (id INTEGER PRIMARY KEY, title TEXT NOT NULL);
                            INSERT INTO books (title) VALUES ('dune');
                            INSERT INTO no_such_table VALUES (1);
                            SELECT id, title FROM books;
                            """), 10, hook);

            assertThat(results).hasSize(4);
            assertThat(results.get(0).error()).isNull();
            assertThat(results.get(1).updateCount()).isEqualTo(1);
            assertThat(results.get(2).isError()).as("error-and-continue").isTrue();
            QueryResult select = results.get(3);
            assertThat(select.columnNames()).containsExactly("id", "title");
            assertThat(select.rows()).containsExactly(List.of("1", "dune"));

            List<TableInfo> tables = JdbcCore.listTables(borrowed, null, null, "borrowed");
            assertThat(tables).extracting(TableInfo::name).contains("books");

            TableInfo books = tables.stream()
                    .filter(t -> t.name().equals("books")).findFirst().orElseThrow();
            List<ColumnInfo> columns = JdbcCore.columns(borrowed, books);
            assertThat(columns).extracting(ColumnInfo::name).containsExactly("id", "title");
            assertThat(columns.get(0).primaryKey()).isTrue();
            assertThat(columns.get(1).nullable()).isFalse();

            // JdbcCore never closed the connection it was lent
            assertThat(borrowed.isClosed()).isFalse();
        }
    }
}
