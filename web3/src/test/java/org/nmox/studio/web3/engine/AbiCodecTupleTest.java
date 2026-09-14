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
 * Tuples/structs through the codec: canonical signatures, head/tail
 * encoding with dynamic members, tuple arrays, nesting, decoded returns,
 * events, and the refusals that name a component and its position.
 *
 * <p>Every expected hex below was produced independently by Foundry's
 * {@code cast} (1.8.1), with the command written beside the fixture, so
 * the encoder is checked against a second implementation rather than
 * against itself.
 */
class AbiCodecTupleTest {

    private static final String AA = "0x00000000000000000000000000000000000000aa";
    private static final String BB = "0x00000000000000000000000000000000000000bb";

    private static AbiParam uint(String name) {
        return AbiParam.of(name, "uint256");
    }

    private static AbiParam addr(String name) {
        return AbiParam.of(name, "address");
    }

    private static AbiEntry fn(String name, AbiParam... inputs) {
        return AbiEntry.function(name, List.of(inputs), List.of(), "nonpayable");
    }

    // ---- signatures ------------------------------------------------------

    @Test
    @DisplayName("Uniswap V3 exactInputSingle hashes to its published selector 0x414bf389")
    void uniswapSelector() {
        AbiEntry exactInputSingle = fn("exactInputSingle", AbiParam.tuple("params", "tuple",
                List.of(addr("tokenIn"), addr("tokenOut"), AbiParam.of("fee", "uint24"),
                        addr("recipient"), uint("deadline"), uint("amountIn"),
                        uint("amountOutMinimum"), AbiParam.of("sqrtPriceLimitX96", "uint160"))));
        assertThat(exactInputSingle.signature()).isEqualTo(
                "exactInputSingle((address,address,uint24,address,uint256,uint256,uint256,uint160))");
        // cast sig "exactInputSingle((address,address,uint24,address,uint256,uint256,uint256,uint160))"
        assertThat(Hex.toHex(Keccak256.selector(exactInputSingle.signature()))).isEqualTo("414bf389");
    }

    @Test
    @DisplayName("a tuple array renders as its member list plus the suffix; aliases canonicalize inside")
    void tupleArraySignature() {
        AbiEntry f = fn("f",
                AbiParam.tuple("orders", "tuple[]", List.of(AbiParam.of("n", "uint"), addr("to"))),
                AbiParam.of("data", "bytes"));
        assertThat(f.signature()).isEqualTo("f((uint256,address)[],bytes)");
    }

    // ---- encoding --------------------------------------------------------

    @Test
    @DisplayName("a static tuple lays out inline — f((uint256,address))")
    void staticTuple() {
        AbiEntry f = fn("f", AbiParam.tuple("p", "tuple", List.of(uint("n"), addr("to"))));
        // cast calldata "f((uint256,address))" "(69,0x…aa)"
        assertThat(AbiCodec.encodeCall(f, List.of("[69, \"" + AA + "\"]"))).isEqualTo(
                "0x31e3e7da"
                + "0000000000000000000000000000000000000000000000000000000000000045"
                + "00000000000000000000000000000000000000000000000000000000000000aa");
    }

    @Test
    @DisplayName("a tuple with a dynamic member goes to the tail — f((string,uint256),bytes)")
    void dynamicTuple() {
        AbiEntry f = fn("f",
                AbiParam.tuple("p", "tuple", List.of(AbiParam.of("label", "string"), uint("n"))),
                AbiParam.of("data", "bytes"));
        // cast calldata "f((string,uint256),bytes)" '("hi",7)' 0xdeadbeef
        assertThat(AbiCodec.encodeCall(f, List.of("[\"hi\", \"7\"]", "0xdeadbeef"))).isEqualTo(
                "0x976c284f"
                + "0000000000000000000000000000000000000000000000000000000000000040"
                + "00000000000000000000000000000000000000000000000000000000000000c0"
                + "0000000000000000000000000000000000000000000000000000000000000040"
                + "0000000000000000000000000000000000000000000000000000000000000007"
                + "0000000000000000000000000000000000000000000000000000000000000002"
                + "6869000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000004"
                + "deadbeef00000000000000000000000000000000000000000000000000000000");
    }

