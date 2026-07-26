package org.nmox.studio.apiclient.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.nmox.studio.apiclient.model.ApiModel;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;
import org.nmox.studio.apiclient.model.ApiModel.Pair;

/**
 * Reads the {@code .http}/{@code .rest} request-file dialect (the VS
 * Code REST Client / JetBrains HTTP-client format) into API Studio
 * requests. Pure text work — no network, no IO.
 *
 * <p>The dialect maps almost one-to-one onto the studio's own model:
 * requests separated by {@code ###} (the separator line's trailing text
 * is the request name), a {@code METHOD url [HTTP/x]} start line,
 * headers until the first blank line, body after it, {@code #} and
 * {@code //} comments, and — the happy accident that makes this import
 * lossless — {@code {{variables}}} in exactly API Studio's syntax, kept
 * verbatim. File-level {@code @name = value} definitions are returned
 * separately so the UI can offer them into an environment. An
 * {@code Authorization: Bearer/Basic} header is lifted into the Auth
 * field, same as the curl import (the v1.97.0 secrets law).
 */
public final class HttpFileCodec {

    private HttpFileCodec() {
    }

    /** Parsed requests plus any file-level {@code @var = value} definitions. */
    public record Imported(List<ApiModel.Request> requests,
                           Map<String, String> variables,
                           List<String> notes) {
    }

    private static final java.util.Set<String> METHODS = java.util.Set.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE");

    public static Imported parse(String text) {
        List<ApiModel.Request> requests = new ArrayList<>();
        Map<String, String> variables = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();

        String pendingName = null;
        ApiModel.Request cur = null;
        boolean inBody = false;
        StringBuilder body = new StringBuilder();

        for (String rawLine : text.split("\n", -1)) {
            String line = rawLine.stripTrailing();
            if (line.startsWith("###")) {
                finish(requests, cur, body, notes);
                cur = null;
                inBody = false;
                String name = line.substring(3).trim();
                pendingName = name.isEmpty() ? null : name;
                continue;
            }
            if (cur == null || !inBody) {
                // comments only count outside the body — a JSON body line
                // could legitimately start with //
                String t = line.trim();
                if (t.startsWith("#") || t.startsWith("//")) {
                    continue;
                }
                if (cur == null && t.startsWith("@")) {
                    int eq = t.indexOf('=');
                    if (eq > 1) {
                        variables.put(t.substring(1, eq).trim(), t.substring(eq + 1).trim());
                    } else {
                        notes.add("Ignored malformed variable line: " + t);
                    }
                    continue;
                }
            }
            if (cur == null) {
                if (line.isBlank()) {
                    continue;
                }
                cur = startLine(line, pendingName, notes);
                pendingName = null;
                continue;
            }
            if (!inBody) {
                if (line.isBlank()) {
                    inBody = true;
                    continue;
                }
                int colon = line.indexOf(':');
                if (colon < 1) {
                    notes.add("Ignored malformed header line: " + line.trim());
                    continue;
                }
                liftOrAdd(cur, line.substring(0, colon).trim(),
                        line.substring(colon + 1).trim(), notes);
                continue;
            }
            body.append(rawLine).append('\n');
        }
        finish(requests, cur, body, notes);
        if (requests.isEmpty()) {
            throw new IllegalArgumentException(
                    "No requests found — expected a 'METHOD url' line.");
        }
        return new Imported(requests, variables, notes);
    }

    private static ApiModel.Request startLine(String line, String name, List<String> notes) {
        String[] parts = line.trim().split("\\s+");
        ApiModel.Request r = new ApiModel.Request();
        if (parts.length >= 2 && METHODS.contains(parts[0].toUpperCase(java.util.Locale.ROOT))) {
            r.method = parts[0].toUpperCase(java.util.Locale.ROOT);
            r.url = parts[1];
            if (parts.length > 2 && !parts[2].toUpperCase(java.util.Locale.ROOT).startsWith("HTTP/")) {
                notes.add("Ignored trailing text on request line: " + parts[2]);
            }
        } else {
            // the dialect allows a bare URL line meaning GET
            r.method = "GET";
            r.url = parts[0];
        }
        // unnamed requests: no method prefix — the tree renderer adds it
        r.name = name != null ? name
                : r.url.replaceFirst("^https?://", "");
        if (r.name.length() > 48) {
            r.name = r.name.substring(0, 48) + "…";
        }
        return r;
    }

    /** Same Authorization lift as the curl import — secrets go keychain-side. */
    private static void liftOrAdd(ApiModel.Request r, String name, String value,
            List<String> notes) {
        if ("Authorization".equalsIgnoreCase(name)) {
            if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
                r.authType = AuthType.BEARER;
                r.authToken = value.substring(7).trim();
                return;
            }
            if (value.regionMatches(true, 0, "Basic ", 0, 6)) {
                String rest = value.substring(6).trim();
                // the dialect allows both raw base64 AND "Basic user pass"
                if (rest.contains(" ")) {
                    r.authType = AuthType.BASIC;
                    r.authToken = rest.replaceFirst("\\s+", ":");
                    return;
                }
                try {
                    String decoded = new String(java.util.Base64.getDecoder().decode(rest),
                            java.nio.charset.StandardCharsets.UTF_8);
                    if (decoded.contains(":")) {
                        r.authType = AuthType.BASIC;
                        r.authToken = decoded;
                        return;
                    }
                } catch (IllegalArgumentException notBase64) {
                    // fall through: may be a {{var}} — keep as a header
                }
            }
            notes.add("Authorization header kept as a header — consider the Auth "
                    + "field, which stores the secret in the OS keychain.");
        }
        r.headers.add(new Pair(name, value));
    }

    private static void finish(List<ApiModel.Request> requests, ApiModel.Request cur,
            StringBuilder body, List<String> notes) {
        if (cur == null) {
            body.setLength(0);
            return;
        }
        String b = body.toString().strip();
        if (b.startsWith("<")) {
            // "< ./payload.json" pulls a file from disk in the dialect —
            // importing would surprise-read files; refuse into a note
            notes.add("Body file reference (" + b.split("\n", 2)[0]
                    + ") not imported — paste the payload into the request.");
            b = "";
        }
        cur.body = b;
        body.setLength(0);
        requests.add(cur);
    }
}
