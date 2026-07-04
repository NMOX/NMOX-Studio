package org.nmox.studio.dbstudio.engine;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.db.explorer.ConnectionManager;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.nmox.studio.dbstudio.model.ColumnInfo;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.nmox.studio.dbstudio.model.TableInfo;

/**
 * The bridge to the NetBeans Database Explorer (the Services window):
 * wraps one of its {@link DatabaseConnection}s as a {@link DbBackend},
 * so ANY database registered there — Java DB/Derby, Oracle, anything
 * with a driver the user pointed NetBeans at — gets DB Studio's tree,
 * peek and console for free. All actual work rides the live
 * {@link java.sql.Connection} the explorer manages, through the same
 * {@link JdbcCore} that serves {@link DbClient}.
 *
 * <p><b>Passwords never pass through this code.</b> NetBeans owns the
 * driver, the credentials and the connection lifecycle; {@link #open()}
 * merely asks {@link ConnectionManager#connect} to bring the shared
 * connection up, and when NetBeans cannot do that without asking the
 * user (no stored password), the returned message tells the user to
 * connect once in the Services window — this class never raises UI.
 * Symmetrically, {@link #close()} is a documented no-op: the underlying
 * connection belongs to the whole IDE.
 *
 * <p>Same contract as every backend: synchronous, thread-safe (the
 * instance monitor serializes our calls; NetBeans hands the same
 * {@code java.sql.Connection} to its own tooling, which we cannot
 * lock against), never throws, never on the EDT —
 * {@code ConnectionManager.connect} itself refuses the EDT.
 */
public final class ServicesBackend implements DbBackend {

    /**
     * Prefix of every synthesized {@link ConnectionSpec#id()}, so UI
     * maps can tell a Services entry from a workspace one at a glance.
     */
    public static final String ID_PREFIX = "nb:";

    private static final Logger LOG = Logger.getLogger(ServicesBackend.class.getName());

    private final DatabaseConnection connection;
    private final ConnectionSpec spec;
    private final JdbcCore.CancelHook cancelHook = new JdbcCore.CancelHook();

    public ServicesBackend(DatabaseConnection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.spec = specFor(connection);
    }

    /**
     * A read-only display identity for a Services connection — NOT a
     * persisted workspace spec: the id is {@value #ID_PREFIX} plus the
     * explorer's connection name, the engine is inferred from the JDBC
     * URL ({@code null} for dialects DB Studio doesn't model, e.g.
     * Derby or Oracle), host/port/database are parsed best-effort from
     * the URL (blank when unparseable), and there is no file path.
     * It never reaches {@code .nmoxdb.json} and never keys a keychain
     * entry.
     */
    public static ConnectionSpec specFor(DatabaseConnection connection) {
        String url = connection.getDatabaseURL();
        return new ConnectionSpec(
                ID_PREFIX + connection.getName(),
                connection.getName(),
                JdbcUrlDialects.engineFor(url),
                hostOf(url),
                portOf(url),
                databaseOf(url),
                connection.getUser(),
                null);
    }

    /** The synthesized display identity — see {@link #specFor}. */
    @Override
    public ConnectionSpec spec() {
        return spec;
    }

    /**
     * A Services connection has no side-channel probe: NetBeans owns
     * the credentials, so the only honest test is bringing the shared
     * connection up — delegates to {@link #open()}. (DB Studio's UI
     * disables Test for Services entries; the Services window owns
     * them.)
     */
    @Override
    public String test() {
        return open();
    }

    /**
     * Ensures the explorer's shared connection is up: when
     * {@code getJDBCConnection()} is null, asks
     * {@link ConnectionManager#connect} to connect it — off-EDT, as the
     * {@link DbBackend} contract already requires (NetBeans enforces
     * it). When NetBeans cannot connect without user interaction
     * (typically a password it wasn't told to remember), the returned
     * message says to connect once in the Services window — no dialogs
     * are raised from here, and no credentials pass through DB Studio.
     */
    @Override
    public synchronized String open() {
        if (isOpenNow()) {
            return null;
        }
        try {
            boolean connected = ConnectionManager.getDefault().connect(connection);
            if (!connected || !isOpenNow()) {
                return notConnectedMessage(null);
            }
            return null;
        } catch (Exception e) {
            return notConnectedMessage(e);
        }
    }

    private String notConnectedMessage(Exception cause) {
        String hint = "connect \"" + connection.getDisplayName()
                + "\" once in the Services window — NetBeans keeps its driver and credentials";
        return cause == null
                ? "Not connected: " + hint
                : JdbcCore.humanize(cause) + " — " + hint;
    }

    /** True while the explorer's shared connection is up and not known-dead. */
    @Override
    public synchronized boolean isOpen() {
        return isOpenNow();
    }

