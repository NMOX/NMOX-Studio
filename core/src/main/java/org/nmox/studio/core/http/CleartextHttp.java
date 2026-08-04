package org.nmox.studio.core.http;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;

/**
 * Pins plain-http requests to HTTP/1.1 — the java.net.http twin of the
 * v1.226.0 WebView fix.
 *
 * <p>{@code HttpClient} defaults to HTTP/2, and on a cleartext
 * {@code http://} URL that means the RFC 7540 §3.2 h2c upgrade dance:
 * {@code Upgrade: h2c} + {@code HTTP2-Settings} on the first request.
 * Angular's esbuild dev server ({@code ng serve}) accepts the
 * connection and NEVER answers such a request — measured 2026-08-04 on
 * the shipped runtime with a one-flag bisect: {@code HTTP_2} against
 * {@code http://[::1]:4321/} timed out at 5s while {@code HTTP_1_1}
 * answered 200 in 54ms. So even with the {@link LoopbackUrls} rewrite
 * connecting to the right stack, an API Studio send, BEACON probe or
 * JSON-RPC call to an esbuild-class dev server hung to its timeout.
 *
 * <p>Pinning costs nothing real: h2c upgrades are essentially never
 * accepted in practice (the v1.226.0 argument verbatim), and
 * {@code https://} URLs are untouched here — they negotiate HTTP/2
 * through ALPN inside TLS, which involves no upgrade request at all.
 */
public final class CleartextHttp {

    private CleartextHttp() {
    }

    /**
     * Pins the builder to HTTP/1.1 when {@code url} is cleartext http;
     * leaves https (and anything else) on the client default. Returns
     * the same builder for call-site chaining.
     */
    public static HttpRequest.Builder pinVersion(HttpRequest.Builder b, String url) {
        if (url != null && url.regionMatches(true, 0, "http:", 0, 5)) {
            b.version(HttpClient.Version.HTTP_1_1);
        }
        return b;
    }
}
