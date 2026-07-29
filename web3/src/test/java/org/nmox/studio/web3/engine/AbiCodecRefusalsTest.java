package org.nmox.studio.web3.engine;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.web3.model.AbiEntry;
import org.nmox.studio.web3.model.AbiParam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The codec's honest-refusal edges and rarely-hit value shapes: every
 * unsupported or malformed input must come back as a named
 * IllegalArgumentException (the Interact pane shows the message
 * verbatim), and the odd-but-legal encodings — bare {@code uint},
 * negative hex, boolean false, a full bytes32 — must produce exactly
 * the spec bytes. All pure; fixtures are hand-built hex.
 */
class AbiCodecRefusalsTest {

    private static final String WORD_5 =
            "0000000000000000000000000000000000000000000000000000000000000005";

    // ---- argument-count and value refusals ----

    @Test
    @DisplayName("one missing argument is reported in the singular")
    void singularArgumentCount() {
        assertThatThrownBy(() -> AbiCodec.encodeArgs(
                List.of(AbiParam.of("x", "uint256")), List.of()))
                .hasMessage("Expected 1 argument, got 0.");
    }

    @Test
    @DisplayName("a null value is refused by parameter label")
    void nullValueRefused() {
        assertThatThrownBy(() -> AbiCodec.encodeArgs(
                List.of(AbiParam.of("x", "uint256")), Arrays.asList((String) null)))
                .hasMessageContaining("'x' has no value.");
    }

    @Test
    @DisplayName("nested arrays and tuple arrays are refused with the cast hint")
    void nestedAndTupleArraysRefused() {
        assertThatThrownBy(() -> AbiCodec.encodeArgs(
                List.of(AbiParam.of("m", "uint256[2][2]")), List.of("[[1,2],[3,4]]")))
                .hasMessageContaining("Nested arrays aren't supported yet");
        assertThatThrownBy(() -> AbiCodec.encodeArgs(
                List.of(AbiParam.of("t", "tuple[2]")), List.of("[a,b]")))
                .hasMessageContaining("Tuple parameters aren't supported yet");
    }

    @Test
    @DisplayName("unsupported integer widths are refused, by label")
    void badIntegerWidthsRefused() {
        assertThatThrownBy(() -> AbiCodec.encodeArgs(
                List.of(AbiParam.of("x", "uintx")), List.of("1")))
                .hasMessageContaining("unsupported type 'uintx'");
        assertThatThrownBy(() -> AbiCodec.encodeArgs(
                List.of(AbiParam.of("x", "uint7")), List.of("1")))
                .hasMessageContaining("width must be 8..256 in steps of 8");
    }

    @Test
    @DisplayName("bytesN needs N of 1..32, and a numeric N")
    void badFixedBytesRefused() {
        assertThatThrownBy(() -> AbiCodec.encodeArgs(
                List.of(AbiParam.of("b", "byteszz")), List.of("0x00")))
                .hasMessageContaining("unsupported type 'byteszz'");
        assertThatThrownBy(() -> AbiCodec.encodeArgs(
                List.of(AbiParam.of("b", "bytes33")), List.of("0x00")))
                .hasMessageContaining("bytesN needs N of 1..32");
    }

    @Test
    @DisplayName("bad hex for a bytes parameter names the parameter and the cause")
    void badHexRefused() {
        assertThatThrownBy(() -> AbiCodec.encodeArgs(
                List.of(AbiParam.of("data", "bytes")), List.of("0xzz")))
                .hasMessageContaining("'data' expects hex data");
    }

    @Test
    @DisplayName("a non-hex address is refused even at the right length")
    void nonHexAddressRefused() {
        assertThatThrownBy(() -> AbiCodec.encodeArgs(
                List.of(AbiParam.of("to", "address")), List.of("0x" + "Z".repeat(40))))
                .hasMessageContaining("needs an address");
    }

    @Test
    @DisplayName("an uppercase-hex address is accepted (isHex knows A-F)")
    void uppercaseAddressAccepted() {
        byte[] word = AbiCodec.encodeArgs(
                List.of(AbiParam.of("to", "address")),
                List.of("0x" + "AB".repeat(20)));
        assertThat(Hex.toHex(word)).endsWith("ab".repeat(20));
    }

    // ---- rare but legal encodings ----

