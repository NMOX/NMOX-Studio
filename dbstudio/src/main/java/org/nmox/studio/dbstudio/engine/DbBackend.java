package org.nmox.studio.dbstudio.engine;

import java.util.List;
import java.util.Objects;
import org.nmox.studio.dbstudio.model.ColumnInfo;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.nmox.studio.dbstudio.model.TableInfo;

/**
 * What every DB Studio backend can do, whatever the wire protocol —
 * JDBC ({@link DbClient}), the MongoDB driver ({@link MongoBackend}),
 * or plain HTTP ({@link CouchBackend}). The UI holds one of these per
 * connection and never cares which engine family it got; dispatch
 * happens once, in {@link #create}.
 *
 * <p>The vocabulary is deliberately neutral: a <em>container</em> is a
 * table or view on SQL engines, a collection on MongoDB, a database on
 * CouchDB; the <em>console</em> takes a SQL script, a MongoDB command
 * document, or a Mango query respectively. {@link TableInfo} and
 * {@link ColumnInfo} generalize cleanly — document backends fill them
 * with honest analogues (see each backend's javadoc).
 *
 * <p><b>Shared contract</b> (identical to the one DbClient set):
 * every method is synchronous and throws nothing — failures come back
 * as strings ({@link #test()}, {@link #open()}) or inside
 * {@link QueryResult#error()}. Implementations are thread-safe. No
 * Swing anywhere; never call from the EDT.
 */
public interface DbBackend extends AutoCloseable {

    /** The spec this backend was built for. */
    ConnectionSpec spec();

    /**
     * Probes the connection without keeping it. Returns null when the
     * server answered, else a human-readable reason.
     */
    String test();

    /**
     * Establishes (or re-validates) the backend's connection. Returns
     * null on success (including "was already open"), else a
     * human-readable reason — surfacing this string is how the UI
     * learns why the tree is empty; call it before
     * {@link #listContainers()}.
     */
    String open();

    /** True while the last {@link #open()} succeeded and the backend wasn't closed. */
    boolean isOpen();

    /** Releases the connection; safe to call when already closed. Never throws. */
    @Override
    void close();

    /**
     * The containers in the connection's scope: tables/views (SQL),
     * collections (MongoDB), databases (CouchDB). On failure returns an
     * empty list and logs — the error itself is {@link #open()}'s
     * return value.
     */
    List<TableInfo> listContainers();

    /**
     * The columns (SQL) or sampled document shape (MongoDB, CouchDB) of
     * one container. Same failure contract as {@link #listContainers()}.
     */
    List<ColumnInfo> columns(TableInfo container);

    /**
     * Executes console input — a SQL script, a MongoDB command
     * document, or a Mango query — and returns one {@link QueryResult}
     * per executed statement (document backends always execute exactly
     * one). Result sets are capped at {@code rowLimit} rows
     * ({@code <= 0} means unlimited) and marked truncated when more
     * remained. Errors land in {@link QueryResult#error()}, never as a
     * throw.
     */
    List<QueryResult> runConsole(String text, int rowLimit);

    /**
     * Best-effort cancellation of the statement currently executing in
     * {@link #runConsole}; a no-op when idle (and on backends that
     * cannot cancel — see their javadoc). Safe to call from any thread
     * while runConsole is blocked.
     */
    void cancel();

    /**
     * The one place engine dispatch happens: SQL engines get the JDBC
     * {@link DbClient}, MongoDB its driver-backed {@link MongoBackend},
     * CouchDB the HTTP {@link CouchBackend}.
     *
     * @param spec     the connection to speak to
     * @param password the secret from {@link Passwords#read}, or null
     *                 for password-less connections; every backend
     *                 copies the array, the caller may wipe its own
     */
    static DbBackend create(ConnectionSpec spec, char[] password) {
        Objects.requireNonNull(spec, "spec");
        return switch (spec.engine()) {
            case MYSQL, MARIADB, POSTGRES, SQLITE -> new DbClient(spec, password);
            case MONGODB -> new MongoBackend(spec, password);
            case COUCHDB -> new CouchBackend(spec, password);
        };
    }

    /**
     * Convenience mirror of {@link DbEngine#kind()} so UI code holding
     * a backend can branch on family (e.g. console placeholder text)
     * without reaching back into the spec.
     */
    default DbEngine.Kind kind() {
        return spec().engine().kind();
    }
}
