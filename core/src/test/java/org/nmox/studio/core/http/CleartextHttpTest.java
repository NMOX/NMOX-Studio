package org.nmox.studio.core.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cleartext h2c law for java.net.http (v1.260.0): HTTP_2-by-default
 * on a plain http URL means the RFC 7540 upgrade dance esbuild accepts
 * and never answers — bisected live (HTTP_2 timed out at 5s, HTTP_1_1
 * answered in 54ms against the same ng serve). http pins 1.1; https
 * stays on the client default because ALPN-negotiated HTTP/2 involves
 * no upgrade request at all.
 */
class CleartextHttpTest {

    private static HttpRequest req(String url) {
        return CleartextHttp.pinVersion(
                HttpRequest.newBuilder(URI.create(url)), url).build();
    }

    @Test
    @DisplayName("plain http pins HTTP/1.1")
    void httpPinsH1() {
        assertThat(req("http://localhost:4321/").version())
                .isEqualTo(Optional.of(HttpClient.Version.HTTP_1_1));
        assertThat(req("HTTP://EXAMPLE.COM/").version())
                .as("scheme match is case-insensitive")
                .isEqualTo(Optional.of(HttpClient.Version.HTTP_1_1));
    }

    @Test
    @DisplayName("https stays on the client default (ALPN h2 has no upgrade request)")
    void httpsUntouched() {
        assertThat(req("https://api.anthropic.com/v1/messages").version())
                .isEmpty();
    }

    @Test
    @DisplayName("Gate: the five HttpClient seams pin cleartext versions")
    void seamsPinCleartext() throws Exception {
        // same seam list as LoopbackUrlsTest minus the Browser, whose
        // WebView engine has its own flag (com.sun.webkit.useHTTP2Loader,
        // v1.226.0) and never touches java.net.http
        java.util.List<String> sites = java.util.List.of(
                "../apiclient/src/main/java/org/nmox/studio/apiclient/api/ApiClient.java",
                "../rack/src/main/java/org/nmox/studio/rack/devices/HttpDevice.java",
                "../rack/src/main/java/org/nmox/studio/rack/devices/BeaconDevice.java",
                "../dbstudio/src/main/java/org/nmox/studio/dbstudio/engine/CouchBackend.java",
                "../web3/src/main/java/org/nmox/studio/web3/engine/JsonRpcClient.java");
        for (String site : sites) {
            assertThat(java.nio.file.Files.readString(java.nio.file.Path.of(site)))
                    .as("%s pins cleartext requests to HTTP/1.1", site)
                    .contains("CleartextHttp.pinVersion");
        }
    }
}