    private boolean isOpenNow() {
        try {
            Connection jdbc = connection.getJDBCConnection();
            return jdbc != null && !jdbc.isClosed();
        } catch (SQLException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Deliberate no-op. The underlying {@code java.sql.Connection}
     * belongs to the NetBeans Database Explorer and is shared with the
     * rest of the IDE (the Services tree, the platform SQL editor) —
     * closing it here would yank it out from under them. Disconnecting
     * is the Services window's job; DB Studio just drops its reference
     * to this backend when it is done.
     */
    @Override
    public void close() {
        // no-op by design — see javadoc: the Services window owns the lifecycle
    }

    /**
     * Tables and views through the shared connection, scoped to the
     * URL-derived catalog for engines where that means "database"
     * (MySQL family, PostgreSQL) and to the explorer connection's
     * schema when it has one (Derby's APP, an Oracle user schema) —
     * unscoped otherwise.
     */
    @Override
    public synchronized List<TableInfo> listContainers() {
        String openError = open();
        if (openError != null) {
            LOG.log(Level.WARNING, "listContainers: cannot open {0}: {1}",
                    new Object[]{spec.name(), openError});
            return new ArrayList<>();
        }
        return JdbcCore.listTables(connection.getJDBCConnection(),
                scopeCatalog(), JdbcCore.blankToNull(connection.getSchema()), spec.name());
    }

    /** Same failure contract as {@link #listContainers()}. */
    @Override
    public synchronized List<ColumnInfo> columns(TableInfo container) {
        if (container == null || open() != null) {
            return new ArrayList<>();
        }
        return JdbcCore.columns(connection.getJDBCConnection(), container);
    }

    /**
     * Runs the console SQL through the shared connection — statement
     * splitting, error-and-continue, row limits and truncation exactly
     * as {@link DbClient#runScript} (both delegate to
     * {@link JdbcCore}).
     */
    @Override
    public synchronized List<QueryResult> runConsole(String text, int rowLimit) {
        List<QueryResult> results = new ArrayList<>();
        List<String> statements = SqlSplitter.split(text);
        if (statements.isEmpty()) {
            return results;
        }
        String openError = open();
        if (openError != null) {
            results.add(JdbcCore.errorResult(statements.get(0), 0,
                    "Could not open connection: " + openError));
            return results;
        }
        return JdbcCore.runStatements(connection.getJDBCConnection(),
                statements, rowLimit, cancelHook);
    }

    /**
     * Cancels the statement currently executing inside
     * {@link #runConsole}, if any; a no-op otherwise. NOT synchronized,
     * so it can fire while runConsole holds the monitor — the same
     * volatile-statement pattern as {@link DbClient#cancel()}.
     */
    @Override
    public void cancel() {
        cancelHook.cancel();
    }

    /**
     * Always {@link DbEngine.Kind#SQL}: a Services connection is JDBC
     * by definition, even when {@link ConnectionSpec#engine()} is null
     * because DB Studio has no dialect for it. (The default would NPE
     * on that null.)
     */
    @Override
    public DbEngine.Kind kind() {
        return DbEngine.Kind.SQL;
    }

    // ---- best-effort URL anatomy (display identity only) -----------

    /** Host between {@code //} and the next {@code : / ; ?}, or {@code ""}. */
    static String hostOf(String jdbcUrl) {
        String authority = authorityOf(jdbcUrl);
        int colon = authority.indexOf(':');
        return colon >= 0 ? authority.substring(0, colon) : authority;
    }

    /** Port after the host's colon, or {@code -1} when absent/unparseable. */
    static int portOf(String jdbcUrl) {
        String authority = authorityOf(jdbcUrl);
        int colon = authority.indexOf(':');
        if (colon < 0) {
            return -1;
        }
        try {
            return Integer.parseInt(authority.substring(colon + 1));
        } catch (NumberFormatException notAPort) {
            return -1;
        }
    }

    /** Path segment after the authority, stripped of parameters, or {@code ""}. */
    static String databaseOf(String jdbcUrl) {
        if (jdbcUrl == null) {
            return "";
        }
        int slashes = jdbcUrl.indexOf("//");
        if (slashes < 0) {
            return "";
        }
        int slash = jdbcUrl.indexOf('/', slashes + 2);
        return slash < 0 ? "" : upTo(jdbcUrl.substring(slash + 1), '?', ';', '&');
    }

    private static String authorityOf(String jdbcUrl) {
        if (jdbcUrl == null) {
            return "";
        }
        int slashes = jdbcUrl.indexOf("//");
        if (slashes < 0) {
            return "";
        }
        return upTo(jdbcUrl.substring(slashes + 2), '/', '?', ';');
    }

    private static String upTo(String s, char... stops) {
        String result = s;
        for (char stop : stops) {
            int i = result.indexOf(stop);
            if (i >= 0) {
                result = result.substring(0, i);
            }
        }
        return result;
    }

    private String scopeCatalog() {
        DbEngine engine = spec.engine();
        if (engine == DbEngine.MYSQL || engine == DbEngine.MARIADB
                || engine == DbEngine.POSTGRES) {
            return JdbcCore.blankToNull(spec.database());
        }
        return null;
    }
}
