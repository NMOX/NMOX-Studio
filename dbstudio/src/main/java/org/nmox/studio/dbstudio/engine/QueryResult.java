package org.nmox.studio.dbstudio.engine;

import java.util.List;

/**
 * What one executed statement produced — a result grid, an update
 * count, or an error — already stringified so the UI can pour it
 * straight into a table model without touching JDBC types.
 *
 * <p>Exactly one of three shapes:
 * <ul>
 *   <li><b>result set</b>: {@code columnNames}/{@code rows} filled,
 *       {@code updateCount == -1}, {@code error == null}</li>
 *   <li><b>update</b>: empty columns/rows, {@code updateCount >= 0},
 *       {@code error == null}</li>
 *   <li><b>error</b>: {@code error} is the human message; the script
 *       kept running after it</li>
 * </ul>
 *
 * @param columnNames result-set column labels, empty for updates/errors
 * @param rows        stringified cells ({@code NULL} values become the
 *                    string {@code "NULL"}), at most the caller's row
 *                    limit
 * @param rowCount    rows actually fetched (== {@code rows.size()})
 * @param updateCount rows affected by an update statement, or
 *                    {@code -1} when the statement produced a result
 *                    set (or failed)
 * @param truncated   true when the result set had more rows than the
 *                    limit allowed us to fetch
 * @param elapsedMs   wall-clock execute-and-fetch time
 * @param error       human-readable failure for THIS statement, or
 *                    null on success
 * @param statement   the SQL text that produced this result
 */
public record QueryResult(
        List<String> columnNames,
        List<List<String>> rows,
        int rowCount,
        int updateCount,
        boolean truncated,
        long elapsedMs,
        String error,
        String statement) {

    public QueryResult {
        columnNames = List.copyOf(columnNames);
        rows = List.copyOf(rows);
    }

    /** True when this statement failed. */
    public boolean isError() {
        return error != null;
    }

    /** True when this statement produced a result grid. */
    public boolean isResultSet() {
        return error == null && updateCount < 0;
    }
}
