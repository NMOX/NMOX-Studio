package org.nmox.studio.dbstudio.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.nmox.studio.dbstudio.model.ColumnInfo;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.nmox.studio.dbstudio.model.TableInfo;

/**
 * The correctness heart of in-grid editing: turns "the user changed
 * these cells of this row" into ONE primary-key-scoped {@code UPDATE}
 * statement — or refuses loudly. Pure and total: same inputs, same
 * statement; anything unsafe or ambiguous throws
 * {@link IllegalArgumentException} with a message written for the
 * status bar, never a silently wrong WHERE clause.
 *
 * <p><b>Safety contract</b> — the builder refuses when:
 * <ul>
 *   <li>the engine is a document engine (no SQL to build);</li>
 *   <li>nothing was actually edited;</li>
 *   <li>the table has no primary key (the WHERE could hit siblings);</li>
 *   <li>an edited grid column is not a real column of the table
 *       (an expression or alias — there is nothing to assign to);</li>
 *   <li>an edited column IS part of the primary key (v1: the key
 *       addresses the row being updated; changing it would change
 *       which row the WHERE finds);</li>
 *   <li>a primary-key column is missing from the result grid (no
 *       original value to address the row with);</li>
 *   <li>a primary-key column's original value is {@code null} or reads
 *       {@code "NULL"} — the grid shows SQL NULL as the string
 *       {@code "NULL"} (see {@link QueryResult#rows}), so a genuine
 *       four-letter {@code 'NULL'} key value is indistinguishable and
 *       refusal is the safe bias.</li>
 * </ul>
 *
 * <p><b>Value rendering</b>, keyed by {@link ColumnInfo#typeName}
 * (case-insensitive, prefix-tolerant — {@code "INT UNSIGNED"},
 * {@code "DECIMAL(10,2)"}, PostgreSQL's {@code "int4"} all classify):
 * numeric types render bare when the text parses as a SQL numeric
 * literal (else refuse — quoting a non-number into an INT column helps
 * nobody); boolean types pass {@code TRUE}/{@code FALSE}/{@code 0}/
 * {@code 1} through as typed (else refuse); everything else is
 * single-quoted with the standard {@code ''} escape. A Java
 * {@code null} in the edits map is the UI's "the user typed NULL" and
 * renders the {@code NULL} keyword.
 *
 * <p>Identifiers ride {@link SqlDialect} — the same quoting the peek
 * queries use. SET columns appear in grid order (ascending column
 * index), WHERE columns in grid order too; the statement always ends
 * with {@code ";"}.
 */
public final class UpdateBuilder {

    /** Base type names rendered bare when the value parses as a number. */
    private static final Set<String> NUMERIC_TYPES = Set.of(
            "INT", "INTEGER", "BIGINT", "SMALLINT", "TINYINT",
            "DECIMAL", "NUMERIC", "REAL", "FLOAT", "DOUBLE");

    /** Base type names that take TRUE/FALSE/0/1 passthrough. */
    private static final Set<String> BOOLEAN_TYPES = Set.of("BOOLEAN", "BOOL");

    private UpdateBuilder() {
    }

    /**
     * Builds the one UPDATE statement for a row edit.
     *
     * @param engine              the SQL engine the statement will run on
     * @param table               the table the grid was selected from
     * @param columns             the table's column metadata (types + PK flags)
     * @param columnNames         the grid's column labels, in grid order
     * @param originalRow         the row's cells BEFORE the edit, aligned
     *                            with {@code columnNames}
     * @param editedByColumnIndex grid column index → new text; a Java
     *                            {@code null} value means "set to SQL NULL"
     * @return the UPDATE statement, {@code ";"}-terminated
     * @throws IllegalArgumentException whenever the edit cannot be made
     *         safe — the message says why, in words meant for the user
     */
    public static String update(DbEngine engine, TableInfo table, List<ColumnInfo> columns,
            List<String> columnNames, List<String> originalRow,
            Map<Integer, String> editedByColumnIndex) {
        if (engine.kind() != DbEngine.Kind.SQL) {
            throw new IllegalArgumentException(engine.displayName()
                    + " is a document engine — in-grid SQL editing applies to SQL engines only.");
        }
        if (editedByColumnIndex == null || editedByColumnIndex.isEmpty()) {
            throw new IllegalArgumentException("Nothing was edited — no UPDATE to build.");
        }
        List<ColumnInfo> pkColumns = columns.stream().filter(ColumnInfo::primaryKey).toList();
        if (pkColumns.isEmpty()) {
            throw new IllegalArgumentException("Table " + table.name()
                    + " has no primary key — a safe single-row UPDATE cannot be built without one.");
        }

        String quote = SqlDialect.identifierQuote(engine);
        SortedMap<Integer, String> edits = new TreeMap<>(editedByColumnIndex);

        // SET — edited cells in grid order
        List<String> sets = new ArrayList<>();
        for (Map.Entry<Integer, String> edit : edits.entrySet()) {
            int index = edit.getKey();
            if (index < 0 || index >= columnNames.size()) {
                throw new IllegalArgumentException("Edited column index " + index
                        + " is outside the grid (" + columnNames.size() + " columns).");
            }
            String gridName = columnNames.get(index);
            ColumnInfo column = columnNamed(columns, gridName);
            if (column == null) {
                throw new IllegalArgumentException(gridName + " is not a column of table "
                        + table.name() + " — only real table columns can be edited.");
            }
            if (column.primaryKey()) {
                throw new IllegalArgumentException(column.name()
                        + " is part of the primary key — primary-key edits are not supported"
                        + " (the key is what addresses the row being updated).");
            }
            sets.add(SqlDialect.quote(quote, column.name()) + " = "
                    + render(column, edit.getValue()));
        }

        // WHERE — every PK column, original values, grid order
        List<String> wheres = new ArrayList<>();
        for (int i = 0; i < columnNames.size(); i++) {
            ColumnInfo pk = pkNamed(pkColumns, columnNames.get(i));
            if (pk == null) {
                continue;
            }
            String original = i < originalRow.size() ? originalRow.get(i) : null;
            if (original == null || "NULL".equals(original)) {
                throw new IllegalArgumentException("Primary key column " + pk.name()
                        + " reads NULL in this row — the row cannot be addressed safely.");
            }
            wheres.add(SqlDialect.quote(quote, pk.name()) + " = " + render(pk, original));
        }
        for (ColumnInfo pk : pkColumns) {
            if (indexOfIgnoreCase(columnNames, pk.name()) < 0) {
                throw new IllegalArgumentException("Primary key column " + pk.name()
                        + " is not in the result grid — re-run the query selecting it"
                        + " (SELECT * always works).");
            }
        }

        return "UPDATE " + SqlDialect.qualifiedTable(engine, table)
                + " SET " + String.join(", ", sets)
                + " WHERE " + String.join(" AND ", wheres) + ";";
    }

