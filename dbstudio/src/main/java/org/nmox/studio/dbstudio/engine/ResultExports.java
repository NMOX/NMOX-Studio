package org.nmox.studio.dbstudio.engine;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Pure result-grid exporters — the strings behind "Export as CSV /
 * JSON". The UI owns the file dialog and the disk write; these methods
 * own the format, so every escaping rule is pinned by tests instead of
 * discovered by a spreadsheet.
 *
 * <p>Cells arrive already stringified by the backend (see
 * {@link QueryResult#rows} — JDBC NULL becomes the string
 * {@code "NULL"}); a genuinely {@code null} cell (a backend that kept
 * nulls, or a short row) exports as an empty CSV field / JSON
 * {@code null}.
 */
public final class ResultExports {

    private ResultExports() {
    }

    /**
     * RFC 4180 CSV: a header row from {@code columnNames}, CRLF line
     * endings (every record, including the last), and any field
     * containing a comma, double quote, CR or LF is wrapped in double
     * quotes with embedded quotes doubled. A {@code null} cell becomes
     * an empty field.
     */
    public static String toCsv(QueryResult result) {
        StringBuilder csv = new StringBuilder();
        appendCsvRow(csv, result.columnNames(), result.columnNames().size());
        for (List<String> row : result.rows()) {
            appendCsvRow(csv, row, result.columnNames().size());
        }
        return csv.toString();
    }

    /**
     * A JSON array of one object per row, keyed by {@code columnNames};
     * duplicate column names get {@code _2}, {@code _3}, … suffixes so
     * no cell is silently dropped. Values are JSON strings exactly as
     * the grid shows them; a {@code null} cell becomes JSON
     * {@code null}. Pretty-printed (2-space indent).
     */
    public static String toJson(QueryResult result) {
        List<String> keys = uniqueKeys(result.columnNames());
        JSONArray array = new JSONArray();
        for (List<String> row : result.rows()) {
            JSONObject object = new JSONObject();
            for (int i = 0; i < keys.size(); i++) {
                String cell = i < row.size() ? row.get(i) : null;
                object.put(keys.get(i), cell == null ? JSONObject.NULL : cell);
            }
            array.put(object);
        }
        return array.toString(2);
    }

    /**
     * The default export file base name for a result: the bare table
     * name when the statement is a simple single-table SELECT (last
     * segment of a qualified name, filesystem-hostile characters
     * replaced with {@code _}), otherwise {@code "results"}. The UI
     * appends {@code .csv}/{@code .json}.
     */
    public static String suggestedBaseName(String statement) {
        return SimpleSelectParser.singleTable(statement)
                .map(name -> name.substring(name.lastIndexOf('.') + 1))
                .map(name -> name.replaceAll("[^A-Za-z0-9._-]", "_"))
                .filter(name -> !name.isBlank())
                .orElse("results");
    }

    /** Column names disambiguated: the second {@code id} becomes {@code id_2}. */
    static List<String> uniqueKeys(List<String> names) {
        Set<String> used = new LinkedHashSet<>();
        for (String name : names) {
            String key = name == null ? "" : name;
            String candidate = key;
            int n = 1;
            while (!used.add(candidate)) {
                n++;
                candidate = key + "_" + n;
            }
        }
        return List.copyOf(used);
    }

    private static void appendCsvRow(StringBuilder csv, List<String> cells, int width) {
        for (int i = 0; i < width; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(csvField(i < cells.size() ? cells.get(i) : null));
        }
        csv.append("\r\n");
    }

    private static String csvField(String value) {
        if (value == null) {
            return "";
        }
        // Formula-injection defense: a DB value (which can be
        // attacker-controlled on a shared database) beginning = + - @
        // is executed as a formula when the CSV opens in Excel/Sheets.
        // A leading apostrophe forces text; the cell then needs quoting.
        String safe = value;
        if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) {
            safe = "'" + value;
        }
        boolean needsQuoting = safe != value
                || safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0
                || safe.indexOf('\r') >= 0 || safe.indexOf('\n') >= 0;
        if (!needsQuoting) {
            return safe;
        }
        return '"' + safe.replace("\"", "\"\"") + '"';
    }
}
