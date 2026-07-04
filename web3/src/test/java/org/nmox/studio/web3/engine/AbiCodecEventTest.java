package org.nmox.studio.web3.engine;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.web3.model.AbiEntry;
import org.nmox.studio.web3.model.AbiParam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * decodeEventLog: indexed params from topics, the rest from data, in
 * declaration order — built around the ERC-20 Transfer event, whose
 * topic hash is one of the pinned Keccak vectors.
 */
class AbiCodecEventTest {

    /** Transfer(address indexed from, address indexed to, uint256 value). */
    private static AbiEntry transfer() {
        return AbiEntry.event("Transfer", List.of(
                new AbiParam("from", "address", true),
                new AbiParam("to", "address", true),
                new AbiParam("value", "uint256", false)));
    }

    @Test
    @DisplayName("an ERC-20 Transfer log decodes: two indexed addresses, one data uint")
    void erc20Transfer() {
        Map<String, String> decoded = AbiCodec.decodeEventLog(transfer(),
                List.of(
                        "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
                        "0x000000000000000000000000a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
                        "0x000000000000000000000000feedfacefeedfacefeedfacefeedfacefeedface"),
                "0x0000000000000000000000000000000000000000000000000000000000000064");
        assertThat(decoded).containsExactly(
                Map.entry("from", "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"),
                Map.entry("to", "0xfeedfacefeedfacefeedfacefeedfacefeedface"),
                Map.entry("value", "100"));
    }

    @Test
    @DisplayName("declaration order survives even when data params come before indexed ones")
    void mixedOrder() {
        AbiEntry event = AbiEntry.event("Mixed", List.of(
                new AbiParam("amount", "uint256", false),
                new AbiParam("who", "address", true),
                new AbiParam("note", "string", false)));
        Map<String, String> decoded = AbiCodec.decodeEventLog(event,
                List.of("0x" + "0".repeat(64),
                        "0x000000000000000000000000feedfacefeedfacefeedfacefeedfacefeedface"),
                "0x0000000000000000000000000000000000000000000000000000000000000007"
                + "0000000000000000000000000000000000000000000000000000000000000040"
                + "0000000000000000000000000000000000000000000000000000000000000002"
                + "6869000000000000000000000000000000000000000000000000000000000000");
        assertThat(decoded.keySet()).containsExactly("amount", "who", "note");
        assertThat(decoded).containsEntry("amount", "7")
                .containsEntry("who", "0xfeedfacefeedfacefeedfacefeedfacefeedface")
                .containsEntry("note", "hi");
    }

    @Test
    @DisplayName("a dynamic indexed param is only its hash on-chain — shown as hash:0x…, labeled")
    void dynamicIndexedIsHash() {
        AbiEntry event = AbiEntry.event("Note", List.of(
                new AbiParam("text", "string", true)));
        String topicHash = "0x" + Keccak256.hashHex("hello");
        Map<String, String> decoded = AbiCodec.decodeEventLog(event,
                List.of("0x" + Keccak256.hashHex("Note(string)"), topicHash), "0x");
        assertThat(decoded).containsExactly(Map.entry("text", "hash:" + topicHash));
    }

    @Test
    @DisplayName("unnamed parameters are labeled arg0, arg1, … by position")
    void unnamedParams() {
        AbiEntry event = AbiEntry.event("Anon", List.of(
                new AbiParam("", "uint256", true),
                new AbiParam("", "bool", false)));
        Map<String, String> decoded = AbiCodec.decodeEventLog(event,
                List.of("0x" + "0".repeat(64),
                        "0x" + "0".repeat(63) + "5"),
                "0x" + "0".repeat(63) + "1");
        assertThat(decoded).containsExactly(
                Map.entry("arg0", "5"),
                Map.entry("arg1", "true"));
    }

    @Test
    @DisplayName("an indexed bool decodes from its topic word")
    void indexedStaticBool() {
        AbiEntry event = AbiEntry.event("Flagged", List.of(
                new AbiParam("flag", "bool", true)));
        Map<String, String> decoded = AbiCodec.decodeEventLog(event,
                List.of("0x" + "0".repeat(64), "0x" + "0".repeat(63) + "1"), "0x");
        assertThat(decoded).containsExactly(Map.entry("flag", "true"));
    }

    @Test
    @DisplayName("too few topics for the indexed params is refused, naming the parameter")
    void tooFewTopics() {
        assertThatThrownBy(() -> AbiCodec.decodeEventLog(transfer(),
                List.of("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
                        "0x000000000000000000000000a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"),
                "0x" + "0".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'to'");
    }

    @Test
    @DisplayName("an event with no parameters decodes to an empty map")
    void noParams() {
        AbiEntry event = AbiEntry.event("Ping", List.of());
        assertThat(AbiCodec.decodeEventLog(event,
                List.of("0x" + Keccak256.hashHex("Ping()")), "0x")).isEmpty();
    }
}
