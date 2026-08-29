package org.nmox.studio.web3.engine;

import java.util.ArrayList;
import java.util.List;
import org.nmox.studio.web3.model.AbiEntry;
import org.nmox.studio.web3.model.ContractArtifact;

/**
 * The event-history query's pure half (v2.47.0, the
 * definitive-engagement arc's "any history" batch): validated block
 * ranges, decoded rows, and CSV emission — everything except the
 * socket, so every rule is a unit test.
 *
 * <p>Laws: the range is BOUNDED ({@value #SPAN_CAP} blocks) and an
 * over-cap ask is REFUSED with the reason, never silently clamped — a
 * clamped range would report "history" that quietly omits most of it
 * (the honest-truncation law). CSV cells are quoted and
 * formula-neutralized (the v1.101.0 DB Studio law: a cell starting
 * with {@code = + - @} gets a leading apostrophe so a spreadsheet
 * never executes chain data).
 */
public final class EventHistory {

    /** The widest span one query may ask a node for. */
    public static final int SPAN_CAP = 5_000;

    /** A validated inclusive block range. */
    public record Range(long from, long to) {
    }

    /** One decoded (or honestly undecoded) log row. */
    public record Row(long block, String txHash, String event, String details) {
    }

    private EventHistory() {
    }

    /**
     * Parses and validates a range. Refusals speak: non-numbers,
     * inverted ranges, and spans past the cap all throw with the
     * reason. {@code latestBlock} anchors an empty "to" field.
     */
    public static Range range(String fromText, String toText, long latestBlock) {
        long to = parseBlock(toText, latestBlock, "to");
        long from = fromText == null || fromText.isBlank()
                ? Math.max(0, to - 999) : parseBlock(fromText, latestBlock, "from");
        if (from > to) {
            throw new IllegalArgumentException(
                    "Range is inverted — from " + from + " is past to " + to);
        }
        if (to - from + 1 > SPAN_CAP) {
            throw new IllegalArgumentException("Span " + (to - from + 1)
                    + " blocks is past the cap (" + SPAN_CAP
                    + ") — narrow the range; a clamped answer would be a lie");
        }
        return new Range(from, to);
    }

    private static long parseBlock(String text, long latestBlock, String field) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty() || "latest".equalsIgnoreCase(t)) {
            return latestBlock;
        }
        try {
            long value = Long.parseLong(t);
            if (value < 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException bad) {
            throw new IllegalArgumentException("Not a block number for '" + field
                    + "': " + t);
        }
    }

    /**
     * Decodes fetched logs into rows against the artifacts' events plus
     * the canonical token events — an unmatched log keeps its first
     * topic, honestly undecoded.
     */
    public static List<Row> rows(List<JsonRpcClient.LogEntry> logs,
            List<ContractArtifact> artifacts) {
        List<AbiEntry> candidates = new ArrayList<>();
        for (ContractArtifact artifact : artifacts) {
            for (AbiEntry entry : artifact.abi()) {
                if (entry.kind() == AbiEntry.Kind.EVENT) {
                    candidates.add(entry);
                }
            }
        }
        candidates.add(ErcStandards.ERC20_TRANSFER);
        candidates.add(ErcStandards.ERC20_APPROVAL);
        candidates.add(ErcStandards.ERC721_TRANSFER);
        List<Row> out = new ArrayList<>();
        for (JsonRpcClient.LogEntry log : logs) {
            String event = null;
            String details = null;
            for (AbiEntry candidate : candidates) {
                if (log.topics().isEmpty()) {
                    break;
                }
                String own = "0x" + Hex.toHex(Keccak256.hash(candidate.signature()
                        .getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
                if (!own.equalsIgnoreCase(log.topics().get(0))) {
                    continue;
                }
                try {
                    var decoded = AbiCodec.decodeEventLog(
                            candidate, log.topics(), log.data());
                    StringBuilder b = new StringBuilder();
                    decoded.forEach((k, v) -> {
                        if (b.length() > 0) {
                            b.append(", ");
                        }
                        b.append(k).append('=').append(v);
                    });
                    event = candidate.name();
                    details = b.toString();
                    break;
                } catch (RuntimeException mismatch) {
                    // same topic, different indexing — try the next candidate
                }
            }
            if (event == null) {
                event = log.topics().isEmpty() ? "(anonymous)" : "(unknown)";
                details = log.topics().isEmpty() ? "no topics" : log.topics().get(0);
            }
            out.add(new Row(log.blockNumber(), log.txHash(), event, details));
        }
        return out;
    }

    /** The rows as CSV — quoted, formula-neutralized (v1.101.0). */
    public static String toCsv(List<Row> rows) {
        StringBuilder b = new StringBuilder("block,txHash,event,details\n");
        for (Row row : rows) {
            b.append(row.block()).append(',')
                    .append(cell(row.txHash())).append(',')
                    .append(cell(row.event())).append(',')
                    .append(cell(row.details())).append('\n');
        }
        return b.toString();
    }

    private static String cell(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v; // a spreadsheet must never execute chain data
        }
        return '"' + v.replace("\"", "\"\"") + '"';
    }
}
