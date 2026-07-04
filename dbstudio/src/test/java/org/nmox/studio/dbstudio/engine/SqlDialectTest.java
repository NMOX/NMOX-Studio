package org.nmox.studio.dbstudio.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.nmox.studio.dbstudio.model.TableInfo;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shared quoting seam — PeekQueries and UpdateBuilder both ride
 * these rules, so they are pinned once here.
 */
class SqlDialectTest {

    @Test
    @DisplayName("The MySQL family quotes with backticks, everyone else with double quotes")
    void identifierQuotePerEngine() {
        assertThat(SqlDialect.identifierQuote(DbEngine.MYSQL)).isEqualTo("`");
        assertThat(SqlDialect.identifierQuote(DbEngine.MARIADB)).isEqualTo("`");
        assertThat(SqlDialect.identifierQuote(DbEngine.POSTGRES)).isEqualTo("\"");
        assertThat(SqlDialect.identifierQuote(DbEngine.SQLITE)).isEqualTo("\"");
        // an unmodeled engine (Services-window Derby/Oracle/…) gets the SQL standard
        assertThat(SqlDialect.identifierQuote(null)).isEqualTo("\"");
    }

    @Test
    @DisplayName("Quoting doubles an embedded quote character — no breakout")
    void quoteDoubling() {
        assertThat(SqlDialect.quote("\"", "we\"ird")).isEqualTo("\"we\"\"ird\"");
        assertThat(SqlDialect.quote("`", "we`ird")).isEqualTo("`we``ird`");
        assertThat(SqlDialect.quote(DbEngine.POSTGRES, "users")).isEqualTo("\"users\"");
        assertThat(SqlDialect.quote(DbEngine.MYSQL, "users")).isEqualTo("`users`");
        assertThat(SqlDialect.quote("\"", null)).isEqualTo("\"\"");
    }

    @Test
    @DisplayName("Table references qualify with the schema only when one exists")
    void qualifiedTable() {
        assertThat(SqlDialect.qualifiedTable(DbEngine.MYSQL,
                new TableInfo("", "shop", "orders", "TABLE"))).isEqualTo("`shop`.`orders`");
        assertThat(SqlDialect.qualifiedTable(DbEngine.POSTGRES,
                new TableInfo("", "public", "users", "TABLE"))).isEqualTo("\"public\".\"users\"");
        assertThat(SqlDialect.qualifiedTable(DbEngine.SQLITE,
                new TableInfo("", "", "beers", "TABLE"))).isEqualTo("\"beers\"");
        assertThat(SqlDialect.qualifiedTable(DbEngine.SQLITE,
                new TableInfo("", null, "beers", "TABLE"))).isEqualTo("\"beers\"");
        assertThat(SqlDialect.qualifiedTable("\"",
                new TableInfo("", "APP", "INVOICES", "TABLE"))).isEqualTo("\"APP\".\"INVOICES\"");
    }
}
