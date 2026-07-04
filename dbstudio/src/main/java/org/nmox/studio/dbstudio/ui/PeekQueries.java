package org.nmox.studio.dbstudio.ui;

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
                    ? "{\"find\": \"" + table.name() + "\", \"limit\": " + limit + "}"
                    : "{\"selector\": {}, \"limit\": " + limit + "}";
        };
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
        String q = (engine == DbEngine.MYSQL || engine == DbEngine.MARIADB) ? "`" : "\"";
        String name = q + table.name() + q;
        String schema = table.schema();
        return (schema == null || schema.isBlank()) ? name : q + schema + q + "." + name;
    }
}
