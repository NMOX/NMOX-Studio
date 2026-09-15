package org.nmox.studio.dbstudio.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cursor paging (debt ledger 10) against a fake server: a firstBatch,
 * then getMore batches, then a cursor id of 0. No MongoDB anywhere.
 */
class MongoCursorPagerTest {

    private static final long CURSOR = 4242L;

    /** A scripted server: answers getMore from a queue, records every command. */
    private static final class FakeServer implements MongoCursorPager.Transport {

        final Deque<Document> replies = new ArrayDeque<>();
        final List<Document> commands = new ArrayList<>();
        final List<Document> released = new ArrayList<>();
        Runnable onGetMore = () -> { };

        @Override
        public Document run(Document command) {
            commands.add(command);
            if (command.containsKey("getMore")) {
                onGetMore.run();
            }
            Document reply = replies.poll();
            if (reply == null) {
                throw new IllegalStateException("script exhausted at " + command.toJson());
            }
            return reply;
        }

        @Override
        public Document release(Document killCursors) {
            released.add(killCursors);
            return new Document("ok", 1.0);
        }

        long getMores() {
            return commands.stream().filter(c -> c.containsKey("getMore")).count();
        }
    }

    private static List<Document> docs(int from, int count) {
        List<Document> out = new ArrayList<>();
        for (int i = from; i < from + count; i++) {
            out.add(new Document("n", i));
        }
        return out;
    }

    private static Document first(long id, List<Document> batch) {
        return new Document("cursor", new Document("id", id).append("ns", "appdb.users")
                .append("firstBatch", batch)).append("ok", 1.0);
    }

    private static Document next(long id, List<Document> batch) {
        return new Document("cursor", new Document("id", id).append("ns", "appdb.users")
                .append("nextBatch", batch)).append("ok", 1.0);
    }

    private static List<Integer> numbers(MongoCursorPager.Page page) {
        return page.documents().stream().map(d -> d.getInteger("n")).toList();
    }

    @Test
    @DisplayName("getMore is followed past the first batch until the cursor id is 0")
    void followsGetMoreUntilExhausted() {
        FakeServer server = new FakeServer();
        server.replies.add(next(CURSOR, docs(3, 3)));
        server.replies.add(next(0, docs(6, 2)));

        MongoCursorPager.Page page = MongoCursorPager.follow(
                first(CURSOR, docs(0, 3)), 100, () -> false, server);

        assertThat(numbers(page)).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
        assertThat(page.truncated()).as("the cursor ran dry — the whole set").isFalse();
        assertThat(page.cancelled()).isFalse();
        assertThat(server.getMores()).isEqualTo(2);
        Document getMore = server.commands.get(0);
        assertThat(getMore.get("getMore")).isEqualTo(CURSOR);
        assertThat(getMore.getString("collection")).isEqualTo("users");
        assertThat(server.released).as("an exhausted cursor needs no release").isEmpty();
    }

    @Test
    @DisplayName("the row cap stops paging, marks the set truncated, and asks only for what is still needed")
    void capStopsPagingAndMarksTruncated() {
        FakeServer server = new FakeServer();
        server.replies.add(next(CURSOR, docs(3, 3)));
        server.replies.add(next(CURSOR, docs(6, 3)));

        MongoCursorPager.Page page = MongoCursorPager.follow(
                first(CURSOR, docs(0, 3)), 5, () -> false, server);

        assertThat(numbers(page)).containsExactly(0, 1, 2, 3, 4);
        assertThat(page.truncated()).isTrue();
        assertThat(server.getMores()).as("one getMore reached the cap plus one").isEqualTo(1);
        assertThat(server.commands.get(0).getInteger("batchSize"))
                .as("limit + 1 - kept: bounded by the cap, not the collection").isEqualTo(3);
        assertThat(server.replies).as("the second batch was never asked for").hasSize(1);
    }

    @Test
    @DisplayName("an abandoned cursor is released with killCursors naming its collection and id")
    void abandonedCursorIsKilled() {
        FakeServer server = new FakeServer();

        MongoCursorPager.Page page = MongoCursorPager.follow(
                first(CURSOR, docs(0, 10)), 4, () -> false, server);

        assertThat(page.truncated()).isTrue();
        assertThat(server.getMores()).as("the first batch already held more than the cap").isZero();
        assertThat(server.released).hasSize(1);
        Document kill = server.released.get(0);
        assertThat(kill.getString("killCursors")).isEqualTo("users");
        assertThat(kill.getList("cursors", Long.class)).containsExactly(CURSOR);
    }

    @Test
    @DisplayName("exactly the cap with the cursor still open: one more getMore settles whether more exist")
    void exactCapProbesOnce() {
        FakeServer server = new FakeServer();
        server.replies.add(next(0, List.of()));

        MongoCursorPager.Page page = MongoCursorPager.follow(
                first(CURSOR, docs(0, 4)), 4, () -> false, server);

        assertThat(numbers(page)).containsExactly(0, 1, 2, 3);
        assertThat(page.truncated()).as("the server said nothing more exists").isFalse();
        assertThat(server.commands.get(0).getInteger("batchSize")).isEqualTo(1);
        assertThat(server.released).isEmpty();
    }

