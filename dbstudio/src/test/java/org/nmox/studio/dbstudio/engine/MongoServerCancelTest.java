package org.nmox.studio.dbstudio.engine;

import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The server half of a MongoDB cancel (debt ledger 10): which
 * operations {@code killOp} is sent for. The fake {@code currentOp}
 * reply matches the shape measured on mongo:7: top-level
 * {@code appName} and {@code opid}, with the {@code currentOp} call
 * itself in the list.
 */
class MongoServerCancelTest {

    private static final String ME = "NMOX Studio DB 1234";

    private static Document op(Object opid, String appName, Document command) {
        Document op = new Document("opid", opid).append("type", "op").append("active", true);
        if (appName != null) {
            op.append("appName", appName);
        }
        if (command != null) {
            op.append("command", command);
        }
        return op;
    }

    private static Document currentOpReply(Document... ops) {
        return new Document("inprog", List.of(ops)).append("ok", 1.0);
    }

    @Test
    @DisplayName("kills this backend's running command, and not its own currentOp or another app's work")
    void killsOnlyOwnRunningCommand() {
        Document reply = currentOpReply(
                op(5301, ME, new Document("find", "docs").append("filter", new Document())),
                op(5310, ME, new Document("currentOp", 1).append("$ownOps", true)),
                op(5295, "mongosh 2.10.0", new Document("aggregate", "orders")),
                op(5063, null, new Document()));
        List<Document> sent = new ArrayList<>();
        MongoCursorPager.Transport admin = command -> {
            sent.add(command);
            return command.containsKey("currentOp") ? reply : new Document("ok", 1.0);
        };

        int killed = MongoServerCancel.kill(admin, ME);

        assertThat(killed).isEqualTo(1);
        assertThat(sent).hasSize(2);
        assertThat(sent.get(0)).isEqualTo(MongoServerCancel.currentOpCommand(ME));
        assertThat(sent.get(0).getBoolean("$ownOps")).isTrue();
        assertThat(sent.get(0).getString("appName")).isEqualTo(ME);
        assertThat(sent.get(1).getInteger("killOp")).isEqualTo(1);
        assertThat(sent.get(1).get("op")).isEqualTo(5301);
    }

    @Test
    @DisplayName("a getMore in flight is this backend's operation too, and a sharded opid passes through untouched")
    void getMoreAndShardedOpids() {
        Document reply = currentOpReply(
                op("shard01:7788", ME, new Document("getMore", 99L).append("collection", "docs")),
                op(12L, ME, new Document("killOp", 1).append("op", 11)));

        assertThat(MongoServerCancel.opsToKill(reply, ME)).containsExactly("shard01:7788");
    }

    @Test
    @DisplayName("a failed currentOp or killOp never throws; the cancel falls back to the interrupt")
    void failuresAreSwallowed() {
        assertThat(MongoServerCancel.kill(command -> {
            throw new IllegalStateException("not authorized");
        }, ME)).isZero();

        Document reply = currentOpReply(op(1, ME, new Document("find", "a")),
                op(2, ME, new Document("find", "b")));
        assertThat(MongoServerCancel.kill(command -> {
            if (command.containsKey("killOp") && Integer.valueOf(1).equals(command.get("op"))) {
                throw new IllegalStateException("op already gone");
            }
            return command.containsKey("currentOp") ? reply : new Document("ok", 1.0);
        }, ME)).as("the second kill still went out").isEqualTo(1);
    }

    @Test
    @DisplayName("malformed replies and entries without an opid yield nothing to kill")
    void malformedReplies() {
        assertThat(MongoServerCancel.opsToKill(null, ME)).isEmpty();
        assertThat(MongoServerCancel.opsToKill(new Document("ok", 1.0), ME)).isEmpty();
        assertThat(MongoServerCancel.opsToKill(new Document("inprog", List.of("x", 3)), ME)).isEmpty();
        assertThat(MongoServerCancel.opsToKill(currentOpReply(op(null, ME, null)), ME)).isEmpty();
        assertThat(MongoServerCancel.opsToKill(currentOpReply(op(8, ME, null)), null)).isEmpty();
        assertThat(MongoServerCancel.opsToKill(currentOpReply(op(8, ME, null)), ME))
                .as("an op with no command document is still ours to kill").containsExactly(8);
    }
}