    @Test
    @DisplayName("bool false is a zero word")
    void boolFalse() {
        byte[] word = AbiCodec.encodeArgs(
                List.of(AbiParam.of("flag", "bool")), List.of("false"));
        assertThat(Hex.toHex(word)).isEqualTo("00".repeat(32));
    }

    @Test
    @DisplayName("bare uint means uint256")
    void bareUintIs256() {
        byte[] word = AbiCodec.encodeArgs(
                List.of(AbiParam.of("x", "uint")), List.of("5"));
        assertThat(Hex.toHex(word)).isEqualTo(WORD_5);
    }

    @Test
    @DisplayName("negative hex int256 encodes two's-complement")
    void negativeHexInt() {
        byte[] word = AbiCodec.encodeArgs(
                List.of(AbiParam.of("x", "int256")), List.of("-0x10"));
        assertThat(Hex.toHex(word)).isEqualTo("f".repeat(62) + "f0");
    }

    @Test
    @DisplayName("a uint256 with the top bit set survives BigInteger's sign byte")
    void topBitUint() {
        byte[] word = AbiCodec.encodeArgs(
                List.of(AbiParam.of("x", "uint256")),
                List.of("0x8000000000000000000000000000000000000000000000000000000000000000"));
        assertThat(Hex.toHex(word)).isEqualTo("80" + "00".repeat(31));
    }

    @Test
    @DisplayName("a full-width bytes32 value passes through unpadded")
    void fullBytes32() {
        byte[] word = AbiCodec.encodeArgs(
                List.of(AbiParam.of("h", "bytes32")), List.of("0x" + "11".repeat(32)));
        assertThat(Hex.toHex(word)).isEqualTo("11".repeat(32));
    }

    // ---- decode refusals and edges ----

    @Test
    @DisplayName("a null return payload for a value-returning function is called out")
    void nullReturnRefused() {
        AbiEntry get = AbiEntry.function("get", List.of(),
                List.of(AbiParam.of("", "uint256")), "view");
        assertThatThrownBy(() -> AbiCodec.decodeReturn(get, null))
                .hasMessageContaining("returned no data");
    }

    @Test
    @DisplayName("tuple and unknown return types are refused by name")
    void badReturnTypesRefused() {
        AbiEntry tup = AbiEntry.function("t", List.of(),
                List.of(AbiParam.of("", "tuple")), "view");
        assertThatThrownBy(() -> AbiCodec.decodeReturn(tup, "0x" + "00".repeat(32)))
                .hasMessageContaining("Tuple parameters aren't supported yet");

        AbiEntry odd = AbiEntry.function("o", List.of(),
                List.of(AbiParam.of("", "foo")), "view");
        assertThatThrownBy(() -> AbiCodec.decodeReturn(odd, "0x" + "00".repeat(32)))
                .hasMessageContaining("unsupported type 'foo'");
    }

    @Test
    @DisplayName("a bad array size in a return type is refused")
    void badArraySizeRefused() {
        AbiEntry odd = AbiEntry.function("o", List.of(),
                List.of(AbiParam.of("", "uint256[x]")), "view");
        assertThatThrownBy(() -> AbiCodec.decodeReturn(odd, "0x" + "00".repeat(32)))
                .hasMessageContaining("unsupported array type 'uint256[x]'");
    }

    @Test
    @DisplayName("an impossible offset word is refused, not index-crashed")
    void impossibleOffsetRefused() {
        AbiEntry s = AbiEntry.function("s", List.of(),
                List.of(AbiParam.of("", "string")), "view");
        // offset word of 2^64 — far beyond any int-addressable payload
        String huge = "0".repeat(47) + "1" + "0".repeat(16);
        assertThatThrownBy(() -> AbiCodec.decodeReturn(s, "0x" + huge))
                .hasMessageContaining("impossible offset/length");
    }

    @Test
    @DisplayName("a fixed-size array of strings decodes through the dynamic path")
    void fixedArrayOfStrings() {
        // return value string[2] = ["hi","yo"], offsets hand-built per spec
        String data = "0x"
                + "0000000000000000000000000000000000000000000000000000000000000020"
                + "0000000000000000000000000000000000000000000000000000000000000040"
                + "0000000000000000000000000000000000000000000000000000000000000080"
                + "0000000000000000000000000000000000000000000000000000000000000002"
                + "6869000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000002"
                + "796f000000000000000000000000000000000000000000000000000000000000";
        AbiEntry f = AbiEntry.function("f", List.of(),
                List.of(AbiParam.of("", "string[2]")), "view");
        assertThat(AbiCodec.decodeReturn(f, data)).containsExactly("[hi, yo]");
    }

