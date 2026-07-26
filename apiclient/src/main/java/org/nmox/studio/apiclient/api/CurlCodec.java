package org.nmox.studio.apiclient.api;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.nmox.studio.apiclient.model.ApiModel;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;
import org.nmox.studio.apiclient.model.ApiModel.Pair;

/**
 * curl in, curl out. {@link #parse} turns a pasted curl command line
 * into a saved request; {@link #render} turns a request into the exact
 * command a terminal would run. Pure string work — no network, no IO,
 * no process.
 *
 * <p>Parsing is honest about its limits: multipart forms and
 * {@code @file} bodies are refused with a reason (importing them would
 * silently drop or surprise-read files), unknown flags are ignored and
 * reported in the import notes rather than guessed at, and an
 * {@code Authorization: Bearer/Basic} header is lifted into the Auth
 * field so the secret lands in the OS keychain instead of the
 * committable workspace file (the v1.97.0 secrets law).
 */
public final class CurlCodec {

    private CurlCodec() {
    }

    /** A parsed request plus anything the import wants the user to know. */
    public record Imported(ApiModel.Request request, List<String> notes) {
    }

    /** Transport/output flags that change nothing about the saved request. */
    private static final java.util.Set<String> IGNORED_FLAGS = java.util.Set.of(
            "-s", "-S", "--silent", "--show-error", "-v", "--verbose",
            "-L", "--location", "-k", "--insecure", "--compressed",
            "-f", "--fail", "-i", "--include", "-#", "--progress-bar",
            "--no-progress-meter", "--http1.1", "--http2", "--http3",
            "-4", "-6", "--globoff", "-g");

    /** Ignored flags that consume the following value token. */
    private static final java.util.Set<String> IGNORED_WITH_VALUE = java.util.Set.of(
            "-o", "--output", "-w", "--write-out", "-m", "--max-time",
            "--connect-timeout", "--retry", "-c", "--cookie-jar",
            "-x", "--proxy", "--cacert", "--capath");

    public static Imported parse(String text) {
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty() || !"curl".equals(tokens.get(0))) {
            throw new IllegalArgumentException(
                    "Not a curl command — the first word must be 'curl'.");
        }
        ApiModel.Request r = new ApiModel.Request();
        List<String> notes = new ArrayList<>();
        List<String> dataParts = new ArrayList<>();
        boolean explicitMethod = false;
        boolean asGet = false;
        boolean json = false;

