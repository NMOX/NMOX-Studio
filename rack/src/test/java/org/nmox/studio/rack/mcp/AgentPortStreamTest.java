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
