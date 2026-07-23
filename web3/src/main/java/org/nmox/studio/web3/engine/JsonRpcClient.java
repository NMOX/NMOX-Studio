package org.nmox.studio.web3.engine;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nmox.studio.core.http.HttpClientFactory;

/**
 * A JSON-RPC 2.0 client for EVM nodes (anvil, hardhat node, geth,
 * public gateways). Synchronous; never call from the EDT.
 *
 * <p><b>The security boundary, part two:</b> the only write path is
 * {@link #sendTransaction}, i.e. {@code eth_sendTransaction} — the node
 * signs with its <em>own</em> unlocked accounts (what anvil and hardhat
 * devnets provide). This class holds no key material and has no signing
 * code, by design. And because the endpoint URL may embed an API key,
 * it never appears in logs, exceptions, or {@link #toString()} —
 * everything goes through {@link Redacted#url}.
 *
 * <p>The {@link Transport} seam is the CouchBackend idiom: production
 * posts over the IDE's shared {@link HttpClientFactory} client; tests
 * inject canned responses and never open a socket.
 */
public final class JsonRpcClient {

    /** How a request body reaches the node; the seam tests inject. */
    public interface Transport {

        /**
         * POSTs the JSON body and returns the response body.
         *
         * @throws IOException when the node can't be reached — the
         *         message must not contain the full URL
         */
        String post(String url, String jsonBody) throws IOException;
    }

    /**
     * An RPC-level failure: the node answered with an error object.
     * The message carries code and text, plus the decoded revert reason
     * when the error data held revert bytes.
     */
    public static final class RpcException extends IOException {

        private final int code;
        private final String data;

        public RpcException(int code, String message, String data) {
            super(message);
            this.code = code;
            this.data = data == null ? "" : data;
        }

        /** The JSON-RPC error code, e.g. -32000. */
        public int code() {
            return code;
        }

        /** The raw error data hex ({@code ""} when the node sent none). */
        public String data() {
            return data;
        }
    }

    /** One block header, plus its transaction hashes. */
    public record Block(long number, String hash, long timestampSeconds,
            long gasUsed, long gasLimit, List<String> txHashes) {

        public Block {
            txHashes = List.copyOf(txHashes);
        }

        /** How many transactions the block carries. */
        public int txCount() {
            return txHashes.size();
        }
    }

    /** One log entry as {@code eth_getLogs}/receipts return it. */
    public record LogEntry(String address, List<String> topics, String data,
            long blockNumber, String txHash) {

        public LogEntry {
            topics = List.copyOf(topics);
        }
    }

    /** One transaction receipt; {@code contractAddress} is {@code ""} unless a deployment. */
    public record Receipt(String txHash, boolean success, String contractAddress,
            long blockNumber, long gasUsed, List<LogEntry> logs) {

        public Receipt {
            logs = List.copyOf(logs);
        }
    }

    private static final int TIMEOUT_SECONDS = 10;

    private final String url;
    private final Transport transport;
    private final AtomicLong nextId = new AtomicLong(1);

    /** Production client over the IDE's shared HTTP pool. */
    public JsonRpcClient(String url) {
        this(url, httpTransport());
    }

    /** Seam constructor — tests hand in a canned transport. */
    public JsonRpcClient(String url, Transport transport) {
        this.url = Objects.requireNonNull(url, "url");
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    /**
     * Whether this client points at the local machine — the devnet
     * case (anvil, hardhat node) where a broadcast is throwaway. The
     * UI uses it to decide when SEND/Deploy deserve a confirmation:
     * anything NOT provably loopback gets one. The raw URL never
     * leaves this class.
     */
    public boolean isLoopbackEndpoint() {
        return loopback(url);
    }

    /** Pure classification; unparseable or hostless URLs are NOT loopback. */
    static boolean loopback(String url) {
        String host;
        try {
            host = URI.create(url.trim()).getHost();
        } catch (RuntimeException unparseable) {
            return false;
        }
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("localhost") || h.equals("::1") || h.equals("[::1]")
                || h.startsWith("127.");
    }

    // ---- the eth_* surface -----------------------------------------------

    /** {@code eth_chainId} — the EIP-155 chain id. */
    public long chainId() throws IOException {
        return hexToLong(call("eth_chainId").getString("result"));
    }

    /** {@code eth_blockNumber} — the latest block number. */
    public long blockNumber() throws IOException {
        return hexToLong(call("eth_blockNumber").getString("result"));
    }

    /**
     * {@code eth_getBlockByNumber}. Accepts {@code latest} /
     * {@code earliest} / {@code pending} verbatim, or a decimal or
     * 0x-hex number. Returns null when the node knows no such block.
     */
    public Block getBlockByNumber(String numberOrLatest, boolean fullTxs) throws IOException {
        JSONObject response = call("eth_getBlockByNumber",
                blockTag(numberOrLatest), fullTxs);
        JSONObject block = response.optJSONObject("result");
        if (block == null) {
            return null;
        }
        List<String> txHashes = new ArrayList<>();
        JSONArray txs = block.optJSONArray("transactions");
        if (txs != null) {
            for (int i = 0; i < txs.length(); i++) {
                Object tx = txs.get(i);
                if (tx instanceof JSONObject full) {
                    txHashes.add(full.optString("hash", ""));
                } else {
                    txHashes.add(String.valueOf(tx));
                }
            }
        }
        return new Block(
                hexToLong(block.optString("number", "0x0")),
                block.optString("hash", ""),
                hexToLong(block.optString("timestamp", "0x0")),
                hexToLong(block.optString("gasUsed", "0x0")),
                hexToLong(block.optString("gasLimit", "0x0")),
                txHashes);
    }

    /** {@code eth_accounts} — the node's own unlocked accounts. */
    public List<String> accounts() throws IOException {
        JSONArray array = call("eth_accounts").getJSONArray("result");
        List<String> out = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            out.add(array.getString(i));
        }
        return out;
    }

