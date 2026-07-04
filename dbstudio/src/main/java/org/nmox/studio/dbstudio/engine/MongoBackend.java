package org.nmox.studio.dbstudio.engine;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bson.BsonRegularExpression;
import org.bson.BsonTimestamp;
import org.bson.Document;
import org.bson.types.Binary;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.nmox.studio.dbstudio.model.ColumnInfo;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.nmox.studio.dbstudio.model.TableInfo;

/**
 * The MongoDB {@link DbBackend}, wrapping the official sync driver:
 * one backend per connection spec, holding at most one live
 * {@code MongoClient}. Same contract as {@link DbClient} — every
 * method synchronous, nothing thrown, failures as strings or inside
 * {@link QueryResult#error()}; connection state guarded by this
 * object's monitor. Never call from the EDT.
 *
 * <p><b>Vocabulary mapping:</b> containers are the collections of
 * {@link ConnectionSpec#database()} (which is REQUIRED — a friendly
 * error says so when blank), reported as
 * {@code TableInfo(catalog "", schema database, name collection,
 * type "COLLECTION")}. Console input is ONE MongoDB Extended-JSON
 * command document (e.g. {@code {"find": "users", "filter": {...},
 * "limit": 50}}) executed via {@code runCommand}; a reply carrying
 * {@code cursor.firstBatch} is flattened to a grid by
 * {@link DocumentGrid}, any other reply renders as a single flattened
 * row. Only the first batch is shown — cursor continuation
 * ({@code getMore}) is out of v1 scope, so cap {@code "limit"} in the
 * command (or accept the server's default first batch of 101).
 *
 * <p>The connection string carries URL-encoded credentials and is
 * never logged.
 */
public final class MongoBackend implements DbBackend {

    private static final Logger LOG = Logger.getLogger(MongoBackend.class.getName());

    /** Connect and server-selection timeout, as connection-string options. */
    private static final int TIMEOUT_MS = 5_000;

    private static final String NO_DATABASE =
            "No database set — MongoDB connections need a database name "
            + "in the connection settings.";

    private final ConnectionSpec spec;
    private final char[] password;

    private MongoClient client; // guarded by this

    /**
     * @param spec     the connection to speak to; must be a
     *                 {@link DbEngine#MONGODB} spec
     * @param password the secret from {@link Passwords#read}, or null;
     *                 the array is copied, the caller may wipe its own
     */
    public MongoBackend(ConnectionSpec spec, char[] password) {
        this.spec = Objects.requireNonNull(spec, "spec");
        if (spec.engine() != DbEngine.MONGODB) {
            throw new IllegalArgumentException("MongoBackend speaks MongoDB only, not "
                    + spec.engine().displayName());
        }
        this.password = password == null ? null : password.clone();
    }

    @Override
    public ConnectionSpec spec() {
        return spec;
    }

    /**
     * Probes the deployment without keeping the client: connects, runs
     * {@code ping} (against the spec's database, or {@code admin} when
     * none is set — test() alone doesn't need one), closes. Null on
     * success, else a human-readable reason within
     * {@value #TIMEOUT_MS}ms.
     */
    @Override
    public String test() {
        try (MongoClient probe = MongoClients.create(connectionString())) {
            String database = blank(spec.database()) ? "admin" : spec.database();
            probe.getDatabase(database).runCommand(new Document("ping", 1));
            return null;
        } catch (RuntimeException e) {
            return humanize(e);
        }
    }

    /**
     * Opens the held client if not already open and proves it with a
     * {@code ping} (the driver connects lazily — creating a client
     * against a dead host "succeeds"). Requires
     * {@link ConnectionSpec#database()}: everything downstream
     * (tree, console) is database-scoped, so a blank one fails here,
     * once, with the friendly message.
     */
    @Override
    public synchronized String open() {
        if (client != null) {
            return null;
        }
        if (blank(spec.database())) {
            return NO_DATABASE;
        }
        MongoClient fresh = null;
        try {
            fresh = MongoClients.create(connectionString());
            fresh.getDatabase(spec.database()).runCommand(new Document("ping", 1));
            client = fresh;
            return null;
        } catch (RuntimeException e) {
            if (fresh != null) {
                fresh.close();
            }
            return humanize(e);
        }
    }

    /** True while the held client is open (last {@link #open()} succeeded). */
    @Override
    public synchronized boolean isOpen() {
        return client != null;
    }

