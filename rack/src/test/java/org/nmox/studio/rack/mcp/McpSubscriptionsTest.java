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

    @Test
    @DisplayName("a client that vanished without closing is dropped by the keepalive, not held until the next event")
    void ghostStreamDroppedByKeepalive() throws Exception {
        McpSubscriptions subs = new McpSubscriptions(40);
        AtomicInteger closed = new AtomicInteger();
        OutputStream ghost = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("peer reset");
            }
        };
        ByteArrayOutputStream live = new ByteArrayOutputStream();
        subs.attach(ghost, closed::incrementAndGet);
        subs.attach(live, () -> { });
        // no subscription, no event: only the schedule can notice the ghost
        long deadline = System.currentTimeMillis() + 5_000;
        while (subs.attachedCount() != 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertThat(subs.attachedCount()).as("the ghost is gone, the live stream stays").isEqualTo(1);
        assertThat(closed.get()).isEqualTo(1);
        subs.awaitIdle();
        assertThat(live.toString(StandardCharsets.UTF_8)).as("the live stream saw a comment its parser ignores")
                .contains(McpSubscriptions.KEEPALIVE).doesNotContain("event:");
        subs.close();
        assertThat(subs.attachedCount()).isZero();
    }

    @Test
    @DisplayName("a stream whose client stopped reading stalls only itself: the other streams keep flowing and its keepalive is skipped (the review's find)")
    void stuckStreamIsIsolated() throws Exception {
        McpSubscriptions subs = new McpSubscriptions(40);
        java.util.concurrent.CountDownLatch gate = new java.util.concurrent.CountDownLatch(1);
        OutputStream stuck = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                try {
                    gate.await();
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            }
        };
        ByteArrayOutputStream live = new ByteArrayOutputStream();
        subs.attach(stuck, () -> { });
        subs.attach(live, () -> { });
        subs.subscribe("nmox://runs");
        subs.setLevel("debug");
        for (int i = 0; i < McpSubscriptions.MAX_PENDING + 5; i++) {
            subs.log("debug", "Run — x", "line " + i);
        }
        subs.updated("nmox://runs");
        // the live stream saw every line and the update while the stuck one has written nothing
        long deadline = System.currentTimeMillis() + 5_000;
        while (!live.toString(StandardCharsets.UTF_8).contains("resources/updated") && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        String text = live.toString(StandardCharsets.UTF_8);
        assertThat(text).contains("line " + (McpSubscriptions.MAX_PENDING + 4)).contains("resources/updated");
        assertThat(subs.attachedCount()).as("the stuck stream is not dropped — it is alive, just slow").isEqualTo(2);
        Thread.sleep(120);
        assertThat(live.toString(StandardCharsets.UTF_8)).as("keepalives reach the live stream while the other is stuck")
                .contains(McpSubscriptions.KEEPALIVE);
        gate.countDown();
        subs.close();
        assertThat(subs.attachedCount()).isZero();
    }

    @Test
    @DisplayName("log lines reach the streams only at or above the set level; the default is info")
    void logLevelGates() throws Exception {
        McpSubscriptions subs = new McpSubscriptions();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        subs.attach(out, () -> { });
        assertThat(subs.level()).isEqualTo("info");
        subs.log("debug", "Run — x", "compiled 3 files");
        subs.log("info", "Run — x", "$ npm run build");
        subs.log("error", "Run — x", "[exit 1]");
        subs.awaitIdle();
        String text = out.toString(StandardCharsets.UTF_8);
        assertThat(text).contains("\"method\":\"notifications/message\"")
                .contains("$ npm run build").contains("[exit 1]").doesNotContain("compiled 3 files");
        assertThat(subs.setLevel("debug")).isTrue();
        assertThat(subs.setLevel("loud")).as("a level the spec does not name").isFalse();
        subs.log("debug", "Run — x", "compiled 3 files");
        subs.awaitIdle();
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("compiled 3 files");
        assertThat(subs.setLevel("error")).isTrue();
        subs.log("warning", "Run — x", "deprecated");
        subs.awaitIdle();
        assertThat(out.toString(StandardCharsets.UTF_8)).doesNotContain("deprecated");
        subs.close();
    }

    @Test
    @DisplayName("a firehose past the pending cap is counted and announced once, never silently lost")
    void overflowIsCountedNotLost() throws Exception {
        McpSubscriptions subs = new McpSubscriptions();
        java.util.concurrent.CountDownLatch gate = new java.util.concurrent.CountDownLatch(1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OutputStream slow = new OutputStream() {
            boolean first = true;
            @Override
            public void write(int b) throws IOException {
                if (first) {
                    first = false;
                    try {
                        gate.await();
                    } catch (InterruptedException e) {
                        throw new IOException(e);
                    }
                }
                out.write(b);
            }
        };
        subs.attach(slow, () -> { });
        subs.setLevel("debug");
        int total = McpSubscriptions.MAX_PENDING + 250;
        for (int i = 0; i < total; i++) {
            subs.log("debug", "Run — x", "line " + i);
        }
        gate.countDown();
        subs.awaitIdle();
        String text = out.toString(StandardCharsets.UTF_8);
        int lines = text.split("notifications/message").length - 1;
        assertThat(text).contains("250 log lines dropped");
        assertThat(lines).as("the cap's worth of lines plus the one notice").isEqualTo(McpSubscriptions.MAX_PENDING + 1);
        assertThat(text).contains("line 0").contains("line " + (McpSubscriptions.MAX_PENDING - 1)).doesNotContain("line " + McpSubscriptions.MAX_PENDING + "\"");
        subs.close();
    }

    @Test
    @DisplayName("a sink dropped between the snapshot and its submit is left alone: the catch is the mechanism (structurally pinned — the race has no deterministic reproduction)")
    void droppedSinkSubmitIsCaught() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/org/nmox/studio/rack/mcp/McpSubscriptions.java"));
        assertThat(src.split("catch \\(java.util.concurrent.RejectedExecutionException").length - 1)
                .as("submit and log both catch the shut-down writer; awaitIdle too").isEqualTo(3);
    }

    @Test
    @DisplayName("an outline subscription follows its file: a change on disk announces the URI, a vanished file announces once and is dropped (v2.84.0)")
    void fileSubscriptionFollowsTheFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root,
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path elsewhere) throws Exception {
        java.nio.file.Files.createDirectories(root.resolve("src"));
        // a REAL file outside the aim, reached by a relative escape: the
        // containment law, not the missing-file law, must refuse it (the
        // first mutant survived on a fixture whose escape target did not exist)
        java.nio.file.Path secret = java.nio.file.Files.writeString(elsewhere.resolve("secret.js"), "KEY=1");
        String escape = root.relativize(secret).toString().replace(java.io.File.separatorChar, '/');
        assertThat(escape).startsWith("..");
        java.nio.file.Path app = root.resolve("src/app.js");
        java.nio.file.Files.writeString(app, "const a = 1;\n");
        McpSubscriptions subs = new McpSubscriptions(60_000, 30);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        subs.attach(out, () -> { });
        assertThat(subs.subscribeFile("nmox://outline/src/app.js", root.toFile(), "src/app.js")).isNull();
        assertThat(subs.subscribeFile("nmox://outline/../x", root.toFile(), "../x")).startsWith("not found");
        assertThat(subs.subscribeFile("nmox://outline/" + escape, root.toFile(), escape))
                .as("an existing file outside the aim is refused by containment").startsWith("not found");
        try {
            java.nio.file.Files.createSymbolicLink(root.resolve("src/link.js"), secret);
            assertThat(subs.subscribeFile("nmox://outline/src/link.js", root.toFile(), "src/link.js"))
                    .as("a symlink inside pointing outside is refused by REAL-path containment").startsWith("not found");
        } catch (UnsupportedOperationException | IOException noSymlinks) {
            // a filesystem without symlinks has no such escape to refuse
        }
        assertThat(subs.subscribeFile("nmox://outline/src", root.toFile(), "src")).as("a directory is not a file").startsWith("not found");
        assertThat(subs.subscribeFile("nmox://outline/none.js", root.toFile(), "none.js")).startsWith("not found");
        assertThat(subs.watchedFiles()).isEqualTo(1);
        Thread.sleep(80);
        assertThat(out.toString(StandardCharsets.UTF_8)).as("unchanged: silence").isEmpty();
        java.nio.file.Files.writeString(app, "const a = 1;\nconst b = 2;\n");
        java.nio.file.Files.setLastModifiedTime(app, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5_000));
        long deadline = System.currentTimeMillis() + 5_000;
        while (!out.toString(StandardCharsets.UTF_8).contains("nmox://outline/src/app.js") && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("resources/updated").contains("nmox://outline/src/app.js");
        java.nio.file.Files.delete(app);
        deadline = System.currentTimeMillis() + 5_000;
        while (subs.watchedFiles() != 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertThat(subs.watchedFiles()).as("a vanished file is dropped after one announcement").isZero();
        subs.awaitIdle();
        assertThat(out.toString(StandardCharsets.UTF_8).split("nmox://outline/src/app.js").length - 1).isEqualTo(2);
        subs.close();
    }

    @Test
    @DisplayName("file subscriptions are capped at 32; unsubscribe frees a slot")
    void fileSubscriptionsCapped(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
        McpSubscriptions subs = new McpSubscriptions(60_000, 60_000);
        for (int i = 0; i < McpSubscriptions.MAX_FILE_WATCHES; i++) {
            java.nio.file.Files.writeString(root.resolve("f" + i + ".js"), "x");
            assertThat(subs.subscribeFile("nmox://outline/f" + i + ".js", root.toFile(), "f" + i + ".js")).isNull();
        }
        java.nio.file.Files.writeString(root.resolve("more.js"), "x");
        assertThat(subs.subscribeFile("nmox://outline/more.js", root.toFile(), "more.js")).startsWith("capped");
        assertThat(subs.subscribeFile("nmox://outline/f0.js", root.toFile(), "f0.js")).as("re-subscribing a watched file is not a new slot").isNull();
        subs.unsubscribe("nmox://outline/f0.js");
        assertThat(subs.subscribeFile("nmox://outline/more.js", root.toFile(), "more.js")).isNull();
        subs.close();
    }
}
