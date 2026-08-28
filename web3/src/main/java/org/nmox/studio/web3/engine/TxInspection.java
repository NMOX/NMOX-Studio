package org.nmox.studio.web3.engine;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nmox.studio.web3.model.AbiEntry;
import org.nmox.studio.web3.model.ContractArtifact;

/**
 * The transaction inspector's pure half (v2.44.0): given the node's
 * own answers ({@code eth_getTransactionByHash} +
 * {@code eth_getTransactionReceipt}) and the workspace's scanned
 * artifacts, assemble the honest report — who, to what, how much
 * (through {@link Units}), what call (input decoded against the
 * artifacts' ABIs via {@link AbiCodec#decodeCallInput}), and what the
 * logs say (decoded against artifact events first, then the CANONICAL
 * token events from {@link ErcStandards}). Everything undecodable is
 * shown as itself — a raw selector or topic is more honest than a
 * guess.
 *
 * <p>Pure: no sockets, no EDT — the UI fetches, this assembles, so
 * every rule here is a unit test.
 */
public final class TxInspection {

    /** One assembled report, ready to render line by line. */
    public record Report(List<String> lines) {
    }

    private TxInspection() {
    }

    public static Report assemble(JSONObject tx, JSONObject receipt,
            List<ContractArtifact> artifacts) {
        List<String> lines = new ArrayList<>();
        lines.add("hash     " + tx.optString("hash", "?"));
        lines.add("from     " + tx.optString("from", "?"));
        String to = tx.optString("to", "");
        String created = receipt == null ? "" : receipt.optString("contractAddress", "");
        if (to.isEmpty() || "null".equals(to)) {
            lines.add("to       (contract creation)"
                    + (created.isEmpty() || "null".equals(created)
                            ? "" : " → deployed " + created));
        } else {
            lines.add("to       " + to);
        }
        BigInteger value = quantity(tx, "value");
        lines.add("value    " + Units.formatWei(value));
        if (receipt != null) {
            String status = receipt.optString("status", "");
            lines.add("status   " + ("0x1".equals(status) ? "SUCCESS"
                    : "0x0".equals(status) ? "REVERTED"
                    : status.isEmpty() ? "(pending — no receipt)" : status));
            lines.add("gasUsed  " + quantity(receipt, "gasUsed"));
            lines.add("block    " + quantity(receipt, "blockNumber"));
        } else {
            lines.add("status   (pending — no receipt)");
        }
        String input = tx.optString("input", "0x");
        if (input.length() > 2) {
            String call = null;
            for (ContractArtifact artifact : artifacts) {
                call = AbiCodec.decodeCallInput(artifact.abi(), input);
                if (call != null) {
                    lines.add("call     " + call + "   [" + artifact.name() + "]");
                    break;
                }
            }
            if (call == null) {
                String selector = input.substring(0, Math.min(10, input.length()));
                lines.add("call     " + selector + "… (no matching artifact — "
                        + (input.length() - 2) / 2 + " bytes of calldata)");
            }
        }
        if (receipt != null) {
            JSONArray logs = receipt.optJSONArray("logs");
            for (int i = 0; logs != null && i < logs.length(); i++) {
                lines.add("log " + i + "    " + decodeLog(logs.getJSONObject(i), artifacts));
            }
        }
        return new Report(List.copyOf(lines));
    }

    /**
     * One log line: tries every artifact event, then the canonical
     * token events; an unmatched log shows its first topic honestly.
     */
    static String decodeLog(JSONObject log, List<ContractArtifact> artifacts) {
        JSONArray topicsJson = log.optJSONArray("topics");
        List<String> topics = new ArrayList<>();
        for (int i = 0; topicsJson != null && i < topicsJson.length(); i++) {
            topics.add(topicsJson.getString(i));
        }
        String data = log.optString("data", "0x");
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
        for (AbiEntry event : candidates) {
            if (topics.isEmpty()) {
                break;
            }
            String own = "0x" + Hex.toHex(Keccak256.hash(
                    event.signature().getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            if (!own.equalsIgnoreCase(topics.get(0))) {
                continue;
            }
            try {
                Map<String, String> decoded = AbiCodec.decodeEventLog(
                        event, topics, data);
                StringBuilder b = new StringBuilder(event.name()).append('(');
                boolean first = true;
                for (Map.Entry<String, String> e : decoded.entrySet()) {
                    if (!first) {
                        b.append(", ");
                    }
                    b.append(e.getKey()).append('=').append(e.getValue());
                    first = false;
                }
                return b.append(')').toString();
            } catch (RuntimeException mismatch) {
                // same topic hash, different indexing (ERC-20 vs ERC-721
                // Transfer) — try the next candidate
            }
        }
        return topics.isEmpty() ? "(anonymous log — no topics)"
                : topics.get(0) + " (no matching event)";
    }

    private static BigInteger quantity(JSONObject o, String key) {
        String hex = o.optString(key, "0x0");
        if (hex.startsWith("0x")) {
            hex = hex.substring(2);
        }
        return hex.isEmpty() ? BigInteger.ZERO : new BigInteger(hex, 16);
    }
}
