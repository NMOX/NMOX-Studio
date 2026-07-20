package org.nmox.studio.dbstudio.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the per-engine EXPLAIN wrapping and the explainable gate.
 */
class ExplainQueriesTest {

    @Test
    @DisplayName("MySQL, MariaDB and PostgreSQL wrap with plain EXPLAIN")
    void plainExplainEngines() {
        assertThat(ExplainQueries.explain(DbEngine.MYSQL, "SELECT * FROM t"))
                .isEqualTo("EXPLAIN SELECT * FROM t;");
        assertThat(ExplainQueries.explain(DbEngine.MARIADB, "SELECT * FROM t"))
                .isEqualTo("EXPLAIN SELECT * FROM t;");
        assertThat(ExplainQueries.explain(DbEngine.POSTGRES, "SELECT * FROM t"))
                .isEqualTo("EXPLAIN SELECT * FROM t;");
    }

    @Test
    @DisplayName("SQLite wraps with EXPLAIN QUERY PLAN — bare EXPLAIN dumps opcodes there")
    void sqliteQueryPlan() {
        assertThat(ExplainQueries.explain(DbEngine.SQLITE, "SELECT * FROM t"))
                .isEqualTo("EXPLAIN QUERY PLAN SELECT * FROM t;");
    }

    @Test
    @DisplayName("A trailing semicolon (and stray whitespace) is absorbed, never doubled")
    void trailingSemicolonAbsorbed() {
        assertThat(ExplainQueries.explain(DbEngine.POSTGRES, "SELECT 1;"))
                .isEqualTo("EXPLAIN SELECT 1;");
        assertThat(ExplainQueries.explain(DbEngine.POSTGRES, "  SELECT 1 ;  "))
                .isEqualTo("EXPLAIN SELECT 1;");
        assertThat(ExplainQueries.explain(DbEngine.SQLITE, "SELECT 1;\n"))
                .isEqualTo("EXPLAIN QUERY PLAN SELECT 1;");
    }

    @Test
    @DisplayName("Document engines have no EXPLAIN — explain() refuses loudly")
    void documentEnginesRefused() {
        assertThatThrownBy(() -> ExplainQueries.explain(DbEngine.MONGODB, "SELECT 1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MongoDB")
                .hasMessageContaining("document engine");
        assertThatThrownBy(() -> ExplainQueries.explain(DbEngine.COUCHDB, "SELECT 1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CouchDB");
    }

    @Test
    @DisplayName("explainable: SELECT and WITH statements on SQL engines")
    void explainableAccepts() {
        assertThat(ExplainQueries.explainable(DbEngine.MYSQL, "SELECT 1")).isTrue();
        assertThat(ExplainQueries.explainable(DbEngine.POSTGRES,
                "WITH x AS (SELECT 1) SELECT * FROM x")).isTrue();
        assertThat(ExplainQueries.explainable(DbEngine.SQLITE, "select * from t")).isTrue();
    }

    @Test
    @DisplayName("explainable skips leading whitespace and comments before deciding")
    void explainableSkipsComments() {
        assertThat(ExplainQueries.explainable(DbEngine.POSTGRES,
                "  -- what does the planner think?\nSELECT * FROM t")).isTrue();
        assertThat(ExplainQueries.explainable(DbEngine.MYSQL,
                "/* hint */ SELECT * FROM t")).isTrue();
        assertThat(ExplainQueries.explainable(DbEngine.MYSQL,
                "# mysql style\nWITH x AS (SELECT 1) SELECT 1")).isTrue();
    }

    @Test
    @DisplayName("explainable rejects non-queries, blanks, and every document engine")
    void explainableRejects() {
        assertThat(ExplainQueries.explainable(DbEngine.MYSQL, "UPDATE t SET a = 1")).isFalse();
        assertThat(ExplainQueries.explainable(DbEngine.MYSQL, "INSERT INTO t VALUES (1)"))
                .isFalse();
        assertThat(ExplainQueries.explainable(DbEngine.MYSQL, "DROP TABLE t")).isFalse();
        assertThat(ExplainQueries.explainable(DbEngine.MYSQL, "")).isFalse();
        assertThat(ExplainQueries.explainable(DbEngine.MYSQL, null)).isFalse();
        assertThat(ExplainQueries.explainable(DbEngine.MYSQL, "-- only a comment")).isFalse();
        // document engines are false even for SELECT-shaped text
        assertThat(ExplainQueries.explainable(DbEngine.MONGODB, "SELECT 1")).isFalse();
        assertThat(ExplainQueries.explainable(DbEngine.COUCHDB, "SELECT 1")).isFalse();
        assertThat(ExplainQueries.explainable(null, "SELECT 1")).isFalse();
    }

    @Test
    @DisplayName("Multi-statement text is NOT explainable — a trailing DELETE must never execute")
    void multiStatementRefused() {
        // explain() prefixes EXPLAIN to the whole text and re-splits; a
        // trailing write would run for real under a read-only button.
        assertThat(ExplainQueries.explainable(DbEngine.MYSQL,
                "SELECT * FROM t; DELETE FROM t;")).isFalse();
        assertThat(ExplainQueries.explainable(DbEngine.POSTGRES,
                "SELECT 1; SELECT 2")).isFalse();
        // a single statement, with or without a trailing ;, still passes
        assertThat(ExplainQueries.explainable(DbEngine.MYSQL, "SELECT * FROM t;")).isTrue();
        assertThat(ExplainQueries.explainable(DbEngine.MYSQL, "SELECT * FROM t")).isTrue();
    }
}
