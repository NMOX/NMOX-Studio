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
    /** Delivers {@code total} bytes, one every {@code gapMs}; an IOException once closed. */
    private static final class TrickleStream extends InputStream {
        private final int total;
        private final long gapMs;
        private int sent;
        private volatile boolean closed;

        TrickleStream(int total, long gapMs) {
            this.total = total;
            this.gapMs = gapMs;
        }

        @Override
        public int read() throws IOException {
            if (sent >= total) {
                return -1;
            }
            long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(gapMs);
            while (System.nanoTime() < until) {
                if (closed) {
                    throw new IOException("closed");
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted");
                }
            }
            sent++;
            return 'x';
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int c = read();
            if (c < 0) {
                return -1;
            }
            b[off] = (byte) c;
            return 1;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Test
    @DisplayName("the deadline is idle time: a body still arriving is read whole, past the deadline in total (the 3.4 review)")
    void slowButLiveBodyIsRead() throws IOException {
        // 8 bytes, one every 100 ms: ~800 ms in all against a 500 ms deadline.
        // The gap is a fifth of the deadline so that a runner pausing this
        // thread for a few hundred ms cannot make a live body look stopped.
        HttpBodies.Capped c = HttpBodies.readUtf8(new TrickleStream(8, 100), 1024, Duration.ofMillis(500));
        assertThat(c.text()).isEqualTo("xxxxxxxx");
    }

    @Test
    @DisplayName("a server that trickles forever ends at the ceiling, said as too slow, not as stopped")
    void tricklingForeverHitsTheCeiling() {
        HttpBodies.StalledException e = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertThrows(
                HttpBodies.StalledException.class,
                // a byte every 10 ms against a 500 ms idle deadline (ceiling 2 s):
                // only a 500 ms pause of this thread could read as a silence
                () -> HttpBodies.readUtf8(new TrickleStream(Integer.MAX_VALUE, 10), 1 << 20, Duration.ofMillis(500))));
        assertThat(e.getMessage()).contains("still arriving").doesNotContain("stopped sending");
    }

    private static final long MS = 1_000_000L;

    @Test
    @DisplayName("at the ceiling, a body whose last byte is recent is too slow, however short the last look was")
    void ceilingAfterAShortLastLookIsTooSlow() {
        // idle 150 ms, ceiling at 600 ms. The look before came late, at 599 ms,
        // so this one is 1 ms after it; the last byte arrived at 560 ms.
        HttpBodies.Verdict v = HttpBodies.look(600 * MS, 560 * MS, 600 * MS, 150 * MS);
        assertThat(v.look()).as("no byte in the last millisecond is not a silence").isEqualTo(HttpBodies.Look.TOO_SLOW);
    }

    @Test
    @DisplayName("a silence of one idle period is stopped, before the ceiling and at it")
    void aFullIdlePeriodIsStopped() {
        assertThat(HttpBodies.look(400 * MS, 250 * MS, 600 * MS, 150 * MS).look())
                .isEqualTo(HttpBodies.Look.STOPPED);
        assertThat(HttpBodies.look(600 * MS, 450 * MS, 600 * MS, 150 * MS).look())
                .as("at the ceiling a server gone quiet is still said as stopped")
                .isEqualTo(HttpBodies.Look.STOPPED);
        assertThat(HttpBodies.look(400 * MS, 251 * MS, 600 * MS, 150 * MS).look())
                .as("one millisecond short of the idle period is not a silence")
                .isEqualTo(HttpBodies.Look.WAIT);
    }

    @Test
    @DisplayName("the next look is when the silence or the ceiling could first be true")
    void theNextLookIsTheEarlierOfTheTwo() {
        // last byte at 300 ms: silence possible at 450 ms, ceiling at 600 ms
        assertThat(HttpBodies.look(310 * MS, 300 * MS, 600 * MS, 150 * MS))
                .isEqualTo(new HttpBodies.Verdict(HttpBodies.Look.WAIT, 140 * MS));
        // last byte at 580 ms: the ceiling comes first
        assertThat(HttpBodies.look(585 * MS, 580 * MS, 600 * MS, 150 * MS))
                .isEqualTo(new HttpBodies.Verdict(HttpBodies.Look.WAIT, 15 * MS));
    }

    @Test
    @DisplayName("a stream whose close blocks cannot stop every other deadline: the close is not the watchdog's")
    void blockingCloseDoesNotStallTheWatchdog() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        InputStream stuck = new InputStream() {
            @Override
            public int read() throws IOException {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new IOException("released");
            }

            @Override
            public void close() {
                // the JDK server's request stream drains on close: it waits on the reader
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        Thread first = org.nmox.studio.core.util.Threads.startDaemon(() -> {
            try {
                HttpBodies.readUtf8(stuck, 1024, Duration.ofMillis(50));
            } catch (IOException expected) {
                // released at the end
            }
        }, "stuck-reader");
        try {
            Thread.sleep(200); // its alarm has fired and its close is blocked
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertThrows(HttpBodies.StalledException.class,
                    () -> HttpBodies.readUtf8(new StallingStream(new byte[0]), 1024, Duration.ofMillis(100))));
        } finally {
            release.countDown();
            first.join(2000);
        }
    }

    @Test
    @DisplayName("a Cancel mid-body is a cancel, not the server's broken body")
    void interruptedReadIsCancelled() {
        InputStream cut = new InputStream() {
            @Override
            public int read() throws IOException {
                IOException e = new IOException("Interrupted");
                e.initCause(new InterruptedException());
                throw e;
            }
        };
        assertThrows(java.io.InterruptedIOException.class, () -> HttpBodies.readUtf8(cut, 1024, T));
    }

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