    // ---- event-log edges ----

    @Test
    @DisplayName("an indexed-only event tolerates null data but not missing topics")
    void eventTopicEdges() {
        AbiEntry transfer = AbiEntry.event("Ping",
                List.of(new AbiParam("who", "address", true)));
        String sig = "0x" + "aa".repeat(32);

        var decoded = AbiCodec.decodeEventLog(transfer,
                List.of(sig, "0x" + "00".repeat(12) + "11".repeat(20)), null);
        assertThat(decoded).containsEntry("who", "0x" + "11".repeat(20));

        assertThatThrownBy(() -> AbiCodec.decodeEventLog(transfer, null, null))
                .hasMessageContaining("too few topics")
                .hasMessageContaining("who");
    }

    // ---- revert decoding: malformed payloads and panic names ----

    @Test
    @DisplayName("odd-length hex after the selector is unrecognizable, not a crash")
    void oddHexRevertData() {
        assertThat(AbiCodec.decodeRevert("0x08c379a0abc"))
                .startsWith("Reverted with unrecognizable data");
    }

    @Test
    @DisplayName("a Panic whose code overflows int is malformed, honestly")
    void hugePanicCode() {
        String panic = "0x4e487b71" + "7f" + "00".repeat(31);
        assertThat(AbiCodec.decodeRevert(panic))
                .isEqualTo("Reverted with a malformed Panic(uint256) payload.");
    }

    @Test
    @DisplayName("the panic table knows the compiler's named codes")
    void panicNames() {
        assertThat(AbiCodec.decodeRevert(panicWith(0x00))).contains("generic compiler panic");
        assertThat(AbiCodec.decodeRevert(panicWith(0x22))).contains("corrupted storage byte array");
        assertThat(AbiCodec.decodeRevert(panicWith(0x31))).contains("pop on an empty array");
        assertThat(AbiCodec.decodeRevert(panicWith(0x51)))
                .contains("call to an uninitialized function pointer");
    }

    @Test
    @DisplayName("custom-error matching skips non-error ABI entries and prints named args")
    void customErrorMatching() {
        AbiEntry deficit = AbiEntry.error("Deficit",
                List.of(AbiParam.of("needed", "uint256")));
        AbiEntry decoyFn = AbiEntry.function("Deficit", List.of(), List.of(), "pure");
        String selector = Hex.toHex(Keccak256.selector("Deficit(uint256)"));

        String decoded = AbiCodec.decodeRevert("0x" + selector + WORD_5,
                List.of(decoyFn, deficit));
        assertThat(decoded).isEqualTo("Deficit(needed: 5)");
    }

    @Test
    @DisplayName("a matching custom error with no fields prints as a bare call")
    void customErrorNoFields() {
        AbiEntry empty = AbiEntry.error("Halted", List.of());
        String selector = Hex.toHex(Keccak256.selector("Halted()"));
        assertThat(AbiCodec.decodeRevert("0x" + selector, List.of(empty)))
                .isEqualTo("Halted()");
    }

    @Test
    @DisplayName("a matching custom error whose data won't decode says so instead of lying")
    void customErrorUndecodableData() {
        AbiEntry deficit = AbiEntry.error("Deficit",
                List.of(AbiParam.of("needed", "uint256")));
        String selector = Hex.toHex(Keccak256.selector("Deficit(uint256)"));
        assertThat(AbiCodec.decodeRevert("0x" + selector + "0011", List.of(deficit)))
                .contains("custom error 0x" + selector)
                .contains("but its data would not decode");
    }

    // ---- the argument splitter's quoting and nesting ----

    @Test
    @DisplayName("splitArray honors escaped quotes and nested brackets")
    void splitArrayQuotingAndNesting() {
        assertThat(AbiCodec.splitArray("[\"a\\\"b\", \"c,d\"]", "x"))
                .containsExactly("\"a\\\"b\"", "\"c,d\"");
        assertThat(AbiCodec.splitArray("[[1,2], [3]]", "x"))
                .containsExactly("[1,2]", "[3]");
    }

    private static String panicWith(int code) {
        return "0x4e487b71" + "00".repeat(31) + String.format("%02x", code);
    }
}
