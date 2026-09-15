package org.nmox.studio.web3.model;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AbiParam's canonical rendering and its components list's immutability. */
class AbiParamTest {

    @Test
    @DisplayName("scalars canonicalize as before; tuples render as parenthesized member lists")
    void canonicalTypes() {
        assertThat(AbiParam.of("x", "uint").canonicalType()).isEqualTo("uint256");
        assertThat(AbiParam.of("x", "int[3]").canonicalType()).isEqualTo("int256[3]");
        AbiParam nested = AbiParam.tuple("p", "tuple[][2]", List.of(
                AbiParam.of("a", "uint"),
                AbiParam.tuple("b", "tuple", List.of(AbiParam.of("c", "bytes32[]")))));
        assertThat(nested.canonicalType()).isEqualTo("(uint256,(bytes32[]))[][2]");
        assertThat(new AbiParam("n", null, false).canonicalType()).isEmpty();
    }

    @Test
    @DisplayName("components are copied and immutable; null means none")
    void componentsImmutable() {
        List<AbiParam> members = new ArrayList<>(List.of(AbiParam.of("a", "bool")));
        AbiParam p = AbiParam.tuple("p", "tuple", members);
        members.add(AbiParam.of("sneaky", "bool"));
        assertThat(p.components()).hasSize(1);
        assertThatThrownBy(() -> p.components().add(AbiParam.of("x", "bool")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(new AbiParam("a", "uint8", true, null).components()).isEmpty();
    }
}
