package org.nmox.studio.apiclient.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;
import org.nmox.studio.apiclient.model.ApiModel.Pair;
import org.nmox.studio.apiclient.model.ApiModel.Request;

/**
 * Fires a saved {@link Request} over {@code java.net.http}, resolving
 * variables first, appending enabled query params, applying enabled
 * headers and auth. Pure enough to build the request in a unit test;
 * the send itself is a blocking call meant for a worker thread.
 */
public final class ApiClient {

    private final HttpClient client = org.nmox.studio.core.http.HttpClientFactory.shared();

    /** Builds the wire request from a saved request + resolved variables. */
    public static HttpRequest build(Request request, Map<String, String> vars) {
        String url = appendParams(Variables.resolve(request.url, vars), request.params, vars);
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url.trim()))
                .timeout(Duration.ofSeconds(30));

        boolean bodyMethod = request.method.equals("POST") || request.method.equals("PUT")
                || request.method.equals("PATCH");
        String body = Variables.resolve(request.body, vars);
        if (bodyMethod && body != null && !body.isBlank()) {
            b.method(request.method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            b.method(request.method, HttpRequest.BodyPublishers.noBody());
        }

        boolean hasContentType = false;
        for (Pair h : request.headers) {
            if (h != null && h.enabled && h.name != null && !h.name.isBlank()) {
                String name = Variables.resolve(h.name, vars);
                b.header(name, Variables.resolve(h.value == null ? "" : h.value, vars));
                hasContentType |= name.equalsIgnoreCase("Content-Type");
            }
        }
        if (bodyMethod && !hasContentType && looksJson(body)) {
            b.header("Content-Type", "application/json");
        }
        applyAuth(b, request, vars);
        return b.build();
    }

    private static void applyAuth(HttpRequest.Builder b, Request request, Map<String, String> vars) {
        if (request.authType == AuthType.BEARER && request.authToken != null && !request.authToken.isBlank()) {
            b.header("Authorization", "Bearer " + Variables.resolve(request.authToken, vars).trim());
        } else if (request.authType == AuthType.BASIC && request.authToken != null && !request.authToken.isBlank()) {
            // Resolve {{vars}} FIRST, then check for the user:password
            // colon — the credential is commonly a single {{creds}} var
            // (the Auth tab advertises it), and the raw "{{creds}}" has
            // no colon, so a pre-resolution check silently sent no
            // Authorization header at all (v1.97.0).
            String creds = Variables.resolve(request.authToken, vars);
            if (creds.contains(":")) {
                b.header("Authorization", "Basic "
                        + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8)));
            }
        }
    }

    static String appendParams(String url, java.util.List<Pair> params, Map<String, String> vars) {
        StringBuilder query = new StringBuilder();
        for (Pair p : params) {
            if (p == null || !p.enabled || p.name == null || p.name.isBlank()) {
                continue;
            }
            query.append(query.length() == 0 ? "" : "&")
                    .append(enc(Variables.resolve(p.name, vars)))
                    .append('=')
                    .append(enc(Variables.resolve(p.value == null ? "" : p.value, vars)));
        }
        if (query.length() == 0) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + query;
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    static boolean looksJson(String body) {
        return org.nmox.studio.core.util.JsonUtil.looksJson(body);
    }

    /**
     * The response-body capture cap. {@code ofString()} buffered the
     * whole body unbounded, so a runaway endpoint (a misconfigured file
     * download, a log-streaming route) could OOM the IDE. Everything a
     * response viewer and test runner need fits well under this; the
     * truncation is flagged honestly on the {@link ApiResponse}.
     */
    public static final int MAX_BODY_BYTES = 8 * 1024 * 1024;

    /** Sends the request and captures timing, size, headers, and body. */
    public ApiResponse send(Request request, Map<String, String> vars) {
        long start = System.nanoTime();
        try {
            HttpResponse<java.io.InputStream> response = client.send(build(request, vars),
                    HttpResponse.BodyHandlers.ofInputStream());
            org.nmox.studio.core.http.HttpBodies.Capped capped;
            try (java.io.InputStream in = response.body()) {
                capped = org.nmox.studio.core.http.HttpBodies.read(in, MAX_BODY_BYTES,
                        charsetOf(response.headers().firstValue("content-type").orElse("")));
                // closing the stream aborts the rest of the transfer —
                // we never drain what we won't show
            }
            long ms = (System.nanoTime() - start) / 1_000_000;
            Map<String, java.util.List<String>> headers = new LinkedHashMap<>(response.headers().map());
            return new ApiResponse(response.statusCode(), ms,
                    capped.byteLength(), headers, capped.text(), null, capped.truncated());
        } catch (InterruptedException cancelled) {
            // a Cancel press interrupts the send worker (the RP is
            // created interruptible) — this is a user verdict, not a
            // network failure, and the flag must be restored for the
            // pool thread
            Thread.currentThread().interrupt();
            long ms = (System.nanoTime() - start) / 1_000_000;
            return ApiResponse.failure(ms, "cancelled");
        } catch (Exception ex) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            return ApiResponse.failure(ms,
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    /** Charset from a Content-Type header value; UTF-8 when absent or unknown. */
    static java.nio.charset.Charset charsetOf(String contentType) {
        int i = contentType.toLowerCase(java.util.Locale.ROOT).indexOf("charset=");
        if (i >= 0) {
            String name = contentType.substring(i + "charset=".length()).trim();
            int end = name.indexOf(';');
            if (end >= 0) {
                name = name.substring(0, end);
            }
            name = name.replace("\"", "").trim();
            try {
                return java.nio.charset.Charset.forName(name);
            } catch (RuntimeException unknown) {
                // fall through to UTF-8
            }
        }
        return StandardCharsets.UTF_8;
    }
}
