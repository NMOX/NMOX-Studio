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
        java.util.concurrent.CountDownLatch wedged = new java.util.concurrent.CountDownLatch(1);
        OutputStream stuck = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                wedged.countDown();
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

        // the isolation itself, as a rendezvous and not a race: wait for
        // the stuck writer to be provably INSIDE its first byte, then
        // await the live stream's OWN writer. awaitIdle() over every
        // stream cannot serve here — it would wait on the wedged one and
        // time out — and awaiting the frame by deadline cannot either,
        // because a frame the cap legitimately drops never arrives at all
        subs.log("debug", "Run — x", "first line");
        subs.updated("nmox://runs");
        assertThat(wedged.await(30, java.util.concurrent.TimeUnit.SECONDS))
                .as("the stuck client is wedged inside its first byte").isTrue();
        subs.awaitIdle(live);
        assertThat(live.toString(StandardCharsets.UTF_8))
                .as("the live stream wrote both frames while the other stream's writer is wedged")
                .contains("first line").contains("resources/updated");

        // now saturate the wedged stream past the cap: it stays attached
        // (alive, just slow) and its keepalive is skipped there, while the
        // live stream's own writer keeps draining. Nothing is asserted
        // about WHICH of these lines the live stream kept: a producer that
        // outruns any consumer may reach that consumer's cap too, and
        // dropping there is the behaviour the cap exists for
        for (int i = 0; i < McpSubscriptions.MAX_PENDING + 5; i++) {
            subs.log("debug", "Run — x", "line " + i);
        }
        subs.awaitIdle(live);
        assertThat(subs.attachedCount()).as("the stuck stream is not dropped — it is alive, just slow").isEqualTo(2);

        // poll, never a fixed sleep: a loaded CI runner can starve a 40 ms
        // scheduler for longer than any single window (the flake class).
        // The live stream's backlog has drained above, so this condition
        // can only become true — the leash names a real failure, never a
        // slow one
        long deadline = System.currentTimeMillis() + 30_000;
        while (!live.toString(StandardCharsets.UTF_8).contains(McpSubscriptions.KEEPALIVE) && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
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
        // the exact drop count below is only true once the writer has TAKEN the
        // first frame — until then that frame still occupies a queue slot and
        // one more is dropped. Rendezvous rather than hope for the schedule
        // (v2.99.1, twice in this class now; macOS CI is where it showed).
        java.util.concurrent.CountDownLatch writing = new java.util.concurrent.CountDownLatch(1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OutputStream slow = new OutputStream() {
            boolean first = true;
            @Override
            public void write(int b) throws IOException {
                if (first) {
                    first = false;
                    writing.countDown();
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
        subs.log("debug", "Run — x", "line 0");
        assertThat(writing.await(10, java.util.concurrent.TimeUnit.SECONDS))
                .as("the writer should have taken the first frame before the flood").isTrue();
        for (int i = 1; i < total; i++) {
            subs.log("debug", "Run — x", "line " + i);
        }
        gate.countDown();
        subs.awaitIdle();
        String text = out.toString(StandardCharsets.UTF_8);
        int lines = text.split("notifications/message").length - 1;
        assertThat(text).contains("250 frames dropped");
        assertThat(lines).as("the cap's worth of lines plus the one notice").isEqualTo(McpSubscriptions.MAX_PENDING + 1);
        assertThat(text).contains("line 0").contains("line " + (McpSubscriptions.MAX_PENDING - 1)).doesNotContain("line " + McpSubscriptions.MAX_PENDING + "\"");
        subs.close();
    }

    @Test
    @DisplayName("resource updates past the pending cap are bounded and announced like log lines — every frame kind rides one backlog (the v2.85.0 review)")
    void updatesPastTheCapAreBoundedToo() throws Exception {
        McpSubscriptions subs = new McpSubscriptions();
        java.util.concurrent.CountDownLatch gate = new java.util.concurrent.CountDownLatch(1);
        // the exact drop count below is only true once the writer has TAKEN the
        // first frame — until then that frame still occupies a queue slot and
        // one more is dropped. Rendezvous rather than hope for the schedule
        // (v2.99.1, twice in this class now; macOS CI is where it showed).
        java.util.concurrent.CountDownLatch writing = new java.util.concurrent.CountDownLatch(1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OutputStream slow = new OutputStream() {
            boolean first = true;
            @Override
            public void write(int b) throws IOException {
                if (first) {
                    first = false;
                    writing.countDown();
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
        subs.subscribe("nmox://runs");
        int total = McpSubscriptions.MAX_PENDING + 250;
        subs.updated("nmox://runs");
        assertThat(writing.await(10, java.util.concurrent.TimeUnit.SECONDS))
                .as("the writer should have taken the first frame before the flood").isTrue();
        for (int i = 1; i < total; i++) {
            subs.updated("nmox://runs");
        }
        gate.countDown();
        subs.awaitIdle();
        String text = out.toString(StandardCharsets.UTF_8);
        int updates = text.split("resources/updated").length - 1;
        assertThat(updates).as("the cap's worth of updates, not the flood").isEqualTo(McpSubscriptions.MAX_PENDING);
        assertThat(text).contains("250 frames dropped");
        subs.close();
    }

    @Test
    @DisplayName("a stream attaching after close is refused at once — nothing would keep it alive")
    void attachAfterCloseRefuses() throws Exception {
        McpSubscriptions subs = new McpSubscriptions();
        subs.close();
        boolean[] closedHook = {false};
        java.util.concurrent.atomic.AtomicBoolean streamClosed = new java.util.concurrent.atomic.AtomicBoolean();
        OutputStream out = new OutputStream() {
            @Override
            public void write(int b) {
            }
            @Override
            public void close() {
                streamClosed.set(true);
            }
        };
        subs.attach(out, () -> closedHook[0] = true);
        assertThat(subs.attachedCount()).isZero();
        assertThat(streamClosed.get()).as("the socket is closed").isTrue();
        assertThat(closedHook[0]).as("the close hook ran").isTrue();
    }

    @Test
    @DisplayName("a sink dropped between the snapshot and its submit is left alone: the catch is the mechanism (structurally pinned — the race has no deterministic reproduction)")
    void droppedSinkSubmitIsCaught() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/org/nmox/studio/rack/mcp/McpSubscriptions.java"));
        assertThat(src.split("catch \\(java.util.concurrent.RejectedExecutionException").length - 1)
                .as("the one enqueue path catches the shut-down writer; awaitIdle too (v2.85.0: log rides submit)").isEqualTo(2);
    }

    @Test
    @DisplayName("an outline subscription follows its file: a change on disk announces the URI ONCE, a vanished file announces once and is dropped (v2.84.0)")
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
        // the poll period is out of reach ON PURPOSE: this test drives
        // pollFiles() itself, so each count below is a function of the
        // change it just made and not of how many ticks a loaded runner
        // fitted around it. Racing a 30 ms schedule made the end count
        // 3 whenever a tick landed between the rewrite and the mtime bump
        // and announced that half-change on its own. That the schedule is
        // armed at all is pinned by fileWatchesRideThePollSchedule
        McpSubscriptions subs = new McpSubscriptions(60_000, 60_000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        subs.attach(out, () -> { });
        String uri = "nmox://outline/src/app.js";
        assertThat(subs.subscribeFile(uri, root.toFile(), "src/app.js")).isNull();
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

        subs.pollFiles();
        subs.pollFiles();
        subs.awaitIdle();
        assertThat(out.toString(StandardCharsets.UTF_8)).as("unchanged: silence").isEmpty();

        java.nio.file.Files.writeString(app, "const a = 1;\nconst b = 2;\n");
        java.nio.file.Files.setLastModifiedTime(app, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5_000));
        subs.pollFiles();
        subs.awaitIdle();
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("resources/updated").contains(uri);
        assertThat(announcements(out, uri)).as("one change, one announcement").isEqualTo(1);
        subs.pollFiles();
        subs.awaitIdle();
        assertThat(announcements(out, uri)).as("a settled file announces nothing further").isEqualTo(1);

        java.nio.file.Files.delete(app);
        subs.pollFiles();
        subs.awaitIdle();
        assertThat(subs.watchedFiles()).as("a vanished file is dropped after one announcement").isZero();
        assertThat(subs.isSubscribed(uri)).as("and its subscription goes with it").isFalse();
        assertThat(announcements(out, uri)).as("the vanishing is the second and last announcement").isEqualTo(2);
        subs.pollFiles();
        subs.awaitIdle();
        assertThat(announcements(out, uri)).as("the drop is final: a gone file is never announced twice").isEqualTo(2);
        subs.close();
    }

    @Test
    @DisplayName("file watches ride the poll schedule: a change announces with nobody calling the poll")
    void fileWatchesRideThePollSchedule(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
        java.nio.file.Path app = java.nio.file.Files.writeString(root.resolve("app.js"), "const a = 1;\n");
        McpSubscriptions subs = new McpSubscriptions(60_000, 20);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        subs.attach(out, () -> { });
        assertThat(subs.subscribeFile("nmox://outline/app.js", root.toFile(), "app.js")).isNull();
        java.nio.file.Files.writeString(app, "const a = 1;\nconst b = 2;\n");
        java.nio.file.Files.setLastModifiedTime(app, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5_000));
        // an EXISTENCE property, never a count: the schedule being armed is
        // what is under test, and how many ticks a loaded runner fits around
        // one change is the runner's business. Waiting can only end one way
        // here, so a long leash costs nothing but names a real failure
        long deadline = System.currentTimeMillis() + 30_000;
        while (!out.toString(StandardCharsets.UTF_8).contains("nmox://outline/app.js") && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertThat(out.toString(StandardCharsets.UTF_8))
                .as("subscribeFile armed the poll schedule — nothing else could have announced this")
                .contains("resources/updated").contains("nmox://outline/app.js");
        subs.close();
    }

    /** How many times {@code uri} was announced on this stream. */
    private static int announcements(ByteArrayOutputStream out, String uri) {
        return out.toString(StandardCharsets.UTF_8).split(java.util.regex.Pattern.quote(uri), -1).length - 1;
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

    @Test
    @DisplayName("a keepalive that cannot be scheduled does not cost the stream, nor its slot twice")
    void attachSurvivesAKeepaliveThatCannotStart() throws Exception {
        // The arc review of v2.109.0 (v2.114.0): attach() adds the sink and
        // THEN schedules the keepalive. If that scheduling throws, the sink
        // is already in the list — so AgentPort.openStream releases the slot
        // in its catch, and the sink's own drop releases it AGAIN later. Two
        // releases for one reservation walks the counter below the number of
        // live streams, and enough of them turn the cap off entirely.
        //
        // A negative period is the reachable trigger: scheduleAtFixedRate
        // refuses it, which is exactly the shape of any scheduling failure.
        McpSubscriptions subs = new McpSubscriptions(-1L);
        try {
            assertThat(subs.reserve(8)).isTrue();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            java.util.concurrent.atomic.AtomicInteger closed = new java.util.concurrent.atomic.AtomicInteger();
            subs.attach(out, () -> {
                subs.release();
                closed.incrementAndGet();
            });
            assertThat(subs.attachedCount())
                    .as("a stream that works is not thrown away because its keepalive would not start")
                    .isEqualTo(1);
            assertThat(subs.slotsTaken()).as("still exactly one slot, taken once").isEqualTo(1);
            subs.close();
            assertThat(closed.get()).as("the sink is dropped exactly once").isEqualTo(1);
            assertThat(subs.slotsTaken())
                    .as("one reservation, one release — never two, or the cap stops being a cap")
                    .isZero();
        } finally {
            subs.close();
        }
    }
}
