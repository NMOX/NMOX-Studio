package org.nmox.studio.dbstudio.engine;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nmox.studio.dbstudio.model.ColumnInfo;
import org.nmox.studio.dbstudio.model.TableInfo;

/**
 * The shared JDBC core: everything DB Studio does with a live
 * {@link Connection} — run a statement script, list tables and views,
 * describe columns — as static methods that take the connection as a
 * parameter. Extracted verbatim from {@link DbClient} so the same code
 * serves both a connection DB Studio opened itself (DbClient) and one
 * the NetBeans Database Explorer manages ({@link ServicesBackend}).
 *
 * <p>Stateless and thread-agnostic: callers own the connection's
 * locking (both backends serialize on their own monitor). Failure
 * contract matches the {@link DbBackend} one — metadata methods catch,
 * log and return what they gathered; script execution folds errors
 * into per-statement {@link QueryResult}s and never throws.
 *
 * <p><b>Deliberate design:</b> the console executes exactly the SQL the
 * user typed via {@code Statement.execute} — that is the product (a SQL
 * console, like every DB tool), not an injection surface; there is no
 * server-side trust boundary being crossed on the user's own machine.
 * The corresponding find-sec-bugs exclusion is scoped to this class in
 * {@code config/spotbugs-exclude.xml}.
 */
final class JdbcCore {

    private static final Logger LOG = Logger.getLogger(JdbcCore.class.getName());

    private JdbcCore() {
    }

    /**
     * The cross-thread cancellation seam: {@link #runScript} parks the
     * in-flight {@link Statement} here so a concurrent {@link #cancel()}
     * can interrupt it while the executing thread holds its backend's
     * monitor. One hook per backend instance — exactly the volatile
     * {@code inFlight} field DbClient always had, now shareable.
     */
    static final class CancelHook {

        private volatile Statement inFlight;

        /**
         * Cancels the statement currently executing, if any; a no-op
         * otherwise. Deliberately lock-free so it can fire while the
         * backend's monitor is held by the running script.
         */
        void cancel() {
            Statement running = inFlight;
            if (running != null) {
                try {
                    running.cancel();
                } catch (SQLException e) {
                    LOG.log(Level.FINE, "cancel failed", e);
                }
            }
        }
    }

    /**
     * Tables and views via {@code DatabaseMetaData.getTables}, scoped by
     * the caller's catalog and schema pattern (either may be null for
     * "all"). On failure returns what was gathered and logs a WARNING
     * carrying {@code connectionName} — the connect error itself is the
     * backend's {@code open()} return value.
     */
    static List<TableInfo> listTables(Connection connection, String catalog,
            String schemaPattern, String connectionName) {
        List<TableInfo> tables = new ArrayList<>();
        try {
            DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet rs = meta.getTables(catalog, schemaPattern, null,
                    new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    tables.add(new TableInfo(
                            nz(rs.getString("TABLE_CAT")),
                            nz(rs.getString("TABLE_SCHEM")),
                            nz(rs.getString("TABLE_NAME")),
                            nz(rs.getString("TABLE_TYPE"))));
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "listTables failed for " + connectionName, e);
        }
        return tables;
    }

