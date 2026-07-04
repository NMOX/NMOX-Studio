package org.nmox.studio.dbstudio.engine;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nmox.studio.dbstudio.model.ColumnInfo;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.nmox.studio.dbstudio.model.TableInfo;

/**
 * The JDBC wrapper: one client per connection spec, holding at most one
 * live {@link Connection}. Every method is synchronous and throws
 * nothing — failures come back as strings ({@link #test()},
 * {@link #open()}) or inside {@link QueryResult#error()} — so the UI
 * layer can call from any worker thread and simply render what it gets.
 *
 * <p><b>Threading contract:</b> connection state is guarded by this
 * object's monitor; a long-running {@link #runScript} holds it, so a
 * concurrent {@link #close()} waits for the script (call
 * {@link #cancel()} first — it is deliberately unsynchronized and
 * interrupts the in-flight statement). Never call from the EDT.
 *
 * <p><b>Deliberate design:</b> the console executes exactly the SQL the
 * user typed via {@code Statement.execute} — that is the product (a SQL
 * console, like every DB tool), not an injection surface; there is no
 * server-side trust boundary being crossed on the user's own machine.
 * The corresponding find-sec-bugs exclusion is scoped to this class in
 * {@code config/spotbugs-exclude.xml}.
 *
 * <p>As a {@link DbBackend}, "containers" are tables and views and the
 * "console" is a SQL script: {@link #listContainers()} delegates to
 * {@link #listTables()} and {@link #runConsole} to {@link #runScript}
 * — the older names remain the implementation (and API) because the
 * JDBC-aware call sites and tests predate the interface.
 */
public final class DbClient implements DbBackend {

    private static final Logger LOG = Logger.getLogger(DbClient.class.getName());

    /** Connect-phase timeout applied to every open/test. */
    private static final int CONNECT_TIMEOUT_SECONDS = 5;

    private final ConnectionSpec spec;
    private final char[] password;

    private Connection connection;           // guarded by this
    private volatile Statement inFlight;     // read by cancel() from other threads

    /**
     * @param spec     the connection to speak to
     * @param password the secret from {@link Passwords#read}, or null
     *                 for password-less connections (SQLite, trust
     *                 auth); the array is copied, the caller may wipe
     *                 its own
     */
    public DbClient(ConnectionSpec spec, char[] password) {
        this.spec = Objects.requireNonNull(spec, "spec");
        if (spec.engine().kind() != DbEngine.Kind.SQL) {
            throw new IllegalArgumentException("DbClient speaks JDBC only — "
                    + spec.engine().displayName()
                    + " connections come from DbBackend.create(spec, password)");
        }
        this.password = password == null ? null : password.clone();
    }

    /** The spec this client was built for. */
    @Override
    public ConnectionSpec spec() {
        return spec;
    }

    /**
     * Probes the connection without keeping it: opens with a
     * {@value #CONNECT_TIMEOUT_SECONDS}s connect timeout (passed as a
     * per-engine URL parameter — {@code DriverManager.setLoginTimeout}
     * is JVM-global, so we never touch it), runs {@code SELECT 1}, and
     * closes. Returns null when everything worked, else a
     * human-readable reason.
     */
    @Override
    public String test() {
        try {
            Class.forName(spec.engine().driverClass());
            try (Connection probe = DriverManager.getConnection(urlWithConnectTimeout(), credentials());
                 Statement st = probe.createStatement();
                 ResultSet rs = st.executeQuery("SELECT 1")) {
                rs.next();
                return null;
            }
        } catch (Exception e) {
            return humanize(e);
        }
    }

    /**
     * Opens the held connection if not already open. Returns null on
     * success (including "was already open"), else a human-readable
     * reason. Surfacing this string is how the UI learns why the
     * schema tree is empty — call it before {@link #listTables()}.
     */
    @Override
    public synchronized String open() {
        if (isOpenLocked()) {
            return null;
        }
        try {
            Class.forName(spec.engine().driverClass());
            connection = DriverManager.getConnection(urlWithConnectTimeout(), credentials());
            return null;
        } catch (Exception e) {
            connection = null;
            return humanize(e);
        }
    }

    /** True while the held connection is open and not known-dead. */
    @Override
    public synchronized boolean isOpen() {
        return isOpenLocked();
    }

