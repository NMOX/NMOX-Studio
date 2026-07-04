package org.nmox.studio.dbstudio.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the URL-to-dialect inference behind the Services branch: known
 * engines map to their DbEngine, everything else falls back to null +
 * the SQL-standard double quote.
 */
class JdbcUrlDialectsTest {

    @Test
    @DisplayName("Known JDBC URL prefixes map to their engines")
    void knownEngines() {
        assertThat(JdbcUrlDialects.engineFor("jdbc:mysql://db.example:3306/shop"))
                .isEqualTo(DbEngine.MYSQL);
        assertThat(JdbcUrlDialects.engineFor("jdbc:mariadb://localhost/app"))
                .isEqualTo(DbEngine.MARIADB);
        assertThat(JdbcUrlDialects.engineFor("jdbc:postgresql://localhost:5432/app"))
                .isEqualTo(DbEngine.POSTGRES);
        assertThat(JdbcUrlDialects.engineFor("jdbc:sqlite:/tmp/x.db"))
                .isEqualTo(DbEngine.SQLITE);
    }

    @Test
    @DisplayName("Unknown, null and malformed URLs fall back to null")
    void unknownFallsBackToNull() {
        assertThat(JdbcUrlDialects.engineFor("jdbc:derby://localhost:1527/sample")).isNull();
        assertThat(JdbcUrlDialects.engineFor("jdbc:oracle:thin:@//host:1521/svc")).isNull();
        assertThat(JdbcUrlDialects.engineFor("jdbc:h2:mem:test")).isNull();
        assertThat(JdbcUrlDialects.engineFor(null)).isNull();
        assertThat(JdbcUrlDialects.engineFor("")).isNull();
        assertThat(JdbcUrlDialects.engineFor("not a jdbc url")).isNull();
        // prefix must match at the start, not merely appear
        assertThat(JdbcUrlDialects.engineFor("x jdbc:mysql://h/d")).isNull();
    }

    @Test
    @DisplayName("Matching is case-insensitive and trims whitespace")
    void caseInsensitive() {
        assertThat(JdbcUrlDialects.engineFor("JDBC:MySQL://Host/db"))
                .isEqualTo(DbEngine.MYSQL);
        assertThat(JdbcUrlDialects.engineFor("  jdbc:postgresql://h/d  "))
                .isEqualTo(DbEngine.POSTGRES);
    }

    @Test
    @DisplayName("Backtick for the MySQL family, double quote for everything else")
    void identifierQuotes() {
        assertThat(JdbcUrlDialects.identifierQuote("jdbc:mysql://h/d")).isEqualTo("`");
        assertThat(JdbcUrlDialects.identifierQuote("jdbc:mariadb://h/d")).isEqualTo("`");
        assertThat(JdbcUrlDialects.identifierQuote("jdbc:postgresql://h/d")).isEqualTo("\"");
        assertThat(JdbcUrlDialects.identifierQuote("jdbc:sqlite:/tmp/x.db")).isEqualTo("\"");
        // the safe SQL-standard default for engines DB Studio doesn't model
        assertThat(JdbcUrlDialects.identifierQuote("jdbc:derby://h:1527/sample")).isEqualTo("\"");
        assertThat(JdbcUrlDialects.identifierQuote("jdbc:oracle:thin:@//h/s")).isEqualTo("\"");
        assertThat(JdbcUrlDialects.identifierQuote(null)).isEqualTo("\"");
    }
}
