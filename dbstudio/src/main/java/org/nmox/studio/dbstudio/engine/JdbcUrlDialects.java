package org.nmox.studio.dbstudio.engine;

import java.util.Locale;
import org.nmox.studio.dbstudio.model.DbEngine;

/**
 * Pure dialect inference from a JDBC URL — for connections DB Studio
 * did not create. The NetBeans Database Explorer (the Services window)
 * hands {@link ServicesBackend} a URL and a live connection, nothing
 * more; these two lookups recover what DB Studio's dialect-aware
 * surfaces (peek queries, identifier quoting) need from the URL alone.
 *
 * <p>Deliberately best-effort: an engine DB Studio has no dialect for
 * (Derby, Oracle, H2, DB2, …) maps to {@code null} and the SQL-standard
 * double-quote — the safe defaults every ANSI engine honors.
 */
public final class JdbcUrlDialects {

    private JdbcUrlDialects() {
    }

    /**
     * The DB Studio engine a JDBC URL belongs to: the MySQL family,
     * PostgreSQL, or SQLite — or {@code null} for any other engine
     * (callers fall back to SQL-standard behavior). Case-insensitive,
     * null-safe.
     */
    public static DbEngine engineFor(String jdbcUrl) {
        String url = normalized(jdbcUrl);
        if (url.startsWith("jdbc:mysql:")) {
            return DbEngine.MYSQL;
        }
        if (url.startsWith("jdbc:mariadb:")) {
            return DbEngine.MARIADB;
        }
        if (url.startsWith("jdbc:postgresql:")) {
            return DbEngine.POSTGRES;
        }
        if (url.startsWith("jdbc:sqlite:")) {
            return DbEngine.SQLITE;
        }
        return null;
    }

    /**
     * The identifier-quote character for a JDBC URL: the backtick for
     * the MySQL family, otherwise the double quote — the SQL standard,
     * honored by PostgreSQL, SQLite, Derby, Oracle, H2 and every other
     * ANSI engine, so it is the safe default for URLs
     * {@link #engineFor} cannot place.
     */
    public static String identifierQuote(String jdbcUrl) {
        return SqlDialect.identifierQuote(engineFor(jdbcUrl));
    }

    private static String normalized(String jdbcUrl) {
        return jdbcUrl == null ? "" : jdbcUrl.trim().toLowerCase(Locale.ROOT);
    }
}
