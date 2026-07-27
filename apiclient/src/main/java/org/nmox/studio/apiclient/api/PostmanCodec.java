package org.nmox.studio.apiclient.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nmox.studio.apiclient.model.ApiModel;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;
import org.nmox.studio.apiclient.model.ApiModel.Pair;

/**
 * Reads a Postman Collection (format v2.0/v2.1 JSON — the export every
 * Postman user has on disk) into API Studio requests. Pure text work —
 * no network, no IO.
 *
 * <p>The mapping is unusually direct because Postman's {@code
 * {{variable}}} syntax IS API Studio's: variables import verbatim and
 * resolve against the active environment. Folders flatten into
 * "Folder / Request" names. Auth follows the v1.97.0 secrets law:
 * bearer tokens and basic credentials land in the keychain-backed Auth
 * field, never as a plaintext header row; collection-level auth is
 * inherited by requests that declare none, exactly as Postman resolves
 * it.
 *
 * <p>Honest refusals, not silent mangling: multipart form-data and
 * file bodies are noted and skipped (same stance as the curl import),
 * Postman scripts are counted and named as not-runnable-here, v1
 * collections and environment files are refused with the fix spelled
 * out.
 */
public final class PostmanCodec {

    private PostmanCodec() {
    }

    /** The collection, its requests, and its collection-level variables. */
    public record Imported(String name, List<ApiModel.Request> requests,
                           Map<String, String> variables, List<String> notes) {
    }

    public static Imported parse(String text) {
        JSONObject root;
        try {
            root = new JSONObject(text);
        } catch (JSONException ex) {
            throw new IllegalArgumentException(
                    "Not JSON: " + ex.getMessage());
        }
        // a Postman ENVIRONMENT export is {name, values:[...]} — close
        // enough to confuse, different enough to mangle
        if (root.has("values") && !root.has("item")) {
            throw new IllegalArgumentException("This is a Postman environment "
                    + "file, not a collection — use Import… ▸ Postman Environment…");
        }
        // the v1 format is {id, name, requests:[...]} — long dead in
        // Postman itself, still floating around in old repos
        if (root.has("requests") && !root.has("item")) {
            throw new IllegalArgumentException("This is a Postman v1 collection. "
                    + "Re-export it from Postman as Collection v2.1 and import that.");
        }
        JSONObject info = root.optJSONObject("info");
        if (info != null && info.optString("schema", "").contains("/v1")) {
            throw new IllegalArgumentException("Postman collection schema v1 is "
                    + "not supported — re-export as Collection v2.1.");
        }

        String name = info != null && !info.optString("name", "").isBlank()
                ? info.getString("name") : "Postman import";
        List<ApiModel.Request> requests = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        int[] scripts = {0};

        walk(root.optJSONArray("item"), "", root.optJSONObject("auth"),
                requests, notes, scripts);
        if (requests.isEmpty()) {
            throw new IllegalArgumentException(
                    "No requests found in this collection.");
        }
        if (scripts[0] > 0) {
            notes.add(scripts[0] + " Postman script" + (scripts[0] == 1 ? "" : "s")
                    + " (pre-request/tests) not imported — the Postman sandbox "
                    + "doesn't run here; re-create checks on the Tests tab.");
        }

        Map<String, String> variables = new LinkedHashMap<>();
        JSONArray vars = root.optJSONArray("variable");
        if (vars != null) {
            for (int i = 0; i < vars.length(); i++) {
                JSONObject v = vars.optJSONObject(i);
                if (v != null && v.has("key") && !v.optBoolean("disabled", false)) {
                    variables.put(v.getString("key"), v.optString("value", ""));
                }
            }
        }
        return new Imported(name, requests, variables, notes);
    }

    /** A Postman environment: its name, its importable values, notes. */
    public record ImportedEnvironment(String name, Map<String, String> values,
                                      List<String> notes) {
    }

