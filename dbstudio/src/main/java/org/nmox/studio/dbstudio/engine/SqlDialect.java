package org.nmox.studio.dbstudio.engine;

import org.nmox.studio.dbstudio.model.DbEngine;
import org.nmox.studio.dbstudio.model.TableInfo;

/**
 * The ONE place DB Studio knows how to quote a SQL identifier — the
 * MySQL family uses the backtick, every other engine (PostgreSQL,
 * SQLite, and any ANSI engine reached through the Services window)
 * uses the SQL-standard double quote. Extracted from
 * {@code PeekQueries} so the peek query and {@link UpdateBuilder}
 * cannot drift apart; {@link JdbcUrlDialects#identifierQuote} rides
 * the same switch.
 *
 * <p>Quoting doubles an embedded quote character
 * ({@code we"ird} → {@code "we""ird"}) — the standard escape both
 * families honor, so a hostile identifier can never break out of its
 * quotes.
 */
public final class SqlDialect {

    private SqlDialect() {
    }

    /**
     * The identifier-quote character for a modeled engine: the backtick
     * for MySQL/MariaDB, otherwise the SQL-standard double quote.
     * Null-tolerant — an unmodeled engine ({@code null}, as
     * {@link JdbcUrlDialects#engineFor} reports for Derby, Oracle, H2,
     * …) gets the double quote every ANSI engine honors.
     */
    public static String identifierQuote(DbEngine engine) {
        return engine == DbEngine.MYSQL || engine == DbEngine.MARIADB ? "`" : "\"";
    }

    /**
     * One identifier wrapped in the given quote, any embedded quote
     * character doubled.
     */
    public static String quote(String quote, String identifier) {
        String name = identifier == null ? "" : identifier;
        return quote + name.replace(quote, quote + quote) + quote;
    }

    /** One identifier quoted for the given engine's dialect. */
    public static String quote(DbEngine engine, String identifier) {
        return quote(identifierQuote(engine), identifier);
    }

    /**
     * The table reference for a statement: schema-qualified when the
     * table carries a schema, each part quoted with the given quote.
     */
    public static String qualifiedTable(String quote, TableInfo table) {
        String name = quote(quote, table.name());
        String schema = table.schema();
        return (schema == null || schema.isBlank()) ? name : quote(quote, schema) + "." + name;
    }

    /** The table reference quoted for the given engine's dialect. */
    public static String qualifiedTable(DbEngine engine, TableInfo table) {
        return qualifiedTable(identifierQuote(engine), table);
    }
}
