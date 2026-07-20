package org.nmox.studio.dbstudio.engine;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nmox.studio.core.http.HttpClientFactory;
import org.nmox.studio.core.util.JsonUtil;
import org.nmox.studio.dbstudio.model.ColumnInfo;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.nmox.studio.dbstudio.model.TableInfo;

/**
 * The CouchDB {@link DbBackend} — no driver at all, just CouchDB's
 * plain HTTP API over the IDE's shared {@link HttpClientFactory}
 * client with {@value #TIMEOUT_SECONDS}s per-request timeouts and
 * Basic auth when the spec has a user. Same contract as
 * {@link DbClient}: synchronous, nothing thrown, failures as strings
 * or inside {@link QueryResult#error()}; thread-safe (the only state
 * is one volatile flag). Never call from the EDT.
 *
 * <p><b>Vocabulary mapping:</b> containers are the server's databases
 * ({@code GET /_all_dbs}), reported as {@code TableInfo("", "", db,
 * "DATABASE")}; system databases (leading {@code _}) are skipped
 * unless {@link ConnectionSpec#database()} names one. Console input is
 * a Mango query — either a full {@code {"selector": ...}} body or a
 * bare selector, which gets wrapped — POSTed to
 * {@code /{database}/_find}. One convenience: the exact text
 * {@code _all_dbs} returns the database list as a one-column grid,
 * handy while the spec has no database chosen yet.
 *
 * <p>HTTP is stateless, so {@link #open()} means "the server answered
 * just now" and {@link #isOpen()} "the last open() succeeded and no
 * close() since" — there is no held connection to break.
 *
 * <p>All response parsing goes through package-private static
 * String-input seams ({@link #parseWelcome}, {@link #parseAllDbs},
 * {@link #parseFindDocs}, ...) so tests feed canned JSON without a
 * server — the {@code DigitalOceanClient} idiom.
 */
public final class CouchBackend implements DbBackend {

    private static final Logger LOG = Logger.getLogger(CouchBackend.class.getName());

    private static final int TIMEOUT_SECONDS = 5;

    /**
     * Response-body cap. {@code ofString()} buffered the whole body
     * unbounded; a {@code _find} against a huge collection (or a
     * hostile endpoint) could OOM the IDE. The grid's row cap bounds
     * document COUNT, not byte size, so the read itself must be
     * bounded — the apiclient v1.99.0 fix, here.
     */
    static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;

    private static final String NO_DATABASE =
            "No database set — CouchDB queries need a database name in the "
            + "connection settings (query \"_all_dbs\" to list what the server has).";

    private final ConnectionSpec spec;
    private final char[] password;

    private volatile boolean open;

    /**
     * @param spec     the connection to speak to; must be a
     *                 {@link DbEngine#COUCHDB} spec
     * @param password the secret from {@link Passwords#read}, or null;
     *                 the array is copied, the caller may wipe its own
     */
    public CouchBackend(ConnectionSpec spec, char[] password) {
        this.spec = Objects.requireNonNull(spec, "spec");
        if (spec.engine() != DbEngine.COUCHDB) {
            throw new IllegalArgumentException("CouchBackend speaks CouchDB only, not "
                    + spec.engine().displayName());
        }
        this.password = password == null ? null : password.clone();
    }

    @Override
    public ConnectionSpec spec() {
        return spec;
    }

    /**
     * {@code GET /} and checks for CouchDB's welcome document. Null
     * when a CouchDB answered, else a human-readable reason (including
     * "answered, but isn't CouchDB").
     */
    @Override
    public String test() {
        try {
            String body = get("/");
            if (parseWelcome(body) == null) {
                return "The server answered but does not look like CouchDB (no welcome document)";
            }
            return null;
        } catch (Exception e) {
            return humanize(e);
        }
    }

    /**
     * Validates reachability with {@link #test()} and remembers the
     * verdict — stateless HTTP has nothing to actually hold open.
     */
    @Override
    public String open() {
        String error = test();
        open = error == null;
        return error;
    }

    /** True when the last {@link #open()} succeeded and {@link #close()} hasn't run since. */
    @Override
    public boolean isOpen() {
        return open;
    }

    /** Forgets the last successful {@link #open()}; nothing to release. */
    @Override
    public void close() {
        open = false;
    }

    /**
     * The server's databases ({@code GET /_all_dbs}), one
     * {@code TableInfo("", "", db, "DATABASE")} each. System databases
     * (leading {@code _}) are skipped unless the spec's database names
     * one. Failure contract as {@link DbClient#listTables()}: empty
     * list plus a log line; the reason is {@link #open()}'s return.
     */
    @Override
    public List<TableInfo> listContainers() {
        List<TableInfo> containers = new ArrayList<>();
        try {
            for (String db : parseAllDbs(get("/_all_dbs"))) {
                if (db.startsWith("_") && !db.equals(spec.database())) {
                    continue; // system database (_users, _replicator, ...)
                }
                containers.add(new TableInfo("", "", db, "DATABASE"));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "listContainers: cannot list databases of {0}: {1}",
                    new Object[]{spec.name(), humanize(e)});
        }
        return containers;
    }