    /**
     * Reads a Postman ENVIRONMENT export ({@code {name, values:[…]}}).
     * Two kinds of value never import: disabled rows (skipped and
     * counted), and rows Postman marks {@code "type":"secret"} —
     * API Studio environments live in the COMMITTABLE .nmoxapi.json,
     * which is exactly where a secret must never land (v1.97.0). The
     * note tells the user where secrets belong instead: each request's
     * keychain-backed Auth field.
     */
    public static ImportedEnvironment parseEnvironment(String text) {
        JSONObject root;
        try {
            root = new JSONObject(text);
        } catch (JSONException ex) {
            throw new IllegalArgumentException("Not JSON: " + ex.getMessage());
        }
        JSONArray values = root.optJSONArray("values");
        if (values == null) {
            throw new IllegalArgumentException(root.has("item")
                    ? "This is a Postman COLLECTION — use Import… ▸ Postman Collection…"
                    : "Not a Postman environment file — expected {name, values:[…]}.");
        }
        String name = root.optString("name", "").isBlank()
                ? "Postman environment" : root.getString("name");
        Map<String, String> out = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();
        int disabled = 0, secrets = 0;
        for (int i = 0; i < values.length(); i++) {
            JSONObject v = values.optJSONObject(i);
            if (v == null || v.optString("key", "").isBlank()) {
                continue;
            }
            if (!v.optBoolean("enabled", true)) {
                disabled++;
                continue;
            }
            if ("secret".equals(v.optString("type"))) {
                secrets++;
                continue;
            }
            out.put(v.getString("key"), v.optString("value", ""));
        }
        if (secrets > 0) {
            notes.add(secrets + " secret-typed value" + (secrets == 1 ? "" : "s")
                    + " NOT imported — environments live in the committable "
                    + ".nmoxapi.json; put secrets in each request's Auth field "
                    + "(OS keychain).");
        }
        if (disabled > 0) {
            notes.add(disabled + " disabled value" + (disabled == 1 ? "" : "s")
                    + " skipped.");
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("No importable values in this "
                    + "environment" + (secrets > 0
                    ? " — all " + secrets + " are secret-typed, and secrets "
                    + "belong in the keychain-backed Auth field, not a "
                    + "committable environment." : "."));
        }
        return new ImportedEnvironment(name, out, notes);
    }

    /** Depth-first over items; folders contribute their name as a prefix. */
    private static void walk(JSONArray items, String prefix, JSONObject inheritedAuth,
            List<ApiModel.Request> out, List<String> notes, int[] scripts) {
        if (items == null) {
            return;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            countScripts(item.optJSONArray("event"), scripts);
            if (item.has("item")) { // a folder
                String folder = item.optString("name", "").trim();
                String next = folder.isEmpty() ? prefix
                        : prefix.isEmpty() ? folder : prefix + " / " + folder;
                JSONObject folderAuth = item.optJSONObject("auth");
                walk(item.getJSONArray("item"), next,
                        folderAuth != null ? folderAuth : inheritedAuth,
                        out, notes, scripts);
                continue;
            }
            if (!item.has("request")) {
                continue;
            }
            out.add(request(item, prefix, inheritedAuth, notes));
        }
    }

    private static ApiModel.Request request(JSONObject item, String prefix,
            JSONObject inheritedAuth, List<String> notes) {
        ApiModel.Request r = new ApiModel.Request();
        Object raw = item.get("request");
        JSONObject req = raw instanceof JSONObject o ? o : null;

        if (req == null) { // the string shorthand: "request": "https://…"
            r.method = "GET";
            r.url = pathVars(String.valueOf(raw));
        } else {
            r.method = req.optString("method", "GET").toUpperCase(Locale.ROOT);
            url(req.opt("url"), r);
            headers(req.optJSONArray("header"), r);
            JSONObject auth = req.optJSONObject("auth");
            auth(auth != null ? auth : inheritedAuth, r, notes);
            body(req.optJSONObject("body"), r, notes);
        }

        String own = item.optString("name", "").trim();
        if (own.isEmpty()) {
            own = r.url.replaceFirst("^https?://", "");
        }
        r.name = prefix.isEmpty() ? own : prefix + " / " + own;
        if (r.name.length() > 48) {
            r.name = r.name.substring(0, 48) + "…";
        }
        return r;
    }

