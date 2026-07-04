package org.nmox.studio.web3.model;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The AbiEntry family record: canonical signatures (what selectors and
 * topics are hashed from), the kind factories, and immutability.
 */
class AbiEntryTest {

    @Test
    @DisplayName("signature joins the input types: transfer(address,uint256)")
    void basicSignature() {
        AbiEntry transfer = AbiEntry.function("transfer",
                List.of(AbiParam.of("to", "address"), AbiParam.of("amount", "uint256")),
                List.of(AbiParam.of("", "bool")), "nonpayable");
        assertThat(transfer.signature()).isEqualTo("transfer(address,uint256)");
    }

    @Test
    @DisplayName("bare uint and int canonicalize to their 256-bit forms in signatures")
    void aliasCanonicalization() {
        AbiEntry f = AbiEntry.function("f",
                List.of(AbiParam.of("a", "uint"), AbiParam.of("b", "int"),
                        AbiParam.of("c", "uint[]"), AbiParam.of("d", "int[3]")),
                List.of(), "pure");
        assertThat(f.signature()).isEqualTo("f(uint256,int256,uint256[],int256[3])");
    }

    @Test
    @DisplayName("canonicalType leaves already-canonical and unrelated types alone")
    void canonicalTypePassthrough() {
        assertThat(AbiEntry.canonicalType("uint256")).isEqualTo("uint256");
        assertThat(AbiEntry.canonicalType("uint8")).isEqualTo("uint8");
        assertThat(AbiEntry.canonicalType("address")).isEqualTo("address");
        assertThat(AbiEntry.canonicalType("bytes32[]")).isEqualTo("bytes32[]");
        assertThat(AbiEntry.canonicalType(null)).isEmpty();
    }

    @Test
    @DisplayName("a no-argument function signs as name()")
    void noArgSignature() {
        assertThat(AbiEntry.function("number", List.of(), List.of(), "view").signature())
                .isEqualTo("number()");
    }

    @Test
    @DisplayName("the factories stamp the right kinds and defaults")
    void factories() {
        assertThat(AbiEntry.function("f", List.of(), List.of(), "view").kind())
                .isEqualTo(AbiEntry.Kind.FUNCTION);
        AbiEntry event = AbiEntry.event("E", List.of());
        assertThat(event.kind()).isEqualTo(AbiEntry.Kind.EVENT);
        assertThat(event.outputs()).isEmpty();
        AbiEntry ctor = AbiEntry.constructor(List.of(), "payable");
        assertThat(ctor.kind()).isEqualTo(AbiEntry.Kind.CONSTRUCTOR);
        assertThat(ctor.name()).isEmpty();
        assertThat(AbiEntry.error("Err", List.of()).kind()).isEqualTo(AbiEntry.Kind.ERROR);
    }

    @Test
    @DisplayName("readOnly is true for view and pure, false for the rest")
    void readOnlyFlag() {
        assertThat(AbiEntry.function("f", List.of(), List.of(), "view").readOnly()).isTrue();
        assertThat(AbiEntry.function("f", List.of(), List.of(), "pure").readOnly()).isTrue();
        assertThat(AbiEntry.function("f", List.of(), List.of(), "nonpayable").readOnly()).isFalse();
        assertThat(AbiEntry.function("f", List.of(), List.of(), "payable").readOnly()).isFalse();
        assertThat(AbiEntry.event("E", List.of()).readOnly()).isFalse();
    }

    @Test
    @DisplayName("input lists are defensively copied and immutable")
    void immutability() {
        List<AbiParam> mutable = new ArrayList<>();
        mutable.add(AbiParam.of("a", "uint256"));
        AbiEntry f = AbiEntry.function("f", mutable, List.of(), "pure");
        mutable.add(AbiParam.of("sneaky", "bool"));

        assertThat(f.inputs()).hasSize(1);
        assertThatThrownBy(() -> f.inputs().add(AbiParam.of("x", "bool")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
