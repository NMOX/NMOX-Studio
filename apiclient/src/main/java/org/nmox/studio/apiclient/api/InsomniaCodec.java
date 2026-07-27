package org.nmox.studio.apiclient.api;

import java.util.ArrayList;
import java.util.HashMap;
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
 * Reads an Insomnia v4 export ("Export Data" JSON) into API Studio
 * requests — the third migration source after Postman and the browser.
 * Pure text work — no network, no IO.
 *
 * <p>Insomnia's flat {@code resources[]} list becomes structure again
 * by chasing {@code parentId} chains: request-group ancestry turns
 * into "Folder / Request" names, exactly like the Postman import.
 * Insomnia's template syntax {@code {{ _.name }}} is rewritten to API
 * Studio's {@code {{name}}}; the base environment's plain values come
 * along for the never-clobber environment offer. The secrets law
 * holds at the border: bearer/basic authentication lands in the
 * keychain-backed Auth field, never a plaintext header row, and other
 * auth types are named as not-imported rather than mangled.
 */
public final class InsomniaCodec {

    private InsomniaCodec() {
    }

    public record Imported(String name, List<ApiModel.Request> requests,
                           Map<String, String> variables, List<String> notes) {
    }

    public static Imported parse(String text) {
        JSONObject root;
        try {
            root = new JSONObject(text);
        } catch (JSONException ex) {
            throw new IllegalArgumentException("Not JSON: " + ex.getMessage());
        }
        JSONArray resources = root.optJSONArray("resources");
        if (resources == null || !"export".equals(root.optString("_type"))) {
            throw new IllegalArgumentException("Not an Insomnia export — "
                    + "expected {_type: \"export\", resources: […]} (Insomnia ▸ "
                    + "Preferences ▸ Data ▸ Export).");
        }

        // first pass: name every container so parent chains resolve
        Map<String, JSONObject> byId = new HashMap<>();
        String workspaceName = "Insomnia import";
        for (int i = 0; i < resources.length(); i++) {
            JSONObject r = resources.optJSONObject(i);
            if (r == null) {
                continue;
            }
            byId.put(r.optString("_id"), r);
            if ("workspace".equals(r.optString("_type"))) {
                String n = r.optString("name", "");
                if (!n.isBlank()) {
                    workspaceName = n;
                }
            }
        }

        List<ApiModel.Request> requests = new ArrayList<>();
        Map<String, String> variables = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();

        for (int i = 0; i < resources.length(); i++) {
            JSONObject r = resources.optJSONObject(i);
            if (r == null) {
                continue;
            }
            switch (r.optString("_type")) {
                case "request" -> requests.add(request(r, byId, notes));
                case "environment" -> {
                    // the base environment hangs off the workspace; sub
                    // environments hang off the base. Plain values from
                    // ALL of them merge first-wins, which favors the base.
                    JSONObject data = r.optJSONObject("data");
                    if (data != null) {
                        for (String key : data.keySet()) {
                            Object v = data.get(key);
                            if (v instanceof String || v instanceof Number
                                    || v instanceof Boolean) {
                                variables.putIfAbsent(key, String.valueOf(v));
                            }
                        }
                    }
                }
                case "websocket_request" -> note(notes,
                        "WebSocket requests not imported — API Studio speaks HTTP.");
                case "grpc_request" -> note(notes,
                        "gRPC requests not imported — API Studio speaks HTTP.");
                default -> {
                }
            }
        }
        if (requests.isEmpty()) {
            throw new IllegalArgumentException("No HTTP requests found in this export.");
        }
        return new Imported(workspaceName, requests, variables, notes);
    }

