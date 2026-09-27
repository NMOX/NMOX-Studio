package org.nmox.studio.core.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Ledger 56 closed: the one capped HTTP-body read every module routes
 * through. Mechanics proven here; truncation POLICY (flag/refuse/shrug)
 * stays at the call sites, and the gate below keeps them from quietly
 * re-inlining the mechanics.
 */
class HttpBodiesTest {

    /** A deadline no in-memory stream comes near. */
    private static final Duration T = Duration.ofSeconds(30);

    @Test
    @DisplayName("A body under the cap comes back whole and unflagged")
    void underCap() throws IOException {
        HttpBodies.Capped c = HttpBodies.readUtf8(stream("hello"), 100, T);
        assertThat(c.text()).isEqualTo("hello");
        assertThat(c.byteLength()).isEqualTo(5);
        assertThat(c.truncated()).isFalse();
    }

    @Test
    @DisplayName("A body exactly at the cap is whole and unflagged — the cap bit means MORE existed")
    void exactlyAtCap() throws IOException {
        HttpBodies.Capped c = HttpBodies.readUtf8(stream("12345"), 5, T);
        assertThat(c.text()).isEqualTo("12345");
        assertThat(c.truncated())
                .as("exactly-cap is not truncation; only a real extra byte is")
                .isFalse();
    }

    @Test
    @DisplayName("A body past the cap is held at the cap and flagged; the tail is never read")
    void overCap() throws IOException {
        CountingStream in = new CountingStream("1234567890".getBytes(StandardCharsets.UTF_8));
        HttpBodies.Capped c = HttpBodies.readUtf8(in, 4, T);
        assertThat(c.text()).isEqualTo("1234");
        assertThat(c.byteLength()).isEqualTo(4);
        assertThat(c.truncated()).isTrue();
        assertThat(in.readCount)
                .as("cap + one probe byte, never the whole stream — a gigabyte "
                        + "body must cost the cap, not the gigabyte")
                .isEqualTo(5);
    }

    @Test
    @DisplayName("The charset parameter decodes non-UTF-8 bodies")
    void charsetHonored() throws IOException {
        byte[] latin1 = "café".getBytes(StandardCharsets.ISO_8859_1);
        HttpBodies.Capped c = HttpBodies.read(
                new ByteArrayInputStream(latin1), 100, StandardCharsets.ISO_8859_1, T);
        assertThat(c.text()).isEqualTo("café");
    }

    @Test
    @DisplayName("A body that stops arriving is refused by name when the deadline closes the stream (3.4)")
    void stalledBodyIsRefusedByName() {
        StallingStream in = new StallingStream("abc".getBytes(StandardCharsets.UTF_8));
        HttpBodies.StalledException stalled = assertTimeoutPreemptively(Duration.ofSeconds(20),
                () -> assertThrows(HttpBodies.StalledException.class,
                        () -> HttpBodies.readUtf8(in, 1000, Duration.ofMillis(100))));
        assertThat(stalled.bytesReceived())
                .as("what arrived before the silence is counted, not lost")
                .isEqualTo(3);
        assertThat(stalled.getMessage()).contains("stopped sending").contains("3");
        assertThat(in.closed).as("the deadline CLOSES the stream — the only thing that frees a blocked read")
                .isTrue();
    }