        for (int i = 1; i < tokens.size(); i++) {
            String t = tokens.get(i);
            switch (t) {
                case "-X", "--request" -> {
                    r.method = need(tokens, ++i, t).toUpperCase(java.util.Locale.ROOT);
                    explicitMethod = true;
                }
                case "-H", "--header" -> header(r, need(tokens, ++i, t), notes);
                case "-d", "--data", "--data-raw", "--data-binary", "--data-ascii" -> {
                    String v = need(tokens, ++i, t);
                    if (v.startsWith("@") && !"--data-raw".equals(t)) {
                        throw new IllegalArgumentException(
                                "File bodies (" + v + ") aren't imported — paste the payload itself.");
                    }
                    dataParts.add(v);
                }
                case "--data-urlencode" -> {
                    dataParts.add(need(tokens, ++i, t));
                    notes.add("--data-urlencode imported verbatim — curl would URL-encode it on send.");
                }
                case "--json" -> {
                    dataParts.add(need(tokens, ++i, t));
                    json = true;
                }
                case "-u", "--user" -> {
                    r.authType = AuthType.BASIC;
                    r.authToken = need(tokens, ++i, t);
                }
                case "--oauth2-bearer" -> {
                    r.authType = AuthType.BEARER;
                    r.authToken = need(tokens, ++i, t);
                }
                case "-G", "--get" -> asGet = true;
                case "-I", "--head" -> {
                    r.method = "HEAD";
                    explicitMethod = true;
                }
                case "-b", "--cookie" -> {
                    String v = need(tokens, ++i, t);
                    if (!v.contains("=")) {
                        throw new IllegalArgumentException(
                                "Cookie files (-b " + v + ") aren't imported — paste name=value pairs.");
                    }
                    r.headers.add(new Pair("Cookie", v));
                }
                case "-A", "--user-agent" -> r.headers.add(new Pair("User-Agent", need(tokens, ++i, t)));
                case "-e", "--referer" -> r.headers.add(new Pair("Referer", need(tokens, ++i, t)));
                case "-F", "--form", "--form-string" -> throw new IllegalArgumentException(
                        "Multipart form imports (-F) aren't supported — build the body by hand.");
                case "-T", "--upload-file" -> throw new IllegalArgumentException(
                        "File uploads (-T) aren't imported — paste the payload itself.");
                case "--url" -> r.url = need(tokens, ++i, t);
                default -> {
                    if (IGNORED_FLAGS.contains(t)) {
                        // transport/output tuning — nothing to save
                    } else if (IGNORED_WITH_VALUE.contains(t)) {
                        need(tokens, ++i, t);
                        notes.add("Ignored " + t + " (transport/output flag).");
                    } else if (t.startsWith("-") && t.length() > 1) {
                        // unknown flag: never guess its arity — skipping a
                        // value it owns could eat the URL, so leave the next
                        // token alone and say so
                        notes.add("Ignored unknown flag " + t + ".");
                    } else if (r.url.isEmpty()) {
                        r.url = t;
                    } else {
                        notes.add("Ignored extra argument '" + t + "' — URL already set.");
                    }
                }
            }
        }
        if (r.url.isEmpty()) {
            throw new IllegalArgumentException("No URL found in the curl command.");
        }
        String body = String.join("&", dataParts);
        if (asGet && !body.isEmpty()) {
            r.url = r.url + (r.url.contains("?") ? "&" : "?") + body;
            body = "";
        }
        r.body = body;
        if (json) {
            if (r.headers.stream().noneMatch(h -> "Content-Type".equalsIgnoreCase(h.name))) {
                r.headers.add(new Pair("Content-Type", "application/json"));
            }
            if (r.headers.stream().noneMatch(h -> "Accept".equalsIgnoreCase(h.name))) {
                r.headers.add(new Pair("Accept", "application/json"));
            }
        }
        if (!explicitMethod) {
            r.method = r.body.isEmpty() ? "GET" : "POST";
        }
        r.name = r.method + " " + r.url.replaceFirst("^https?://", "");
        if (r.name.length() > 48) {
            r.name = r.name.substring(0, 48) + "…";
        }
        return new Imported(r, notes);
    }

    /** An Authorization header becomes the keychain-backed Auth field. */
    private static void header(ApiModel.Request r, String raw, List<String> notes) {
        int colon = raw.indexOf(':');
        if (colon < 1) {
            throw new IllegalArgumentException("Malformed header (expected 'Name: value'): " + raw);
        }
        String name = raw.substring(0, colon).trim();
        String value = raw.substring(colon + 1).trim();
        if ("Authorization".equalsIgnoreCase(name)) {
            if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
                r.authType = AuthType.BEARER;
                r.authToken = value.substring(7).trim();
                return;
            }
            if (value.regionMatches(true, 0, "Basic ", 0, 6)) {
                try {
                    String decoded = new String(Base64.getDecoder()
                            .decode(value.substring(6).trim()), StandardCharsets.UTF_8);
                    if (decoded.contains(":")) {
                        r.authType = AuthType.BASIC;
                        r.authToken = decoded;
                        return;
                    }
                } catch (IllegalArgumentException notBase64) {
                    // fall through to the plain-header path below
                }
            }
            notes.add("Authorization header kept as a header — consider the Auth "
                    + "field, which stores the secret in the OS keychain.");
        }
        r.headers.add(new Pair(name, value));
    }

    private static String need(List<String> tokens, int i, String flag) {
        if (i >= tokens.size()) {
            throw new IllegalArgumentException("Flag " + flag + " needs a value.");
        }
        return tokens.get(i);
    }

    /**
     * Renders the exact command a terminal would run for this request —
     * the same URL, headers, auth, and body {@code send()} would use,
     * with the active environment's {@code {{vars}}} resolved.
     */
    public static String render(ApiModel.Request r, Map<String, String> vars) {
        StringBuilder sb = new StringBuilder("curl");
        boolean bodied = r.body != null && !r.body.isBlank();
        if (!"GET".equals(r.method) || !bodied && !"GET".equals(r.method)) {
            sb.append(" -X ").append(r.method);
        } else if (!"GET".equals(r.method)) {
            sb.append(" -X ").append(r.method);
        }
        sb.append(' ').append(quote(
                ApiClient.appendParams(Variables.resolve(r.url, vars), r.params, vars)));
        for (Pair h : r.headers) {
            if (h.enabled && h.name != null && !h.name.isBlank()) {
                cont(sb).append("-H ").append(quote(
                        h.name + ": " + Variables.resolve(h.value == null ? "" : h.value, vars)));
            }
        }
        if (r.authType == AuthType.BEARER && !r.authToken.isBlank()) {
            cont(sb).append("-H ").append(quote(
                    "Authorization: Bearer " + Variables.resolve(r.authToken, vars).trim()));
        } else if (r.authType == AuthType.BASIC && !r.authToken.isBlank()) {
            cont(sb).append("-u ").append(quote(Variables.resolve(r.authToken, vars)));
        }
        if (bodied) {
            cont(sb).append("--data ").append(quote(Variables.resolve(r.body, vars)));
        }
        return sb.toString();
    }

    private static StringBuilder cont(StringBuilder sb) {
        return sb.append(" \\\n  ");
    }

    /** Single-quote shell quoting; embedded quotes via the '\'' idiom. */
    private static String quote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    /**
     * Shell-style tokenizer: single quotes are literal, double quotes
     * honor backslash escapes, a bare backslash escapes the next
     * character (a backslash-newline is the classic line continuation).
     * ANSI-C quoting ({@code $'…'}) is refused rather than mis-read.
     */
    static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean started = false;
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '$' && i + 1 < n && text.charAt(i + 1) == '\'') {
                throw new IllegalArgumentException(
                        "ANSI-C quoting ($'…') isn't supported — use plain quotes.");
            }
            if (c == '\'') {
                started = true;
                int end = text.indexOf('\'', i + 1);
                if (end < 0) {
                    throw new IllegalArgumentException("Unclosed single quote.");
                }
                cur.append(text, i + 1, end);
                i = end + 1;
            } else if (c == '"') {
                started = true;
                i++;
                while (i < n && text.charAt(i) != '"') {
                    char d = text.charAt(i);
                    if (d == '\\' && i + 1 < n
                            && "\"\\$`\n".indexOf(text.charAt(i + 1)) >= 0) {
                        i++;
                        d = text.charAt(i);
                        if (d == '\n') {
                            i++;
                            continue; // continuation inside quotes
                        }
                    }
                    cur.append(d);
                    i++;
                }
                if (i >= n) {
                    throw new IllegalArgumentException("Unclosed double quote.");
                }
                i++;
            } else if (c == '\\') {
                if (i + 1 < n && text.charAt(i + 1) == '\n') {
                    i += 2; // line continuation
                } else if (i + 1 < n) {
                    started = true;
                    cur.append(text.charAt(i + 1));
                    i += 2;
                } else {
                    i++;
                }
            } else if (Character.isWhitespace(c)) {
                if (started) {
                    out.add(cur.toString());
                    cur.setLength(0);
                    started = false;
                }
                i++;
            } else {
                started = true;
                cur.append(c);
                i++;
            }
        }
        if (started) {
            out.add(cur.toString());
        }
        return out;
    }
}
