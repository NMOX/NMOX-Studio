package org.nmox.studio.core.http;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * The IDE's one HTTP connection pool. Every subsystem that speaks HTTP
 * (PING and BEACON devices, API Studio, the cloud clients) used to
 * build its own {@link HttpClient} — four pools, four executors, one
 * per classloader. An HttpClient is thread-safe and made to be shared;
 * per-request deadlines belong on the request, not the client.
 */
public final class HttpClientFactory {

    private static final HttpClient SHARED = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private HttpClientFactory() {
    }

    /** The shared client. Set timeouts per request via HttpRequest.timeout(). */
    public static HttpClient shared() {
        return SHARED;
    }
}