    /** Closes the held client; safe to call when already closed. */
    @Override
    public synchronized void close() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    /**
     * The collections of the spec's database, one
     * {@code TableInfo("", database, collection, "COLLECTION")} each.
     * Failure contract as {@link DbClient#listTables()}: empty list
     * plus a log line; the reason is {@link #open()}'s return value.
     */
    @Override
    public synchronized List<TableInfo> listContainers() {
        List<TableInfo> containers = new ArrayList<>();
        String openError = open();
        if (openError != null) {
            LOG.log(Level.WARNING, "listContainers: cannot open {0}: {1}",
                    new Object[]{spec.name(), openError});
            return containers;
        }
        try {
            for (String name : client.getDatabase(spec.database()).listCollectionNames()) {
                containers.add(new TableInfo("", spec.database(), name, "COLLECTION"));
            }
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "listContainers failed for " + spec.name(), e);
        }
        return containers;
    }

    /**
     * An honest SHAPE SAMPLE, not a schema: MongoDB collections have
     * none, so this fetches ONE document ({@code find().first()}) and
     * reports its top-level fields as pseudo-columns — name, BSON type
     * name, size 0, nullable true, {@code _id} flagged as the primary
     * key. A different document may have different fields; an empty
     * collection reports no columns.
     */
    @Override
    public synchronized List<ColumnInfo> columns(TableInfo container) {
        if (container == null || open() != null) {
            return new ArrayList<>();
        }
        try {
            Document first = client.getDatabase(spec.database())
                    .getCollection(container.name()).find().first();
            return shapeSample(first);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "columns failed for " + container.name(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Runs the console text as ONE Extended-JSON command document via
     * {@code runCommand} — always exactly one {@link QueryResult}
     * (none for blank input). Parse errors, the missing-database case,
     * connection failures and server errors all land in
     * {@link QueryResult#error()}.
     */
    @Override
    public synchronized List<QueryResult> runConsole(String text, int rowLimit) {
        List<QueryResult> results = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return results;
        }
        long start = System.nanoTime();
        Document command;
        try {
            command = Document.parse(text);
        } catch (RuntimeException e) {
            results.add(errorResult(text, elapsedMs(start),
                    "Not a MongoDB command document: " + humanize(e)));
            return results;
        }
        String openError = open();
        if (openError != null) {
            results.add(errorResult(text, elapsedMs(start),
                    "Could not open connection: " + openError));
            return results;
        }
        try {
            Document reply = client.getDatabase(spec.database()).runCommand(command);
            results.add(toResult(reply, rowLimit, elapsedMs(start), text));
        } catch (RuntimeException e) {
            results.add(errorResult(text, elapsedMs(start), humanize(e)));
        }
        return results;
    }

    /**
     * Best-effort no-op: the sync driver offers no per-operation
     * cancellation handle, and a server-side {@code killOp} hunt is out
     * of v1 scope. Commands are bounded by the driver timeouts instead;
     * a long-running command simply finishes. Documented rather than
     * pretended.
     */
    @Override
    public void cancel() {
        // deliberately empty — see javadoc
    }

    // ---- internals (static seams, testable without a server) -------

    /**
     * {@code mongodb://[user:password@]host:port/?timeouts} with
     * URL-encoded credentials. Built on demand and handed only to the
     * driver — never logged.
     */
    private String connectionString() {
        int port = spec.port() > 0 ? spec.port() : spec.engine().defaultPort();
        return connectionString(spec.user(), password, spec.host(), port);
    }

    static String connectionString(String user, char[] password, String host, int port) {
        StringBuilder url = new StringBuilder("mongodb://");
        if (user != null && !user.isBlank()) {
            url.append(urlEncode(user));
            if (password != null && password.length > 0) {
                url.append(':').append(urlEncode(new String(password)));
            }
            url.append('@');
        }
        url.append(host == null ? "" : host).append(':').append(port)
                .append("/?connectTimeoutMS=").append(TIMEOUT_MS)
                .append("&serverSelectionTimeoutMS=").append(TIMEOUT_MS);
        return url.toString();
    }

    /** Shape-samples one document; see {@link #columns}. Null (empty collection) → no columns. */
    static List<ColumnInfo> shapeSample(Document first) {
        List<ColumnInfo> columns = new ArrayList<>();
        if (first == null) {
            return columns;
        }
        for (Map.Entry<String, Object> field : first.entrySet()) {
            columns.add(new ColumnInfo(field.getKey(), bsonTypeName(field.getValue()),
                    0, true, "_id".equals(field.getKey())));
        }
        return columns;
    }

    /** The BSON type name (as {@code $type} spells it) of one decoded value. */
    static String bsonTypeName(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Integer) {
            return "int";
        }
        if (value instanceof Long) {
            return "long";
        }
        if (value instanceof Double) {
            return "double";
        }
        if (value instanceof Boolean) {
            return "bool";
        }
        if (value instanceof ObjectId) {
            return "objectId";
        }
        if (value instanceof Date) {
            return "date";
        }
        if (value instanceof Decimal128) {
            return "decimal";
        }
        if (value instanceof Binary || value instanceof byte[]) {
            return "binData";
        }
        if (value instanceof BsonTimestamp) {
            return "timestamp";
        }
        if (value instanceof BsonRegularExpression) {
            return "regex";
        }
        if (value instanceof Map) {
            return "object";
        }
        if (value instanceof List) {
            return "array";
        }
        return value.getClass().getSimpleName();
    }

    /**
     * Maps a {@code runCommand} reply to a result: a reply carrying
     * {@code cursor.firstBatch} flattens those documents (capped at
     * {@code rowLimit}, truncated flag when more were in the batch);
     * anything else renders as the reply itself, one flattened row.
     */
    static QueryResult toResult(Document reply, int rowLimit, long elapsedMs, String statement) {
        List<Document> batch = firstBatch(reply);
        DocumentGrid.Grid grid = batch != null
                ? DocumentGrid.fromMaps(batch, rowLimit)
                : DocumentGrid.fromMaps(List.of(reply), 0);
        return new QueryResult(grid.columnNames(), grid.rows(), grid.rows().size(),
                -1, grid.truncated(), elapsedMs, null, statement);
    }

    /** {@code cursor.firstBatch} as documents, or null when the reply has no (clean) cursor. */
    private static List<Document> firstBatch(Document reply) {
        if (!(reply.get("cursor") instanceof Document cursor)
                || !(cursor.get("firstBatch") instanceof List<?> batch)) {
            return null;
        }
        List<Document> docs = new ArrayList<>(batch.size());
        for (Object item : batch) {
            if (!(item instanceof Document doc)) {
                return null; // not a document batch — render the raw reply instead
            }
            docs.add(doc);
        }
        return docs;
    }

    private static QueryResult errorResult(String statement, long elapsedMs, String error) {
        return new QueryResult(List.of(), List.of(), 0, -1, false, elapsedMs, error, statement);
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
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
}
