package org.nmox.studio.apiclient.api;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nmox.studio.apiclient.model.ApiModel;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;
import org.nmox.studio.apiclient.model.ApiModel.Pair;

/**
 * Reads a HAR 1.2 archive (browser devtools' "Save all as HAR") into
 * API Studio requests — the fastest path from "I watched my app do it
 * in the Network tab" to "I can replay and assert on it". Pure text
 * work — no network, no IO.
 *
 * <p>A HAR of a real session is full of things a request workbench
 * must NOT keep: session cookies, live Authorization headers, and
 * hundreds of page-asset fetches. The rules, in the order they bite:
 * {@code Cookie} headers are dropped and counted (a captured session
 * cookie in a committable workspace file is a leaked credential);
 * {@code Authorization} Bearer/Basic is lifted into the keychain-backed
 * Auth field (the v1.97.0 secrets law); when the capture marks resource
 * types (Chrome's {@code _resourceType}), only XHR/fetch entries import
 * and the page assets are counted out loud; repeated method+URL pairs
 * collapse to the first occurrence; and non-replayable schemes
 * ({@code data:}, {@code blob:}, {@code ws:}) are skipped by name.
 */
public final class HarCodec {

    /** A page load can carry thousands of entries; this is a workbench. */
    public static final int MAX_REQUESTS = 200;
    /** Enough body to replay any hand-made API call. */
    public static final int MAX_BODY_CHARS = 100_000;

    private HarCodec() {
    }

    public record Imported(List<ApiModel.Request> requests, List<String> notes) {
    }

    public static Imported parse(String text) {
        JSONObject root;
        try {
            root = new JSONObject(text);
        } catch (JSONException ex) {
            throw new IllegalArgumentException("Not JSON: " + ex.getMessage());
        }
        JSONObject log = root.optJSONObject("log");
        JSONArray entries = log != null ? log.optJSONArray("entries") : null;
        if (entries == null) {
            throw new IllegalArgumentException(
                    "Not a HAR file — expected log.entries.");
        }

        // Chrome tags entries with _resourceType; when ANY entry carries
        // it, we can separate the API traffic from the page assets
        boolean typed = false;
        for (int i = 0; i < entries.length() && !typed; i++) {
            JSONObject e = entries.optJSONObject(i);
            typed = e != null && e.has("_resourceType");
        }

        List<ApiModel.Request> requests = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int assets = 0, dupes = 0, cookies = 0, unreplayable = 0, overflow = 0;

        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            JSONObject req = entry != null ? entry.optJSONObject("request") : null;
            if (req == null) {
                continue;
            }
            if (typed) {
                String type = entry.optString("_resourceType", "");
                if (!"xhr".equals(type) && !"fetch".equals(type)) {
                    assets++;
                    continue;
                }
            }
            String url = req.optString("url", "");
            String scheme = url.indexOf(':') > 0
                    ? url.substring(0, url.indexOf(':')).toLowerCase(Locale.ROOT) : "";
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                unreplayable++;
                continue;
            }
            String method = req.optString("method", "GET").toUpperCase(Locale.ROOT);
            if (!seen.add(method + " " + url)) {
                dupes++;
                continue;
            }
            if (requests.size() >= MAX_REQUESTS) {
                overflow++;
                continue;
            }
            ApiModel.Request r = new ApiModel.Request();
            r.method = method;
            int q = url.indexOf('?');
            r.url = q < 0 ? url : url.substring(0, q);
            r.name = r.method + " " + r.url.replaceFirst("^https?://", "");
            if (r.name.length() > 48) {
                r.name = r.name.substring(0, 48) + "…";
            }

            JSONArray query = req.optJSONArray("queryString");
            if (query != null) {
                for (int j = 0; j < query.length(); j++) {
                    JSONObject p = query.optJSONObject(j);
                    if (p != null && p.has("name")) {
                        r.params.add(new Pair(p.getString("name"),
                                p.optString("value", "")));
                    }
                }
            }