    @Test
    @DisplayName("A real HttpClient body stalled mid-transfer frees its reader at the deadline (the 90 s hang)")
    void realServerStallingMidBodyIsRefused() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, 1000);
            OutputStream out = exchange.getResponseBody();
            out.write(new byte[500]);
            out.flush();
            try {
                release.await(60, TimeUnit.SECONDS); // the stall: headers + half a body, then silence
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        try {
            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            HttpResponse<InputStream> response = client.send(HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"))
                    .timeout(Duration.ofSeconds(5)).build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            assertThat(response.statusCode()).as("the headers arrive: java.net.http's timeout is satisfied")
                    .isEqualTo(200);
            HttpBodies.StalledException stalled = assertTimeoutPreemptively(Duration.ofSeconds(20),
                    () -> {
                        try (InputStream in = response.body()) {
                            return assertThrows(HttpBodies.StalledException.class,
                                    () -> HttpBodies.readUtf8(in, HttpBodies.DEFAULT_CAP_BYTES,
                                            Duration.ofMillis(300)));
                        }
                    });
            assertThat(stalled.bytesReceived()).isEqualTo(500);
        } finally {
            release.countDown();
            server.stop(0);
        }
    }

    @Test
    @DisplayName("A body cut short says so: 500 of 1000 bytes, not the JDK's bare \"closed\" (3.4)")
    void bodyCutShortNamesTheShortfall() throws Exception {
        try (java.net.ServerSocket server = new java.net.ServerSocket(0, 5, InetAddress.getLoopbackAddress())) {
            Thread serving = new Thread(() -> {
                try (java.net.Socket s = server.accept()) {
                    java.io.BufferedReader r = new java.io.BufferedReader(
                            new java.io.InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
                    String line;
                    while ((line = r.readLine()) != null && !line.isEmpty()) {
                        // consume the request head
                    }
                    OutputStream out = s.getOutputStream();
                    out.write("HTTP/1.1 200 OK\r\nContent-Length: 1000\r\n\r\n"
                            .getBytes(StandardCharsets.US_ASCII));
                    out.write(new byte[500]);
                    out.flush();
                    // the socket closes here: the way a crashing server drops mid-body
                } catch (IOException ignored) {
                    // the client side asserts the outcome
                }
            }, "broken-body-fixture");
            serving.setDaemon(true);
            serving.start();
            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            HttpResponse<InputStream> response = client.send(HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + server.getLocalPort() + "/"))
                    .timeout(Duration.ofSeconds(5)).build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            HttpBodies.BrokenBodyException broken = assertTimeoutPreemptively(Duration.ofSeconds(20),
                    () -> {
                        try (InputStream in = response.body()) {
                            return assertThrows(HttpBodies.BrokenBodyException.class,
                                    () -> HttpBodies.readUtf8(in, HttpBodies.DEFAULT_CAP_BYTES, T));
                        }
                    });
            assertThat(broken.bytesExpected()).isEqualTo(1000);
            assertThat(broken.bytesReceived()).isEqualTo(500);
            assertThat(broken.getMessage()).contains("500").doesNotStartWith("closed");
        }
    }

    @Test
    @DisplayName("A break with no declared length still names what arrived")
    void breakWithoutLengthNamesTheCount() {
        HttpBodies.BrokenBodyException broken = HttpBodies.BrokenBodyException.from(
                new IOException("closed", new IOException("chunked transfer encoding, state: READING_DATA")),
                42);
        assertThat(broken.bytesExpected()).isEqualTo(-1);
        assertThat(broken.bytesReceived()).isEqualTo(42);
        assertThat(broken.getMessage()).contains("42");
    }

    @Test
    @DisplayName("Gate: all seven former ofString sites route through HttpBodies, none re-inlines readNBytes")
    void sitesRouteThroughHelper() throws Exception {
        // the seven sites the v1.99.0–v1.104.0 arc capped one by one,
        // unified here; LegacyWeb's readNBytes is a LOCAL file scan, not
        // HTTP, and deliberately stays inline
        List<String> sites = List.of(
                "../apiclient/src/main/java/org/nmox/studio/apiclient/api/ApiClient.java",
                "../web3/src/main/java/org/nmox/studio/web3/engine/JsonRpcClient.java",
                "../dbstudio/src/main/java/org/nmox/studio/dbstudio/engine/CouchBackend.java",
                "../rack/src/main/java/org/nmox/studio/rack/engine/KvasirClient.java",
                "../rack/src/main/java/org/nmox/studio/rack/devices/HttpDevice.java",
                "../infra/src/main/java/org/nmox/studio/infra/api/DigitalOceanClient.java",
                "../ui/src/main/java/org/nmox/studio/ui/UpdateCheck.java");
        for (String site : sites) {
            String src = Files.readString(Path.of(site)).replace("\r\n", "\n");
            assertThat(src).as("%s routes through the core helper", site)
                    .contains("HttpBodies");
            assertThat(src).as("%s must not re-inline the capped-read mechanics", site)
                    .doesNotContain("readNBytes")
                    .doesNotContain("BodyHandlers.ofString");
        }
    }

    /** Hands out its bytes, then blocks until closed — a server gone quiet. */
    private static final class StallingStream extends InputStream {
        private final byte[] head;
        private int pos;
        private final CountDownLatch closedLatch = new CountDownLatch(1);
        volatile boolean closed;

        StallingStream(byte[] head) {
            this.head = head;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (pos < head.length) {
                int n = Math.min(len, head.length - pos);
                System.arraycopy(head, pos, b, off, n);
                pos += n;
                return n;
            }
            try {
                closedLatch.await();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            throw new IOException("closed");
        }

        @Override
        public void close() {
            closed = true;
            closedLatch.countDown();
        }
    }

    private static InputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    /** Counts bytes handed out so the never-drains-the-tail law is provable. */
    private static final class CountingStream extends ByteArrayInputStream {
        int readCount;

        CountingStream(byte[] buf) {
            super(buf);
        }

        @Override
        public synchronized int read() {
            int b = super.read();
            if (b != -1) {
                readCount++;
            }
            return b;
        }

        @Override
        public synchronized int read(byte[] b, int off, int len) {
            int n = super.read(b, off, len);
            if (n > 0) {
                readCount += n;
            }
            return n;
        }
    }
}
