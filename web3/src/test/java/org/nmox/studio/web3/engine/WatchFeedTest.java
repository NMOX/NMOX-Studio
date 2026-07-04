package org.nmox.studio.web3.engine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Watch pane's pure feed model: newest-first ordering, block
 * dedupe by hash, the 500-row ring, and snapshot isolation.
 */
class WatchFeedTest {

    @Test
    @DisplayName("rows come back newest first")
    void newestFirst() {
        WatchFeed feed = new WatchFeed();
        feed.addBlock(1, 0, 0, 30_000_000, "0xaaa");
        feed.addBlock(2, 1, 21000, 30_000_000, "0xbbb");
        feed.addEvent(2, "Counter", "Incremented", Map.of("newValue", "1"));

        List<WatchFeed.Row> rows = feed.rows();
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).isInstanceOf(WatchFeed.EventRow.class);
        assertThat(((WatchFeed.BlockRow) rows.get(1)).number()).isEqualTo(2);
        assertThat(((WatchFeed.BlockRow) rows.get(2)).number()).isEqualTo(1);
    }

    @Test
    @DisplayName("a block hash seen twice is dropped — overlapping polls stay clean")
    void blockDedupe() {
        WatchFeed feed = new WatchFeed();
        assertThat(feed.addBlock(5, 0, 0, 0, "0xsame")).isTrue();
        assertThat(feed.addBlock(5, 0, 0, 0, "0xsame")).isFalse();
        assertThat(feed.rows()).hasSize(1);
    }

    @Test
    @DisplayName("events never dedupe — the same event shape can fire twice")
    void eventsNotDeduped() {
        WatchFeed feed = new WatchFeed();
        feed.addEvent(1, "Counter", "Incremented", Map.of("v", "1"));
        feed.addEvent(1, "Counter", "Incremented", Map.of("v", "1"));
        assertThat(feed.rows()).hasSize(2);
    }

    @Test
    @DisplayName("the ring caps at 500 rows; the oldest fall off")
    void ringCap() {
        WatchFeed feed = new WatchFeed();
        for (int i = 0; i < 600; i++) {
            feed.addBlock(i, 0, 0, 0, "0x" + i);
        }
        List<WatchFeed.Row> rows = feed.rows();
        assertThat(rows).hasSize(WatchFeed.CAP);
        assertThat(((WatchFeed.BlockRow) rows.get(0)).number()).isEqualTo(599);
        assertThat(((WatchFeed.BlockRow) rows.get(rows.size() - 1)).number()).isEqualTo(100);
    }

    @Test
    @DisplayName("an evicted block's hash can re-enter the ring later")
    void evictedHashForgotten() {
        WatchFeed feed = new WatchFeed();
        for (int i = 0; i < WatchFeed.CAP + 1; i++) {
            feed.addBlock(i, 0, 0, 0, "0x" + i);
        }
        // block 0 has been evicted; its hash is no longer "seen"
        assertThat(feed.addBlock(0, 0, 0, 0, "0x0")).isTrue();
    }

    @Test
    @DisplayName("clear empties the feed and forgets the seen hashes")
    void clearResets() {
        WatchFeed feed = new WatchFeed();
        feed.addBlock(1, 0, 0, 0, "0xaaa");
        feed.clear();
        assertThat(feed.rows()).isEmpty();
        assertThat(feed.addBlock(1, 0, 0, 0, "0xaaa")).isTrue();
    }

    @Test
    @DisplayName("rows() is a snapshot — later adds don't mutate it")
    void snapshotIsolation() {
        WatchFeed feed = new WatchFeed();
        feed.addBlock(1, 0, 0, 0, "0xaaa");
        List<WatchFeed.Row> snapshot = feed.rows();
        feed.addBlock(2, 0, 0, 0, "0xbbb");
        assertThat(snapshot).hasSize(1);
    }

    @Test
    @DisplayName("an event row keeps its decoded map order and is immutable")
    void eventRowMapSemantics() {
        Map<String, String> decoded = new LinkedHashMap<>();
        decoded.put("from", "0xa");
        decoded.put("to", "0xb");
        decoded.put("value", "1");
        WatchFeed feed = new WatchFeed();
        feed.addEvent(3, "Token", "Transfer", decoded);
        decoded.put("tamper", "later"); // caller mutation must not show

        WatchFeed.EventRow row = (WatchFeed.EventRow) feed.rows().get(0);
        assertThat(row.decoded().keySet()).containsExactly("from", "to", "value");
        assertThat(row.contractName()).isEqualTo("Token");
        assertThat(row.eventName()).isEqualTo("Transfer");
    }

    @Test
    @DisplayName("null hash and null decoded map are tolerated")
    void nullTolerance() {
        WatchFeed feed = new WatchFeed();
        assertThat(feed.addBlock(1, 0, 0, 0, null)).isTrue();
        assertThat(feed.addBlock(2, 0, 0, 0, null)).isFalse(); // both "", deduped
        feed.addEvent(1, "C", "E", null);
        assertThat(feed.rows()).hasSize(2);
    }
}
