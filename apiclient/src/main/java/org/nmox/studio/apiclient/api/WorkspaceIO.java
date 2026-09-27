package org.nmox.studio.apiclient.api;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nmox.studio.apiclient.model.ApiModel.Assertion;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;
import org.nmox.studio.apiclient.model.ApiModel.Collection;
import org.nmox.studio.apiclient.model.ApiModel.Environment;
import org.nmox.studio.apiclient.model.ApiModel.Pair;
import org.nmox.studio.apiclient.model.ApiModel.Request;
import org.nmox.studio.apiclient.model.ApiModel.Workspace;
import org.nmox.studio.apiclient.model.SendHistory;
import org.nmox.studio.core.util.AtomicFiles;

/**
 * Reads and writes the workspace as {@code .nmoxapi.json} beside the
 * project - the file is meant to be committed and shared, so it never
 * carries secrets by policy: environment values that look like tokens
 * are the developer's to keep in a git-ignored env file, mirroring the
 * rack's ATMOS rule. (Here we persist what the user typed; the UI warns
 * that secrets belong in variables kept out of source control.)
 *
 * <p>Since 3.4 the file holds only what a team means to share —
 * collections and environments. One person's state (the send history and
 * the active environment) is written by {@link #personalJson} into
 * {@code core.util.PersonalState}, outside the project.
 */
public final class WorkspaceIO {

    public static final String FILENAME = ".nmoxapi.json";

    /** The studio key this workspace's per-person document is filed under. */
    public static final String PERSONAL_STUDIO = "api";

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(WorkspaceIO.class.getName());

    private WorkspaceIO() {
    }

    /**
     * The SHARED file: collections and environments, the things a team
     * means to share. Since 3.4 it no longer carries the send history or
     * the active environment — both are one person's state, both were
     * rewritten on every Send and every pick, and so every merge of
     * {@code .nmoxapi.json} between two people conflicted over state
     * neither meant to share. They live in {@link #personalJson} now; a
     * file written before 3.4 still has them and {@link #fromJson} still
     * reads them once, so the first load migrates.
     */
    public static String toJson(Workspace w) {
        JSONObject root = new JSONObject();
        root.put("version", 1);

        JSONArray cols = new JSONArray();
        for (Collection c : w.collections) {
            JSONObject cj = new JSONObject();
            cj.put("name", c.name);
            JSONArray reqs = new JSONArray();
            for (Request r : c.requests) {
                reqs.put(requestJson(r));
            }
            cj.put("requests", reqs);
            cols.put(cj);
        }
        root.put("collections", cols);

        JSONArray envs = new JSONArray();
        for (Environment e : w.environments) {
            JSONObject ej = new JSONObject();
            ej.put("name", e.name);
            ej.put("variables", new JSONObject(e.variables));
            envs.put(ej);
        }
        root.put("environments", envs);
        return root.toString(2);
    }

    /**
     * One person's state about this workspace (3.4): the active environment
     * and the send history. Written to {@code core.util.PersonalState},
     * never into the committed file.
     */
    public static String personalJson(Workspace w) {
        JSONObject root = new JSONObject();
        root.put("version", 1);
        root.put("activeEnvironment", w.activeEnvironment == null ? "" : w.activeEnvironment);
        root.put("history", historyJson(w.history));
        return root.toString(2);
    }

    /**
     * Applies a {@link #personalJson} document over a loaded workspace: its
     * active environment and history replace whatever the shared file
     * carried (a pre-3.4 file's legacy copy). A document that does not
     * parse changes nothing — it is ours, it is small, and the next save
     * rewrites it.
     */
    public static void applyPersonal(Workspace w, String personal) {
        if (w == null || personal == null || personal.isBlank()) {
            return;
        }
        JSONObject root;
        try {
            root = new JSONObject(personal);
        } catch (RuntimeException malformed) {
            LOG.log(java.util.logging.Level.WARNING,
                    "Ignoring an unreadable personal API Studio state ({0})", malformed.getMessage());
            return;
        }
        w.activeEnvironment = root.optString("activeEnvironment", "");
        w.history.clear();
        readHistory(root.optJSONArray("history"), w.history);
    }

