package org.nmox.studio.ui.site;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bundled-site server's laws, proven over real sockets: loopback
 * serving with correct types, 404 for the missing, 405 for the
 * unwriteable, and — the one that matters — canonical containment: a
 * traversal path answers 404, never a file outside the site.
 */
class SiteServerTest {

    @Test
    @DisplayName("serves on loopback; 404 beyond; 405 for POST; traversal refused")
    void laws(@TempDir Path work) throws Exception {
        File root = new File(work.toFile(), "site");
        Files.createDirectories(root.toPath());
        Files.writeString(new File(root, "index.html").toPath(), "<h1>site</h1>");
        Files.writeString(new File(work.toFile(), "SECRET.txt").toPath(), "outside");

        SiteServer server = new SiteServer(root);
        String url = server.start();
        try {
            assertThat(url).startsWith("http://127.0.0.1:");
            // the URL literal can't witness the BIND: assert the socket
            // itself is loopback, not wildcard (the mutant that survived)
            assertThat(server.boundAddress().isLoopbackAddress()).isTrue();
            assertThat(server.boundAddress().isAnyLocalAddress()).isFalse();
            HttpClient http = HttpClient.newHttpClient();

            HttpResponse<String> ok = http.send(HttpRequest.newBuilder(URI.create(url))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(ok.statusCode()).isEqualTo(200);
            assertThat(ok.body()).contains("<h1>site</h1>");
            assertThat(ok.headers().firstValue("Content-Type").orElse(""))
                    .startsWith("text/html");

            assertThat(http.send(HttpRequest.newBuilder(URI.create(url + "nope.css"))
                    .build(), HttpResponse.BodyHandlers.ofString()).statusCode())
                    .isEqualTo(404);

            assertThat(http.send(HttpRequest.newBuilder(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(405);

            // the containment rule, straight at the resolver: an escaped
            // path resolves to null whatever the client encoding did
            assertThat(SiteServer.resolveInside(root, "../SECRET.txt")).isNull();
            assertThat(SiteServer.resolveInside(root, "index.html")).isNotNull();
        } finally {
            server.stop();
        }
    }
}