    @Test
    @DisplayName("a tuple array — f((uint256,address)[],bytes)")
    void tupleArray() {
        AbiEntry f = fn("f",
                AbiParam.tuple("orders", "tuple[]", List.of(uint("n"), addr("to"))),
                AbiParam.of("data", "bytes"));
        // cast calldata "f((uint256,address)[],bytes)" "[(1,0x…aa),(2,0x…bb)]" 0x01
        assertThat(AbiCodec.encodeCall(f, List.of(
                "[[1, \"" + AA + "\"], [2, " + BB + "]]", "0x01"))).isEqualTo(
                "0x6c218d15"
                + "0000000000000000000000000000000000000000000000000000000000000040"
                + "00000000000000000000000000000000000000000000000000000000000000e0"
                + "0000000000000000000000000000000000000000000000000000000000000002"
                + "0000000000000000000000000000000000000000000000000000000000000001"
                + "00000000000000000000000000000000000000000000000000000000000000aa"
                + "0000000000000000000000000000000000000000000000000000000000000002"
                + "00000000000000000000000000000000000000000000000000000000000000bb"
                + "0000000000000000000000000000000000000000000000000000000000000001"
                + "0100000000000000000000000000000000000000000000000000000000000000");
    }

    @Test
    @DisplayName("a nested tuple array of dynamic tuples — f((uint256,(string,bool)[]),uint8)")
    void nestedTuples() {
        AbiEntry f = fn("f",
                AbiParam.tuple("outer", "tuple", List.of(uint("n"),
                        AbiParam.tuple("items", "tuple[]", List.of(
                                AbiParam.of("tag", "string"), AbiParam.of("on", "bool"))))),
                AbiParam.of("k", "uint8"));
        // cast calldata "f((uint256,(string,bool)[]),uint8)" '(5,[("a",true),("bc",false)])' 9
        assertThat(AbiCodec.encodeCall(f, List.of(
                "[5, [[\"a\", true], [\"bc\", false]]]", "9"))).isEqualTo(
                "0x288384bc"
                + "0000000000000000000000000000000000000000000000000000000000000040"
                + "0000000000000000000000000000000000000000000000000000000000000009"
                + "0000000000000000000000000000000000000000000000000000000000000005"
                + "0000000000000000000000000000000000000000000000000000000000000040"
                + "0000000000000000000000000000000000000000000000000000000000000002"
                + "0000000000000000000000000000000000000000000000000000000000000040"
                + "00000000000000000000000000000000000000000000000000000000000000c0"
                + "0000000000000000000000000000000000000000000000000000000000000040"
                + "0000000000000000000000000000000000000000000000000000000000000001"
                + "0000000000000000000000000000000000000000000000000000000000000001"
                + "6100000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000040"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000002"
                + "6263000000000000000000000000000000000000000000000000000000000000");
    }

    @Test
    @DisplayName("a fixed array of dynamic tuples is itself dynamic — f((string,uint256)[2])")
    void fixedArrayOfDynamicTuples() {
        AbiEntry f = fn("f", AbiParam.tuple("pair", "tuple[2]",
                List.of(AbiParam.of("s", "string"), uint("n"))));
        // cast calldata "f((string,uint256)[2])" '[("x",1),("y",2)]'
        assertThat(AbiCodec.encodeCall(f, List.of("[[\"x\", 1], [\"y\", 2]]"))).isEqualTo(
                "0x1ecbcf96"
                + "0000000000000000000000000000000000000000000000000000000000000020"
                + "0000000000000000000000000000000000000000000000000000000000000040"
                + "00000000000000000000000000000000000000000000000000000000000000c0"
                + "0000000000000000000000000000000000000000000000000000000000000040"
                + "0000000000000000000000000000000000000000000000000000000000000001"
                + "0000000000000000000000000000000000000000000000000000000000000001"
                + "7800000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000040"
                + "0000000000000000000000000000000000000000000000000000000000000002"
                + "0000000000000000000000000000000000000000000000000000000000000001"
                + "7900000000000000000000000000000000000000000000000000000000000000");
    }

