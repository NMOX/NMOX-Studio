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

            // the containment rule, walked over the WIRE rather than at
            // the resolver (which lives in core.util.Containment since
            // ledger 111): a raw socket sends the traversal the HTTP
            // client would have normalized away before it left
            assertThat(rawGet(server, "/../SECRET.txt")).startsWith("HTTP/1.1 404");
            assertThat(rawGet(server, "/index.html")).startsWith("HTTP/1.1 200");
            // the root itself is not a page — this used to resolve to the
            // site directory and be caught one step later by isFile()
            assertThat(rawGet(server, "/.")).startsWith("HTTP/1.1 404");
        } finally {
            server.stop();
        }
    }

    @Test
    @DisplayName("a symlink out of the site root answers 404, never the file it points at")
    void symlinkOutOfTheRootIs404(@TempDir Path work) throws Exception {
        File root = new File(work.toFile(), "site");
        Files.createDirectories(root.toPath());
        Files.writeString(new File(root, "index.html").toPath(), "<h1>site</h1>");
        Path outside = Files.createDirectories(work.resolve("elsewhere"));
        Files.writeString(outside.resolve("SECRET.txt"), "sk-not-yours");
        try {
            Files.createSymbolicLink(root.toPath().resolve("leak"), outside);
        } catch (UnsupportedOperationException | java.io.IOException noSymlinks) {
            return; // a platform without symlinks has nothing to prove here
        }

        SiteServer server = new SiteServer(root);
        server.start();
        try {
            String answer = rawGet(server, "/leak/SECRET.txt");
            assertThat(answer).startsWith("HTTP/1.1 404");
            assertThat(answer).doesNotContain("sk-not-yours");
        } finally {
            server.stop();
        }
    }

    /**
     * One raw HTTP/1.1 request, sent exactly as written — the JDK's
     * HttpClient normalizes {@code ..} out of a URI before the request
     * leaves, so a client-side walk cannot witness this server's own
     * containment refusal at all.
     */
    private static String rawGet(SiteServer server, String path) throws Exception {
        int port = Integer.parseInt(server.url().replaceAll(".*:(\\d+)/?$", "$1"));
        try (java.net.Socket s = new java.net.Socket("127.0.0.1", port)) {
            s.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1\r\n"
                    + "Connection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            s.getOutputStream().flush();
            // bounded: this server's answers are small and the socket
            // closes, but a read is a read (the standing law)
            byte[] all = s.getInputStream().readNBytes(64 * 1024);
            return new String(all, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("HEAD answers quietly: no body, and no JDK warning per request")
    void headLaw(@TempDir Path work) throws Exception {
        File root = new File(work.toFile(), "site");
        Files.createDirectories(root.toPath());
        Files.writeString(new File(root, "index.html").toPath(), "<h1>site</h1>");

        // the outcome witness (v2.40.1 review): sendResponseHeaders with a
        // real length on a HEAD request makes the JDK's httpserver WARN
        // once per request — a browser's probe HEADs would spam
        // messages.log. The logger NAME is the System.Logger's
        // "com.sun.net.httpserver", not the implementation package the
        // record's source class shows (probed live — a tap on
        // sun.net.httpserver let both mutants survive)
        java.util.logging.Logger jdk =
                java.util.logging.Logger.getLogger("com.sun.net.httpserver");
        java.util.List<java.util.logging.LogRecord> warnings =
                java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.logging.Handler tap = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord r) {
                if (r.getLevel().intValue()
                        >= java.util.logging.Level.WARNING.intValue()) {
                    warnings.add(r);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        jdk.addHandler(tap);
        SiteServer server = new SiteServer(root);
        String url = server.start();
        try {
            HttpClient http = HttpClient.newHttpClient();
            HttpResponse<String> hit = http.send(HttpRequest.newBuilder(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(hit.statusCode()).isEqualTo(200);
            assertThat(hit.body()).isEmpty();
            HttpResponse<String> miss = http.send(
                    HttpRequest.newBuilder(URI.create(url + "nope.css"))
                            .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(miss.statusCode()).isEqualTo(404);
            assertThat(miss.body()).isEmpty();
            assertThat(warnings)
                    .as("no sun.net.httpserver warnings across HEAD hit + miss")
                    .isEmpty();
        } finally {
            server.stop();
            jdk.removeHandler(tap);
        }
    }
}
