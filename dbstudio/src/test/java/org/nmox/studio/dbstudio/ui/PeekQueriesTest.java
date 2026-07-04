package org.nmox.studio.dbstudio.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.nmox.studio.dbstudio.model.TableInfo;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the double-click-peek console text per engine — the shapes behind
 * "click a table, see its data".
 */
class PeekQueriesTest {

    private static TableInfo table(String schema, String name) {
        return new TableInfo(null, schema, name, "TABLE");
    }

    @Test
    @DisplayName("SQL engines get a quoted SELECT with the dialect's identifier quote")
    void sqlDialectQuoting() {
        assertThat(PeekQueries.consoleTextFor(DbEngine.MYSQL, table(null, "users"), 200))
                .isEqualTo("SELECT * FROM `users` LIMIT 200;");
        assertThat(PeekQueries.consoleTextFor(DbEngine.MARIADB, table("shop", "orders"), 50))
                .isEqualTo("SELECT * FROM `shop`.`orders` LIMIT 50;");
        assertThat(PeekQueries.consoleTextFor(DbEngine.POSTGRES, table("public", "users"), 10))
                .isEqualTo("SELECT * FROM \"public\".\"users\" LIMIT 10;");
        assertThat(PeekQueries.consoleTextFor(DbEngine.SQLITE, table("", "beers"), 5))
                .isEqualTo("SELECT * FROM \"beers\" LIMIT 5;");
    }

    @Test
    @DisplayName("Mongo peeks with a find command, Couch with a Mango selector")
    void documentShapes() {
        assertThat(PeekQueries.consoleTextFor(DbEngine.MONGODB,
                new TableInfo("", "appdb", "users", "COLLECTION"), 25))
                .isEqualTo("{\"find\": \"users\", \"limit\": 25}");
        assertThat(PeekQueries.consoleTextFor(DbEngine.COUCHDB,
                new TableInfo("", "", "invoices", "DATABASE"), 25))
                .isEqualTo("{\"selector\": {}, \"limit\": 25}");
    }

    @Test
    @DisplayName("The explicit-quote path speaks SQL standard: quoted identifiers + FETCH FIRST")
    void explicitQuotePath() {
        // Derby through the Services window: double-quote identifiers, and the
        // row cap must be FETCH FIRST — Derby rejects the LIMIT extension
        assertThat(PeekQueries.consoleTextFor("\"", table("APP", "INVOICES"), 200))
                .isEqualTo("SELECT * FROM \"APP\".\"INVOICES\" FETCH FIRST 200 ROWS ONLY;");
        assertThat(PeekQueries.consoleTextFor("\"", table(null, "T"), 5))
                .isEqualTo("SELECT * FROM \"T\" FETCH FIRST 5 ROWS ONLY;");
        assertThat(PeekQueries.consoleTextFor("\"", table("", "T"), 5))
                .isEqualTo("SELECT * FROM \"T\" FETCH FIRST 5 ROWS ONLY;");
        // the quote is the caller's choice (JdbcUrlDialects decides)
        assertThat(PeekQueries.consoleTextFor("`", table("shop", "orders"), 10))
                .isEqualTo("SELECT * FROM `shop`.`orders` FETCH FIRST 10 ROWS ONLY;");
    }

    @Test
    @DisplayName("Couch peek is only runnable when the spec is aimed at that database")
    void couchAimGuard() {
        ConnectionSpec aimed = new ConnectionSpec("id1", "couch", DbEngine.COUCHDB,
                "localhost", 5984, "invoices", "admin", null);
        assertThat(PeekQueries.runnableAgainst(aimed,
                new TableInfo("", "", "invoices", "DATABASE"))).isTrue();
        assertThat(PeekQueries.runnableAgainst(aimed,
                new TableInfo("", "", "receipts", "DATABASE"))).isFalse();
        // every other engine targets the container inside the console text
        ConnectionSpec sqlite = new ConnectionSpec("id2", "db", DbEngine.SQLITE,
                "", 0, "", "", "/tmp/x.db");
        assertThat(PeekQueries.runnableAgainst(sqlite, table(null, "any"))).isTrue();
    }
}