    /**
     * Renders one cell value as a SQL literal per the column's type.
     * Package-visible for tests; {@code null} renders the NULL keyword.
     */
    static String render(ColumnInfo column, String value) {
        if (value == null) {
            return "NULL";
        }
        String type = baseType(column.typeName());
        if (NUMERIC_TYPES.contains(type)) {
            String trimmed = value.trim();
            if (!isNumericLiteral(trimmed)) {
                throw new IllegalArgumentException("\"" + value + "\" is not a number — column "
                        + column.name() + " is " + column.typeName() + ".");
            }
            return trimmed;
        }
        if (BOOLEAN_TYPES.contains(type)) {
            String trimmed = value.trim();
            String upper = trimmed.toUpperCase(Locale.ROOT);
            if (!upper.equals("TRUE") && !upper.equals("FALSE")
                    && !trimmed.equals("0") && !trimmed.equals("1")) {
                throw new IllegalArgumentException("\"" + value + "\" is not a boolean — column "
                        + column.name() + " is " + column.typeName()
                        + " (use TRUE, FALSE, 0 or 1).");
            }
            return trimmed;
        }
        return "'" + value.replace("'", "''") + "'";
    }

    /**
     * The classification key of a driver-reported type name: the first
     * word, uppercased, precision and modifiers dropped, a trailing
     * width digit stripped so PostgreSQL's {@code int4}/{@code float8}
     * classify — while {@code INTERVAL} (no trailing digits, not in the
     * set) stays textual.
     */
    static String baseType(String typeName) {
        String upper = (typeName == null ? "" : typeName).trim().toUpperCase(Locale.ROOT);
        int end = 0;
        while (end < upper.length() && (Character.isLetterOrDigit(upper.charAt(end))
                || upper.charAt(end) == '_')) {
            end++;
        }
        String token = upper.substring(0, end);
        if (NUMERIC_TYPES.contains(token) || BOOLEAN_TYPES.contains(token)) {
            return token;
        }
        String stripped = token.replaceFirst("\\d+$", "");
        return (NUMERIC_TYPES.contains(stripped) || BOOLEAN_TYPES.contains(stripped))
                ? stripped : token;
    }

    /**
     * A SQL numeric literal: optional sign, ASCII digits with an
     * optional decimal point (at least one digit somewhere), optional
     * signed exponent. A hand-rolled single-pass scan — deliberately
     * not a regex, so nothing here can be coaxed into backtracking.
     */
    static boolean isNumericLiteral(String s) {
        int i = 0;
        int n = s.length();
        if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
            i++;
        }
        int mantissaDigits = 0;
        while (i < n && isAsciiDigit(s.charAt(i))) {
            i++;
            mantissaDigits++;
        }
        if (i < n && s.charAt(i) == '.') {
            i++;
            while (i < n && isAsciiDigit(s.charAt(i))) {
                i++;
                mantissaDigits++;
            }
        }
        if (mantissaDigits == 0) {
            return false;
        }
        if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
            i++;
            if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                i++;
            }
            int exponentDigits = 0;
            while (i < n && isAsciiDigit(s.charAt(i))) {
                i++;
                exponentDigits++;
            }
            if (exponentDigits == 0) {
                return false;
            }
        }
        return i == n;
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static ColumnInfo columnNamed(List<ColumnInfo> columns, String name) {
        for (ColumnInfo column : columns) {
            if (column.name().equalsIgnoreCase(name)) {
                return column;
            }
        }
        return null;
    }

    private static ColumnInfo pkNamed(List<ColumnInfo> pkColumns, String name) {
        for (ColumnInfo pk : pkColumns) {
            if (pk.name().equalsIgnoreCase(name)) {
                return pk;
            }
        }
        return null;
    }

    private static int indexOfIgnoreCase(List<String> names, String name) {
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }
}