    /**
     * Closes the held connection; safe to call when already closed.
     * Blocks while a script is running — {@link #cancel()} first.
     */
    @Override // both DbBackend and AutoCloseable
    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                LOG.log(Level.FINE, "close failed for " + spec.name(), e);
            }
            connection = null;
        }
    }

    /**
     * Tables and views in the spec's database scope, via
     * {@code DatabaseMetaData.getTables}. Opens the connection if
     * needed; on any failure returns an empty list and logs — the
     * connection error itself is the return value of {@link #open()}.
     */
    public synchronized List<TableInfo> listTables() {
        List<TableInfo> tables = new ArrayList<>();
        String openError = open();
        if (openError != null) {
            LOG.log(Level.WARNING, "listTables: cannot open {0}: {1}",
                    new Object[]{spec.name(), openError});
            return tables;
        }
        try {
            DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet rs = meta.getTables(scopeCatalog(), null, null,
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
            LOG.log(Level.WARNING, "listTables failed for " + spec.name(), e);
        }
        return tables;
    }

    /** {@link DbBackend}'s name for {@link #listTables()}. */
    @Override
    public List<TableInfo> listContainers() {
        return listTables();
    }

    /**
     * Columns of one table, via {@code getColumns} cross-referenced
     * with {@code getPrimaryKeys}. Same failure contract as
     * {@link #listTables()}.
     */
    @Override
    public synchronized List<ColumnInfo> columns(TableInfo table) {
        List<ColumnInfo> columns = new ArrayList<>();
        if (table == null || open() != null) {
            return columns;
        }
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
     * Splits the script with {@link SqlSplitter} and executes every
     * statement in order on the held connection (opening it first if
     * needed). A failing statement records its error in ITS result and
     * the script keeps going — a DBA expects the rest of the file to
     * run. Result sets are read up to {@code rowLimit} rows
     * ({@code <= 0} means unlimited) and marked {@code truncated} when
     * more remained.
     */
    public synchronized List<QueryResult> runScript(String sql, int rowLimit) {
        List<QueryResult> results = new ArrayList<>();
        List<String> statements = SqlSplitter.split(sql);
        if (statements.isEmpty()) {
            return results;
        }
        String openError = open();
        if (openError != null) {
            results.add(errorResult(statements.get(0), 0,
                    "Could not open connection: " + openError));
            return results;
        }
        for (String statement : statements) {
            results.add(executeOne(statement, rowLimit));
        }
        return results;
    }

    /** {@link DbBackend}'s name for {@link #runScript}. */
    @Override
    public List<QueryResult> runConsole(String text, int rowLimit) {
        return runScript(text, rowLimit);
    }

    /**
     * Cancels the statement currently executing inside
     * {@link #runScript}, if any; a no-op otherwise. Deliberately NOT
     * synchronized so it can fire while runScript holds the monitor.
     * The cancelled statement surfaces as an error in its own result.
     */
    @Override
    public void cancel() {
        Statement running = inFlight;
        if (running != null) {
            try {
                running.cancel();
            } catch (SQLException e) {
                LOG.log(Level.FINE, "cancel failed", e);
            }
        }
    }

    // ---- internals ------------------------------------------------

    private QueryResult executeOne(String statementSql, int rowLimit) {
        long start = System.nanoTime();
        int limit = rowLimit <= 0 ? Integer.MAX_VALUE : rowLimit;
        try (Statement st = connection.createStatement()) {
            inFlight = st;
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
                        String value = rs.getString(c);
                        row.add(value == null ? "NULL" : value);
                    }
                    rows.add(row);
                }
                return new QueryResult(names, rows, rows.size(), -1,
                        truncated, elapsedMs(start), null, statementSql);
            }
        } catch (SQLException e) {
            return errorResult(statementSql, elapsedMs(start), humanize(e));
        } finally {
            inFlight = null;
        }
    }

    private static QueryResult errorResult(String statementSql, long elapsedMs, String error) {
        return new QueryResult(List.of(), List.of(), 0, -1, false, elapsedMs, error, statementSql);
    }

    private boolean isOpenLocked() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * The engine URL plus a connect-phase timeout as a URL parameter —
     * per-driver spelling: MariaDB wants milliseconds
     * ({@code connectTimeout=5000}), PostgreSQL wants seconds
     * ({@code connectTimeout=5}), SQLite opens a local file and needs
     * none.
     */
    private String urlWithConnectTimeout() {
        String url = spec.engine().jdbcUrl(spec);
        return switch (spec.engine()) {
            case MYSQL, MARIADB -> url + "?connectTimeout=" + (CONNECT_TIMEOUT_SECONDS * 1000);
            case POSTGRES -> url + "?connectTimeout=" + CONNECT_TIMEOUT_SECONDS;
            case SQLITE -> url;
            // unreachable: the constructor rejects non-SQL engines
            case MONGODB, COUCHDB -> throw new IllegalStateException("not a JDBC engine");
        };
    }

    private Properties credentials() {
        Properties props = new Properties();
        if (spec.engine() != DbEngine.SQLITE) {
            if (spec.user() != null && !spec.user().isBlank()) {
                props.setProperty("user", spec.user());
            }
            if (password != null) {
                props.setProperty("password", new String(password));
            }
        }
        return props;
    }

    private static String humanize(Throwable t) {
        String message = t.getMessage();
        if (message == null || message.isBlank()) {
            message = t.getClass().getSimpleName();
        }
        return message.trim();
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private String scopeCatalog() {
        return switch (spec.engine()) {
            case MYSQL, MARIADB, POSTGRES -> blankToNull(spec.database());
            case SQLITE -> null;
            // unreachable: the constructor rejects non-SQL engines
            case MONGODB, COUCHDB -> throw new IllegalStateException("not a JDBC engine");
        };
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
