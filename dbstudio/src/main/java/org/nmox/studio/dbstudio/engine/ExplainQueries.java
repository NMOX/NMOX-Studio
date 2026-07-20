package org.nmox.studio.dbstudio.engine;

import java.util.Locale;
import org.nmox.studio.dbstudio.model.DbEngine;

/**
 * The text behind the console's "Explain" gesture: wraps a statement
 * in the engine's plan-inspection form. MySQL/MariaDB and PostgreSQL
 * take {@code EXPLAIN <stmt>}; SQLite takes
 * {@code EXPLAIN QUERY PLAN <stmt>} (bare {@code EXPLAIN} there dumps
 * VDBE opcodes — bytecode, not a plan). Pure text-in/text-out; the
 * console runs the result like any other statement.
 */
public final class ExplainQueries {

    private ExplainQueries() {
    }

    /**
     * The statement wrapped in the engine's EXPLAIN form. A trailing
     * {@code ;} (plus trailing whitespace) is stripped before wrapping
     * and one {@code ;} is re-appended, so the output is always exactly
     * one terminated statement.
     *
     * @throws IllegalArgumentException for document engines — they have
     *         no EXPLAIN; gate with {@link #explainable} first
     */
    public static String explain(DbEngine engine, String statement) {
        String bare = stripTerminator(statement == null ? "" : statement);
        return switch (engine) {
            case MYSQL, MARIADB, POSTGRES -> "EXPLAIN " + bare + ";";
            case SQLITE -> "EXPLAIN QUERY PLAN " + bare + ";";
            case MONGODB, COUCHDB -> throw new IllegalArgumentException(
                    engine.displayName() + " is a document engine — EXPLAIN applies to"
                    + " SQL engines only.");
        };
    }

    /**
     * Whether the Explain gesture applies: a SQL engine, and the
     * statement — after leading whitespace and comments — starts with
     * {@code SELECT} or {@code WITH} (case-insensitive). Document
     * engines are always {@code false}.
     */
    public static boolean explainable(DbEngine engine, String statement) {
        if (engine == null || engine.kind() != DbEngine.Kind.SQL) {
            return false;
        }
        // EXPLAIN must be read-only. explain() prefixes EXPLAIN to the
        // WHOLE console text, which is then re-split and run — so a
        // trailing "; DELETE FROM t" would EXECUTE while the user
        // believes the button only inspects a plan. Refuse anything but
        // a single statement (the plan-inspection gesture is for one
        // query at a time anyway).
        if (SqlSplitter.split(statement == null ? "" : statement).size() != 1) {
            return false;
        }
        String word = firstWord(statement == null ? "" : statement).toUpperCase(Locale.ROOT);
        return word.equals("SELECT") || word.equals("WITH");
    }

    /** Trailing whitespace and one terminating {@code ;} removed. */
    private static String stripTerminator(String statement) {
        String trimmed = statement.strip();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).stripTrailing();
        }
        return trimmed;
    }

    /** The first keyword, skipping whitespace and {@code --}/{@code #}/block comments. */
    private static String firstWord(String statement) {
        int i = 0;
        int n = statement.length();
        while (i < n) {
            char c = statement.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '-' && i + 1 < n && statement.charAt(i + 1) == '-') {
                i = skipLine(statement, i);
            } else if (c == '#') {
                i = skipLine(statement, i);
            } else if (c == '/' && i + 1 < n && statement.charAt(i + 1) == '*') {
                int close = statement.indexOf("*/", i + 2);
                i = close < 0 ? n : close + 2;
            } else {
                break;
            }
        }
        int start = i;
        while (i < n && (Character.isLetterOrDigit(statement.charAt(i))
                || statement.charAt(i) == '_')) {
            i++;
        }
        return statement.substring(start, i);
    }

    private static int skipLine(String s, int from) {
        int nl = s.indexOf('\n', from);
        return nl < 0 ? s.length() : nl + 1;
    }
}
