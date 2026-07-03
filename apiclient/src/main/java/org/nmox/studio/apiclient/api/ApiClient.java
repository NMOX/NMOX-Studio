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

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

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
        } else if (request.authType == AuthType.BASIC && request.authToken != null && request.authToken.contains(":")) {
            String creds = Variables.resolve(request.authToken, vars);
            b.header("Authorization", "Basic "
                    + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8)));
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
        if (body == null) {
            return false;
        }
        String t = body.strip();
        return t.startsWith("{") || t.startsWith("[");
    }

    /** Sends the request and captures timing, size, headers, and body. */
    public ApiResponse send(Request request, Map<String, String> vars) {
        long start = System.nanoTime();
        try {
            HttpResponse<String> response = client.send(build(request, vars),
                    HttpResponse.BodyHandlers.ofString());
            long ms = (System.nanoTime() - start) / 1_000_000;
            String body = response.body() == null ? "" : response.body();
            Map<String, java.util.List<String>> headers = new LinkedHashMap<>(response.headers().map());
            return new ApiResponse(response.statusCode(), ms,
                    body.getBytes(StandardCharsets.UTF_8).length, headers, body, null);
        } catch (Exception ex) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            return ApiResponse.failure(ms,
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }
}
