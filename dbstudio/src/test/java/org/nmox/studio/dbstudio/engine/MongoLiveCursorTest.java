package org.nmox.studio.dbstudio.engine;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The paging and cancel decisions against a REAL MongoDB — skipped
 * unless {@code -Dnmox.mongo.live=host:port} names one (debt ledger 11:
 * CI has no server). Run it against a throwaway container:
 * {@code docker run --rm -p 127.0.0.1:27777:27017 mongo:7}, then
 * {@code -Dnmox.mongo.live=127.0.0.1:27777}.
 */
@EnabledIfSystemProperty(named = "nmox.mongo.live", matches = ".+:\\d+")
class MongoLiveCursorTest {

    private static final String DB = "nmox_live_cursor";

    private static String host() {
        return System.getProperty("nmox.mongo.live").split(":")[0];
    }

    private static int port() {
        return Integer.parseInt(System.getProperty("nmox.mongo.live").split(":")[1]);
    }

    private static MongoClient admin() {
        return MongoClients.create("mongodb://" + host() + ":" + port());
    }

    private static MongoBackend backend() {
        return new MongoBackend(new ConnectionSpec("live", "live", DbEngine.MONGODB,
                host(), port(), DB, "", ""), null);
    }

    private static void seed(MongoClient client, int count) {
        MongoDatabase db = client.getDatabase(DB);
        db.getCollection("docs").drop();
        List<Document> docs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            docs.add(new Document("n", i));
        }
        db.getCollection("docs").insertMany(docs);
    }

    private static int openCursors(MongoClient client) {
        Document status = client.getDatabase("admin").runCommand(new Document("serverStatus", 1));
        Document open = status.get("metrics", Document.class).get("cursor", Document.class)
                .get("open", Document.class);
        return ((Number) open.get("total")).intValue();
    }

    @Test
    @DisplayName("live: 250 documents page past the first batch of 101, and a capped read leaks no cursor")
    void pagesPastFirstBatchAndReleases() {
        try (MongoClient client = admin()) {
            seed(client, 250);
            MongoBackend backend = backend();
            try {
                QueryResult whole = backend.runConsole("{\"find\": \"docs\"}", 1000).get(0);
                System.out.println("[live] whole: rows=" + whole.rowCount()
                        + " truncated=" + whole.truncated() + " error=" + whole.error());
                assertThat(whole.error()).isNull();
                assertThat(whole.rowCount()).isEqualTo(250);
                assertThat(whole.truncated()).isFalse();

                int before = openCursors(client);
                QueryResult capped = backend.runConsole("{\"find\": \"docs\"}", 150).get(0);
                int after = openCursors(client);
                System.out.println("[live] capped: rows=" + capped.rowCount()
                        + " truncated=" + capped.truncated()
                        + " openCursors before=" + before + " after=" + after);
                assertThat(capped.rowCount()).isEqualTo(150);
                assertThat(capped.truncated()).isTrue();
                assertThat(after).as("the abandoned cursor was killed").isEqualTo(before);
            } finally {
                backend.close();
            }
        }
    }

    @Test
    @DisplayName("live: Cancel stops a slow command, says so, and the connection answers the next one")
    void cancelStopsSlowCommand() throws Exception {
        try (MongoClient client = admin()) {
            seed(client, 40);
            MongoBackend backend = backend();
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                assertThat(backend.open()).isNull();
                long start = System.nanoTime();
                Future<List<QueryResult>> run = pool.submit(() -> backend.runConsole(
                        "{\"find\": \"docs\", \"filter\": {\"$where\": \"sleep(500) || true\"}}", 100));
                Thread.sleep(1_500);
                backend.cancel();
                QueryResult result = run.get(10, TimeUnit.SECONDS).get(0);
                long ms = (System.nanoTime() - start) / 1_000_000L;
                System.out.println("[live] cancel: after " + ms + " ms error=" + result.error());
                assertThat(result.error()).startsWith("Cancelled");
                assertThat(ms).as("well short of the ~20 s the command would take").isLessThan(8_000);

                QueryResult next = backend.runConsole("{\"count\": \"docs\"}", 10).get(0);
                System.out.println("[live] after cancel: " + next.rows() + " error=" + next.error());
                assertThat(next.error()).isNull();
                assertThat(backend.isOpen()).isTrue();
            } finally {
                pool.shutdownNow();
                backend.close();
            }
        }
    }
}