    /** Postman's {@code /users/:id} path variables become {{id}} — API
     *  Studio's own syntax. Ports ({@code host:8080}) are untouched: the
     *  rewrite only fires on a colon directly after a slash. */
    private static String pathVars(String url) {
        return url.replaceAll("(?<=/):([A-Za-z_][A-Za-z0-9_]*)", "{{$1}}");
    }

    private static void url(Object url, ApiModel.Request r) {
        if (url instanceof String s) {
            splitQuery(pathVars(s), r, null);
            return;
        }
        if (!(url instanceof JSONObject u)) {
            return;
        }
        splitQuery(pathVars(u.optString("raw", "")), r, u.optJSONArray("query"));
    }

    /** The query string moves off the URL into the params grid, keeping
     *  Postman's per-param enabled/disabled state when it's declared. */
    private static void splitQuery(String raw, ApiModel.Request r, JSONArray query) {
        int q = raw.indexOf('?');
        r.url = q < 0 ? raw : raw.substring(0, q);
        if (query != null) {
            for (int i = 0; i < query.length(); i++) {
                JSONObject p = query.optJSONObject(i);
                if (p == null || !p.has("key")) {
                    continue;
                }
                Pair pair = new Pair(p.getString("key"), p.optString("value", ""));
                pair.enabled = !p.optBoolean("disabled", false);
                r.params.add(pair);
            }
            return;
        }
        if (q < 0) {
            return;
        }
        for (String part : raw.substring(q + 1).split("&")) {
            if (part.isBlank()) {
                continue;
            }
            int eq = part.indexOf('=');
            r.params.add(eq < 0 ? new Pair(part, "")
                    : new Pair(part.substring(0, eq), part.substring(eq + 1)));
        }
    }

    private static void headers(JSONArray headers, ApiModel.Request r) {
        if (headers == null) {
            return;
        }
        for (int i = 0; i < headers.length(); i++) {
            JSONObject h = headers.optJSONObject(i);
            if (h == null || h.optString("key", "").isBlank()) {
                continue;
            }
            Pair pair = new Pair(h.getString("key"), h.optString("value", ""));
            pair.enabled = !h.optBoolean("disabled", false);
            r.headers.add(pair);
        }
    }

    /** The secrets law at import time: bearer/basic go to the keychain-backed
     *  Auth field. An apikey stays a visible row — with a note saying so —
     *  because the model has no keychain slot for arbitrary header names. */
    private static void auth(JSONObject auth, ApiModel.Request r, List<String> notes) {
        if (auth == null) {
            return;
        }
        String type = auth.optString("type", "noauth");
        switch (type) {
            case "noauth" -> {
            }
            case "bearer" -> {
                String token = authValue(auth, "bearer", "token");
                if (!token.isEmpty()) {
                    r.authType = AuthType.BEARER;
                    r.authToken = token;
                }
            }
            case "basic" -> {
                String user = authValue(auth, "basic", "username");
                String pass = authValue(auth, "basic", "password");
                if (!user.isEmpty() || !pass.isEmpty()) {
                    r.authType = AuthType.BASIC;
                    r.authToken = user + ":" + pass;
                }
            }
            case "apikey" -> {
                String key = authValue(auth, "apikey", "key");
                String value = authValue(auth, "apikey", "value");
                if (key.isEmpty()) {
                    return;
                }
                if ("query".equals(authValue(auth, "apikey", "in"))) {
                    r.params.add(new Pair(key, value));
                } else {
                    r.headers.add(new Pair(key, value));
                }
                note(notes, "API-key auth imported as a plain " + key
                        + " row — it will be saved in .nmoxapi.json; move real "
                        + "secrets to the Auth field (OS keychain).");
            }
            default -> note(notes, "Auth type \"" + type
                    + "\" not imported — set the request's Auth field by hand.");
        }
    }

