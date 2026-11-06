package org.nmox.studio.infra.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.json.JSONObject;
import org.nmox.studio.core.http.HttpBodies;
import org.nmox.studio.core.http.HttpClientFactory;

/**
 * One bearer-token JSON round trip to a cloud API, the way every cloud
 * call in the product makes it: the shared client, a 60 s leash on the
 * headers and another on the body, the body read through
 * {@link HttpBodies} so a misbehaving endpoint behind a secret cannot
 * hold the heap, and an error that carries the status and the body —
 * never the token. The Infra Designer's client and the Ops Hub's
 * providers both send through here, so the rules have one home.
 */
public final class CloudHttp {

    /** The seam: a provider's transport. Tests answer from a map; the product answers over HTTPS. */
    public interface Transport {
        JSONObject send(String method, String path, String body) throws IOException, InterruptedException;
    }

    private static final HttpClient HTTP = HttpClientFactory.shared();

    private CloudHttp() {
    }

    /** A transport for a provider, resolving its token at send time (so a token set later is seen). */
    public static Transport over(CloudProvider provider) {
        return (method, path, body) -> {
            String tok = provider.token();
            if (tok == null) {
                throw new IOException("no " + provider.displayName() + " API token configured");
            }
            return send(provider.apiBase(), tok, method, path, body);
        };
    }

    public static JSONObject send(String apiBase, String token, String method, String path, String body)
            throws IOException, InterruptedException {
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(apiBase + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60));
        switch (method) {
            case "POST" -> rb.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
            case "DELETE" -> rb.DELETE();
            default -> rb.GET();
        }
        HttpResponse<InputStream> response = HTTP.send(rb.build(), HttpResponse.BodyHandlers.ofInputStream());
        String text;
        try (InputStream in = response.body()) {
            text = HttpBodies.readUtf8(in, HttpBodies.DEFAULT_CAP_BYTES, Duration.ofSeconds(60)).text();
        }
        if (response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + ": " + compact(text));
        }
        return text == null || text.isBlank() ? new JSONObject() : new JSONObject(text);
    }

    /** One line, at most 160 characters: the shape an error dialog or a status line can carry. */
    public static String compact(String text) {
        if (text == null) {
            return "unknown error";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 160 ? oneLine.substring(0, 160) + "…" : oneLine;
    }
}