    /**
     * Columns of one table via {@code getColumns} cross-referenced with
     * {@code getPrimaryKeys}. Same failure contract as
     * {@link #listTables}.
     */
    static List<ColumnInfo> columns(Connection connection, TableInfo table) {
        List<ColumnInfo> columns = new ArrayList<>();
        String catalog = blankToNull(table.catalog());
        String schema = blankToNull(table.schema());
        try {
            DatabaseMetaData meta = connection.getMetaData();
            Set<String> pkNames = new HashSet<>();
            try (ResultSet rs = meta.getPrimaryKeys(catalog, schema, table.name())) {
                while (rs.next()) {
                    pkNames.add(rs.getString("COLUMN_NAME"));
                }
            }
            try (ResultSet rs = meta.getColumns(catalog, schema, table.name(), null)) {
                while (rs.next()) {
                    String name = nz(rs.getString("COLUMN_NAME"));
                    columns.add(new ColumnInfo(
                            name,
                            nz(rs.getString("TYPE_NAME")),
                            rs.getInt("COLUMN_SIZE"),
                            rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                            pkNames.contains(name)));
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "columns failed for " + table.name(), e);
        }
        return columns;
    }

    /**
     * Executes every statement in order on the given connection. A
     * failing statement records its error in ITS result and the script
     * keeps going — a DBA expects the rest of the file to run. Result
     * sets are read up to {@code rowLimit} rows ({@code <= 0} means
     * unlimited) and marked {@code truncated} when more remained. The
     * in-flight statement is parked on {@code hook} for cancellation.
     */
    static List<QueryResult> runStatements(Connection connection, List<String> statements,
            int rowLimit, CancelHook hook) {
        List<QueryResult> results = new ArrayList<>();
        for (String statement : statements) {
            results.add(executeOne(connection, statement, rowLimit, hook));
        }
        return results;
    }

    /**
     * Per-cell character ceiling (ledger 54 M4). The row cap bounds row
     * COUNT, but a single multi-hundred-MB BLOB/CLOB/{@code bytea} was fully
     * materialized by {@code getString} — one such cell could OOM the IDE
     * despite a tiny result. A grid cell can't usefully show more than this
     * anyway; oversize values are truncated with an honest marker.
     */
    static final int MAX_CELL_CHARS = 64 * 1024;

    /**
     * One cell as display text, capped at {@link #MAX_CELL_CHARS}. For LOB
     * columns the length is checked from metadata FIRST so a giant value is
     * never fully pulled into a String — only its capped prefix is read.
     */
    static String cell(ResultSet rs, int c) throws SQLException {
        int type = rs.getMetaData().getColumnType(c);
        if (type == java.sql.Types.CLOB || type == java.sql.Types.NCLOB) {
            java.sql.Clob clob = rs.getClob(c);
            if (clob == null) {
                return "NULL";
            }
            long len = clob.length();
            String prefix = clob.getSubString(1, (int) Math.min(len, MAX_CELL_CHARS));
            return len > MAX_CELL_CHARS ? prefix + " …[" + len + " chars, truncated]" : prefix;
        }
        if (type == java.sql.Types.BLOB || type == java.sql.Types.LONGVARBINARY
                || type == java.sql.Types.VARBINARY || type == java.sql.Types.BINARY) {
            java.sql.Blob blob = rs.getBlob(c);
            if (blob == null) {
                return "NULL";
            }
            return "[" + blob.length() + " bytes]"; // never stringify binary
        }
        String value = rs.getString(c);
        if (value == null) {
            return "NULL";
        }
        return value.length() > MAX_CELL_CHARS
                ? value.substring(0, MAX_CELL_CHARS) + " …[" + value.length()
                        + " chars, truncated]"
                : value;
    }

    private static QueryResult executeOne(Connection connection, String statementSql,
            int rowLimit, CancelHook hook) {
        long start = System.nanoTime();
        int limit = rowLimit <= 0 ? Integer.MAX_VALUE : rowLimit;
        try (Statement st = connection.createStatement()) {
            hook.inFlight = st;
            boolean producedResultSet = st.execute(statementSql);
            if (!producedResultSet) {
                return new QueryResult(List.of(), List.of(), 0, st.getUpdateCount(),
                        false, elapsedMs(start), null, statementSql);
            }
            try (ResultSet rs = st.getResultSet()) {
                ResultSetMetaData md = rs.getMetaData();
                int columnCount = md.getColumnCount();
                List<String> names = new ArrayList<>(columnCount);
                for (int c = 1; c <= columnCount; c++) {
                    names.add(md.getColumnLabel(c));
                }
                List<List<String>> rows = new ArrayList<>();
                boolean truncated = false;
                while (rs.next()) {
                    if (rows.size() >= limit) {
                        truncated = true;
                        break;
                    }
                    List<String> row = new ArrayList<>(columnCount);
                    for (int c = 1; c <= columnCount; c++) {
                        row.add(cell(rs, c));
                    }
                    rows.add(row);
                }
                return new QueryResult(names, rows, rows.size(), -1,
                        truncated, elapsedMs(start), null, statementSql);
            }
        } catch (SQLException e) {
            return errorResult(statementSql, elapsedMs(start), humanize(e));
        } finally {
            hook.inFlight = null;
        }
    }

    /** A {@link QueryResult} carrying only a failure. */
    static QueryResult errorResult(String statementSql, long elapsedMs, String error) {
        return new QueryResult(List.of(), List.of(), 0, -1, false, elapsedMs, error, statementSql);
    }

    /** The throwable's message, or its type name when it has none. */
    static String humanize(Throwable t) {
        String message = t.getMessage();
        if (message == null || message.isBlank()) {
            message = t.getClass().getSimpleName();
        }
        return message.trim();
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