            cookies += headers(req.optJSONArray("headers"), r, notes);
            body(req.optJSONObject("postData"), r, notes);
            requests.add(r);
        }

        if (requests.isEmpty()) {
            throw new IllegalArgumentException(typed && assets > 0
                    ? "No XHR/fetch entries found — this capture is all page assets."
                    : "No importable requests found in this HAR.");
        }
        if (assets > 0) {
            notes.add(assets + " page-asset entries skipped (kept XHR/fetch only).");
        }
        if (dupes > 0) {
            notes.add(dupes + " repeated method+URL entries collapsed.");
        }
        if (cookies > 0) {
            notes.add(cookies + " Cookie header" + (cookies == 1 ? "" : "s")
                    + " dropped — a captured session cookie is a credential and "
                    + "never lands in the workspace file.");
        }
        if (unreplayable > 0) {
            notes.add(unreplayable + " non-HTTP entries (data:/blob:/ws:) skipped.");
        }
        if (overflow > 0) {
            notes.add(overflow + " entries beyond the first " + MAX_REQUESTS
                    + " not imported.");
        }
        return new Imported(requests, notes);
    }

    /** @return how many Cookie headers were dropped */
    private static int headers(JSONArray headers, ApiModel.Request r, List<String> notes) {
        if (headers == null) {
            return 0;
        }
        int cookies = 0;
        for (int i = 0; i < headers.length(); i++) {
            JSONObject h = headers.optJSONObject(i);
            if (h == null) {
                continue;
            }
            String name = h.optString("name", "");
            String value = h.optString("value", "");
            if (name.isBlank() || name.startsWith(":")) {
                continue; // HTTP/2 pseudo-headers aren't request headers
            }
            if ("Cookie".equalsIgnoreCase(name)) {
                cookies++;
                continue;
            }
            // recomputed by any client at send time; replaying stale copies
            // breaks requests
            if ("Host".equalsIgnoreCase(name) || "Content-Length".equalsIgnoreCase(name)
                    || "Connection".equalsIgnoreCase(name)
                    || "Accept-Encoding".equalsIgnoreCase(name)) {
                continue;
            }
            if ("Authorization".equalsIgnoreCase(name)) {
                if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    r.authType = AuthType.BEARER;
                    r.authToken = value.substring(7).trim();
                    continue;
                }
                if (value.regionMatches(true, 0, "Basic ", 0, 6)) {
                    try {
                        String decoded = new String(
                                java.util.Base64.getDecoder().decode(value.substring(6).trim()),
                                java.nio.charset.StandardCharsets.UTF_8);
                        if (decoded.contains(":")) {
                            r.authType = AuthType.BASIC;
                            r.authToken = decoded;
                            continue;
                        }
                    } catch (IllegalArgumentException notBase64) {
                        // fall through to the drop below
                    }
                }
                // 2026-07-26 review find: a HAR is a RECORDING, so any
                // Authorization value in it is a live credential. The
                // liftable schemes go to the keychain above; everything
                // else ("Token …", AWS SigV4, an opaque blob) follows the
                // Cookie rule — dropped and counted, never a plaintext row
                // in the committable workspace file. This deliberately
                // differs from the curl import, where the user TYPED the
                // header and keeping it is honoring their input.
                if (!notes.contains(AUTH_NOTE)) {
                    notes.add(AUTH_NOTE);
                }
                continue;
            }
            r.headers.add(new Pair(name, value));
        }
        return cookies;
    }

    private static final String AUTH_NOTE = "A captured Authorization header "
            + "that isn't Bearer/Basic was DROPPED — recorded credentials never "
            + "land in the workspace file; set the request's Auth field yourself.";

    private static void body(JSONObject postData, ApiModel.Request r, List<String> notes) {
        if (postData == null) {
            return;
        }
        String mime = postData.optString("mimeType", "");
        if (mime.toLowerCase(Locale.ROOT).startsWith("multipart/")) {
            notes.add(r.method + " " + r.url
                    + ": multipart body not imported (same stance as the curl import).");
            return;
        }
        String text = postData.optString("text", "");
        if (text.length() > MAX_BODY_CHARS) {
            // an over-cap body is REFUSED, not truncated: a silently cut
            // payload would replay as a DIFFERENT request and "work"
            notes.add(r.method + " " + r.url + ": body over "
                    + MAX_BODY_CHARS + " chars not imported — paste it in if you "
                    + "really need it.");
            return;
        }
        r.body = text;
        if (!mime.isBlank() && r.headers.stream()
                .noneMatch(h -> "Content-Type".equalsIgnoreCase(h.name))) {
            r.headers.add(new Pair("Content-Type", mime));
        }
    }
}