    /**
     * An honest SHAPE SAMPLE, not a schema: CouchDB documents have
     * none, so this fetches ONE document
     * ({@code POST /{db}/_find {"selector":{},"limit":1}}) and reports
     * its top-level fields as pseudo-columns — name, JSON type name,
     * size 0, nullable true, {@code _id} flagged as the primary key. A
     * different document may have different fields; an empty database
     * reports no columns.
     */
    @Override
    public List<ColumnInfo> columns(TableInfo container) {
        if (container == null) {
            return new ArrayList<>();
        }
        try {
            String body = post("/" + encodePath(container.name()) + "/_find",
                    "{\"selector\":{},\"limit\":1}");
            return shapeSample(parseFindDocs(body));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "columns failed for " + container.name(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Runs the console text as a Mango query against
     * {@code /{database}/_find} — always exactly one
     * {@link QueryResult} (none for blank input). A body without
     * {@code "selector"} is treated as a bare selector and wrapped;
     * when the user set no {@code "limit"}, {@code rowLimit + 1} is
     * injected (one probe row past the cap keeps the truncated flag
     * honest). The exact text {@code _all_dbs} instead returns the
     * database list as a one-column grid. Parse errors, the
     * missing-database case, HTTP failures and CouchDB error replies
     * all land in {@link QueryResult#error()}.
     */
    @Override
    public List<QueryResult> runConsole(String text, int rowLimit) {
        List<QueryResult> results = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return results;
        }
        long start = System.nanoTime();
        String trimmed = text.trim();
        if ("_all_dbs".equals(trimmed)) {
            try {
                results.add(allDbsResult(parseAllDbs(get("/_all_dbs")), elapsedMs(start), trimmed));
            } catch (Exception e) {
                results.add(errorResult(trimmed, elapsedMs(start), humanize(e)));
            }
            return results;
        }
        if (blank(spec.database())) {
            results.add(errorResult(trimmed, elapsedMs(start), NO_DATABASE));
            return results;
        }
        String mango;
        try {
            mango = mangoBody(trimmed, rowLimit);
        } catch (RuntimeException e) {
            results.add(errorResult(trimmed, elapsedMs(start),
                    "Not a Mango query (expected a JSON selector): " + humanize(e)));
            return results;
        }
        try {
            List<JSONObject> docs = parseFindDocs(
                    post("/" + encodePath(spec.database()) + "/_find", mango));
            DocumentGrid.Grid grid = DocumentGrid.fromJsonObjects(docs, rowLimit);
            results.add(new QueryResult(grid.columnNames(), grid.rows(), grid.rows().size(),
                    -1, grid.truncated(), elapsedMs(start), null, trimmed));
        } catch (Exception e) {
            results.add(errorResult(trimmed, elapsedMs(start), humanize(e)));
        }
        return results;
    }

    /**
     * No-op: each console run is a single bounded HTTP request
     * ({@value #TIMEOUT_SECONDS}s timeout), so there is nothing
     * meaningful to cancel — the timeout is the cancellation.
     */
    @Override
    public void cancel() {
        // deliberately empty — see javadoc
    }

    // ---- parsing seams (static, String-in, no server needed) --------

    /**
     * CouchDB's root welcome, e.g.
     * {@code {"couchdb":"Welcome","version":"3.4.2",...}} — returns a
     * short "CouchDB 3.4.2" description, or null when the body isn't a
     * CouchDB welcome.
     */
    static String parseWelcome(String json) {
        if (!JsonUtil.looksJson(json)) {
            return null;
        }
        try {
            JSONObject root = new JSONObject(json);
            if (!root.has("couchdb")) {
                return null;
            }
            String version = root.optString("version", "");
            return version.isBlank() ? "CouchDB" : "CouchDB " + version;
        } catch (RuntimeException notJson) {
            return null;
        }
    }

    /** {@code GET /_all_dbs}' body — a JSON array of names — as a list. */
    static List<String> parseAllDbs(String json) {
        List<String> dbs = new ArrayList<>();
        JSONArray array = new JSONArray(json);
        for (int i = 0; i < array.length(); i++) {
            dbs.add(array.getString(i));
        }
        return dbs;
    }

    /**
     * The documents of a {@code _find} reply
     * ({@code {"docs":[...], ...}}). A CouchDB error body
     * ({@code {"error":..., "reason":...}}) throws with the server's
     * reason so callers surface it verbatim.
     */
    static List<JSONObject> parseFindDocs(String json) {
        JSONObject root = new JSONObject(json);
        if (root.has("error")) {
            throw new IllegalStateException(errorSummary(root));
        }
        List<JSONObject> docs = new ArrayList<>();
        JSONArray array = root.optJSONArray("docs");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                docs.add(array.getJSONObject(i));
            }
        }
        return docs;
    }

    /**
     * Builds the {@code _find} body from console text: already a
     * {@code {"selector": ...}} body → used as-is; anything else is
     * treated as a bare selector and wrapped. When the text set no
     * {@code "limit"} and {@code rowLimit > 0}, injects
     * {@code rowLimit + 1} — the one extra row is the truncation
     * probe; {@link DocumentGrid} caps the grid at {@code rowLimit}.
     * Malformed JSON throws (a {@code JSONException}) for the caller
     * to wrap.
     */
    static String mangoBody(String text, int rowLimit) {
        JSONObject parsed = new JSONObject(text);
        JSONObject mango = parsed.has("selector")
                ? parsed
                : new JSONObject().put("selector", parsed);
        if (!mango.has("limit") && rowLimit > 0) {
            mango.put("limit", rowLimit + 1);
        }
        return mango.toString();
    }

    /** Shape-samples the first document; see {@link #columns}. No documents → no columns. */
    static List<ColumnInfo> shapeSample(List<JSONObject> docs) {
        List<ColumnInfo> columns = new ArrayList<>();
        if (docs == null || docs.isEmpty()) {
            return columns;
        }
        JSONObject first = docs.get(0);
        for (String key : first.keySet()) {
            columns.add(new ColumnInfo(key, jsonTypeName(first.get(key)),
                    0, true, "_id".equals(key)));
        }
        return columns;
    }

    /** The JSON type name of one parsed value: string/number/boolean/object/array/null. */
    static String jsonTypeName(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "null";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof JSONObject) {
            return "object";
        }
        if (value instanceof JSONArray) {
            return "array";
        }
        return value.getClass().getSimpleName();
    }

