package org.nmox.studio.web3.engine;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.web3.model.AbiEntry;
import org.nmox.studio.web3.model.AbiParam;
import org.nmox.studio.web3.model.ContractArtifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The token tranche's laws (v2.44.0): standards detected by COMPLETE
 * required-signature sets, amounts converted exactly with refusals for
 * money-losing input, calldata decoded by real selector match, and the
 * inspector honest about everything it cannot decode.
 */
class TokenTrancheTest {

    // --- fixtures ---------------------------------------------------

    private static List<AbiEntry> erc20Abi(boolean withMetadata) {
        List<AbiEntry> abi = new ArrayList<>(List.of(
                fn("totalSupply", List.of(), List.of(out("uint256"))),
                fn("balanceOf", List.of(in("owner", "address")), List.of(out("uint256"))),
                fn("transfer", List.of(in("to", "address"), in("value", "uint256")),
                        List.of(out("bool"))),
                fn("transferFrom", List.of(in("from", "address"), in("to", "address"),
                        in("value", "uint256")), List.of(out("bool"))),
                fn("approve", List.of(in("spender", "address"), in("value", "uint256")),
                        List.of(out("bool"))),
                fn("allowance", List.of(in("owner", "address"), in("spender", "address")),
                        List.of(out("uint256"))),
                ErcStandards.ERC20_TRANSFER));
        if (withMetadata) {
            abi.add(fn("name", List.of(), List.of(out("string"))));
            abi.add(fn("symbol", List.of(), List.of(out("string"))));
            abi.add(fn("decimals", List.of(), List.of(out("uint8"))));
        }
        return abi;
    }

    private static AbiEntry fn(String name, List<AbiParam> inputs, List<AbiParam> outputs) {
        return AbiEntry.function(name, inputs, outputs, "view");
    }

    private static AbiParam in(String name, String type) {
        return AbiParam.of(name, type);
    }

    private static AbiParam out(String type) {
        return AbiParam.of("", type);
    }

    // --- detection --------------------------------------------------

    @Test
    @DisplayName("a complete ERC-20 detects; one missing required function does not")
    void detection() {
        assertThat(ErcStandards.detect(erc20Abi(false)))
                .isEqualTo(ErcStandards.Standard.ERC20);
        // metadata is optional and must not gate detection
        assertThat(ErcStandards.detect(erc20Abi(true)))
                .isEqualTo(ErcStandards.Standard.ERC20);
        // drop allowance — no longer an ERC-20, and NOT guessed as one
        List<AbiEntry> partial = erc20Abi(false).stream()
                .filter(e -> !"allowance".equals(e.name())).toList();
        assertThat(ErcStandards.detect(partial)).isNull();
        assertThat(ErcStandards.metadataReader(erc20Abi(true), "symbol")).isNotNull();
        assertThat(ErcStandards.metadataReader(erc20Abi(false), "symbol")).isNull();
    }

    // --- amounts ----------------------------------------------------

    @Test
    @DisplayName("amounts are exact both ways; over-precise input is refused, never truncated")
    void amounts() {
        assertThat(TokenAmounts.toHuman(new BigInteger("1500000000000000000"), 18))
                .isEqualTo("1.5");
        assertThat(TokenAmounts.toHuman(BigInteger.ZERO, 18)).isEqualTo("0");
        assertThat(TokenAmounts.toHuman(new BigInteger("42"), 0)).isEqualTo("42");
        assertThat(TokenAmounts.toRaw("1.5", 18))
                .isEqualTo(new BigInteger("1500000000000000000"));
        assertThat(TokenAmounts.toRaw("0.000001", 6)).isEqualTo(BigInteger.ONE);
        // the money law: 7 fractional digits into a 6-decimal token REFUSES
        assertThatThrownBy(() -> TokenAmounts.toRaw("0.0000001", 6))
                .hasMessageContaining("6 decimals");
        assertThatThrownBy(() -> TokenAmounts.toRaw("-1", 18))
                .hasMessageContaining("negative");
        assertThatThrownBy(() -> TokenAmounts.toRaw("abc", 18))
                .hasMessageContaining("Not a number");
    }

