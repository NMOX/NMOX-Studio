package org.nmox.studio.rack.mcp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Subscriptions and their SSE frames (v2.84.0): only subscribed URIs reach a stream; a dead stream is dropped, never retried. */
class McpSubscriptionsTest {

    @Test
    @DisplayName("a frame names the URI and nothing else")
    void frame() {
        String f = McpSubscriptions.frame("nmox://runs");
        assertThat(f).startsWith("event: message\ndata: ").endsWith("\n\n")
                .contains("\"method\":\"notifications/resources/updated\"")
                .contains("\"uri\":\"nmox://runs\"");
    }

    @Test
    @DisplayName("only subscribed URIs are written, to every attached stream")
    void onlySubscribed() throws Exception {
        McpSubscriptions subs = new McpSubscriptions();
        ByteArrayOutputStream a = new ByteArrayOutputStream();
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        subs.attach(a, () -> { });
        subs.attach(b, () -> { });
        subs.subscribe("nmox://runs");
        subs.updated("nmox://runs", "nmox://context");
        subs.awaitIdle();
        String text = a.toString(StandardCharsets.UTF_8);
        assertThat(text).contains("nmox://runs").doesNotContain("nmox://context");
        assertThat(b.toString(StandardCharsets.UTF_8)).isEqualTo(text);
        subs.unsubscribe("nmox://runs");
        subs.updated("nmox://runs");
        subs.awaitIdle();
        assertThat(a.toString(StandardCharsets.UTF_8)).isEqualTo(text);
        subs.close();
    }

    @Test
    @DisplayName("a stream that fails is dropped once, with its close hook run, and the others keep flowing")
    void deadStreamDropped() throws Exception {
        McpSubscriptions subs = new McpSubscriptions();
        AtomicInteger closed = new AtomicInteger();
        OutputStream dead = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("gone");
            }
        };
        ByteArrayOutputStream live = new ByteArrayOutputStream();
        subs.attach(dead, closed::incrementAndGet);
        subs.attach(live, () -> { });
        subs.subscribe("nmox://servers");
        subs.updated("nmox://servers");
        subs.updated("nmox://servers");
        subs.awaitIdle();
        assertThat(closed.get()).isEqualTo(1);
        assertThat(subs.attachedCount()).isEqualTo(1);
        assertThat(live.toString(StandardCharsets.UTF_8).split("event: message").length - 1).isEqualTo(2);
        subs.close();
        assertThat(subs.attachedCount()).isZero();
    }
}