    // ---- decoding --------------------------------------------------------

    @Test
    @DisplayName("tuple returns render with component names — r() returns ((string,uint256)[],(address,bool))")
    void tupleReturns() {
        AbiEntry r = AbiEntry.function("r", List.of(), List.of(
                AbiParam.tuple("entries", "tuple[]", List.of(AbiParam.of("label", "string"), uint("n"))),
                AbiParam.tuple("", "tuple", List.of(addr("who"), AbiParam.of("", "bool")))), "view");
        // cast abi-encode "r((string,uint256)[],(address,bool))" '[("hi",7),("yo",8)]' '(0x…cc,true)'
        String data = "0x"
                + "0000000000000000000000000000000000000000000000000000000000000060"
                + "00000000000000000000000000000000000000000000000000000000000000cc"
                + "0000000000000000000000000000000000000000000000000000000000000001"
                + "0000000000000000000000000000000000000000000000000000000000000002"
                + "0000000000000000000000000000000000000000000000000000000000000040"
                + "00000000000000000000000000000000000000000000000000000000000000c0"
                + "0000000000000000000000000000000000000000000000000000000000000040"
                + "0000000000000000000000000000000000000000000000000000000000000007"
                + "0000000000000000000000000000000000000000000000000000000000000002"
                + "6869000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000040"
                + "0000000000000000000000000000000000000000000000000000000000000008"
                + "0000000000000000000000000000000000000000000000000000000000000002"
                + "796f000000000000000000000000000000000000000000000000000000000000";
        assertThat(AbiCodec.decodeReturn(r, data)).containsExactly(
                "[(label: hi, n: 7), (label: yo, n: 8)]",
                "(who: 0x00000000000000000000000000000000000000cc, true)");
    }

    @Test
    @DisplayName("encode → decodeCallInput round-trips a nested tuple argument")
    void callInputRoundTrip() {
        AbiEntry f = fn("f",
                AbiParam.tuple("outer", "tuple", List.of(uint("n"),
                        AbiParam.tuple("items", "tuple[]", List.of(
                                AbiParam.of("tag", "string"), AbiParam.of("on", "bool"))))),
                AbiParam.of("k", "uint8"));
        String input = AbiCodec.encodeCall(f, List.of("[5, [[\"a\", true], [\"bc\", false]]]", "9"));
        assertThat(AbiCodec.decodeCallInput(List.of(f), input))
                .isEqualTo("f((n: 5, items: [(tag: a, on: true), (tag: bc, on: false)]), 9)");
    }

    @Test
    @DisplayName("events: a non-indexed tuple decodes; an indexed tuple or static array is only its hash")
    void tupleEvents() {
        AbiEntry event = AbiEntry.event("Filled", List.of(
                new AbiParam("order", "tuple", true, List.of(uint("n"), addr("to"))),
                new AbiParam("pair", "uint256[2]", true),
                new AbiParam("fill", "tuple", false, List.of(uint("n"), addr("to")))));
        String orderHash = "0x" + "ab".repeat(32);
        String pairHash = "0x" + "cd".repeat(32);
        Map<String, String> decoded = AbiCodec.decodeEventLog(event,
                List.of("0x" + "11".repeat(32), orderHash, pairHash),
                "0x" + "00".repeat(31) + "2a" + "00".repeat(12) + "bb".repeat(20));
        assertThat(decoded).containsExactly(
                Map.entry("order", "hash:" + orderHash),
                Map.entry("pair", "hash:" + pairHash),
                Map.entry("fill", "(n: 42, to: 0x" + "bb".repeat(20) + ")"));
    }

    @Test
    @DisplayName("a dynamic array count the data cannot hold is refused, not allocated")
    void hostileCountRefused() {
        AbiEntry r = AbiEntry.function("r", List.of(),
                List.of(AbiParam.of("", "uint256[]")), "view");
        String data = "0x" + "00".repeat(31) + "20" + "00".repeat(28) + "7fffffff";
        assertThatThrownBy(() -> AbiCodec.decodeReturn(r, data))
                .hasMessageContaining("impossible offset/length (2147483647)");
    }

