package org.nmox.studio.web3.engine;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.web3.model.AbiEntry;
import org.nmox.studio.web3.model.AbiParam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The encoder against the worked examples of the official Solidity ABI
 * specification (docs.soliditylang.org, "Contract ABI Specification →
 * Examples") — baz, sam, and f are copied verbatim from there, expected
 * hex included. If one of these fails, the encoder is wrong; the
 * fixtures are the spec.
 */
class AbiCodecEncodeTest {

    // ---- the spec's own examples ----------------------------------------

    @Test
    @DisplayName("spec fixture: baz(uint32,bool) with (69, true)")
    void specBaz() {
        AbiEntry baz = AbiEntry.function("baz",
                List.of(AbiParam.of("x", "uint32"), AbiParam.of("y", "bool")),
                List.of(), "pure");
        assertThat(AbiCodec.encodeCall(baz, List.of("69", "true"))).isEqualTo(
                "0xcdcd77c0"
                + "0000000000000000000000000000000000000000000000000000000000000045"
                + "0000000000000000000000000000000000000000000000000000000000000001");
    }

    @Test
    @DisplayName("spec fixture: sam(bytes,bool,uint256[]) with (\"dave\", true, [1,2,3])")
    void specSam() {
        // the spec passes "dave" as the bytes argument (its ASCII bytes)
        AbiEntry sam = AbiEntry.function("sam",
                List.of(AbiParam.of("data", "bytes"), AbiParam.of("flag", "bool"),
                        AbiParam.of("nums", "uint256[]")),
                List.of(), "pure");
        assertThat(AbiCodec.encodeCall(sam, List.of("0x64617665", "true", "[1, 2, 3]")))
                .isEqualTo("0xa5643bf2"
                + "0000000000000000000000000000000000000000000000000000000000000060"
                + "0000000000000000000000000000000000000000000000000000000000000001"
                + "00000000000000000000000000000000000000000000000000000000000000a0"
                + "0000000000000000000000000000000000000000000000000000000000000004"
                + "6461766500000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000003"
                + "0000000000000000000000000000000000000000000000000000000000000001"
                + "0000000000000000000000000000000000000000000000000000000000000002"
                + "0000000000000000000000000000000000000000000000000000000000000003");
    }

    @Test
    @DisplayName("spec fixture: f(uint256,uint32[],bytes10,bytes) — the head/tail showcase")
    void specF() {
        AbiEntry f = AbiEntry.function("f",
                List.of(AbiParam.of("a", "uint256"), AbiParam.of("b", "uint32[]"),
                        AbiParam.of("c", "bytes10"), AbiParam.of("d", "bytes")),
                List.of(), "pure");
        // args from the spec: 0x123, [0x456, 0x789], "1234567890" (as bytes10
        // ASCII = 0x31…30), "Hello, world!" (13 bytes)
        assertThat(AbiCodec.encodeCall(f, List.of(
                "0x123",
                "[0x456, 0x789]",
                "0x31323334353637383930",
                "0x48656c6c6f2c20776f726c6421")))
                .isEqualTo("0x8be65246"
                + "0000000000000000000000000000000000000000000000000000000000000123"
                + "0000000000000000000000000000000000000000000000000000000000000080"
                + "3132333435363738393000000000000000000000000000000000000000000000"
                + "00000000000000000000000000000000000000000000000000000000000000e0"
                + "0000000000000000000000000000000000000000000000000000000000000002"
                + "0000000000000000000000000000000000000000000000000000000000000456"
                + "0000000000000000000000000000000000000000000000000000000000000789"
                + "000000000000000000000000000000000000000000000000000000000000000d"
                + "48656c6c6f2c20776f726c642100000000000000000000000000000000000000");
    }

    // ---- individual types -----------------------------------------------

    @Test
    @DisplayName("string arguments encode UTF-8 with length; quotes are stripped when present")
    void stringArgument() {
        AbiEntry g = fn("g", "string");
        String encoded = AbiCodec.encodeCall(g, List.of("dave"));
        String quoted = AbiCodec.encodeCall(g, List.of("\"dave\""));
        assertThat(encoded).isEqualTo(quoted).endsWith(
                "0000000000000000000000000000000000000000000000000000000000000020"
                + "0000000000000000000000000000000000000000000000000000000000000004"
                + "6461766500000000000000000000000000000000000000000000000000000000");
    }

    @Test
    @DisplayName("address arguments are 0x-tolerant and case-insensitive")
    void addressArgument() {
        AbiEntry g = fn("g", "address");
        String withPrefix = AbiCodec.encodeCall(g,
                List.of("0xA0b86991C6218b36c1d19D4a2e9Eb0cE3606eB48"));
        String bare = AbiCodec.encodeCall(g,
                List.of("a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"));
        assertThat(withPrefix).isEqualTo(bare).endsWith(
                "000000000000000000000000a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48");
    }

    @Test
    @DisplayName("int256 encodes negatives as two's complement")
    void negativeInt() {
        AbiEntry g = fn("g", "int256");
        assertThat(AbiCodec.encodeCall(g, List.of("-1"))).endsWith(
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
        assertThat(AbiCodec.encodeCall(g, List.of("-2"))).endsWith(
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe");
    }

    @Test
    @DisplayName("int8 range: -128 fits, -129 and 128 are refused naming the parameter")
    void int8Range() {
        AbiEntry g = AbiEntry.function("g", List.of(new AbiParam("amount", "int8", false)),
                List.of(), "pure");
        assertThat(AbiCodec.encodeCall(g, List.of("-128"))).endsWith(
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff80");
        assertThatThrownBy(() -> AbiCodec.encodeCall(g, List.of("-129")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'amount'").hasMessageContaining("int8");
        assertThatThrownBy(() -> AbiCodec.encodeCall(g, List.of("128")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    @DisplayName("uint8 range-checks: 255 fits, 256 and -1 are refused")
    void uint8Range() {
        AbiEntry g = fn("g", "uint8");
        assertThat(AbiCodec.encodeCall(g, List.of("255"))).endsWith(
                "00000000000000000000000000000000000000000000000000000000000000ff");
        assertThatThrownBy(() -> AbiCodec.encodeCall(g, List.of("256")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of range");
        assertThatThrownBy(() -> AbiCodec.encodeCall(g, List.of("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("uint256 takes the full 2^256-1; one more is refused")
    void uint256Extremes() {
        AbiEntry g = fn("g", "uint256");
        String max = java.math.BigInteger.ONE.shiftLeft(256)
                .subtract(java.math.BigInteger.ONE).toString();
        assertThat(AbiCodec.encodeCall(g, List.of(max))).endsWith("f".repeat(64));
        assertThatThrownBy(() -> AbiCodec.encodeCall(g,
                List.of(java.math.BigInteger.ONE.shiftLeft(256).toString())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("bytes32 wants exactly 32 bytes — more or fewer is refused with the count")
    void fixedBytesExact() {
        AbiEntry g = fn("g", "bytes32");
        assertThat(AbiCodec.encodeCall(g, List.of("0x" + "11".repeat(32))))
                .endsWith("11".repeat(32));
        assertThatThrownBy(() -> AbiCodec.encodeCall(g, List.of("0x1122")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected exactly 32 bytes, got 2");
    }

    @Test
    @DisplayName("a fixed uint256[2] encodes inline (static), no offset word")
    void fixedStaticArray() {
        AbiEntry g = fn("g", "uint256[2]");
        assertThat(AbiCodec.encodeCall(g, List.of("[7, 8]"))).isEqualTo(
                "0x" + Hex.toHex(Keccak256.selector("g(uint256[2])"))
                + "0000000000000000000000000000000000000000000000000000000000000007"
                + "0000000000000000000000000000000000000000000000000000000000000008");
    }

    @Test
    @DisplayName("a fixed string[2] is dynamic (its element type is) — offset word in the head")
    void fixedDynamicArray() {
        AbiEntry g = fn("g", "string[2]");
        String encoded = AbiCodec.encodeCall(g, List.of("[\"ab\", \"cd\"]"));
        assertThat(encoded).startsWith(
                "0x" + Hex.toHex(Keccak256.selector("g(string[2])"))
                // one head word: the offset to the pair
                + "0000000000000000000000000000000000000000000000000000000000000020"
                // the pair's own head: two offsets relative to the pair
                + "0000000000000000000000000000000000000000000000000000000000000040"
                + "0000000000000000000000000000000000000000000000000000000000000080");
    }

    @Test
    @DisplayName("an empty dynamic array is a single zero count word")
    void emptyDynamicArray() {
        AbiEntry g = fn("g", "uint256[]");
        assertThat(AbiCodec.encodeCall(g, List.of("[]"))).isEqualTo(
                "0x" + Hex.toHex(Keccak256.selector("g(uint256[])"))
                + "0000000000000000000000000000000000000000000000000000000000000020"
                + "0000000000000000000000000000000000000000000000000000000000000000");
    }

    @Test
    @DisplayName("a fixed-size array refuses the wrong element count, naming both numbers")
    void fixedArrayWrongCount() {
        AbiEntry g = fn("g", "uint256[3]");
        assertThatThrownBy(() -> AbiCodec.encodeCall(g, List.of("[1, 2]")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 3").hasMessageContaining("got 2");
    }

    @Test
    @DisplayName("bool wants the words true or false, nothing else")
    void boolStrict() {
        AbiEntry g = fn("g", "bool");
        assertThat(AbiCodec.encodeCall(g, List.of("TRUE"))).endsWith(
                "0000000000000000000000000000000000000000000000000000000000000001");
        assertThatThrownBy(() -> AbiCodec.encodeCall(g, List.of("1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("true or false");
    }

    // ---- the honest refusals ---------------------------------------------

    @Test
    @DisplayName("tuples are refused with the status-bar sentence")
    void tupleRefusal() {
        AbiEntry g = fn("g", "tuple");
        assertThatThrownBy(() -> AbiCodec.encodeCall(g, List.of("whatever")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tuple parameters aren't supported yet — use cast for this call.");
    }

    @Test
    @DisplayName("tuple arrays are refused the same way")
    void tupleArrayRefusal() {
        AbiEntry g = fn("g", "tuple[]");
        assertThatThrownBy(() -> AbiCodec.encodeCall(g, List.of("[]")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tuple parameters aren't supported yet");
    }

    @Test
    @DisplayName("nested arrays are refused with their own honest sentence")
    void nestedArrayRefusal() {
        AbiEntry g = fn("g", "uint256[][]");
        assertThatThrownBy(() -> AbiCodec.encodeCall(g, List.of("[[1]]")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Nested arrays aren't supported yet — use cast for this call.");
    }

    @Test
    @DisplayName("a wrong argument count is refused before any encoding")
    void argumentCountMismatch() {
        AbiEntry g = fn("g", "uint256");
        assertThatThrownBy(() -> AbiCodec.encodeCall(g, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected 1 argument").hasMessageContaining("got 0");
    }

    @Test
    @DisplayName("a non-numeric value for a uint names the parameter and the value")
    void nonNumericUint() {
        AbiEntry g = AbiEntry.function("g",
                List.of(new AbiParam("amount", "uint256", false)), List.of(), "pure");
        assertThatThrownBy(() -> AbiCodec.encodeCall(g, List.of("lots")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'amount'").hasMessageContaining("lots");
    }

    @Test
    @DisplayName("a non-array value for an array parameter shows the [1, 2, 3] hint")
    void nonArrayForArray() {
        AbiEntry g = fn("g", "uint256[]");
        assertThatThrownBy(() -> AbiCodec.encodeCall(g, List.of("1, 2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[1, 2, 3]");
    }

    @Test
    @DisplayName("a malformed address names the parameter and the 40-digit rule")
    void malformedAddress() {
        AbiEntry g = fn("g", "address");
        assertThatThrownBy(() -> AbiCodec.encodeCall(g, List.of("0x1234")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("40 hex digits");
    }

    // ---- encodeArgs (the constructor path) --------------------------------

    @Test
    @DisplayName("encodeArgs carries no selector — what a constructor appends to bytecode")
    void encodeArgsNoSelector() {
        byte[] encoded = AbiCodec.encodeArgs(
                List.of(AbiParam.of("x", "uint256")), List.of("66"));
        assertThat(Hex.toHex(encoded)).isEqualTo(
                "0000000000000000000000000000000000000000000000000000000000000042");
    }

    @Test
    @DisplayName("encodeArgs with no parameters and null args encodes to nothing")
    void encodeArgsEmpty() {
        assertThat(AbiCodec.encodeArgs(List.of(), null)).isEmpty();
        assertThat(AbiCodec.encodeArgs(List.of(), List.of())).isEmpty();
    }

    private static AbiEntry fn(String name, String... types) {
        List<AbiParam> params = new java.util.ArrayList<>();
        for (String type : types) {
            params.add(AbiParam.of("", type));
        }
        return AbiEntry.function(name, params, List.of(), "pure");
    }
}
