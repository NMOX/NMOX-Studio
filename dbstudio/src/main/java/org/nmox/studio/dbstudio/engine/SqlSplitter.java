package org.nmox.studio.dbstudio.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a SQL script into individual statements on {@code ;} — while
 * actually reading the SQL, so a semicolon inside a string, a quoted
 * identifier, or a comment never splits. Pure text-in/list-out; no
 * JDBC anywhere.
 *
 * <p>Understood syntax:
 * <ul>
 *   <li>single-quoted strings with the standard {@code ''} escape
 *       ({@code 'It''s'})</li>
 *   <li>double-quoted identifiers ({@code "weird;name"})</li>
 *   <li>backtick identifiers ({@code `weird;name`}, MySQL)</li>
 *   <li>{@code -- line comments} and {@code # line comments} (the
 *       MySQL idiom; note PostgreSQL's rare {@code #} bitwise operator
 *       would be misread as a comment — an accepted console trade-off)</li>
 *   <li>{@code /* block comments *}{@code /} (non-nesting)</li>
 * </ul>
 *
 * <p>Deliberately NOT understood (documented limitations, not bugs):
 * backslash escapes inside strings (MySQL-only; treating {@code \'} as
 * an escape would mis-split standards-conforming PostgreSQL text where
 * a backslash is literal), and PostgreSQL dollar-quoted bodies
 * ({@code $$...$$}) — send a function body as a single statement
 * without internal semicolons, or run it from psql.
 *
 * <p>Statements keep their embedded comments verbatim (they can be
 * optimizer hints); statements that are empty or comment-only are
 * dropped. A trailing statement without {@code ;} is included.
 */
public final class SqlSplitter {

    private enum State { DEFAULT, SINGLE_QUOTE, DOUBLE_QUOTE, BACKTICK, LINE_COMMENT, BLOCK_COMMENT }

    private SqlSplitter() {
    }

    /** Splits a script into executable statements; null/blank yields an empty list. */
    public static List<String> split(String script) {
        List<String> statements = new ArrayList<>();
        if (script == null || script.isEmpty()) {
            return statements;
        }
        StringBuilder current = new StringBuilder();
        boolean meaningful = false; // saw any char outside comments and whitespace
        State state = State.DEFAULT;
        int i = 0;
        int n = script.length();
        while (i < n) {
            char c = script.charAt(i);
            switch (state) {
                case DEFAULT -> {
                    if (c == ';') {
                        flush(statements, current, meaningful);
                        meaningful = false;
                        i++;
                    } else if (c == '\'') {
                        state = State.SINGLE_QUOTE;
                        current.append(c);
                        meaningful = true;
                        i++;
                    } else if (c == '"') {
                        state = State.DOUBLE_QUOTE;
                        current.append(c);
                        meaningful = true;
                        i++;
                    } else if (c == '`') {
                        state = State.BACKTICK;
                        current.append(c);
                        meaningful = true;
                        i++;
                    } else if (c == '#') {
                        state = State.LINE_COMMENT;
                        current.append(c);
                        i++;
                    } else if (c == '-' && i + 1 < n && script.charAt(i + 1) == '-') {
                        state = State.LINE_COMMENT;
                        current.append("--");
                        i += 2;
                    } else if (c == '/' && i + 1 < n && script.charAt(i + 1) == '*') {
                        state = State.BLOCK_COMMENT;
                        current.append("/*");
                        i += 2;
                    } else {
                        if (!Character.isWhitespace(c)) {
                            meaningful = true;
                        }
                        current.append(c);
                        i++;
                    }
                }
                case SINGLE_QUOTE -> {
                    current.append(c);
                    if (c == '\'') {
                        if (i + 1 < n && script.charAt(i + 1) == '\'') {
                            current.append('\''); // '' escape: still inside the string
                            i += 2;
                            continue;
                        }
                        state = State.DEFAULT;
                    }
                    i++;
                }
                case DOUBLE_QUOTE -> {
                    current.append(c);
                    if (c == '"') {
                        state = State.DEFAULT;
                    }
                    i++;
                }
                case BACKTICK -> {
                    current.append(c);
                    if (c == '`') {
                        state = State.DEFAULT;
                    }
                    i++;
                }
                case LINE_COMMENT -> {
                    current.append(c);
                    if (c == '\n') {
                        state = State.DEFAULT;
                    }
                    i++;
                }
                case BLOCK_COMMENT -> {
                    if (c == '*' && i + 1 < n && script.charAt(i + 1) == '/') {
                        current.append("*/");
                        state = State.DEFAULT;
                        i += 2;
                    } else {
                        current.append(c);
                        i++;
                    }
                }
            }
        }
        flush(statements, current, meaningful);
        return statements;
    }

    private static void flush(List<String> statements, StringBuilder current, boolean meaningful) {
        String statement = current.toString().trim();
        current.setLength(0);
        if (meaningful && !statement.isEmpty()) {
            statements.add(statement);
        }
    }
}
