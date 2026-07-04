package org.nmox.studio.web3.engine;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The pure model behind the Watch pane: a newest-first feed of block
 * rows and decoded-event rows, ring-capped at {@value #CAP}. Blocks
 * dedupe by hash so an overlapping poll never shows the same block
 * twice. Thread-safe — the poller adds off-EDT, the pane snapshots
 * with {@link #rows()} on the EDT.
 */
public final class WatchFeed {

    /** The ring cap — older rows fall off the end. */
    public static final int CAP = 500;

    /** One row of the feed. */
    public sealed interface Row permits BlockRow, EventRow {
    }

    /** A mined block: {@code "#12 · 3 txs · gas 42%"} material. */
    public record BlockRow(long number, int txCount, long gasUsed,
            long gasLimit, String hash) implements Row {
    }

    /** One decoded contract event. */
    public record EventRow(long blockNumber, String contractName,
            String eventName, Map<String, String> decoded) implements Row {

        public EventRow {
            decoded = Collections.unmodifiableMap(new LinkedHashMap<>(decoded));
        }
    }

    private final ArrayDeque<Row> rows = new ArrayDeque<>();
    private final Set<String> seenBlockHashes = new HashSet<>();

    /**
     * Adds a block row, newest-first. A hash already in the feed is a
     * duplicate poll result and is dropped.
     *
     * @return true when the block was new
     */
    public synchronized boolean addBlock(long number, int txCount, long gasUsed,
            long gasLimit, String hash) {
        String key = hash == null ? "" : hash;
        if (!seenBlockHashes.add(key)) {
            return false;
        }
        push(new BlockRow(number, txCount, gasUsed, gasLimit, key));
        return true;
    }

    /** Adds a decoded-event row, newest-first. */
    public synchronized void addEvent(long blockNumber, String contractName,
            String eventName, Map<String, String> decoded) {
        push(new EventRow(blockNumber, contractName, eventName,
                decoded == null ? Map.of() : decoded));
    }

    /** An immutable snapshot, newest first. */
    public synchronized List<Row> rows() {
        return List.copyOf(rows);
    }

    /** Empties the feed (START over). */
    public synchronized void clear() {
        rows.clear();
        seenBlockHashes.clear();
    }

    private void push(Row row) {
        rows.addFirst(row);
        while (rows.size() > CAP) {
            Row evicted = rows.removeLast();
            if (evicted instanceof BlockRow block) {
                seenBlockHashes.remove(block.hash());
            }
        }
    }
}
