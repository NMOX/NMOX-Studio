package org.nmox.studio.rack.mcp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.spi.LiveRuns;
import org.nmox.studio.core.util.Threads;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The GET stream end to end (v2.84.0): a client opens the SSE stream,
 * subscribes to nmox://runs over POST, a run starts in the IDE, and the
 * updated frame arrives — without polling. The whole path is real: the
 * real HttpServer, the real registry listener, the real writer thread.
 */
class AgentPortStreamTest {

    private AgentPort port;

    @AfterEach
    void stop() {
        LiveRuns.stopAll();
        if (port != null) {
            port.stop();
        }
    }

    @Test
    @DisplayName("streams are capped: the ninth GET stream is refused 503 (v2.84.0 review)")
    void streamsCapped() throws Exception {
        port = AgentPort.start(new McpTools(List.of()), "2.84.0");
        HttpClient http = HttpClient.newHttpClient();
        List<HttpResponse<java.io.InputStream>> open = new java.util.ArrayList<>();
        for (int i = 0; i < AgentPort.MAX_STREAMS; i++) {
            HttpResponse<java.io.InputStream> r = http.send(HttpRequest.newBuilder(URI.create(port.url()))
                    .header("Authorization", "Bearer " + port.token())
                    .header("Accept", "text/event-stream").GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            assertThat(r.statusCode()).isEqualTo(200);
            open.add(r);
        }
        // the SLOT, not the attach: the server sends 200 and only then adds
        // the sink, so a client holding its response can be ahead of the
        // registry. The reservation is taken before the 200 goes out, which
        // is why it is the thing an observer can rely on (v2.109.0 — this
        // assertion failed on a loaded ubuntu runner reading the attach)
        assertThat(port.subscriptions().slotsTaken()).isEqualTo(AgentPort.MAX_STREAMS);
        HttpResponse<Void> ninth = http.send(HttpRequest.newBuilder(URI.create(port.url()))
                .header("Authorization", "Bearer " + port.token())
                .header("Accept", "text/event-stream").GET()
                .timeout(java.time.Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(ninth.statusCode()).isEqualTo(503);
        for (HttpResponse<java.io.InputStream> r : open) {
            r.body().close();
        }
    }

    @Test
    @DisplayName("eight GETs arriving together take eight slots, not nine (v2.109.0)")
    void theCapHoldsUnderASimultaneousRush() throws Exception {
        // the defect the ubuntu lane exposed: the cap read the attached count
        // and attached afterwards, so callers racing through that window all
        // saw room. Nine clients starting at once is the shape that proves it
        port = AgentPort.start(new McpTools(List.of()), "2.109.0");
        HttpClient http = HttpClient.newHttpClient();
        int rush = AgentPort.MAX_STREAMS + 1;
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        List<CompletableFuture<Integer>> codes = new java.util.ArrayList<>();
        List<HttpResponse<java.io.InputStream>> got = java.util.Collections
                .synchronizedList(new java.util.ArrayList<>());
        for (int i = 0; i < rush; i++) {
            codes.add(CompletableFuture.supplyAsync(() -> {
                try {
                    go.await();
                    HttpResponse<java.io.InputStream> r = http.send(
                            HttpRequest.newBuilder(URI.create(port.url()))
                                    .header("Authorization", "Bearer " + port.token())
                                    .header("Accept", "text/event-stream").GET()
                                    .timeout(java.time.Duration.ofSeconds(20)).build(),
                            HttpResponse.BodyHandlers.ofInputStream());
                    got.add(r);
                    return r.statusCode();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }));
        }
        go.countDown();
        long accepted = codes.stream().map(CompletableFuture::join).filter(c -> c == 200).count();
        assertThat(accepted).as("the cap is a cap, however many arrive at once")
                .isEqualTo(AgentPort.MAX_STREAMS);
        assertThat(port.subscriptions().slotsTaken()).isEqualTo(AgentPort.MAX_STREAMS);
        for (HttpResponse<java.io.InputStream> r : got) {
            r.body().close();
        }
    }

    @Test
    @DisplayName("a dropped stream gives its slot back, so the ninth client can have it")
    void aDroppedStreamFreesItsSlot() throws Exception {
        port = AgentPort.start(new McpTools(List.of()), "2.109.0");
        HttpClient http = HttpClient.newHttpClient();
        List<HttpResponse<java.io.InputStream>> open = new java.util.ArrayList<>();
        for (int i = 0; i < AgentPort.MAX_STREAMS; i++) {
            open.add(http.send(HttpRequest.newBuilder(URI.create(port.url()))
                    .header("Authorization", "Bearer " + port.token())
                    .header("Accept", "text/event-stream").GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream()));
        }
        assertThat(port.subscriptions().slotsTaken()).isEqualTo(AgentPort.MAX_STREAMS);
        open.remove(0).body().close();
        // the drop is noticed on the next write to the gone stream
        long deadline = System.currentTimeMillis() + 20_000;
        while (port.subscriptions().slotsTaken() == AgentPort.MAX_STREAMS
                && System.currentTimeMillis() < deadline) {
            port.subscriptions().updated("nmox://runs");
            Thread.sleep(50);
        }
        assertThat(port.subscriptions().slotsTaken())
                .as("a reservation that is never released is a leak: the port fills up forever")
                .isLessThan(AgentPort.MAX_STREAMS);
        for (HttpResponse<java.io.InputStream> r : open) {
            r.body().close();
        }
    }

    @Test
    @DisplayName("every watch the port adds, stop removes — incl. the editor registry (source law)")
    void watchesAreSymmetric() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/org/nmox/studio/rack/mcp/AgentPort.java"));
        int watch = src.indexOf("private void watch()");
        assertThat(watch).isPositive();
        String body = src.substring(watch, src.indexOf("private static boolean acceptsEventStream", watch));
        String[][] pairs = {
            {"LiveRuns.addListener(", "LiveRuns.removeListener("},
            {"servings.addListener(", "servings.removeListener("},
            {"getRegistry().addPropertyChangeListener(", "getRegistry().removePropertyChangeListener("},
            {"DiagnosticsBus.addListener(", "DiagnosticsBus.removeListener("},
            {"RackBus.subscribe(", "RackBus.unsubscribe("},
            {"DataObject.getRegistry().addChangeListener(", "DataObject.getRegistry().removeChangeListener("},
            {"addChangeListener(", "removeChangeListener("}};
        for (String[] p : pairs) {
            assertThat(body).as("watch adds " + p[0]).contains(p[0]);
            assertThat(body).as("unwatch removes " + p[1]).contains(p[1]);
        }
        assertThat(body).contains("nmox://editor");
    }

    @Test
    @DisplayName("a subscribed client is told nmox://runs changed when a run starts")
    void pushOnRunStart() throws Exception {
        port = AgentPort.start(McpTools.production(), "2.84.0");
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<java.io.InputStream> stream = http.send(HttpRequest.newBuilder(URI.create(port.url()))
                .header("Authorization", "Bearer " + port.token())
                .header("Accept", "text/event-stream").GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        assertThat(stream.statusCode()).isEqualTo(200);
        assertThat(stream.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream.body(), StandardCharsets.UTF_8));
        // the stream announces itself, so a client knows it is attached
        assertThat(reader.readLine()).isEqualTo(": connected");
        String sub = http.send(HttpRequest.newBuilder(URI.create(port.url()))
                .header("Authorization", "Bearer " + port.token())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/subscribe\",\"params\":{\"uri\":\"nmox://runs\"}}"))
                .build(), HttpResponse.BodyHandlers.ofString()).body();
        assertThat(sub).contains("\"result\":{}");
        assertThat(port.subscriptions().attachedCount()).isEqualTo(1);
        CompletableFuture<String> frame = new CompletableFuture<>();
        Threads.daemon(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        frame.complete(line);
                        return;
                    }
                }
                frame.complete("(stream ended)");
            } catch (java.io.IOException e) {
                frame.complete("(read failed: " + e.getMessage() + ")");
            }
        }, "sse-reader-test").start();
        LiveRuns.add(new LiveRuns.Run("ide-run:/tmp/sse#1", "Run \u2014 sse", () -> { }));
        String data = frame.get(5, TimeUnit.SECONDS);
        assertThat(data).contains("notifications/resources/updated").contains("nmox://runs");
        // an unsubscribed URI is never pushed: nothing more arrives for a servings-only change
        assertThat(port.subscriptions().subscribed()).isEqualTo(java.util.Set.of("nmox://runs"));
        port.stop();
        port = null;
        assertThat(List.of()).isEmpty();
    }
}