    @Test
    @DisplayName("nested arrays sharing one inner tail cannot multiply past the value budget")
    void decodeBudgetRefused() {
        // uint256[][]: 400 outer entries all pointing at the same 400-element
        // inner array — 25 KB of data asking for 160,000 values
        int n = 400;
        StringBuilder data = new StringBuilder("0x");
        data.append(word(0x20)).append(word(n));
        for (int i = 0; i < n; i++) {
            data.append(word(n * 32));
        }
        data.append(word(n));
        for (int i = 0; i < n; i++) {
            data.append(word(i));
        }
        AbiEntry r = AbiEntry.function("r", List.of(),
                List.of(AbiParam.of("", "uint256[][]")), "view");
        assertThatThrownBy(() -> AbiCodec.decodeReturn(r, data.toString()))
                .hasMessageContaining("more than " + AbiCodec.MAX_DECODED_VALUES + " values");
    }

    // ---- the literal: shapes and refusals --------------------------------

    @Test
    @DisplayName("the field shape names each component in order")
    void inputShapes() {
        List<AbiParam> members = List.of(addr("recipient"), uint("amount"));
        assertThat(AbiCodec.inputShape(AbiParam.tuple("p", "tuple", members)))
                .isEqualTo("[recipient: address, amount: uint256]");
        assertThat(AbiCodec.inputShape(AbiParam.tuple("p", "tuple[]", members)))
                .isEqualTo("[[recipient: address, amount: uint256], …]");
        assertThat(AbiCodec.inputShape(AbiParam.tuple("p", "tuple[2]", members)))
                .isEqualTo("[[recipient: address, amount: uint256] × 2]");
        assertThat(AbiCodec.inputShape(AbiParam.tuple("p", "tuple", List.of(
                AbiParam.of("", "uint"), AbiParam.tuple("inner", "tuple", List.of(
                        AbiParam.of("tags", "string[]")))))))
                .isEqualTo("[uint256, inner: [tags: string[]]]");
        assertThat(AbiCodec.inputShape(AbiParam.of("n", "uint[]"))).isEqualTo("uint[]");
        assertThat(AbiCodec.inputShape(AbiParam.of("c", "tuple"))).isEqualTo("tuple");
    }

    @Test
    @DisplayName("a component's bad value names the component and its position")
    void componentRefusalNamesComponent() {
        AbiEntry f = fn("f", AbiParam.tuple("order", "tuple", List.of(addr("to"), uint("amount"))));
        assertThatThrownBy(() -> AbiCodec.encodeCall(f, List.of("[\"" + AA + "\", \"-1\"]")))
                .hasMessage("Parameter 'order'.amount#2 is out of range for uint256 (0 to 2^256-1).");
        AbiEntry unnamed = fn("f", AbiParam.tuple("order", "tuple",
                List.of(addr(""), AbiParam.of("", "bool"))));
        assertThatThrownBy(() -> AbiCodec.encodeCall(unnamed, List.of("[\"" + AA + "\", yes]")))
                .hasMessage("Parameter 'order'.#2 is a bool — write true or false.");
    }

    @Test
    @DisplayName("the money law holds inside a tuple: 1.5 and 1e18 are refused, never truncated")
    void moneyLawInsideTuple() {
        AbiEntry f = fn("f", AbiParam.tuple("order", "tuple", List.of(addr("to"), uint("amount"))));
        assertThatThrownBy(() -> AbiCodec.encodeCall(f, List.of("[\"" + AA + "\", \"1.5\"]")))
                .hasMessageContaining("'order'.amount#2 is a uint256 — '1.5' is not a number");
        assertThatThrownBy(() -> AbiCodec.encodeCall(f, List.of("[\"" + AA + "\", 1e18]")))
                .hasMessageContaining("'1e18' is not a number");
    }