    // send history (v1.197.0): AUTHORED fields + outcome only. The Entry
    // type has no token field, so the secrets law holds by construction —
    // this loop cannot write what the model cannot hold.
    private static JSONArray historyJson(List<SendHistory.Entry> history) {
        JSONArray hist = new JSONArray();
        for (SendHistory.Entry e : history) {
            JSONObject hj = new JSONObject();
            hj.put("timestamp", e.timestamp);
            hj.put("name", e.name);
            hj.put("method", e.method);
            hj.put("url", e.url);
            hj.put("body", e.body);
            hj.put("authType", e.authType.name());
            hj.put("params", pairsJson(e.params));
            hj.put("headers", pairsJson(e.headers));
            hj.put("status", e.status);
            hj.put("durationMs", e.durationMs);
            hist.put(hj);
        }
        return hist;
    }

    private static JSONObject requestJson(Request r) {
        JSONObject rj = new JSONObject();
        rj.put("id", r.id);
        rj.put("name", r.name);
        rj.put("method", r.method);
        rj.put("url", r.url);
        rj.put("body", r.body);
        rj.put("authType", r.authType.name());
        // authToken is DELIBERATELY not serialized (v1.97.0): the secret
        // lives in the OS keychain via ApiSecrets, keyed by r.id. The
        // TopComponent pushes it there on save.
        rj.put("params", pairsJson(r.params));
        rj.put("headers", pairsJson(r.headers));
        JSONArray tests = new JSONArray();
        for (Assertion a : r.tests) {
            tests.put(new JSONObject().put("kind", a.kind.name()).put("target", a.target));
        }
        rj.put("tests", tests);
        return rj;
    }

    private static JSONArray pairsJson(List<Pair> pairs) {
        JSONArray arr = new JSONArray();
        for (Pair p : pairs) {
            arr.put(new JSONObject().put("name", p.name == null ? "" : p.name)
                    .put("value", p.value == null ? "" : p.value)
                    .put("enabled", p.enabled));
        }
        return arr;
    }

    public static Workspace fromJson(String json) {
        return parse(json, new ArrayList<>());
    }

    /**
     * The parse, noting in {@code newer} every value this version does not
     * know (an auth type or an assertion kind a newer NMOX Studio wrote).
     * Each is kept in memory as best the model can, but a workspace with
     * any at all must be bound read-only: the next save would drop them for
     * everyone who shares the file (3.4).
     */
    static Workspace parse(String json, List<String> newer) {
        Workspace w = new Workspace();
        JSONObject root = new JSONObject(json);
        // legacy per-person field: read so a pre-3.4 file migrates
        w.activeEnvironment = root.optString("activeEnvironment", "");
        JSONArray cols = root.optJSONArray("collections");
        if (cols != null) {
            for (int i = 0; i < cols.length(); i++) {
                w.collections.add(collection(cols.getJSONObject(i), newer));
            }
        }
        JSONArray envs = root.optJSONArray("environments");
        if (envs != null) {
            for (int i = 0; i < envs.length(); i++) {
                JSONObject ej = envs.getJSONObject(i);
                Environment e = new Environment();
                e.name = ej.optString("name", "env");
                JSONObject vars = ej.optJSONObject("variables");
                if (vars != null) {
                    for (String key : vars.keySet()) {
                        e.variables.put(key, vars.getString(key));
                    }
                }
                w.environments.add(e);
            }
        }
        // legacy per-person field, as above
        readHistory(root.optJSONArray("history"), w.history);
        healDuplicateIds(w);
        return w;
    }

