package org.nmox.studio.dbstudio.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The editability gate. The bias under test: false negatives (a grid
 * that could have been editable stays read-only) are acceptable; false
 * positives (a grid wrongly declared editable) are BUGS — every reject
 * case here is a row-mapping hazard that must never slip through.
 */
class SimpleSelectParserTest {

    // ---- accepted shapes ---------------------------------------------

    @Test
    @DisplayName("A plain single-table select returns the table")
    void plainSelect() {
        assertThat(SimpleSelectParser.singleTable("SELECT * FROM users")).contains("users");
        assertThat(SimpleSelectParser.singleTable("SELECT id, name FROM users")).contains("users");
    }

    @Test
    @DisplayName("Keywords are case-insensitive; the table comes back as written")
    void caseInsensitiveKeywords() {
        assertThat(SimpleSelectParser.singleTable("select * from Users")).contains("Users");
        assertThat(SimpleSelectParser.singleTable("SeLeCt id FrOm ORDERS")).contains("ORDERS");
    }

    @Test
    @DisplayName("Qualified names come back qualified, quoting stripped")
    void qualifiedAndQuoted() {
        assertThat(SimpleSelectParser.singleTable("SELECT * FROM shop.orders"))
                .contains("shop.orders");
        assertThat(SimpleSelectParser.singleTable("SELECT * FROM \"public\".\"users\""))
                .contains("public.users");
        assertThat(SimpleSelectParser.singleTable("SELECT * FROM `db`.`t`")).contains("db.t");
        assertThat(SimpleSelectParser.singleTable("SELECT * FROM \"weird name\""))
                .contains("weird name");
    }

    @Test
    @DisplayName("Aliases are accepted; the bare table is still returned")
    void aliases() {
        assertThat(SimpleSelectParser.singleTable("SELECT u.id FROM users u")).contains("users");
        assertThat(SimpleSelectParser.singleTable("SELECT u.id FROM users AS u")).contains("users");
        assertThat(SimpleSelectParser.singleTable("SELECT * FROM users \"u\"")).contains("users");
        assertThat(SimpleSelectParser.singleTable(
                "SELECT u.id FROM shop.users AS u WHERE u.id > 1")).contains("shop.users");
    }

    @Test
    @DisplayName("WHERE, ORDER BY, LIMIT, OFFSET, FETCH and FOR clauses ride along")
    void trailingClauses() {
        assertThat(SimpleSelectParser.singleTable(
                "SELECT * FROM t WHERE x > 1 ORDER BY a, b LIMIT 10 OFFSET 5")).contains("t");
        assertThat(SimpleSelectParser.singleTable(
                "SELECT * FROM t FETCH FIRST 5 ROWS ONLY")).contains("t");
        assertThat(SimpleSelectParser.singleTable(
                "SELECT * FROM t WHERE x = 1 FOR UPDATE")).contains("t");
    }

    @Test
    @DisplayName("Leading comments and whitespace and a trailing semicolon are tolerated")
    void commentsAndTerminator() {
        assertThat(SimpleSelectParser.singleTable("  -- peek\nSELECT * FROM t;")).contains("t");
        assertThat(SimpleSelectParser.singleTable("/* generated */ SELECT * FROM t")).contains("t");
        assertThat(SimpleSelectParser.singleTable("# mysql comment\nSELECT * FROM t")).contains("t");
        assertThat(SimpleSelectParser.singleTable("SELECT * FROM t -- trailing note"))
                .contains("t");
    }

    @Test
    @DisplayName("A subquery INSIDE the WHERE only filters — rows still map, accepted")
    void whereSubqueryIsFine() {
        assertThat(SimpleSelectParser.singleTable(
                "SELECT * FROM t WHERE id IN (SELECT id FROM u)")).contains("t");
        assertThat(SimpleSelectParser.singleTable(
                "SELECT * FROM t WHERE EXISTS (SELECT 1 FROM u JOIN v ON u.id = v.id)"))
                .contains("t");
    }

    @Test
    @DisplayName("Non-aggregate functions in the select list keep the row mapping — accepted")
    void scalarFunctionsAreFine() {
        assertThat(SimpleSelectParser.singleTable("SELECT UPPER(name), id FROM t")).contains("t");
        assertThat(SimpleSelectParser.singleTable("SELECT COALESCE(name, 'x') FROM t"))
                .contains("t");
    }

    @Test
    @DisplayName("Columns merely NAMED min or max (no call parens) are not aggregates")
    void aggregateNamesAsColumns() {
        assertThat(SimpleSelectParser.singleTable("SELECT min, max FROM t")).contains("t");
    }

    @Test
    @DisplayName("Keywords inside string literals do not confuse the scan")
    void keywordsInStrings() {
        assertThat(SimpleSelectParser.singleTable(
                "SELECT * FROM t WHERE note = 'GROUP BY UNION JOIN'")).contains("t");
    }

    // ---- rejected shapes: rows would not map to table rows ------------