    /** {@code eth_getBalance(addr, latest)} in wei. */
    public BigInteger getBalance(String address) throws IOException {
        return hexToBigInteger(call("eth_getBalance", address, "latest")
                .getString("result"));
    }

    /** {@code eth_getCode(addr, latest)} — {@code 0x} means no contract there. */
    public String getCode(String address) throws IOException {
        return call("eth_getCode", address, "latest").getString("result");
    }

    /** {@code eth_call} — a read; returns the raw return-data hex. */
    public String ethCall(String to, String data) throws IOException {
        JSONObject tx = new JSONObject().put("to", to).put("data", data);
        return call("eth_call", tx, "latest").getString("result");
    }

    /** {@code eth_estimateGas}; {@code to} null for contract creation. */
    public BigInteger estimateGas(String from, String to, String data,
            String valueWeiHex) throws IOException {
        return hexToBigInteger(call("eth_estimateGas",
                txObject(from, to, data, valueWeiHex)).getString("result"));
    }

    /**
     * {@code eth_sendTransaction} — the node signs with its own
     * unlocked account. {@code to} null deploys a contract. Returns
     * the transaction hash.
     */
    public String sendTransaction(String from, String to, String data,
            String valueWeiHex) throws IOException {
        return call("eth_sendTransaction", txObject(from, to, data, valueWeiHex))
                .getString("result");
    }

    /** {@code eth_getTransactionReceipt}; null while the tx is pending. */
    public Receipt getTransactionReceipt(String txHash) throws IOException {
        JSONObject receipt = call("eth_getTransactionReceipt", txHash)
                .optJSONObject("result");
        if (receipt == null) {
            return null;
        }
        return new Receipt(
                receipt.optString("transactionHash", txHash),
                hexToLong(receipt.optString("status", "0x1")) == 1L,
                receipt.isNull("contractAddress")
                        ? "" : receipt.optString("contractAddress", ""),
                hexToLong(receipt.optString("blockNumber", "0x0")),
                hexToLong(receipt.optString("gasUsed", "0x0")),
                logEntries(receipt.optJSONArray("logs")));
    }

    /**
     * {@code eth_getLogs} for one contract address over a block range
     * (decimal, 0x-hex, or tags like {@code latest}).
     */
    public List<LogEntry> getLogs(String address, String fromBlock,
            String toBlock) throws IOException {
        JSONObject filter = new JSONObject()
                .put("address", address)
                .put("fromBlock", blockTag(fromBlock))
                .put("toBlock", blockTag(toBlock));
        return logEntries(call("eth_getLogs", filter).getJSONArray("result"));
    }

    @Override
    public String toString() {
        return "JsonRpcClient(" + Redacted.url(url) + ")";
    }

    // ---- plumbing ----------------------------------------------------------

    private JSONObject call(String method, Object... params) throws IOException {
        JSONObject request = new JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", nextId.getAndIncrement())
                .put("method", method)
                .put("params", new JSONArray(List.of(params)));
        String body = transport.post(url, request.toString());
        JSONObject response;
        try {
            response = new JSONObject(body);
        } catch (RuntimeException notJson) {
            throw new IOException("The node at " + Redacted.url(url)
                    + " did not answer with JSON-RPC.");
        }
        JSONObject error = response.optJSONObject("error");
        if (error != null) {
            throw toRpcException(error);
        }
        if (!response.has("result")) {
            throw new IOException("The node at " + Redacted.url(url)
                    + " answered without a result.");
        }
        return response;
    }

    /**
     * Builds the exception for an RPC error object; when the error data
     * carries revert bytes, the decoded reason rides along in the
     * message. Package-private seam, tested with canned error objects.
     */
    static RpcException toRpcException(JSONObject error) {
        int code = error.optInt("code", 0);
        String message = error.optString("message", "unknown error");
        String data = revertData(error.opt("data"));
        String full = "RPC error " + code + ": " + message;
        if (!data.isEmpty()) {
            full += " — " + AbiCodec.decodeRevert(data);
        }
        return new RpcException(code, full, data);
    }

