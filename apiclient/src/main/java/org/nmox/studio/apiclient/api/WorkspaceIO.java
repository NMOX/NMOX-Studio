package org.nmox.studio.apiclient.api;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nmox.studio.apiclient.model.ApiModel.Assertion;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;
import org.nmox.studio.apiclient.model.ApiModel.Collection;
import org.nmox.studio.apiclient.model.ApiModel.Environment;
import org.nmox.studio.apiclient.model.ApiModel.Pair;
import org.nmox.studio.apiclient.model.ApiModel.Request;
import org.nmox.studio.apiclient.model.ApiModel.Workspace;
import org.nmox.studio.core.util.AtomicFiles;

/**
 * Reads and writes the workspace as {@code .nmoxapi.json} beside the
 * project - the file is meant to be committed and shared, so it never
 * carries secrets by policy: environment values that look like tokens
 * are the developer's to keep in a git-ignored env file, mirroring the
 * rack's ATMOS rule. (Here we persist what the user typed; the UI warns
 * that secrets belong in variables kept out of source control.)
 */
public final class WorkspaceIO {

    public static final String FILENAME = ".nmoxapi.json";

    private WorkspaceIO() {
    }

    public static String toJson(Workspace w) {
        JSONObject root = new JSONObject();
        root.put("version", 1);
        root.put("activeEnvironment", w.activeEnvironment);

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

    private static JSONArray pairsJson(java.util.List<Pair> pairs) {
        JSONArray arr = new JSONArray();
        for (Pair p : pairs) {
            arr.put(new JSONObject().put("name", p.name == null ? "" : p.name)
                    .put("value", p.value == null ? "" : p.value)
                    .put("enabled", p.enabled));
        }
        return arr;
    }

    public static Workspace fromJson(String json) {
        Workspace w = new Workspace();
        JSONObject root = new JSONObject(json);
        w.activeEnvironment = root.optString("activeEnvironment", "");
        JSONArray cols = root.optJSONArray("collections");
        if (cols != null) {
            for (int i = 0; i < cols.length(); i++) {
                w.collections.add(collection(cols.getJSONObject(i)));
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
        return w;
    }

    private static Collection collection(JSONObject cj) {
        Collection c = new Collection();
        c.name = cj.optString("name", "collection");
        JSONArray reqs = cj.optJSONArray("requests");
        if (reqs != null) {
            for (int i = 0; i < reqs.length(); i++) {
                c.requests.add(request(reqs.getJSONObject(i)));
            }
        }
        return c;
    }

    private static Request request(JSONObject rj) {
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
        try {
            r.authType = AuthType.valueOf(rj.optString("authType", "NONE"));
        } catch (IllegalArgumentException ignored) {
            r.authType = AuthType.NONE;
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
                } catch (IllegalArgumentException ignored) {
                    // unknown assertion kind from a newer file: skip it
                }
            }
        }
        return r;
    }

    private static void readPairs(JSONArray arr, java.util.List<Pair> into) {
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
        return fromJson(Files.readString(f.toPath(), StandardCharsets.UTF_8));
    }

    /**
     * A guarded load: {@code workspace} is null when the file is
     * missing or unreadable (callers substitute the starter);
     * {@code backup} is non-null when the file EXISTED but failed to
     * parse and was copied aside first.
     */
    public record LoadOutcome(Workspace workspace, File backup) {
    }

    /**
     * Loads like {@link #load}, but guards the user's file against the
     * corrupt-load → empty-model → autosave-clobbers-original sequence:
     * when the file exists and fails to parse, the unreadable original
     * is copied to {@code .nmoxapi.json.bak} BEFORE the empty outcome
     * is returned, so the studio's next autosave can never destroy the
     * only copy. A missing file makes no backup; I/O failures still
     * throw — there is nothing readable to back up.
     */
    public static LoadOutcome loadGuarded(File dir) throws IOException {
        File f = new File(dir, FILENAME);
        if (!f.isFile()) {
            return new LoadOutcome(null, null);
        }
        String text = Files.readString(f.toPath(), StandardCharsets.UTF_8);
        try {
            return new LoadOutcome(fromJson(text), null);
        } catch (RuntimeException malformed) {
            java.util.logging.Logger.getLogger(WorkspaceIO.class.getName()).log(
                    java.util.logging.Level.WARNING,
                    "Malformed {0}; keeping a .bak and starting empty ({1})",
                    new Object[]{FILENAME, malformed.getMessage()});
            File backup = new File(dir, FILENAME + ".bak");
            Files.copy(f.toPath(), backup.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return new LoadOutcome(null, backup);
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