    @Test
    @DisplayName("the wrong component count is refused with the expected shape")
    void componentCountRefused() {
        AbiEntry f = fn("f", AbiParam.tuple("order", "tuple", List.of(addr("to"), uint("amount"))));
        assertThatThrownBy(() -> AbiCodec.encodeCall(f, List.of("[\"" + AA + "\"]")))
                .hasMessage("Parameter 'order' is a tuple of 2 components"
                        + " [to: address, amount: uint256] — got 1 value.");
    }

    @Test
    @DisplayName("a tuple field that is not a list, and a list where one value belongs, are refused")
    void structureMismatchRefused() {
        AbiEntry f = fn("f", AbiParam.tuple("order", "tuple", List.of(addr("to"), uint("amount"))));
        assertThatThrownBy(() -> AbiCodec.encodeCall(f, List.of("0xaa, 5")))
                .hasMessage("Parameter 'order' is a tuple — write its components in order as"
                        + " [to: address, amount: uint256].");
        assertThatThrownBy(() -> AbiCodec.encodeCall(f, List.of("[[1], 5]")))
                .hasMessage("Parameter 'order'.to#1 is an address — expected one value, got a list.");
        AbiEntry g = fn("g", AbiParam.tuple("orders", "tuple[]", List.of(uint("n"))));
        assertThatThrownBy(() -> AbiCodec.encodeCall(g, List.of("[5]")))
                .hasMessage("Parameter 'orders'[0] is a tuple — write its components in order as [n: uint256].");
        assertThatThrownBy(() -> AbiCodec.encodeCall(g, List.of("5")))
                .hasMessage("Parameter 'orders' expects an array like [[n: uint256], …].");
    }

    @Test
    @DisplayName("a string component takes the literal's unescaped text verbatim, quotes and all")
    void stringComponentVerbatim() {
        AbiEntry f = fn("f", AbiParam.tuple("p", "tuple", List.of(AbiParam.of("s", "string"))));
        AbiEntry echo = AbiEntry.function("e", List.of(),
                List.of(AbiParam.tuple("p", "tuple", List.of(AbiParam.of("s", "string")))), "view");
        String calldata = AbiCodec.encodeCall(f, List.of("[\"\\\"quoted\\\"\"]"));
        assertThat(AbiCodec.decodeReturn(echo, "0x" + calldata.substring(10)))
                .containsExactly("(s: \"quoted\")");
    }

    @Test
    @DisplayName("types that cannot be laid out are refused by name before any encoding")
    void unlayoutableTypesRefused() {
        assertThatThrownBy(() -> AbiCodec.encodeArgs(
                List.of(AbiParam.of("m", "uint256" + "[]".repeat(34))), List.of("[]")))
                .hasMessage("Parameter 'm' nests deeper than 32 levels.");
        assertThatThrownBy(() -> AbiCodec.encodeArgs(
                List.of(AbiParam.of("m", "uint256[100001]")), List.of("[]")))
                .hasMessageContaining("more than 100000 elements is refused");
        assertThatThrownBy(() -> AbiCodec.encodeArgs(
                List.of(AbiParam.of("m", "uint256[100000][100000]")), List.of("[]")))
                .hasMessageContaining("too large to lay out");
        assertThatThrownBy(() -> AbiCodec.encodeArgs(
                List.of(AbiParam.of("m", "(uint256,address)")), List.of("[1]")))
                .hasMessage("Parameter 'm' has unsupported type '(uint256,address)'.");
        assertThatThrownBy(() -> AbiCodec.encodeArgs(
                List.of(AbiParam.of("m", "[2]")), List.of("[1, 2]")))
                .hasMessage("Parameter 'm' has unsupported array type '[2]'.");
        assertThatThrownBy(() -> AbiCodec.encodeArgs(
                List.of(AbiParam.of("n", "uint256")), List.of("[1]")))
                .hasMessageContaining("'[1]' is not a number");
    }

    private static String word(int value) {
        String hex = Integer.toHexString(value);
        return "0".repeat(64 - hex.length()) + hex;
    }
}