    /** The {@code _all_dbs} convenience grid: one {@code database} column. */
    static QueryResult allDbsResult(List<String> dbs, long elapsedMs, String statement) {
        List<List<String>> rows = new ArrayList<>(dbs.size());
        for (String db : dbs) {
            rows.add(List.of(db));
        }
        return new QueryResult(List.of("database"), rows, rows.size(),
                -1, false, elapsedMs, null, statement);
    }

    /**
     * The Basic auth header value for a spec's credentials, or null
     * when there is no user (CouchDB in admin-party mode needs none).
     */
    static String basicAuth(String user, char[] password) {
        if (user == null || user.isBlank()) {
            return null;
        }
        String credentials = user + ":" + (password == null ? "" : new String(password));
        return "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    /** A CouchDB error body as one line: {@code not_found: Database does not exist.} */
    static String errorSummary(JSONObject errorBody) {
        String error = errorBody.optString("error", "error");
        String reason = errorBody.optString("reason", "");
        return reason.isBlank() ? error : error + ": " + reason;
    }

    // ---- HTTP plumbing ----------------------------------------------

    private String get(String path) throws Exception {
        return send(request(path).GET().build());
    }

    private String post(String path, String body) throws Exception {
        return send(request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build());
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS));
        String auth = basicAuth(spec.user(), password);
        if (auth != null) {
            builder.header("Authorization", auth);
        }
        return builder;
    }

    /**
     * Sends and returns the body; a status {@code >= 400} throws with
     * the status and, when the body is CouchDB's error JSON, its
     * reason.
     */
    private String send(HttpRequest request) throws Exception {
        try {
            HttpResponse<java.io.InputStream> response = HttpClientFactory.shared()
                    .send(request, HttpResponse.BodyHandlers.ofInputStream());
            String body;
            boolean truncated;
            try (java.io.InputStream in = response.body()) {
                byte[] raw = in.readNBytes(MAX_RESPONSE_BYTES);
                truncated = in.read() != -1; // closing aborts the rest of the transfer
                body = new String(raw, StandardCharsets.UTF_8);
            }
            if (response.statusCode() >= 400) {
                String detail = "";
                if (JsonUtil.looksJson(body)) {
                    try {
                        detail = " — " + errorSummary(new JSONObject(body));
                    } catch (RuntimeException notJson) {
                        // status alone will have to do
                    }
                }
                throw new IllegalStateException("HTTP " + response.statusCode() + detail);
            }
            if (truncated) {
                // a _find/_all_docs against a huge collection (or a
                // wrong/hostile endpoint) could stream gigabytes;
                // ofString() would have buffered it all and OOM'd the
                // IDE (the apiclient v1.99.0 bug class). Refuse rather
                // than return a JSON fragment DocumentGrid can't parse.
                throw new IllegalStateException("Response over "
                        + (MAX_RESPONSE_BYTES / (1024 * 1024)) + "MB — narrow the query"
                        + " (add a limit or a selector).");
            }
            return body;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private String baseUrl() {
        int port = spec.port() > 0 ? spec.port() : spec.engine().defaultPort();
        return "http://" + (spec.host() == null ? "" : spec.host()) + ":" + port;
    }

    /** Encodes one path segment (CouchDB db names may contain {@code +/$...}). */
    private static String encodePath(String segment) {
        return URLEncoder.encode(segment == null ? "" : segment, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static QueryResult errorResult(String statement, long elapsedMs, String error) {
        return new QueryResult(List.of(), List.of(), 0, -1, false, elapsedMs, error, statement);
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
