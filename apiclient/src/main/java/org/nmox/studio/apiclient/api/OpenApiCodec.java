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
import org.nmox.studio.apiclient.model.ApiModel.Pair;

/**
 * Reads an OpenAPI 3.x document (JSON form) into an API Studio
 * collection: one request per path+operation, the first server URL
 * offered as the {@code {{baseUrl}}} variable, query/header parameters
 * landing in the request tables, and JSON request-body examples
 * becoming the body. Pure text work — no network, no IO, no $ref
 * chasing beyond what an example field already spells out.
 *
 * <p>Both spec syntaxes import: JSON directly, and (since v1.191.0)
 * YAML through SnakeYAML's SafeConstructor — scalars, maps and lists
 * only, so a hostile {@code !!tag} is refused, never instantiated —
 * feeding the same pipeline so the two front doors cannot drift.
 * Honest limits, refused or noted rather than guessed: Swagger 2.0
 * (convert to OpenAPI 3), schema-only request bodies (imported as
 * {@code {}} with a note), and security schemes (noted — a spec never
 * carries the actual token, so the Auth field is left for the user's
 * own secret).
 */
public final class OpenApiCodec {

    private OpenApiCodec() {
    }

    /** The parsed collection, its variables, and what the import skipped. */
    public record Imported(String title, List<ApiModel.Request> requests,
                           Map<String, String> variables, List<String> notes) {
    }

    private static final List<String> METHODS = List.of(
            "get", "put", "post", "delete", "options", "head", "patch", "trace");

    public static Imported parse(String text) {
        String head = text.stripLeading();
        JSONObject doc;
        if (head.startsWith("{")) {
            try {
                doc = new JSONObject(text);
            } catch (JSONException ex) {
                throw new IllegalArgumentException("Not valid JSON: " + ex.getMessage());
            }
        } else {
            // v1.191.0: the family's last honest refusal, closed — YAML
            // specs load through SnakeYAML's SafeConstructor (scalars,
            // maps, lists ONLY; a hostile !!tag throws instead of
            // instantiating) and join the SAME JSON pipeline below, so
            // the two front doors cannot drift apart.
            Object data;
            try {
                data = new org.yaml.snakeyaml.Yaml(
                        new org.yaml.snakeyaml.constructor.SafeConstructor(
                                new org.yaml.snakeyaml.LoaderOptions()))
                        .load(text);
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("Not valid YAML: " + ex.getMessage());
            }
            if (!(data instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException(
                        "Not an OpenAPI document — the YAML root must be a mapping.");
            }
            doc = new JSONObject(map);
        }
        if (doc.has("swagger")) {
            throw new IllegalArgumentException(
                    "Swagger " + doc.optString("swagger")
                    + " documents aren't supported — convert to OpenAPI 3.");
        }
        if (!doc.has("openapi")) {
            throw new IllegalArgumentException(
                    "Not an OpenAPI document — no 'openapi' version field.");
        }

        List<String> notes = new ArrayList<>();
        Map<String, String> variables = new LinkedHashMap<>();
        JSONArray servers = doc.optJSONArray("servers");
        if (servers != null && !servers.isEmpty()) {
            variables.put("baseUrl", servers.getJSONObject(0).optString("url", ""));
            if (servers.length() > 1) {
                notes.add("Document lists " + servers.length()
                        + " servers — imported the first as {{baseUrl}}.");
            }
        }

        String title = doc.optJSONObject("info") != null
                ? doc.getJSONObject("info").optString("title", "OpenAPI import")
                : "OpenAPI import";

        List<ApiModel.Request> requests = new ArrayList<>();
        JSONObject paths = doc.optJSONObject("paths");
        if (paths != null) {
            for (String path : paths.keySet()) {
                JSONObject item = paths.optJSONObject(path);
                if (item == null) {
                    continue;
                }
                for (String method : METHODS) {
                    JSONObject op = item.optJSONObject(method);
                    if (op != null) {
                        requests.add(request(path, method, op, item, notes));
                    }
                }
            }
        }
        if (requests.isEmpty()) {
            throw new IllegalArgumentException("The document declares no operations.");
        }
        if (doc.has("security") || doc.optJSONObject("components") != null
                && doc.getJSONObject("components").has("securitySchemes")) {
            notes.add("The API declares security schemes — a spec never carries "
                    + "the actual token, so set your secret in each request's Auth field.");
        }
        return new Imported(title, requests, variables, notes);
    }

    private static ApiModel.Request request(String path, String method,
            JSONObject op, JSONObject pathItem, List<String> notes) {
        ApiModel.Request r = new ApiModel.Request();
        r.method = method.toUpperCase(Locale.ROOT);
        // path templates {id} become {{id}} — API Studio's own variable
        // syntax, resolvable per environment
        r.url = "{{baseUrl}}" + path.replaceAll("\\{([^}]+)}", "{{$1}}");
        String summary = op.optString("summary", "");
        r.name = summary.isBlank() ? r.method + " " + path : summary;

        // parameters: operation-level wins, path-item-level fills in
        collectParams(pathItem.optJSONArray("parameters"), r);
        collectParams(op.optJSONArray("parameters"), r);

        JSONObject body = op.optJSONObject("requestBody");
        if (body != null) {
            JSONObject content = body.optJSONObject("content");
            JSONObject json = content != null ? content.optJSONObject("application/json") : null;
            if (json != null) {
                Object example = firstExample(json);
                if (example != null) {
                    r.body = example instanceof JSONObject o ? o.toString(2)
                            : example instanceof JSONArray a ? a.toString(2)
                            : String.valueOf(example);
                } else {
                    r.body = "{}";
                    notes.add(r.method + " " + path
                            + ": request body has a schema but no example — imported {}.");
                }
                r.headers.add(new Pair("Content-Type", "application/json"));
            } else if (content != null && !content.keySet().isEmpty()) {
                notes.add(r.method + " " + path + ": body content type "
                        + content.keySet().iterator().next() + " not imported.");
            }
        }
        return r;
    }

    private static void collectParams(JSONArray params, ApiModel.Request r) {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.length(); i++) {
            JSONObject p = params.optJSONObject(i);
            if (p == null || !p.has("name")) {
                continue; // a bare $ref — nothing local to import
            }
            String name = p.getString("name");
            String in = p.optString("in", "query");
            String example = p.has("example") ? String.valueOf(p.get("example")) : "";
            switch (in) {
                case "query" -> {
                    if (r.params.stream().noneMatch(x -> name.equals(x.name))) {
                        Pair pair = new Pair(name, example);
                        pair.enabled = p.optBoolean("required", false);
                        r.params.add(pair);
                    }
                }
                case "header" -> {
                    if (r.headers.stream().noneMatch(x -> name.equalsIgnoreCase(x.name))) {
                        r.headers.add(new Pair(name, example));
                    }
                }
                default -> {
                    // path params already ride the {{var}} URL; cookie params
                    // are rare enough to leave to the user
                }
            }
        }
    }

    private static Object firstExample(JSONObject mediaType) {
        if (mediaType.has("example")) {
            return mediaType.get("example");
        }
        JSONObject examples = mediaType.optJSONObject("examples");
        if (examples != null) {
            for (String key : examples.keySet()) {
                JSONObject ex = examples.optJSONObject(key);
                if (ex != null && ex.has("value")) {
                    return ex.get("value");
                }
            }
        }
        return null;
    }
}
