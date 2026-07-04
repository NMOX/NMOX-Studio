package org.nmox.studio.web3.engine;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The JSON-RPC client over a canned transport — no sockets anywhere.
 * The tests pin request shapes (method, params, omitted fields), hex
 * quantity parsing, RPC error surfacing with revert decoding, and the
 * no-URL-ever-leaks rule.
 */
class JsonRpcClientTest {

    /** The secret-bearing URL every leak assertion checks against. */
    private static final String URL = "https://eth.example.com/v2/TOPSECRETKEY";

    /** A transport that records request bodies and replays canned responses. */
    private static final class CannedTransport implements JsonRpcClient.Transport {

        final List<JSONObject> requests = new ArrayList<>();
        final Deque<String> responses = new ArrayDeque<>();

        CannedTransport(String... canned) {
            for (String response : canned) {
                responses.add(response);
            }
        }

        @Override
        public String post(String url, String jsonBody) {
            requests.add(new JSONObject(jsonBody));
            return responses.remove();
        }
    }

    private static String result(String json) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":" + json + "}";
    }

    // ---- quantities and simple methods -------------------------------------

    @Test
    @DisplayName("chainId parses the 0x-hex quantity (anvil's 31337)")
    void chainId() throws IOException {
        CannedTransport transport = new CannedTransport(result("\"0x7a69\""));
        assertThat(new JsonRpcClient(URL, transport).chainId()).isEqualTo(31337L);
        assertThat(transport.requests.get(0).getString("method")).isEqualTo("eth_chainId");
        assertThat(transport.requests.get(0).getString("jsonrpc")).isEqualTo("2.0");
    }

    @Test
    @DisplayName("blockNumber parses to a long")
    void blockNumber() throws IOException {
        CannedTransport transport = new CannedTransport(result("\"0x10\""));
        assertThat(new JsonRpcClient(URL, transport).blockNumber()).isEqualTo(16L);
    }

    @Test
    @DisplayName("accounts returns the node's unlocked account list")
    void accounts() throws IOException {
        CannedTransport transport = new CannedTransport(result(
                "[\"0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266\","
                + "\"0x70997970c51812dc3a010c7d01b50e0d17dc79c8\"]"));
        assertThat(new JsonRpcClient(URL, transport).accounts()).containsExactly(
                "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266",
                "0x70997970c51812dc3a010c7d01b50e0d17dc79c8");
    }

    @Test
    @DisplayName("getBalance parses wei to BigInteger — 10000 ETH fits fine")
    void getBalance() throws IOException {
        CannedTransport transport = new CannedTransport(
                result("\"0x21e19e0c9bab2400000\"")); // 10_000 ETH in wei
        assertThat(new JsonRpcClient(URL, transport).getBalance("0xabc"))
                .isEqualTo(new BigInteger("10000000000000000000000"));
        assertThat(transport.requests.get(0).getJSONArray("params").getString(1))
                .isEqualTo("latest");
    }

    @Test
    @DisplayName("getCode passes the address through and returns the hex")
    void getCode() throws IOException {
        CannedTransport transport = new CannedTransport(result("\"0x6080\""));
        assertThat(new JsonRpcClient(URL, transport).getCode("0xabc")).isEqualTo("0x6080");
    }

    @Test
    @DisplayName("ethCall posts {to, data} and returns the raw return hex")
    void ethCall() throws IOException {
        CannedTransport transport = new CannedTransport(result("\"0x2a\""));
        String out = new JsonRpcClient(URL, transport).ethCall("0xcontract", "0xd09de08a");
        assertThat(out).isEqualTo("0x2a");
        JSONObject tx = transport.requests.get(0).getJSONArray("params").getJSONObject(0);
        assertThat(tx.getString("to")).isEqualTo("0xcontract");
        assertThat(tx.getString("data")).isEqualTo("0xd09de08a");
    }

    @Test
    @DisplayName("estimateGas returns a BigInteger quantity")
    void estimateGas() throws IOException {
        CannedTransport transport = new CannedTransport(result("\"0x5208\""));
        assertThat(new JsonRpcClient(URL, transport)
                .estimateGas("0xfrom", "0xto", "0x", "0x0"))
                .isEqualTo(BigInteger.valueOf(21000));
    }

    // ---- sendTransaction: the only write path -------------------------------

    @Test
    @DisplayName("sendTransaction posts from/to/data/value and returns the tx hash")
    void sendTransaction() throws IOException {
        CannedTransport transport = new CannedTransport(result("\"0xtxhash\""));
        String hash = new JsonRpcClient(URL, transport)
                .sendTransaction("0xfrom", "0xto", "0xdata", "0xde0b6b3a7640000");
        assertThat(hash).isEqualTo("0xtxhash");
        JSONObject tx = transport.requests.get(0).getJSONArray("params").getJSONObject(0);
        assertThat(tx.getString("from")).isEqualTo("0xfrom");
        assertThat(tx.getString("to")).isEqualTo("0xto");
        assertThat(tx.getString("value")).isEqualTo("0xde0b6b3a7640000");
    }

    @Test
    @DisplayName("contract creation: to == null means NO to field in the request")
    void contractCreationOmitsTo() throws IOException {
        CannedTransport transport = new CannedTransport(result("\"0xtxhash\""));
        new JsonRpcClient(URL, transport)
                .sendTransaction("0xfrom", null, "0x6080604052", null);
        JSONObject tx = transport.requests.get(0).getJSONArray("params").getJSONObject(0);
        assertThat(tx.has("to")).isFalse();
        assertThat(tx.has("value")).isFalse();
        assertThat(tx.getString("data")).isEqualTo("0x6080604052");
    }

    // ---- blocks, receipts, logs ----------------------------------------------

    @Test
    @DisplayName("getBlockByNumber parses header fields and tx hashes")
    void getBlock() throws IOException {
        CannedTransport transport = new CannedTransport(result("""
                {"number": "0xc", "hash": "0xblockhash", "timestamp": "0x65f00000",
                 "gasUsed": "0x5208", "gasLimit": "0x1c9c380",
                 "transactions": ["0xtx1", "0xtx2"]}"""));
        JsonRpcClient.Block block = new JsonRpcClient(URL, transport)
                .getBlockByNumber("latest", false);
        assertThat(block.number()).isEqualTo(12L);
        assertThat(block.hash()).isEqualTo("0xblockhash");
        assertThat(block.gasUsed()).isEqualTo(21000L);
        assertThat(block.gasLimit()).isEqualTo(30_000_000L);
        assertThat(block.txCount()).isEqualTo(2);
        assertThat(block.txHashes()).containsExactly("0xtx1", "0xtx2");
    }

    @Test
    @DisplayName("getBlockByNumber with fullTxs extracts hashes from the tx objects")
    void getBlockFullTxs() throws IOException {
        CannedTransport transport = new CannedTransport(result("""
                {"number": "0x1", "hash": "0xh", "timestamp": "0x0",
                 "gasUsed": "0x0", "gasLimit": "0x0",
                 "transactions": [{"hash": "0xfull1", "from": "0xa"}]}"""));
        JsonRpcClient.Block block = new JsonRpcClient(URL, transport)
                .getBlockByNumber("1", true);
        assertThat(block.txHashes()).containsExactly("0xfull1");
        // and the decimal "1" went out as 0x-hex
        assertThat(transport.requests.get(0).getJSONArray("params").getString(0))
                .isEqualTo("0x1");
    }

    @Test
    @DisplayName("an unknown block comes back null, not an exception")
    void unknownBlockIsNull() throws IOException {
        CannedTransport transport = new CannedTransport(result("null"));
        assertThat(new JsonRpcClient(URL, transport)
                .getBlockByNumber("99999", false)).isNull();
    }

    @Test
    @DisplayName("a mined receipt parses status, contractAddress and logs")
    void receipt() throws IOException {
        CannedTransport transport = new CannedTransport(result("""
                {"transactionHash": "0xtx", "status": "0x1",
                 "contractAddress": "0xnewcontract", "blockNumber": "0x7",
                 "gasUsed": "0x5208",
                 "logs": [{"address": "0xemitter",
                           "topics": ["0xtopic0", "0xtopic1"],
                           "data": "0xdd", "blockNumber": "0x7",
                           "transactionHash": "0xtx"}]}"""));
        JsonRpcClient.Receipt receipt = new JsonRpcClient(URL, transport)
                .getTransactionReceipt("0xtx");
        assertThat(receipt.success()).isTrue();
        assertThat(receipt.contractAddress()).isEqualTo("0xnewcontract");
        assertThat(receipt.blockNumber()).isEqualTo(7L);
        assertThat(receipt.logs()).hasSize(1);
        assertThat(receipt.logs().get(0).topics()).containsExactly("0xtopic0", "0xtopic1");
    }

    @Test
    @DisplayName("a pending transaction's receipt is null; a failed tx has success=false")
    void pendingAndFailedReceipts() throws IOException {
        CannedTransport transport = new CannedTransport(
                result("null"),
                result("{\"transactionHash\": \"0xtx\", \"status\": \"0x0\","
                        + " \"contractAddress\": null, \"blockNumber\": \"0x1\","
                        + " \"gasUsed\": \"0x0\", \"logs\": []}"));
        JsonRpcClient client = new JsonRpcClient(URL, transport);
        assertThat(client.getTransactionReceipt("0xtx")).isNull();
        JsonRpcClient.Receipt failed = client.getTransactionReceipt("0xtx");
        assertThat(failed.success()).isFalse();
        assertThat(failed.contractAddress()).isEmpty();
    }

    @Test
    @DisplayName("getLogs builds the filter object and parses the entries")
    void getLogs() throws IOException {
        CannedTransport transport = new CannedTransport(result("""
                [{"address": "0xemitter", "topics": ["0xt0"], "data": "0x01",
                  "blockNumber": "0x5", "transactionHash": "0xtx"}]"""));
        List<JsonRpcClient.LogEntry> logs = new JsonRpcClient(URL, transport)
                .getLogs("0xemitter", "3", "latest");
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).blockNumber()).isEqualTo(5L);
        JSONObject filter = transport.requests.get(0).getJSONArray("params").getJSONObject(0);
        assertThat(filter.getString("fromBlock")).isEqualTo("0x3");
        assertThat(filter.getString("toBlock")).isEqualTo("latest");
    }

    // ---- errors ------------------------------------------------------------

    @Test
    @DisplayName("an RPC error surfaces code and message as an RpcException")
    void rpcError() {
        CannedTransport transport = new CannedTransport(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":"
                + "{\"code\":-32601,\"message\":\"method not found\"}}");
        assertThatThrownBy(() -> new JsonRpcClient(URL, transport).blockNumber())
                .isInstanceOf(JsonRpcClient.RpcException.class)
                .hasMessageContaining("-32601")
                .hasMessageContaining("method not found");
    }

    @Test
    @DisplayName("revert data in the error decodes to the reason, riding the message")
    void revertErrorDecoded() {
        String revertData = "0x08c379a0"
                + "0000000000000000000000000000000000000000000000000000000000000020"
                + "000000000000000000000000000000000000000000000000000000000000001a"
                + "4e6f7420656e6f7567682045746865722070726f76696465642e000000000000";
        CannedTransport transport = new CannedTransport(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":3,"
                + "\"message\":\"execution reverted\",\"data\":\"" + revertData + "\"}}");
        assertThatThrownBy(() -> new JsonRpcClient(URL, transport).blockNumber())
                .isInstanceOf(JsonRpcClient.RpcException.class)
                .hasMessageContaining("execution reverted")
                .hasMessageContaining("Not enough Ether provided.");
    }

    @Test
    @DisplayName("nested error.data objects ({data: {data: 0x…}}) still yield the revert hex")
    void nestedRevertData() {
        JSONObject error = new JSONObject()
                .put("code", 3).put("message", "reverted")
                .put("data", new JSONObject().put("data", "0x4e487b71"
                        + "0000000000000000000000000000000000000000000000000000000000000012"));
        JsonRpcClient.RpcException e = JsonRpcClient.toRpcException(error);
        assertThat(e.getMessage()).contains("division by zero");
        assertThat(e.code()).isEqualTo(3);
    }

    @Test
    @DisplayName("a non-JSON answer is an IOException naming only the redacted host")
    void nonJsonAnswer() {
        CannedTransport transport = new CannedTransport("<html>gateway error</html>");
        assertThatThrownBy(() -> new JsonRpcClient(URL, transport).blockNumber())
                .isInstanceOf(IOException.class)
                .hasMessageContaining("https://eth.example.com")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("TOPSECRETKEY"));
    }

    // ---- the no-URL-leak rule -------------------------------------------------

    @Test
    @DisplayName("toString never shows the full URL")
    void toStringRedacts() {
        JsonRpcClient client = new JsonRpcClient(URL, (u, b) -> "{}");
        assertThat(client.toString()).contains("https://eth.example.com")
                .doesNotContain("TOPSECRETKEY");
    }

    @Test
    @DisplayName("sanitizeMessage strips the full URL out of transport exception text")
    void sanitizeMessage() {
        IOException e = new IOException("failed to connect to " + URL + " after 10s");
        assertThat(JsonRpcClient.sanitizeMessage(e, URL))
                .contains("https://eth.example.com")
                .doesNotContain("TOPSECRETKEY");
        assertThat(JsonRpcClient.sanitizeMessage(new IOException(), URL))
                .isEqualTo("IOException");
    }

    // ---- helpers exercised directly ------------------------------------------

    @Test
    @DisplayName("blockTag: tags pass through, decimals become 0x-hex, blank means latest")
    void blockTagShapes() {
        assertThat(JsonRpcClient.blockTag("latest")).isEqualTo("latest");
        assertThat(JsonRpcClient.blockTag("PENDING")).isEqualTo("pending");
        assertThat(JsonRpcClient.blockTag("earliest")).isEqualTo("earliest");
        assertThat(JsonRpcClient.blockTag("0xff")).isEqualTo("0xff");
        assertThat(JsonRpcClient.blockTag("255")).isEqualTo("0xff");
        assertThat(JsonRpcClient.blockTag("0")).isEqualTo("0x0");
        assertThat(JsonRpcClient.blockTag(null)).isEqualTo("latest");
        assertThat(JsonRpcClient.blockTag("  ")).isEqualTo("latest");
    }

    @Test
    @DisplayName("hex quantities: empty and 0x parse to zero; big values stay exact")
    void hexQuantities() {
        assertThat(JsonRpcClient.hexToBigInteger("0x")).isZero();
        assertThat(JsonRpcClient.hexToBigInteger(null)).isZero();
        assertThat(JsonRpcClient.hexToBigInteger("0xde0b6b3a7640000"))
                .isEqualTo(new BigInteger("1000000000000000000"));
        assertThat(JsonRpcClient.hexToLong("0x7a69")).isEqualTo(31337L);
    }

    @Test
    @DisplayName("request ids increment per call")
    void idsIncrement() throws IOException {
        CannedTransport transport = new CannedTransport(
                result("\"0x1\""), result("\"0x2\""));
        JsonRpcClient client = new JsonRpcClient(URL, transport);
        client.blockNumber();
        client.blockNumber();
        assertThat(transport.requests.get(0).getLong("id"))
                .isLessThan(transport.requests.get(1).getLong("id"));
    }
}
