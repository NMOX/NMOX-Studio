package org.nmox.studio.apiclient.api;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Assembles what API Studio is willing to send KVASIR about a response —
 * and, more importantly, what it strips first.
 *
 * <p>Pure and side-effect free, because this text IS the disclosure the
 * consent dialog promises: it must be readable in one screen and
 * testable without a network. The redaction rules:
 *
 * <ul>
 *   <li><b>Credential headers never leave</b> — Authorization, Cookie,
 *       Set-Cookie, Proxy-Authorization, and any header whose name
 *       contains "token", "secret", "api-key" or "apikey" are dropped
 *       and counted, so the user sees that something was withheld
 *       rather than wondering.</li>
 *   <li><b>The URL keeps its shape, not its secrets</b> — query VALUES
 *       are replaced with {@code …}, since API keys ride query strings
 *       constantly; the parameter names stay because they are what makes
 *       a 400 explainable.</li>
 *   <li><b>The body is a capped prefix</b>, code-point-safe (the
 *       v1.149.0 lone-surrogate lesson), marked when truncated.</li>
 * </ul>
 */
public final class ResponseDisclosure {

    /** Enough of a body to diagnose an error; small enough to read. */
    public static final int MAX_BODY_CHARS = 4_000;

    private static final Set<String> DENIED = Set.of(
            "authorization", "cookie", "set-cookie", "proxy-authorization");
    private static final List<String> DENIED_FRAGMENTS =
            List.of("token", "secret", "api-key", "apikey", "password");

    private ResponseDisclosure() {
    }

    /** True when a header name must never be sent. */
    public static boolean isSensitive(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (DENIED.contains(lower)) {
            return true;
        }
        for (String fragment : DENIED_FRAGMENTS) {
            if (lower.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    /** The URL with query VALUES masked, path and parameter names intact. */
    public static String maskQuery(String url) {
        if (url == null) {
            return "";
        }
        int q = url.indexOf('?');
        if (q < 0) {
            return url;
        }
        StringBuilder sb = new StringBuilder(url.substring(0, q + 1));
        String[] pairs = url.substring(q + 1).split("&", -1);
        for (int i = 0; i < pairs.length; i++) {
            if (i > 0) {
                sb.append('&');
            }
            int eq = pairs[i].indexOf('=');
            sb.append(eq < 0 ? pairs[i] : pairs[i].substring(0, eq + 1) + "…");
        }
        return sb.toString();
    }

    /** A code-point-safe prefix: never splits a surrogate pair. */
    public static String cap(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        int end = text.offsetByCodePoints(0, text.codePointCount(0, max));
        return text.substring(0, end);
    }

    /**
     * The one-line summary shown verbatim in the consent dialog. Written
     * as the user would want to read it, and true by construction: the
     * same method's {@link #body} produces exactly these pieces.
     */
    public static String what(ApiResponse r) {
        return "the request's method and URL (query values masked), the response status,"
                + " its headers with credentials removed, and up to "
                + MAX_BODY_CHARS + " characters of the response body";
    }

    /**
     * The text sent as the conversation's opening turn: request line,
     * status, safe headers, capped body — and an honest note about what
     * was withheld.
     */
    public static String body(String method, String url, ApiResponse r) {
        StringBuilder sb = new StringBuilder();
        sb.append("An HTTP response I need explained.\n\n")
                .append("Request: ").append(method == null ? "GET" : method)
                .append(' ').append(maskQuery(url)).append('\n');
        if (r == null || !r.reached()) {
            sb.append("Result: no route — ")
                    .append(r == null ? "unknown error" : String.valueOf(r.error()))
                    .append("\n\nWhat does this mean, and what should I check first?");
            return sb.toString();
        }
        sb.append("Status: ").append(r.status()).append('\n')
                .append("Time: ").append(r.millis()).append("ms\n");

        int withheld = 0;
        Map<String, String> safe = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, List<String>> e : r.headers().entrySet()) {
            if (isSensitive(e.getKey())) {
                withheld++;
            } else {
                safe.put(e.getKey(), String.join(", ", e.getValue()));
            }
        }
        if (!safe.isEmpty()) {
            sb.append("\nResponse headers:\n");
            safe.forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append('\n'));
        }
        if (withheld > 0) {
            sb.append("  [").append(withheld)
                    .append(withheld == 1 ? " credential header" : " credential headers")
                    .append(" withheld]\n");
        }

        String capped = cap(r.body(), MAX_BODY_CHARS);
        if (!capped.isBlank()) {
            sb.append("\nBody:\n").append(capped);
            if (capped.length() < r.body().length()) {
                sb.append("\n[body truncated]");
            }
            sb.append('\n');
        }
        sb.append("\nWhat does this response mean, and what should I check first?");
        return sb.toString();
    }
}