    /** Digs the revert hex out of {@code error.data} (string, or nested {@code {data:"0x…"}}). */
    static String revertData(Object data) {
        if (data instanceof String s && s.startsWith("0x") && s.length() > 2) {
            return s;
        }
        if (data instanceof JSONObject nested) {
            String inner = nested.optString("data", "");
            if (inner.startsWith("0x") && inner.length() > 2) {
                return inner;
            }
        }
        return "";
    }

    /** {@code latest}/{@code earliest}/{@code pending} pass through; numbers become 0x-hex. */
    static String blockTag(String numberOrTag) {
        if (numberOrTag == null || numberOrTag.isBlank()) {
            return "latest";
        }
        String trimmed = numberOrTag.trim().toLowerCase(Locale.ROOT);
        if (trimmed.equals("latest") || trimmed.equals("earliest")
                || trimmed.equals("pending") || trimmed.startsWith("0x")) {
            return trimmed;
        }
        return "0x" + new BigInteger(trimmed).toString(16);
    }

    static long hexToLong(String hex) {
        return hexToBigInteger(hex).longValueExact();
    }

    static BigInteger hexToBigInteger(String hex) {
        String digits = Hex.strip0x(hex == null ? "" : hex.trim());
        if (digits.isEmpty()) {
            return BigInteger.ZERO;
        }
        return new BigInteger(digits, 16);
    }

    private static JSONObject txObject(String from, String to, String data,
            String valueWeiHex) {
        JSONObject tx = new JSONObject().put("from", from);
        if (to != null && !to.isBlank()) {
            tx.put("to", to);
        }
        if (data != null && !data.isBlank()) {
            tx.put("data", data);
        }
        if (valueWeiHex != null && !valueWeiHex.isBlank()) {
            tx.put("value", valueWeiHex);
        }
        return tx;
    }

    private static List<LogEntry> logEntries(JSONArray array) {
        List<LogEntry> out = new ArrayList<>();
        if (array == null) {
            return out;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject log = array.optJSONObject(i);
            if (log == null) {
                continue;
            }
            List<String> topics = new ArrayList<>();
            JSONArray topicsJson = log.optJSONArray("topics");
            if (topicsJson != null) {
                for (int t = 0; t < topicsJson.length(); t++) {
                    topics.add(topicsJson.getString(t));
                }
            }
            out.add(new LogEntry(
                    log.optString("address", ""),
                    topics,
                    log.optString("data", "0x"),
                    hexToLong(log.optString("blockNumber", "0x0")),
                    log.optString("transactionHash", "")));
        }
        return out;
    }

    /**
     * The response-body cap. {@code ofString()} buffered the whole body
     * unbounded, and the Watch pane drives this transport every 2s
     * against arbitrary user-added endpoints — a hostile or
     * misconfigured gateway could OOM the IDE (the apiclient v1.99.0
     * bug class). No legitimate RPC result the studio decodes comes
     * near this; past it the node is refused, honestly and redacted.
     */
    public static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;

    /** The production transport: POST over the shared HTTP pool, URL-redacting failures. */
    public static Transport httpTransport() {
        return (url, jsonBody) -> {
            HttpResponse<java.io.InputStream> response;
            try {
                // URI.create sits INSIDE the redacting try: its
                // IllegalArgumentException echoes the full input URL
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                        .build();
                response = HttpClientFactory.shared()
                        .send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while calling " + Redacted.url(url));
            } catch (IOException | RuntimeException e) {
                // no cause attached: nested messages may echo the full URL
                throw new IOException("Cannot reach " + Redacted.url(url)
                        + " — " + sanitizeMessage(e, url));
            }
            org.nmox.studio.core.http.HttpBodies.Capped capped;
            try (java.io.InputStream in = response.body()) {
                capped = org.nmox.studio.core.http.HttpBodies.readUtf8(in, MAX_RESPONSE_BYTES);
            }
            if (capped.truncated()) {
                // closing aborted the transfer; a truncated JSON-RPC
                // body is useless, so oversize is a refusal
                throw new IOException("Response over "
                        + (MAX_RESPONSE_BYTES / (1024 * 1024)) + "MB from "
                        + Redacted.url(url) + " — refusing to buffer it");
            }
            if (response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode()
                        + " from " + Redacted.url(url));
            }
            return capped.text();
        };
    }

    /**
     * A transport failure's message with any occurrence of the full URL
     * replaced by its redacted form — belt and braces against HTTP-layer
     * exceptions that echo the request URI.
     */
    static String sanitizeMessage(Exception e, String url) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message.replace(url, Redacted.url(url));
    }
}