    @Test
    @DisplayName("REJECT joins — comma lists and every JOIN spelling")
    void rejectsJoins() {
        assertThat(SimpleSelectParser.singleTable("SELECT * FROM a, b")).isEmpty();
        assertThat(SimpleSelectParser.singleTable("SELECT * FROM a JOIN b ON a.id = b.id"))
                .isEmpty();
        assertThat(SimpleSelectParser.singleTable("SELECT * FROM a LEFT JOIN b ON a.id = b.id"))
                .isEmpty();
        assertThat(SimpleSelectParser.singleTable("SELECT * FROM a INNER JOIN b ON a.x = b.x"))
                .isEmpty();
        assertThat(SimpleSelectParser.singleTable("SELECT * FROM a NATURAL JOIN b")).isEmpty();
        assertThat(SimpleSelectParser.singleTable("SELECT * FROM a CROSS JOIN b")).isEmpty();
        assertThat(SimpleSelectParser.singleTable("SELECT * FROM a u, b")).isEmpty();
    }

    @Test
    @DisplayName("REJECT a subquery (or any parenthesis) in FROM")
    void rejectsSubqueryFrom() {
        assertThat(SimpleSelectParser.singleTable("SELECT * FROM (SELECT 1) x")).isEmpty();
        assertThat(SimpleSelectParser.singleTable(
                "SELECT * FROM (SELECT * FROM users) u")).isEmpty();
    }

    @Test
    @DisplayName("REJECT set operations: UNION, EXCEPT, INTERSECT")
    void rejectsSetOperations() {
        assertThat(SimpleSelectParser.singleTable(
                "SELECT a FROM t UNION SELECT b FROM u")).isEmpty();
        assertThat(SimpleSelectParser.singleTable(
                "SELECT a FROM t EXCEPT SELECT b FROM u")).isEmpty();
        assertThat(SimpleSelectParser.singleTable(
                "SELECT a FROM t INTERSECT SELECT b FROM u")).isEmpty();
    }

    @Test
    @DisplayName("REJECT GROUP BY and HAVING — grid rows are groups, not table rows")
    void rejectsGrouping() {
        assertThat(SimpleSelectParser.singleTable("SELECT a FROM t GROUP BY a")).isEmpty();
        assertThat(SimpleSelectParser.singleTable(
                "SELECT a FROM t WHERE x = 1 GROUP BY a HAVING a > 1")).isEmpty();
    }

    @Test
    @DisplayName("REJECT DISTINCT — deduplicated rows have no single source row")
    void rejectsDistinct() {
        assertThat(SimpleSelectParser.singleTable("SELECT DISTINCT a FROM t")).isEmpty();
    }

    @Test
    @DisplayName("REJECT aggregates in the select list, at any nesting depth")
    void rejectsAggregates() {
        assertThat(SimpleSelectParser.singleTable("SELECT COUNT(*) FROM t")).isEmpty();
        assertThat(SimpleSelectParser.singleTable("SELECT SUM(x) FROM t")).isEmpty();
        assertThat(SimpleSelectParser.singleTable("SELECT MAX(id), name FROM t")).isEmpty();
        assertThat(SimpleSelectParser.singleTable(
                "SELECT COALESCE(SUM(x), 0) FROM t")).isEmpty();
    }

    @Test
    @DisplayName("REJECT window functions (OVER) and scalar subqueries in the select list")
    void rejectsWindowsAndScalarSubqueries() {
        assertThat(SimpleSelectParser.singleTable(
                "SELECT ROW_NUMBER() OVER (ORDER BY x), id FROM t")).isEmpty();
        assertThat(SimpleSelectParser.singleTable(
                "SELECT (SELECT MAX(id) FROM u), name FROM t")).isEmpty();
    }

    @Test
    @DisplayName("REJECT CTEs — the FROM names a CTE, not a table")
    void rejectsWith() {
        assertThat(SimpleSelectParser.singleTable(
                "WITH x AS (SELECT 1) SELECT * FROM x")).isEmpty();
    }

    @Test
    @DisplayName("REJECT non-SELECT statements")
    void rejectsNonSelects() {
        assertThat(SimpleSelectParser.singleTable("UPDATE users SET a = 1")).isEmpty();
        assertThat(SimpleSelectParser.singleTable("INSERT INTO users VALUES (1)")).isEmpty();
        assertThat(SimpleSelectParser.singleTable("DELETE FROM users")).isEmpty();
        assertThat(SimpleSelectParser.singleTable("EXPLAIN SELECT * FROM t")).isEmpty();
    }

    @Test
    @DisplayName("REJECT a FROM-less select — there is no table to edit")
    void rejectsNoFrom() {
        assertThat(SimpleSelectParser.singleTable("SELECT 1")).isEmpty();
        assertThat(SimpleSelectParser.singleTable("SELECT 1 + 2")).isEmpty();
    }

    @Test
    @DisplayName("REJECT multiple statements — one grid, one statement")
    void rejectsMultipleStatements() {
        assertThat(SimpleSelectParser.singleTable(
                "SELECT * FROM t; SELECT * FROM u")).isEmpty();
    }

    @Test
    @DisplayName("REJECT degenerate input: null, blank, broken quoting")
    void rejectsDegenerateInput() {
        assertThat(SimpleSelectParser.singleTable(null)).isEmpty();
        assertThat(SimpleSelectParser.singleTable("")).isEmpty();
        assertThat(SimpleSelectParser.singleTable("   \n  ")).isEmpty();
        assertThat(SimpleSelectParser.singleTable("SELECT * FROM \"users")).isEmpty();
        assertThat(SimpleSelectParser.singleTable("SELECT * FROM 'users'")).isEmpty();
    }
}
