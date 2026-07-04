package org.nmox.studio.web3.engine;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.web3.model.AbiEntry;
import org.nmox.studio.web3.model.AbiParam;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * decodeRevert: Error(string) with the Solidity docs' own worked
 * example ("Not enough Ether provided.", from the revert chapter),
 * Panic(uint256) with the documented panic codes, and custom errors
 * matched by selector. decodeRevert never throws — it always has to
 * produce something the status bar can show.
 */
class AbiCodecRevertTest {

    /**
     * The Solidity docs' example: revert("Not enough Ether provided.")
     * — selector 0x08c379a0, offset word, length 0x1a, UTF-8 padded.
     */
    private static final String NOT_ENOUGH_ETHER =
            "0x08c379a0"
            + "0000000000000000000000000000000000000000000000000000000000000020"
            + "000000000000000000000000000000000000000000000000000000000000001a"
            + "4e6f7420656e6f7567682045746865722070726f76696465642e000000000000";

    @Test
    @DisplayName("Error(string) yields the reason text itself")
    void errorString() {
        assertThat(AbiCodec.decodeRevert(NOT_ENOUGH_ETHER))
                .isEqualTo("Not enough Ether provided.");
    }

    @Test
    @DisplayName("Panic 0x01 — assertion failed")
    void panicAssert() {
        assertThat(AbiCodec.decodeRevert(panic(0x01)))
                .isEqualTo("Panic 0x1: assertion failed");
    }

    @Test
    @DisplayName("Panic 0x11 — arithmetic overflow")
    void panicOverflow() {
        assertThat(AbiCodec.decodeRevert(panic(0x11)))
                .isEqualTo("Panic 0x11: arithmetic overflow");
    }

    @Test
    @DisplayName("Panic 0x12 — division by zero")
    void panicDivZero() {
        assertThat(AbiCodec.decodeRevert(panic(0x12)))
                .isEqualTo("Panic 0x12: division by zero");
    }

    @Test
    @DisplayName("Panic 0x21, 0x32, 0x41 — invalid enum, index out of bounds, out of memory")
    void panicOtherNamedCodes() {
        assertThat(AbiCodec.decodeRevert(panic(0x21))).contains("invalid enum");
        assertThat(AbiCodec.decodeRevert(panic(0x32))).contains("index out of bounds");
        assertThat(AbiCodec.decodeRevert(panic(0x41))).contains("out of memory");
    }

    @Test
    @DisplayName("an unknown panic code still shows its number")
    void panicUnknownCode() {
        assertThat(AbiCodec.decodeRevert(panic(0x99)))
                .isEqualTo("Panic 0x99: unknown panic code");
    }

    @Test
    @DisplayName("an unknown selector reads as custom error 0x…")
    void unknownSelector() {
        assertThat(AbiCodec.decodeRevert("0xdeadbeef"))
                .isEqualTo("custom error 0xdeadbeef");
    }

    @Test
    @DisplayName("a custom error matched by selector decodes name and arguments")
    void customErrorMatched() {
        AbiEntry insufficient = AbiEntry.error("InsufficientBalance", List.of(
                new AbiParam("available", "uint256", false),
                new AbiParam("required", "uint256", false)));
        String data = "0x" + Hex.toHex(
                Keccak256.selector("InsufficientBalance(uint256,uint256)"))
                + "0000000000000000000000000000000000000000000000000000000000000005"
                + "000000000000000000000000000000000000000000000000000000000000000a";
        assertThat(AbiCodec.decodeRevert(data, List.of(insufficient)))
                .isEqualTo("InsufficientBalance(available: 5, required: 10)");
    }

    @Test
    @DisplayName("a custom error with no matching entry still reads as custom error 0x…")
    void customErrorUnmatched() {
        AbiEntry other = AbiEntry.error("SomethingElse", List.of());
        assertThat(AbiCodec.decodeRevert("0x12345678", List.of(other)))
                .isEqualTo("custom error 0x12345678");
    }

    @Test
    @DisplayName("empty or 0x revert data reads as reverted without a reason")
    void emptyData() {
        assertThat(AbiCodec.decodeRevert(null)).isEqualTo("Reverted without a reason.");
        assertThat(AbiCodec.decodeRevert("")).isEqualTo("Reverted without a reason.");
        assertThat(AbiCodec.decodeRevert("0x")).isEqualTo("Reverted without a reason.");
    }

    @Test
    @DisplayName("data shorter than a selector is shown raw, never thrown on")
    void shortData() {
        assertThat(AbiCodec.decodeRevert("0xab"))
                .isEqualTo("Reverted with unrecognizable data 0xab.");
    }

    @Test
    @DisplayName("a truncated Error(string) payload degrades to the malformed message")
    void malformedErrorString() {
        assertThat(AbiCodec.decodeRevert("0x08c379a0" + "00".repeat(8)))
                .isEqualTo("Reverted with a malformed Error(string) payload.");
    }

    @Test
    @DisplayName("a Panic with no payload degrades to the malformed message")
    void malformedPanic() {
        assertThat(AbiCodec.decodeRevert("0x4e487b71"))
                .isEqualTo("Reverted with a malformed Panic(uint256) payload.");
    }

    private static String panic(int code) {
        return "0x4e487b71" + String.format("%064x", code);
    }
}
