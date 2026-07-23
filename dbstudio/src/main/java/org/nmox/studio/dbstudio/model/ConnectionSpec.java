package org.nmox.studio.dbstudio.model;

/**
 * Everything needed to describe a database connection — EXCEPT the
 * password. There is deliberately no password field here: specs are
 * serialized to {@code .nmoxdb.json} beside the project (a file meant
 * to be committed and shared), so secrets must never ride along.
 * Passwords live in the OS keychain via
 * {@link org.nmox.studio.dbstudio.engine.Passwords}, keyed by this
 * spec's {@link #id()}.
 *
 * @param id       stable identity, a UUID string minted once when the
 *                 user creates the connection; it keys the keychain
 *                 entry and search indexes, and survives renames
 * @param name     the user's label ("staging replica")
 * @param engine   which engine this connects to
 * @param host     server host; unused for {@link DbEngine#SQLITE}
 * @param port     server port; {@code <= 0} means "use the engine's
 *                 default"; unused for SQLite
 * @param database database (MySQL/MariaDB, PostgreSQL) to scope to;
 *                 unused for SQLite
 * @param user     login user; unused for SQLite
 * @param filePath absolute path to the database file — SQLite only,
 *                 empty for server engines
 * @param secure   TLS (https) for CouchDB's HTTP transport (ledger 54
 *                 L2) — false is the pre-v1.122.0 cleartext behavior;
 *                 JDBC engines carry their TLS in driver URLs and
 *                 ignore this flag
 */
public record ConnectionSpec(
        String id,
        String name,
        DbEngine engine,
        String host,
        int port,
        String database,
        String user,
        String filePath,
        boolean secure) {

    /**
     * Pre-v1.122.0 shape: every existing call site and every workspace
     * file written before the flag existed means cleartext ({@code
     * secure=false}) — the exact behavior those callers always had.
     */
    public ConnectionSpec(String id, String name, DbEngine engine, String host,
            int port, String database, String user, String filePath) {
        this(id, name, engine, host, port, database, user, filePath, false);
    }
}