    /** v2.1 stores auth params as [{key,value}] arrays; v2.0 as an object. */
    private static String authValue(JSONObject auth, String section, String key) {
        Object sec = auth.opt(section);
        if (sec instanceof JSONArray a) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject e = a.optJSONObject(i);
                if (e != null && key.equals(e.optString("key"))) {
                    return e.optString("value", "");
                }
            }
            return "";
        }
        if (sec instanceof JSONObject o) {
            return o.optString(key, "");
        }
        return "";
    }

    private static void body(JSONObject body, ApiModel.Request r, List<String> notes) {
        if (body == null) {
            return;
        }
        switch (body.optString("mode", "")) {
            case "raw" -> {
                r.body = body.optString("raw", "");
                JSONObject options = body.optJSONObject("options");
                JSONObject rawOpt = options != null ? options.optJSONObject("raw") : null;
                if (rawOpt != null && "json".equals(rawOpt.optString("language"))
                        && r.headers.stream().noneMatch(
                                h -> "Content-Type".equalsIgnoreCase(h.name))) {
                    r.headers.add(new Pair("Content-Type", "application/json"));
                }
            }
            case "urlencoded" -> {
                JSONArray fields = body.optJSONArray("urlencoded");
                StringBuilder sb = new StringBuilder();
                if (fields != null) {
                    for (int i = 0; i < fields.length(); i++) {
                        JSONObject f = fields.optJSONObject(i);
                        if (f == null || f.optBoolean("disabled", false)
                                || !f.has("key")) {
                            continue;
                        }
                        if (sb.length() > 0) {
                            sb.append('&');
                        }
                        sb.append(f.getString("key")).append('=')
                                .append(f.optString("value", ""));
                    }
                }
                r.body = sb.toString();
                if (r.headers.stream().noneMatch(
                        h -> "Content-Type".equalsIgnoreCase(h.name))) {
                    r.headers.add(new Pair("Content-Type",
                            "application/x-www-form-urlencoded"));
                }
            }
            case "graphql" -> {
                JSONObject gql = body.optJSONObject("graphql");
                if (gql != null) {
                    // send exactly what Postman sends: a JSON envelope
                    JSONObject envelope = new JSONObject()
                            .put("query", gql.optString("query", ""));
                    String vars = gql.optString("variables", "");
                    if (!vars.isBlank()) {
                        try {
                            envelope.put("variables", new JSONObject(vars));
                        } catch (JSONException notJson) {
                            envelope.put("variables", vars);
                        }
                    }
                    r.body = envelope.toString(2);
                    if (r.headers.stream().noneMatch(
                            h -> "Content-Type".equalsIgnoreCase(h.name))) {
                        r.headers.add(new Pair("Content-Type", "application/json"));
                    }
                }
            }
            case "formdata" -> note(notes, r.method + " " + r.url
                    + ": multipart form-data body not imported (same stance "
                    + "as the curl import).");
            case "file" -> note(notes, r.method + " " + r.url
                    + ": file body not imported — paste the payload in.");
            default -> {
            }
        }
    }

    private static void countScripts(JSONArray events, int[] scripts) {
        if (events == null) {
            return;
        }
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null) {
                continue;
            }
            JSONObject script = e.optJSONObject("script");
            JSONArray exec = script != null ? script.optJSONArray("exec") : null;
            if (exec == null) {
                continue;
            }
            for (int j = 0; j < exec.length(); j++) {
                if (!exec.optString(j, "").isBlank()) {
                    scripts[0]++;
                    break;
                }
            }
        }
    }

    private static void note(List<String> notes, String note) {
        if (!notes.contains(note)) {
            notes.add(note);
        }
    }
}