    @Test
    @DisplayName("a cancel stops the loop before the next getMore, releases the cursor, and says so")
    void cancelStopsPaging() {
        FakeServer server = new FakeServer();
        AtomicBoolean cancelled = new AtomicBoolean();
        server.replies.add(next(CURSOR, docs(3, 3)));
        server.replies.add(next(CURSOR, docs(6, 3)));
        server.onGetMore = () -> cancelled.set(true); // Cancel pressed while the first getMore ran

        MongoCursorPager.Page page = MongoCursorPager.follow(
                first(CURSOR, docs(0, 3)), 100, cancelled::get, server);

        assertThat(page.cancelled()).isTrue();
        assertThat(server.getMores()).as("no getMore after the cancel").isEqualTo(1);
        assertThat(page.truncated()).isTrue();
        assertThat(server.released).hasSize(1);
    }

    @Test
    @DisplayName("a getMore that throws still releases the cursor, then rethrows")
    void failureMidPagingReleases() {
        List<Document> released = new ArrayList<>();
        MongoCursorPager.Transport server = new MongoCursorPager.Transport() {
            @Override
            public Document run(Document command) {
                throw new IllegalStateException("socket closed");
            }

            @Override
            public Document release(Document killCursors) {
                released.add(killCursors);
                throw new IllegalStateException("release fails too — logged, never thrown over the cause");
            }
        };

        assertThatThrownBy(() -> MongoCursorPager.follow(first(CURSOR, docs(0, 1)), 10,
                () -> false, server)).hasMessage("socket closed");
        assertThat(released).as("the open cursor was released before the rethrow").hasSize(1);
        assertThat(released.get(0).getList("cursors", Long.class)).containsExactly(CURSOR);
    }

    @Test
    @DisplayName("a malformed getMore reply stops paging and marks the set partial, never complete")
    void malformedNextBatchIsPartial() {
        FakeServer server = new FakeServer();
        server.replies.add(new Document("ok", 1.0)); // no cursor at all

        MongoCursorPager.Page page = MongoCursorPager.follow(
                first(CURSOR, docs(0, 2)), 10, () -> false, server);

        assertThat(numbers(page)).containsExactly(0, 1);
        assertThat(page.truncated()).isTrue();
        assertThat(server.released).hasSize(1);
    }

    @Test
    @DisplayName("an open cursor with no namespace cannot be paged — partial, and no getMore guessed")
    void noNamespaceIsPartial() {
        FakeServer server = new FakeServer();
        Document reply = new Document("cursor", new Document("id", CURSOR)
                .append("firstBatch", docs(0, 2))).append("ok", 1.0);

        MongoCursorPager.Page page = MongoCursorPager.follow(reply, 10, () -> false, server);

        assertThat(page.truncated()).isTrue();
        assertThat(server.commands).isEmpty();
    }

    @Test
    @DisplayName("unlimited is still bounded, at the ceiling")
    void unlimitedIsBounded() {
        assertThat(MongoCursorPager.effectiveLimit(0)).isEqualTo(MongoCursorPager.UNLIMITED_CEILING);
        assertThat(MongoCursorPager.effectiveLimit(-1)).isEqualTo(MongoCursorPager.UNLIMITED_CEILING);
        assertThat(MongoCursorPager.effectiveLimit(Integer.MAX_VALUE))
                .isEqualTo(MongoCursorPager.UNLIMITED_CEILING);
        assertThat(MongoCursorPager.effectiveLimit(50)).isEqualTo(50);
    }

    @Test
    @DisplayName("a reply without a document cursor is not a page")
    void noCursorIsNull() {
        assertThat(MongoCursorPager.follow(new Document("n", 3), 10, () -> false, new FakeServer()))
                .isNull();
        assertThat(MongoCursorPager.follow(Document.parse(
                "{\"cursor\": {\"firstBatch\": [1, 2]}}"), 10, () -> false, new FakeServer())).isNull();
    }

    @Test
    @DisplayName("the collection is everything after the database's dot, dots included")
    void collectionOfNamespace() {
        assertThat(MongoCursorPager.collectionOf("appdb.users")).isEqualTo("users");
        assertThat(MongoCursorPager.collectionOf("appdb.$cmd.listCollections"))
                .isEqualTo("$cmd.listCollections");
        assertThat(MongoCursorPager.collectionOf("nodot")).isNull();
        assertThat(MongoCursorPager.collectionOf(".users")).isNull();
        assertThat(MongoCursorPager.collectionOf("appdb.")).isNull();
        assertThat(MongoCursorPager.collectionOf(null)).isNull();
    }

    @Test
    @DisplayName("release ignores a closed cursor and one with no namespace")
    void releaseNoOps() {
        FakeServer server = new FakeServer();
        MongoCursorPager.release(0, "users", server);
        MongoCursorPager.release(CURSOR, null, server);
        assertThat(server.released).isEmpty();
    }
}