    private static ApiModel.Request request(JSONObject req,
            Map<String, JSONObject> byId, List<String> notes) {
        ApiModel.Request r = new ApiModel.Request();
        r.method = req.optString("method", "GET").toUpperCase(Locale.ROOT);
        r.url = templates(req.optString("url", ""));

        JSONArray params = req.optJSONArray("parameters");
        if (params != null) {
            for (int i = 0; i < params.length(); i++) {
                JSONObject p = params.optJSONObject(i);
                if (p == null || p.optString("name", "").isBlank()) {
                    continue;
                }
                Pair pair = new Pair(p.getString("name"),
                        templates(p.optString("value", "")));
                pair.enabled = !p.optBoolean("disabled", false);
                r.params.add(pair);
            }
        }

        JSONArray headers = req.optJSONArray("headers");
        if (headers != null) {
            for (int i = 0; i < headers.length(); i++) {
                JSONObject h = headers.optJSONObject(i);
                if (h == null || h.optString("name", "").isBlank()) {
                    continue;
                }
                Pair pair = new Pair(h.getString("name"),
                        templates(h.optString("value", "")));
                pair.enabled = !h.optBoolean("disabled", false);
                r.headers.add(pair);
            }
        }

        JSONObject auth = req.optJSONObject("authentication");
        if (auth != null && !auth.optBoolean("disabled", false)) {
            switch (auth.optString("type", "")) {
                case "" -> {
                }
                case "bearer" -> {
                    String token = templates(auth.optString("token", ""));
                    if (!token.isEmpty()) {
                        r.authType = AuthType.BEARER;
                        r.authToken = token;
                    }
                }
                case "basic" -> {
                    String user = templates(auth.optString("username", ""));
                    String pass = templates(auth.optString("password", ""));
                    if (!user.isEmpty() || !pass.isEmpty()) {
                        r.authType = AuthType.BASIC;
                        r.authToken = user + ":" + pass;
                    }
                }
                default -> note(notes, "Auth type \"" + auth.optString("type")
                        + "\" not imported — set the request's Auth field by hand.");
            }
        }

        JSONObject body = req.optJSONObject("body");
        if (body != null) {
            String mime = body.optString("mimeType", "");
            if (mime.toLowerCase(Locale.ROOT).startsWith("multipart/")) {
                note(notes, r.method + " " + r.url
                        + ": multipart body not imported (same stance as the "
                        + "curl import).");
            } else {
                r.body = templates(body.optString("text", ""));
                if (!mime.isBlank() && !r.body.isBlank() && r.headers.stream()
                        .noneMatch(h -> "Content-Type".equalsIgnoreCase(h.name))) {
                    r.headers.add(new Pair("Content-Type", mime));
                }
            }
        }

        String own = req.optString("name", "").trim();
        if (own.isEmpty()) {
            own = r.url.replaceFirst("^https?://", "");
        }
        String prefix = folderPath(req.optString("parentId"), byId);
        r.name = prefix.isEmpty() ? own : prefix + " / " + own;
        if (r.name.length() > 48) {
            r.name = r.name.substring(0, 48) + "…";
        }
        return r;
    }

    /** Walks request_group ancestry into "Outer / Inner"; cycles bail. */
    private static String folderPath(String parentId, Map<String, JSONObject> byId) {
        List<String> chain = new ArrayList<>();
        String id = parentId;
        for (int hops = 0; hops < 16 && id != null; hops++) {
            JSONObject parent = byId.get(id);
            if (parent == null || !"request_group".equals(parent.optString("_type"))) {
                break;
            }
            String n = parent.optString("name", "").trim();
            if (!n.isEmpty()) {
                chain.add(0, n);
            }
            id = parent.optString("parentId", null);
        }
        return String.join(" / ", chain);
    }

    /** Insomnia's {@code {{ _.name }}} becomes API Studio's {@code {{name}}}. */
    static String templates(String text) {
        return text == null ? ""
                : text.replaceAll("\\{\\{\\s*_\\.([A-Za-z0-9_]+)\\s*\\}\\}", "{{$1}}");
    }

    private static void note(List<String> notes, String note) {
        if (!notes.contains(note)) {
            notes.add(note);
        }
    }
}
