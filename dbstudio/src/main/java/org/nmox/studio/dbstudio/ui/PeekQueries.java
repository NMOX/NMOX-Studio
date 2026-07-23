package org.nmox.studio.dbstudio.ui;

import org.nmox.studio.dbstudio.engine.SqlDialect;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.nmox.studio.dbstudio.model.TableInfo;

/**
 * The console text behind "double-click a table to peek at its data" — the
 * gesture every database tool owes its user. Pure and per-engine: SQL engines
 * get a quoted {@code SELECT * … LIMIT n}, Mongo gets a find command, Couch
 * gets a Mango selector. Kept UI-free so the shapes are pinned by tests.
 */
final class PeekQueries {

    private PeekQueries() {
    }

    /** Console text that previews the container's data, engine-appropriate. */
    static String consoleTextFor(DbEngine engine, TableInfo table, int limit) {
        return switch (engine.kind()) {
            case SQL -> "SELECT * FROM " + qualified(engine, table) + " LIMIT " + limit + ";";
            case DOCUMENT -> engine == DbEngine.MONGODB
                    // quote the name properly: a collection with a " or \ in
                    // it would otherwise yield malformed auto-run JSON (ledger
                    // 54 L4). JSONObject.quote returns the value WITH its
                    // surrounding quotes.
                    ? "{\"find\": " + org.json.JSONObject.quote(table.name())
                            + ", \"limit\": " + limit + "}"
                    : "{\"selector\": {}, \"limit\": " + limit + "}";
        };
    }

    /**
     * Peek text for a JDBC connection whose dialect DB Studio doesn't
     * model — a Services-window database like Derby or Oracle. The
     * identifier quote comes from
     * {@code JdbcUrlDialects.identifierQuote}; the row cap rides the
     * SQL-standard {@code FETCH FIRST n ROWS ONLY} (Derby, Oracle 12c+,
     * H2, DB2 — {@code LIMIT} is a MySQL/PostgreSQL/SQLite extension
     * those engines reject).
     */
    static String consoleTextFor(String identifierQuote, TableInfo table, int limit) {
        return "SELECT * FROM " + qualified(identifierQuote, table)
                + " FETCH FIRST " + limit + " ROWS ONLY;";
    }

    /**
     * Whether running the peek right now can hit the intended container.
     * SQL and Mongo target the table/collection by name inside the console
     * text; CouchDB's console always queries {@code spec.database}, so a
     * peek on a DIFFERENT database node needs the spec re-aimed first.
     */
    static boolean runnableAgainst(ConnectionSpec spec, TableInfo table) {
        if (spec.engine() != DbEngine.COUCHDB) {
            return true;
        }
        return table.name().equals(spec.database());
    }

    /** schema-qualified and identifier-quoted per engine dialect. */
    private static String qualified(DbEngine engine, TableInfo table) {
        return SqlDialect.qualifiedTable(engine, table);
    }

    /** schema-qualified with an explicit identifier quote. */
    private static String qualified(String q, TableInfo table) {
        return SqlDialect.qualifiedTable(q, table);
    }
}