    private static void readHistory(JSONArray hist, List<SendHistory.Entry> into) {
        if (hist == null) {
            return;
        }
        for (int i = 0; i < hist.length() && into.size() < SendHistory.CAP; i++) {
            JSONObject hj = hist.getJSONObject(i);
            SendHistory.Entry e = new SendHistory.Entry();
            e.timestamp = hj.optLong("timestamp", 0L);
            e.name = hj.optString("name", "");
            e.method = hj.optString("method", "GET");
            e.url = hj.optString("url", "");
            e.body = hj.optString("body", "");
            try {
                e.authType = AuthType.valueOf(hj.optString("authType", "NONE"));
            } catch (IllegalArgumentException ex) {
                // a history row only restores a request's SHAPE; its auth
                // type is re-chosen when the restored request is saved
                e.authType = AuthType.NONE;
            }
            readPairs(hj.optJSONArray("params"), e.params);
            readPairs(hj.optJSONArray("headers"), e.headers);
            e.status = hj.optInt("status", 0);
            e.durationMs = hj.optLong("durationMs", 0L);
            into.add(e);
        }
    }

    /**
     * The parse-time heal this file owed since ids became keychain keys
     * (the v2.9.0 law: every runtime invariant over a checked-in file
     * needs a parse-time heal). The runtime never makes two requests
     * share an id — but a keep-both git merge of {@code .nmoxapi.json}
     * can, and then deleting either request wipes the OS-keychain token
     * BOTH resolve (ApiSecrets is keyed by id): silent secret loss on
     * the survivor. The first occurrence keeps the id and therefore the
     * stored token; every later duplicate is re-minted — it shows as
     * needing its auth re-entered, which is honest, recoverable, and
     * structurally sound.
     */
    private static void healDuplicateIds(Workspace w) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (var c : w.collections) {
            for (var r : c.requests) {
                if (!seen.add(r.id)) {
                    r.id = java.util.UUID.randomUUID().toString();
                }
            }
        }
    }

    private static Collection collection(JSONObject cj, List<String> newer) {
        Collection c = new Collection();
        c.name = cj.optString("name", "collection");
        JSONArray reqs = cj.optJSONArray("requests");
        if (reqs != null) {
            for (int i = 0; i < reqs.length(); i++) {
                c.requests.add(request(reqs.getJSONObject(i), newer));
            }
        }
        return c;
    }

    private static Request request(JSONObject rj, List<String> newer) {
        Request r = new Request();
        // Keep an existing id; mint one for a pre-v1.97.0 file so its
        // keychain slot is stable from now on.
        String id = rj.optString("id", "");
        if (!id.isEmpty()) {
            r.id = id;
        }
        r.name = rj.optString("name", "request");
        r.method = rj.optString("method", "GET");
        r.url = rj.optString("url", "");
        r.body = rj.optString("body", "");
        String authType = rj.optString("authType", "NONE");
        try {
            r.authType = AuthType.valueOf(authType);
        } catch (IllegalArgumentException fromNewerVersion) {
            // a newer NMOX Studio's auth type (3.4): NONE in memory, but the
            // name is kept so Send refuses rather than going out without it,
            // and the workspace binds read-only so no save turns it into
            // NONE for everyone who shares the file
            r.authType = AuthType.NONE;
            r.foreignAuthType = authType;
            newer.add("authType " + authType);
        }
        // A pre-v1.97.0 file may still carry a plaintext authToken; keep
        // it in memory as the migration carrier — the TopComponent moves
        // it to the keychain on load and the next save drops it from the
        // file (it is never re-serialized). New files have no such key.
        r.authToken = rj.optString("authToken", "");
        readPairs(rj.optJSONArray("params"), r.params);
        readPairs(rj.optJSONArray("headers"), r.headers);
        JSONArray tests = rj.optJSONArray("tests");
        if (tests != null) {
            for (int i = 0; i < tests.length(); i++) {
                JSONObject tj = tests.getJSONObject(i);
                try {
                    r.tests.add(new Assertion(
                            Assertion.Kind.valueOf(tj.optString("kind", "STATUS_IS")),
                            tj.optString("target", "")));
                } catch (IllegalArgumentException fromNewerVersion) {
                    // an assertion kind from a newer file: this version
                    // cannot model it, so the workspace binds read-only and
                    // no save can drop it (3.4)
                    newer.add("assertion " + tj.optString("kind", ""));
                }
            }
        }
        return r;
    }

    private static void readPairs(JSONArray arr, List<Pair> into) {
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject pj = arr.getJSONObject(i);
            Pair p = new Pair(pj.optString("name", ""), pj.optString("value", ""));
            p.enabled = pj.optBoolean("enabled", true);
            into.add(p);
        }
    }

    public static void save(File dir, Workspace w) throws IOException {
        // atomic rename, never truncate-then-write: the file pulse (and any
        // foreign reader) must never observe a torn .nmoxapi.json
        AtomicFiles.writeString(new File(dir, FILENAME).toPath(), toJson(w));
    }

    public static Workspace load(File dir) throws IOException {
        File f = new File(dir, FILENAME);
        if (!f.isFile()) {
            return null;
        }
        // .nmoxapi.json sits beside the project and travels with a clone
        return fromJson(org.nmox.studio.core.util.BoundedReads.read(f.toPath()));
    }

    /**
     * A guarded load: {@code workspace} is null when the file is
     * missing, unreadable or conflicted (callers substitute a stand-in);
     * {@code backup} is non-null when the file EXISTED but failed to
     * parse and was copied aside first; {@code unreadable} is true when
     * the file EXISTS and its bytes could not be read at all;
     * {@code conflicted} when it holds git's unresolved merge conflict;
     * {@code newerFormat} when it parsed but carries values a newer NMOX
     * Studio wrote (the workspace is handed back, to show, not to save).
     *
     * <p>Unreadable and malformed are different failures and were treated
     * as one. A PARSE failure hands back bytes we have seen and kept as
     * {@code .bak}, so the starter workspace may replace them. A READ
     * failure — a permission error, a transient fault, or
     * {@link org.nmox.studio.core.util.BoundedReads.TooLarge}, which is
     * an {@link IOException} like any other — hands back nothing at all,
     * and the null workspace it used to come with was indistinguishable
     * from a fresh project: {@code ApiClientTopComponent.readWorkspace}
     * caught the throw, substituted the starter, stamped the file as its
     * own, and the next edit wrote a three-request starter over every
     * collection, environment and history row. Measured through the real
     * window on an over-cap file: 9,437,184 bytes became 550.
     *
     * <p>A conflicted file (3.4) was treated as a malformed one: copied to
     * {@code .bak}, replaced by the starter, and written over by the next
     * Send, so {@code git commit} recorded the starter as the merge.
     */
    public record LoadOutcome(Workspace workspace, File backup, boolean unreadable,
            boolean conflicted, boolean newerFormat) {

        /** The pre-3.4 shape: no conflict, nothing from a newer version. */
        public LoadOutcome(Workspace workspace, File backup, boolean unreadable) {
            this(workspace, backup, unreadable, false, false);
        }

        /**
         * True when the file exists and must not be written: it could not
         * be read, it holds git's unresolved merge conflict, or it carries
         * values a newer NMOX Studio wrote. The studio binds read-only and
         * says which.
         */
        public boolean readOnly() {
            return unreadable || conflicted || newerFormat;
        }
    }

    /**
     * Loads like {@link #load}, but guards the user's file against the
     * corrupt-load → empty-model → autosave-clobbers-original sequence:
     * when the file exists and fails to parse, the unreadable original
     * is copied aside BEFORE the empty outcome is returned, so the
     * studio's next autosave can never destroy the only copy. A file that
     * cannot be READ comes back marked {@link LoadOutcome#unreadable()}
     * instead, one holding git's merge conflict {@link
     * LoadOutcome#conflicted()} and one a newer version wrote {@link
     * LoadOutcome#newerFormat()}, because in all three the caller must
     * not write the file. None of those make a backup. Never throws.
     */
    public static LoadOutcome loadGuarded(File dir) {
        File f = new File(dir, FILENAME);
        if (!f.isFile()) {
            return new LoadOutcome(null, null, false);
        }
        String text;
        try {
            text = org.nmox.studio.core.util.BoundedReads.read(f.toPath());
        } catch (IOException unreadable) {
            // No .bak here, and that is deliberate: the parse failure copies
            // the bytes aside because they are about to be replaced, while a
            // file we could not read must not be written over at all — so
            // there is nothing to rescue it from. (Copying would also
            // duplicate the very file the cap refused, and would fail
            // outright on the permission errors that land here beside it.)
            LOG.log(java.util.logging.Level.WARNING,
                    "Unreadable {0}; the workspace is read-only until it can be read ({1})",
                    new Object[]{f, unreadable.getMessage()});
            return new LoadOutcome(null, null, true);
        }
        if (org.nmox.studio.core.util.MergeConflicts.hasMarkers(text)) {
            // git's unresolved merge (3.4): both people's work is IN this
            // file, so it is neither corrupt nor ours to repair. Nothing is
            // copied aside and nothing is parsed; the studio binds
            // read-only until the file changes on disk (resolved in git),
            // and the file pulse reloads it then.
            LOG.log(java.util.logging.Level.WARNING,
                    "{0} has unresolved merge conflicts; read-only until they are resolved", f);
            return new LoadOutcome(null, null, false, true, false);
        }
        try {
            List<String> newer = new ArrayList<>();
            Workspace parsed = parse(text, newer);
            if (!newer.isEmpty()) {
                LOG.log(java.util.logging.Level.WARNING,
                        "{0} holds values a newer NMOX Studio wrote ({1}); read-only",
                        new Object[]{f, newer});
            }
            return new LoadOutcome(parsed, null, false, false, !newer.isEmpty());
        } catch (RuntimeException malformed) {
            LOG.log(java.util.logging.Level.WARNING,
                    "Malformed {0}; keeping a .bak and starting empty ({1})",
                    new Object[]{FILENAME, malformed.getMessage()});
            return new LoadOutcome(null, backupCorrupt(f), false);
        }
    }

    /**
     * Copies the corrupt file aside — {@code <name>.bak}, or the first
     * free numbered sibling when an earlier rescue holds that name (3.4: a
     * second rescue used to overwrite the first); null when even that fails.
     */
    private static File backupCorrupt(File file) {
        try {
            return org.nmox.studio.core.util.KeptCopies.copyAside(file);
        } catch (IOException e) {
            LOG.log(java.util.logging.Level.SEVERE, "Could not back up corrupt " + file, e);
            return null;
        }
    }

    /** Indents a JSON body for the response viewer; non-JSON passes through. */
    public static String pretty(String body) {
        return org.nmox.studio.core.util.JsonUtil.pretty(body);
    }

    /**
     * The pretty of the WORKER thread. The response viewer used to call
     * {@link #pretty} on the EDT for every send: a multi-megabyte body
     * froze the paint thread for the length of the re-parse, and a
     * deeply-nested one threw {@link StackOverflowError} (an Error the
     * parse's RuntimeException guard never catches) straight through
     * it. Bodies past the size guard show raw — a viewer doesn't owe a
     * 2MB+ payload indentation — and a recursion blowup degrades to
     * raw instead of killing the thread.
     */
    public static String prettyForDisplay(String body) {
        if (body == null) {
            return "";
        }
        if (body.length() > 2_000_000) {
            return body;
        }
        try {
            return pretty(body);
        } catch (StackOverflowError deeplyNested) {
            // org.json descends one stack frame per nesting level
            return body;
        }
    }
}
