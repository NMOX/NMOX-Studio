package org.nmox.studio.dbstudio.model;

/**
 * The database engines DB Studio speaks, each carrying what its access
 * layer needs: a display name for the UI, the conventional default
 * port, and — for the SQL engines — the bundled driver class and the
 * URL recipe. Engines come in two {@link Kind kinds}: {@link Kind#SQL}
 * engines ride JDBC through {@code DbClient}; {@link Kind#DOCUMENT}
 * engines (MongoDB, CouchDB) have native backends and no JDBC anything
 * — {@link #jdbcUrl} refuses them loudly rather than fabricating a URL.
 * UI code should dispatch through
 * {@code org.nmox.studio.dbstudio.engine.DbBackend#create} and never
 * care which kind it got.
 *
 * <p>One deliberate economy: MariaDB Connector/J speaks the MySQL wire
 * protocol (MariaDB began as a MySQL fork and the protocol never
 * diverged), so the single bundled {@code org.mariadb.jdbc.Driver}
 * serves BOTH {@link #MYSQL} and {@link #MARIADB} — a
 * {@code jdbc:mariadb://} URL connects happily to a stock MySQL
 * server. Two enum constants remain so the UI can say what the user
 * means and so a future engine-specific quirk has a seam to live in.
 */
public enum DbEngine {

    MYSQL("MySQL", 3306, "org.mariadb.jdbc.Driver"),
    MARIADB("MariaDB", 3306, "org.mariadb.jdbc.Driver"),
    POSTGRES("PostgreSQL", 5432, "org.postgresql.Driver"),
    /** File-based; no host or port — {@code -1} marks "not applicable". */
    SQLITE("SQLite", -1, "org.sqlite.JDBC"),
    /** Document engine — no JDBC; served by {@code MongoBackend}. */
    MONGODB("MongoDB", 27017, Kind.DOCUMENT),
    /** Document engine — no JDBC; spoken over plain HTTP by {@code CouchBackend}. */
    COUCHDB("CouchDB", 5984, Kind.DOCUMENT);

    /**
     * How an engine is accessed: {@link #SQL} through JDBC
     * ({@code DbClient}), {@link #DOCUMENT} through a native backend
     * ({@code MongoBackend}, {@code CouchBackend}).
     */
    public enum Kind { SQL, DOCUMENT }

    private final String displayName;
    private final int defaultPort;
    private final String driverClass;
    private final Kind kind;

    DbEngine(String displayName, int defaultPort, String driverClass) {
        this(displayName, defaultPort, driverClass, Kind.SQL);
    }

    DbEngine(String displayName, int defaultPort, Kind kind) {
        this(displayName, defaultPort, "", kind);
    }

    DbEngine(String displayName, int defaultPort, String driverClass, Kind kind) {
        this.displayName = displayName;
        this.defaultPort = defaultPort;
        this.driverClass = driverClass;
        this.kind = kind;
    }

    /** Human name for combo boxes and search results. */
    public String displayName() {
        return displayName;
    }

    /** The engine's conventional port; {@code -1} for file-based SQLite. */
    public int defaultPort() {
        return defaultPort;
    }

    /**
     * Fully-qualified JDBC driver class, bundled with the module;
     * {@code ""} for {@link Kind#DOCUMENT} engines, which have no JDBC
     * driver at all.
     */
    public String driverClass() {
        return driverClass;
    }

    /** Whether this engine speaks JDBC or a native document protocol. */
    public Kind kind() {
        return kind;
    }

    /**
     * Builds the JDBC URL for a connection spec. Server engines use
     * {@code host:port/database} (a spec port of {@code <= 0} falls back
     * to {@link #defaultPort()}); SQLite uses the spec's
     * {@link ConnectionSpec#filePath() filePath} verbatim.
     *
     * @throws IllegalStateException for {@link Kind#DOCUMENT} engines —
     *         they have no JDBC URL; use
     *         {@code DbBackend.create(spec, password)} instead
     */
    public String jdbcUrl(ConnectionSpec spec) {
        return switch (this) {
            case MYSQL, MARIADB -> "jdbc:mariadb://" + hostPort(spec) + "/" + nz(spec.database());
            case POSTGRES -> "jdbc:postgresql://" + hostPort(spec) + "/" + nz(spec.database());
            case SQLITE -> "jdbc:sqlite:" + nz(spec.filePath());
            case MONGODB, COUCHDB -> throw new IllegalStateException(
                    displayName + " is a document engine — it has no JDBC URL; "
                    + "use DbBackend.create(spec, password) instead");
        };
    }

    private String hostPort(ConnectionSpec spec) {
        int port = spec.port() > 0 ? spec.port() : defaultPort;
        return nz(spec.host()) + ":" + port;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
