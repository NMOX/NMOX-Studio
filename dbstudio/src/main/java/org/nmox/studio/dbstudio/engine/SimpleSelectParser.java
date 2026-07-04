package org.nmox.studio.dbstudio.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Decides when a result grid is safe to edit in place: a statement is
 * a "simple single-table select" exactly when every grid row maps to
 * one addressable row of one table. This gates
 * {@link UpdateBuilder}-driven editing, so the bias is deliberate:
 * <b>false negatives are fine, false positives are bugs</b> — whenever
 * this parser is unsure, it returns {@link Optional#empty()} and the
 * grid simply stays read-only.
 *
 * <p><b>Accepted</b>: {@code SELECT ... FROM <table>} where the table
 * is one possibly-quoted, possibly-qualified identifier
 * ({@code users}, {@code shop.orders}, {@code "public"."users"},
 * {@code `db`.`t`}), optionally aliased ({@code FROM users u},
 * {@code FROM users AS u} — the bare table is still returned), with
 * optional {@code WHERE} / {@code ORDER BY} / {@code LIMIT} /
 * {@code OFFSET} / {@code FETCH} / {@code FOR} clauses (subqueries
 * INSIDE a WHERE are fine — they only filter, rows still map to table
 * rows). Leading comments and whitespace and a trailing {@code ;} are
 * tolerated; keywords are case-insensitive.
 *
 * <p><b>Rejected</b> (grid rows would not map to table rows, or we
 * cannot be sure they do): joins (comma lists or any JOIN keyword),
 * subqueries or parentheses in FROM, {@code UNION}/{@code EXCEPT}/
 * {@code INTERSECT}, CTEs ({@code WITH}), {@code GROUP BY} /
 * {@code HAVING}, {@code DISTINCT}, aggregate or window functions in
 * the select list (they collapse or synthesize rows — {@code SUM},
 * {@code COUNT}, …, anything {@code OVER}), scalar subqueries in the
 * select list, non-SELECT statements, and anything after the trailing
 * semicolon.
 *
 * <p>The returned name is "as written, unquoted": quoting stripped,
 * qualification kept ({@code "public"."users"} → {@code public.users}),
 * letter case untouched.
 */
public final class SimpleSelectParser {

    /** Aggregates whose presence in the select list collapses rows. */
    private static final Set<String> AGGREGATES = Set.of(
            "COUNT", "SUM", "AVG", "MIN", "MAX", "TOTAL",
            "GROUP_CONCAT", "STRING_AGG", "ARRAY_AGG", "LISTAGG",
            "JSON_AGG", "JSONB_AGG", "JSON_ARRAYAGG", "JSON_OBJECTAGG",
            "BIT_AND", "BIT_OR", "BOOL_AND", "BOOL_OR", "EVERY",
            "VARIANCE", "VAR_POP", "VAR_SAMP",
            "STDDEV", "STDDEV_POP", "STDDEV_SAMP",
            "MEDIAN", "PERCENTILE_CONT", "PERCENTILE_DISC");

    /** Words that may legally FOLLOW the table reference (and its alias). */
    private static final Set<String> CLAUSE_STARTERS = Set.of(
            "WHERE", "ORDER", "LIMIT", "OFFSET", "FETCH", "FOR");

    /** Words that can never be a table alias — seeing one there rejects. */
    private static final Set<String> NOT_AN_ALIAS = Set.of(
            "WHERE", "ORDER", "LIMIT", "OFFSET", "FETCH", "FOR",
            "GROUP", "HAVING", "WINDOW", "UNION", "EXCEPT", "INTERSECT",
            "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "CROSS", "NATURAL",
            "STRAIGHT_JOIN", "ON", "USING", "AS");

    /** Depth-0 words after the clauses begin that mean "not a simple select". */
    private static final Set<String> LATE_REJECTS = Set.of(
            "GROUP", "HAVING", "WINDOW", "UNION", "EXCEPT", "INTERSECT",
            "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "CROSS", "NATURAL",
            "STRAIGHT_JOIN");

    private enum Type { WORD, QUOTED_IDENT, STRING, PUNCT }

    private record Token(Type type, String text) {

        boolean isWord(String upper) {
            return type == Type.WORD && text.toUpperCase(Locale.ROOT).equals(upper);
        }

        String upper() {
            return type == Type.WORD ? text.toUpperCase(Locale.ROOT) : "";
        }

        boolean isPunct(char c) {
            return type == Type.PUNCT && text.length() == 1 && text.charAt(0) == c;
        }
    }

    private SimpleSelectParser() {
    }

    /**
     * The bare table name when the statement is a simple single-table
     * select (see class javadoc for the exact rule), otherwise empty.
     * Null-safe; when in doubt, empty.
     */
    public static Optional<String> singleTable(String statement) {
        List<Token> tokens;
        try {
            tokens = tokenize(statement);
        } catch (RuntimeException unterminated) {
            return Optional.empty(); // broken quoting — certainly not editable
        }
        if (tokens.isEmpty() || !tokens.get(0).isWord("SELECT")) {
            return Optional.empty();
        }

        int from = topLevelFrom(tokens);
        if (from < 0) {
            return Optional.empty(); // SELECT without FROM — no table to edit
        }
        if (!selectListIsPlain(tokens.subList(1, from))) {
            return Optional.empty();
        }

        int i = from + 1;
        StringBuilder table = new StringBuilder();
        while (true) {
            if (i >= tokens.size()) {
                return Optional.empty();
            }
            Token part = tokens.get(i);
            if (part.type == Type.QUOTED_IDENT) {
                table.append(part.text());
            } else if (part.type == Type.WORD && !NOT_AN_ALIAS.contains(part.upper())) {
                table.append(part.text());
            } else {
                return Optional.empty(); // FROM ( … or FROM WHERE — not a table
            }
            i++;
            if (i < tokens.size() && tokens.get(i).isPunct('.')) {
                table.append('.');
                i++;
                continue;
            }
            break;
        }

        // optional alias: AS x | bare x | quoted x
        if (i < tokens.size()) {
            Token next = tokens.get(i);
            if (next.isWord("AS")) {
                i++;
                if (i >= tokens.size() || (tokens.get(i).type != Type.WORD
                        && tokens.get(i).type != Type.QUOTED_IDENT)) {
                    return Optional.empty();
                }
                i++;
            } else if (next.type == Type.QUOTED_IDENT
                    || (next.type == Type.WORD && !NOT_AN_ALIAS.contains(next.upper()))) {
                i++;
            }
        }

        // what follows must be nothing, or an accepted clause
        if (i < tokens.size()) {
            Token next = tokens.get(i);
            if (next.type != Type.WORD || !CLAUSE_STARTERS.contains(next.upper())) {
                return Optional.empty(); // comma list, JOIN, UNION, GROUP BY, …
            }
        }

        // scan the clauses at paren depth 0 for anything that reshapes rows
        int depth = 0;
        for (int j = i; j < tokens.size(); j++) {
            Token t = tokens.get(j);
            if (t.isPunct('(')) {
                depth++;
            } else if (t.isPunct(')')) {
                depth--;
            } else if (depth == 0 && t.type == Type.WORD && LATE_REJECTS.contains(t.upper())) {
                return Optional.empty();
            }
        }

        return Optional.of(table.toString());
    }

    /**
     * True when the select list guarantees row-per-table-row: no
     * DISTINCT, no aggregate calls (any depth), no window functions
     * (OVER), no scalar subqueries (SELECT).
     */
    private static boolean selectListIsPlain(List<Token> selectList) {
        for (int i = 0; i < selectList.size(); i++) {
            Token t = selectList.get(i);
            if (t.type != Type.WORD) {
                continue;
            }
            String upper = t.upper();
            if (upper.equals("DISTINCT") || upper.equals("SELECT") || upper.equals("OVER")) {
                return false;
            }
            if (AGGREGATES.contains(upper) && i + 1 < selectList.size()
                    && selectList.get(i + 1).isPunct('(')) {
                return false;
            }
        }
        return true;
    }

    /**
     * Index of the depth-0 FROM keyword, or {@code -1}. Also rejects
     * (returns {@code -1} via caller) nothing itself — pure lookup.
     */
    private static int topLevelFrom(List<Token> tokens) {
        int depth = 0;
        for (int i = 1; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.isPunct('(')) {
                depth++;
            } else if (t.isPunct(')')) {
                depth--;
            } else if (depth == 0 && t.isWord("FROM")) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Comment-dropping, quote-respecting tokenizer. A trailing {@code ;}
     * ends the statement; any meaningful token after it makes the whole
     * input "not one statement" (throws, mapped to empty by the caller).
     */
    private static List<Token> tokenize(String statement) {
        List<Token> tokens = new ArrayList<>();
        if (statement == null) {
            return tokens;
        }
        int i = 0;
        int n = statement.length();
        boolean terminated = false;
        while (i < n) {
            char c = statement.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '-' && i + 1 < n && statement.charAt(i + 1) == '-') {
                i = lineEnd(statement, i);
                continue;
            }
            if (c == '#') {
                i = lineEnd(statement, i);
                continue;
            }
            if (c == '/' && i + 1 < n && statement.charAt(i + 1) == '*') {
                int close = statement.indexOf("*/", i + 2);
                i = close < 0 ? n : close + 2;
                continue;
            }
            if (terminated) {
                throw new IllegalStateException("content after the terminating semicolon");
            }
            if (c == ';') {
                terminated = true;
                i++;
                continue;
            }
            if (c == '\'') {
                i = consumeQuoted(statement, i, '\'');
                tokens.add(new Token(Type.STRING, ""));
                continue;
            }
            if (c == '"' || c == '`') {
                int start = i;
                i = consumeQuoted(statement, i, c);
                String inner = statement.substring(start + 1, i - 1)
                        .replace("" + c + c, "" + c);
                tokens.add(new Token(Type.QUOTED_IDENT, inner));
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < n && (Character.isLetterOrDigit(statement.charAt(i))
                        || statement.charAt(i) == '_' || statement.charAt(i) == '$')) {
                    i++;
                }
                tokens.add(new Token(Type.WORD, statement.substring(start, i)));
                continue;
            }
            tokens.add(new Token(Type.PUNCT, String.valueOf(c)));
            i++;
        }
        return tokens;
    }

    /** Past the closing quote, honoring the doubled-quote escape. */
    private static int consumeQuoted(String s, int open, char quote) {
        int i = open + 1;
        int n = s.length();
        while (i < n) {
            if (s.charAt(i) == quote) {
                if (i + 1 < n && s.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        throw new IllegalStateException("unterminated quote");
    }

    private static int lineEnd(String s, int from) {
        int nl = s.indexOf('\n', from);
        return nl < 0 ? s.length() : nl + 1;
    }
}
