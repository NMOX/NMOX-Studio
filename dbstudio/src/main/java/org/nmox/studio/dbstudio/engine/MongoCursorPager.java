package org.nmox.studio.dbstudio.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bson.Document;

/**
 * Follows a MongoDB command cursor past its first batch (debt ledger
 * 10): the pure paging decision behind {@link MongoBackend}, written
 * against a two-method {@link Transport} so every rule is testable
 * without a server.
 *
 * <p><b>The rules.</b>
 * <ul>
 *   <li>A reply carrying {@code cursor.firstBatch} is followed with
 *       {@code getMore} until the cursor is exhausted (id 0) or ONE
 *       document beyond the row cap has arrived — exactly how the JDBC
 *       path learns "more rows exist" by reading one row past the
 *       limit. Each {@code getMore} asks only for the documents still
 *       needed ({@code batchSize}), so the read is bounded by the cap,
 *       not by the collection.</li>
 *   <li>A cap of {@code <= 0} ("unlimited") is still bounded, at
 *       {@link #UNLIMITED_CEILING} documents — the row spinner's own
 *       maximum. Every read is bounded.</li>
 *   <li>A partial set is never shown as complete: the page is
 *       {@code truncated} whenever the cap cut it short OR the server
 *       cursor was still open when paging stopped (a malformed
 *       {@code getMore} reply, a reply with no namespace to page).</li>
 *   <li>A cursor we abandon is released with {@code killCursors}, so
 *       the server does not hold it until its idle timeout. Release is
 *       best-effort: its failure is logged, never thrown over the
 *       result it followed.</li>
 *   <li>Cancellation is checked before every {@code getMore}; a
 *       cancelled page releases its cursor and says so. A transport
 *       failure mid-paging also releases the cursor, then rethrows.</li>
 * </ul>
 */
final class MongoCursorPager {

    private static final Logger LOG = Logger.getLogger(MongoCursorPager.class.getName());

    /** The ceiling applied when the caller asks for "unlimited" rows. */
    static final int UNLIMITED_CEILING = 1_000_000;

    /** How one command reaches the server; a fake in tests, {@code runCommand} for real. */
    interface Transport {

        /** Runs one command document and returns the server's reply. */
        Document run(Document command);

        /**
         * Runs a {@code killCursors} command. Separate from {@link #run}
         * because the real backend must stop honouring a pending cancel
         * (a thread interrupt) before it can send the release at all.
         */
        default Document release(Document killCursors) {
            return run(killCursors);
        }
    }

    /**
     * What paging produced.
     *
     * @param documents the kept documents, at most the effective cap
     * @param truncated true when more documents existed (or may have)
     *                  than were kept
     * @param cancelled true when a cancel stopped paging
     */
    record Page(List<Document> documents, boolean truncated, boolean cancelled) {

        Page {
            documents = List.copyOf(documents);
        }
    }

    private MongoCursorPager() {
    }

    /** The effective cap for a caller's row limit. */
    static int effectiveLimit(int rowLimit) {
        return rowLimit > 0 ? Math.min(rowLimit, UNLIMITED_CEILING) : UNLIMITED_CEILING;
    }

    /**
     * Follows {@code reply}'s cursor. Returns null when the reply has no
     * clean document cursor (the caller renders the raw reply instead).
     *
     * @param reply     the reply to the user's command
     * @param rowLimit  the grid's row cap ({@code <= 0}: the ceiling)
     * @param cancelled polled before every getMore
     * @param transport where getMore and killCursors go
     */
    static Page follow(Document reply, int rowLimit, BooleanSupplier cancelled, Transport transport) {
        Batch first = batchOf(reply, "firstBatch");
        if (first == null) {
            return null;
        }
        int limit = effectiveLimit(rowLimit);
        String collection = collectionOf(first.namespace());
        List<Document> kept = new ArrayList<>(Math.min(first.documents().size(), limit + 1));
        addUpTo(kept, first.documents(), limit + 1);
        long cursorId = first.id();
        boolean wasCancelled = false;
        try {
            while (cursorId != 0 && kept.size() <= limit && collection != null) {
                if (cancelled.getAsBoolean()) {
                    wasCancelled = true;
                    break;
                }
                Document getMore = new Document("getMore", cursorId)
                        .append("collection", collection)
                        .append("batchSize", limit + 1 - kept.size());
                Batch next = batchOf(transport.run(getMore), "nextBatch");
                if (next == null) {
                    break; // malformed reply — stop, release, mark the set partial
                }
                addUpTo(kept, next.documents(), limit + 1);
                cursorId = next.id();
            }
        } catch (RuntimeException e) {
            release(cursorId, collection, transport);
            throw e;
        }
        boolean truncated = kept.size() > limit || cursorId != 0;
        release(cursorId, collection, transport);
        List<Document> documents = kept.size() > limit ? kept.subList(0, limit) : kept;
        return new Page(documents, truncated, wasCancelled);
    }

    /** Sends {@code killCursors} for an open cursor; a no-op for id 0 or no namespace. */
    static void release(long cursorId, String collection, Transport transport) {
        if (cursorId == 0 || collection == null) {
            return;
        }
        try {
            transport.release(new Document("killCursors", collection)
                    .append("cursors", List.of(cursorId)));
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "killCursors failed; the server will expire the cursor itself", e);
        }
    }

    // ---- internals ------------------------------------------------

    /** One batch as the server described it. */
    private record Batch(long id, String namespace, List<Document> documents) {
    }

    /**
     * {@code cursor.<key>} as documents plus the cursor id and ns, or
     * null when the reply has no clean cursor of that shape.
     */
    private static Batch batchOf(Document reply, String key) {
        if (reply == null || !(reply.get("cursor") instanceof Document cursor)
                || !(cursor.get(key) instanceof List<?> batch)) {
            return null;
        }
        List<Document> docs = new ArrayList<>(batch.size());
        for (Object item : batch) {
            if (!(item instanceof Document doc)) {
                return null;
            }
            docs.add(doc);
        }
        long id = cursor.get("id") instanceof Number n ? n.longValue() : 0L;
        String ns = cursor.get("ns") instanceof String s ? s : null;
        return new Batch(id, ns, docs);
    }

    /** The collection part of {@code db.collection} (which may itself contain dots). */
    static String collectionOf(String namespace) {
        if (namespace == null) {
            return null;
        }
        int dot = namespace.indexOf('.');
        return dot <= 0 || dot == namespace.length() - 1 ? null : namespace.substring(dot + 1);
    }

    private static void addUpTo(List<Document> kept, List<Document> batch, int max) {
        for (Document doc : batch) {
            if (kept.size() >= max) {
                return;
            }
            kept.add(doc);
        }
    }
}
