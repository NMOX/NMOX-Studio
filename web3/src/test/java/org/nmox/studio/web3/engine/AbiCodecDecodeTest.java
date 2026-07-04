package org.nmox.studio.web3.engine;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.web3.model.AbiEntry;
import org.nmox.studio.web3.model.AbiParam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * decodeReturn: the display-string side of the codec, plus the
 * encode → decode round trips that prove head/tail assembly and
 * disassembly agree with each other.
 */
class AbiCodecDecodeTest {

    @Test
    @DisplayName("a uint256 return decodes to decimal")
    void uintReturn() {
        AbiEntry number = returning("uint256");
        assertThat(AbiCodec.decodeReturn(number,
                "0x0000000000000000000000000000000000000000000000000000000000000045"))
                .containsExactly("69");
    }

    @Test
    @DisplayName("an int256 return decodes two's complement back to the negative")
    void negativeIntReturn() {
        AbiEntry f = returning("int256");
        assertThat(AbiCodec.decodeReturn(f, "0x" + "f".repeat(64)))
                .containsExactly("-1");
    }

    @Test
    @DisplayName("an address return is 0x-prefixed lowercase")
    void addressReturn() {
        AbiEntry f = returning("address");
        assertThat(AbiCodec.decodeReturn(f,
                "0x000000000000000000000000a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"))
                .containsExactly("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48");
    }

    @Test
    @DisplayName("bool returns decode to true / false")
    void boolReturn() {
        AbiEntry f = returning("bool");
        assertThat(AbiCodec.decodeReturn(f, "0x" + "0".repeat(63) + "1"))
                .containsExactly("true");
        assertThat(AbiCodec.decodeReturn(f, "0x" + "0".repeat(64)))
                .containsExactly("false");
    }

    @Test
    @DisplayName("a string return decodes its UTF-8 text")
    void stringReturn() {
        AbiEntry f = returning("string");
        assertThat(AbiCodec.decodeReturn(f,
                "0x0000000000000000000000000000000000000000000000000000000000000020"
                + "0000000000000000000000000000000000000000000000000000000000000004"
                + "6461766500000000000000000000000000000000000000000000000000000000"))
                .containsExactly("dave");
    }

    @Test
    @DisplayName("bytes and bytesN returns decode to 0x-hex")
    void bytesReturns() {
        assertThat(AbiCodec.decodeReturn(returning("bytes"),
                "0x0000000000000000000000000000000000000000000000000000000000000020"
                + "0000000000000000000000000000000000000000000000000000000000000002"
                + "beef000000000000000000000000000000000000000000000000000000000000"))
                .containsExactly("0xbeef");
        assertThat(AbiCodec.decodeReturn(returning("bytes4"),
                "0xcafebabe00000000000000000000000000000000000000000000000000000000"))
                .containsExactly("0xcafebabe");
    }

    @Test
    @DisplayName("multiple outputs decode in order (uint256, address, bool)")
    void multipleOutputs() {
        AbiEntry f = returning("uint256", "address", "bool");
        assertThat(AbiCodec.decodeReturn(f,
                "0x0000000000000000000000000000000000000000000000000000000000000064"
                + "000000000000000000000000feedfacefeedfacefeedfacefeedfacefeedface"
                + "0000000000000000000000000000000000000000000000000000000000000001"))
                .containsExactly("100", "0xfeedfacefeedfacefeedfacefeedfacefeedface", "true");
    }

    @Test
    @DisplayName("a dynamic uint256[] return decodes to [a, b, c]")
    void dynamicArrayReturn() {
        AbiEntry f = returning("uint256[]");
        assertThat(AbiCodec.decodeReturn(f,
                "0x0000000000000000000000000000000000000000000000000000000000000020"
                + "0000000000000000000000000000000000000000000000000000000000000003"
                + "0000000000000000000000000000000000000000000000000000000000000001"
                + "0000000000000000000000000000000000000000000000000000000000000002"
                + "0000000000000000000000000000000000000000000000000000000000000003"))
                .containsExactly("[1, 2, 3]");
    }

    @Test
    @DisplayName("round trip: every v1 type encodes then decodes back to its display form")
    void roundTripAllTypes() {
        AbiEntry f = AbiEntry.function("rt",
                List.of(AbiParam.of("a", "uint256"), AbiParam.of("b", "int128"),
                        AbiParam.of("c", "address"), AbiParam.of("d", "bool"),
                        AbiParam.of("e", "bytes4"), AbiParam.of("f", "bytes"),
                        AbiParam.of("g", "string"), AbiParam.of("h", "uint8[]"),
                        AbiParam.of("i", "uint256[2]")),
                List.of(AbiParam.of("a", "uint256"), AbiParam.of("b", "int128"),
                        AbiParam.of("c", "address"), AbiParam.of("d", "bool"),
                        AbiParam.of("e", "bytes4"), AbiParam.of("f", "bytes"),
                        AbiParam.of("g", "string"), AbiParam.of("h", "uint8[]"),
                        AbiParam.of("i", "uint256[2]")),
                "pure");
        List<String> args = List.of(
                "123456789012345678901234567890",
                "-42",
                "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
                "true",
                "0xdeadbeef",
                "0x010203",
                "hello world",
                "[1, 2, 255]",
                "[7, 8]");
        String encoded = AbiCodec.encodeCall(f, args);
        // strip the 4-byte selector; return data has none
        String returnData = "0x" + Hex.strip0x(encoded).substring(8);
        assertThat(AbiCodec.decodeReturn(f, returnData)).containsExactly(
                "123456789012345678901234567890",
                "-42",
                "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
                "true",
                "0xdeadbeef",
                "0x010203",
                "hello world",
                "[1, 2, 255]",
                "[7, 8]");
    }

    @Test
    @DisplayName("a function with no outputs decodes to an empty list, whatever the data")
    void noOutputs() {
        AbiEntry f = AbiEntry.function("store",
                List.of(AbiParam.of("x", "uint256")), List.of(), "nonpayable");
        assertThat(AbiCodec.decodeReturn(f, "0x")).isEmpty();
        assertThat(AbiCodec.decodeReturn(f, null)).isEmpty();
    }

    @Test
    @DisplayName("empty return data for a function with outputs asks the deployed-here question")
    void emptyDataWithOutputs() {
        AbiEntry f = returning("uint256");
        assertThatThrownBy(() -> AbiCodec.decodeReturn(f, "0x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deployed");
    }

    @Test
    @DisplayName("truncated return data is refused with a human message, not an AIOOBE")
    void truncatedData() {
        AbiEntry f = returning("uint256", "uint256");
        assertThatThrownBy(() -> AbiCodec.decodeReturn(f, "0x" + "0".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shorter");
    }

    private static AbiEntry returning(String... types) {
        List<AbiParam> outputs = new java.util.ArrayList<>();
        for (String type : types) {
            outputs.add(AbiParam.of("", type));
        }
        return AbiEntry.function("f", List.of(), outputs, "view");
    }
}