    @Test
    @DisplayName("amount fields: raw passes through, decimals convert, unknown decimals refuse")
    void interpretAmount() {
        assertThat(TokenAmounts.interpretAmount("1500000", 6)).isEqualTo("1500000");
        assertThat(TokenAmounts.interpretAmount("1.5", 6)).isEqualTo("1500000");
        assertThat(TokenAmounts.interpretAmount("0.000001", 6)).isEqualTo("1");
        assertThatThrownBy(() -> TokenAmounts.interpretAmount("1.5", null))
                .hasMessageContaining("type raw units");
        assertThatThrownBy(() -> TokenAmounts.interpretAmount("1.5e3", 6))
                .hasMessageContaining("Not an amount");
        assertThatThrownBy(() -> TokenAmounts.interpretAmount("abc", 6))
                .hasMessageContaining("Not an amount");
    }

    // --- calldata ---------------------------------------------------

    @Test
    @DisplayName("calldata decodes by selector against the ABI; unknown selectors return null")
    void calldata() {
        List<AbiEntry> abi = erc20Abi(false);
        AbiEntry transfer = abi.stream()
                .filter(e -> "transfer".equals(e.name())).findFirst().orElseThrow();
        String input = AbiCodec.encodeCall(transfer, List.of(
                "0x1111111111111111111111111111111111111111", "1500000000000000000"));
        String decoded = AbiCodec.decodeCallInput(abi, input);
        assertThat(decoded).startsWith("transfer(")
                .contains("0x1111111111111111111111111111111111111111")
                .contains("1500000000000000000");
        assertThat(AbiCodec.decodeCallInput(abi, "0xdeadbeef" + "00".repeat(32)))
                .isNull();
    }

    // --- the inspector ----------------------------------------------

    @Test
    @DisplayName("the inspector reports honestly: decoded call, decoded canonical log, raw unknowns")
    void inspector() {
        List<AbiEntry> abi = erc20Abi(false);
        ContractArtifact token = new ContractArtifact("WalkToken", "src/WalkToken.sol",
                abi, "0x", "0x");
        AbiEntry transfer = abi.stream()
                .filter(e -> "transfer".equals(e.name())).findFirst().orElseThrow();
        String input = AbiCodec.encodeCall(transfer, List.of(
                "0x2222222222222222222222222222222222222222", "7"));

        JSONObject tx = new JSONObject()
                .put("hash", "0xabc").put("from", "0xF00").put("to", "0xBA4")
                .put("value", "0x0").put("input", input);
        String transferTopic = "0x" + Hex.toHex(Keccak256.hash(
                "Transfer(address,address,uint256)".getBytes(
                        java.nio.charset.StandardCharsets.US_ASCII)));
        JSONObject log = new JSONObject()
                .put("topics", new org.json.JSONArray(List.of(
                        transferTopic,
                        "0x000000000000000000000000f000000000000000000000000000000000000001",
                        "0x0000000000000000000000002222222222222222222222222222222222222222")))
                .put("data", "0x0000000000000000000000000000000000000000000000000000000000000007");
        JSONObject receipt = new JSONObject()
                .put("status", "0x1").put("gasUsed", "0x5208").put("blockNumber", "0x2")
                .put("logs", new org.json.JSONArray(List.of(log)));

        TxInspection.Report r = TxInspection.assemble(tx, receipt, List.of(token));
        String all = String.join("\n", r.lines());
        assertThat(all).contains("status   SUCCESS");
        assertThat(all).contains("call     transfer(");
        assertThat(all).contains("[WalkToken]");
        assertThat(all).contains("Transfer(");
        assertThat(all).contains("value=7");

        // unknown selector + unknown log topic: shown raw, never guessed
        JSONObject weirdTx = new JSONObject()
                .put("hash", "0x1").put("from", "0xF").put("to", "0xB")
                .put("value", "0x0").put("input", "0x12345678aa");
        JSONObject weirdLog = new JSONObject()
                .put("topics", new org.json.JSONArray(List.of("0x" + "ab".repeat(32))))
                .put("data", "0x");
        JSONObject weirdReceipt = new JSONObject()
                .put("status", "0x0").put("gasUsed", "0x1").put("blockNumber", "0x3")
                .put("logs", new org.json.JSONArray(List.of(weirdLog)));
        String weird = String.join("\n",
                TxInspection.assemble(weirdTx, weirdReceipt, List.of(token)).lines());
        assertThat(weird).contains("REVERTED");
        assertThat(weird).contains("0x12345678");
        assertThat(weird).contains("no matching artifact");
        assertThat(weird).contains("no matching event");
        // pending: no receipt says so instead of inventing a status
        String pending = String.join("\n",
                TxInspection.assemble(weirdTx, null, List.of(token)).lines());
        assertThat(pending).contains("(pending — no receipt)");
    }
}
