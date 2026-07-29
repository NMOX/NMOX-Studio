package org.nmox.studio.dbstudio.engine;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.model.TableInfo;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JdbcCore's failure-contract edges over a real (SQLite) connection:
 * metadata calls on a dead connection degrade to empty lists plus a
 * log line — the tree renders, the reason lives in open()'s verdict —
 * an unlimited console run really is unlimited, and the cancel hook
 * swallows a refusing driver instead of poisoning the cancel path.
 */
class JdbcCoreEdgesTest {

    private static Connection memory() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite::memory:");
    }

    @Test
    @DisplayName("metadata calls on a closed connection answer empty, never throw")
    void closedConnectionMetadataDegrades() throws Exception {
        Connection connection = memory();
        connection.close();

        assertThat(JdbcCore.listTables(connection, null, null, "dead")).isEmpty();
        assertThat(JdbcCore.columns(connection,
                new TableInfo(null, null, "t", "TABLE"))).isEmpty();
    }

    @Test
    @DisplayName("rowLimit <= 0 means unlimited — every row comes back")
    void zeroRowLimitIsUnlimited() throws Exception {
        try (Connection connection = memory()) {
            JdbcCore.CancelHook hook = new JdbcCore.CancelHook();
            JdbcCore.runStatements(connection, List.of(
                    "CREATE TABLE t (n INTEGER)",
                    "INSERT INTO t VALUES (1), (2), (3)"), 50, hook);

            List<QueryResult> results = JdbcCore.runStatements(connection,
                    List.of("SELECT n FROM t"), 0, hook);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).rows()).hasSize(3);
            assertThat(results.get(0).error()).isNull();
        }
    }

    @Test
    @DisplayName("cancelling after the run — or with nothing in flight — is quiet, not fatal")
    void cancelIsAlwaysSafe() throws Exception {
        JdbcCore.CancelHook hook = new JdbcCore.CancelHook();
        hook.cancel(); // nothing in flight: a plain no-op

        Connection connection = memory();
        try {
            // the hook keeps pointing at the (now closed) last statement after
            // a run finishes; a late cancel must swallow the driver's refusal
            JdbcCore.runStatements(connection, List.of("SELECT 1"), 10, hook);
            hook.cancel();
        } finally {
            connection.close();
        }
        hook.cancel(); // even after the connection died
    }
}
