package org.nmox.studio.web3.model;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The artifact record's kind filters and immutability, plus the
 * Network record's documented plainUrl invariant.
 */
class ContractArtifactTest {

    private static ContractArtifact counter() {
        return new ContractArtifact("Counter", "src/Counter.sol", List.of(
                AbiEntry.constructor(List.of(AbiParam.of("start", "uint256")), "nonpayable"),
                AbiEntry.function("increment", List.of(), List.of(), "nonpayable"),
                AbiEntry.function("number", List.of(),
                        List.of(AbiParam.of("", "uint256")), "view"),
                AbiEntry.event("Incremented", List.of(
                        new AbiParam("newValue", "uint256", false))),
                AbiEntry.error("TooBig", List.of(AbiParam.of("value", "uint256")))),
                "0x6080", "0x60806040");
    }

    @Test
    @DisplayName("functions/events/errors/constructor filter the ABI by kind, in order")
    void kindFilters() {
        ContractArtifact artifact = counter();
        assertThat(artifact.functions()).extracting(AbiEntry::name)
                .containsExactly("increment", "number");
        assertThat(artifact.events()).extracting(AbiEntry::name)
                .containsExactly("Incremented");
        assertThat(artifact.errors()).extracting(AbiEntry::name)
                .containsExactly("TooBig");
        assertThat(artifact.constructor()).isPresent();
        assertThat(artifact.constructor().get().inputs()).hasSize(1);
    }

    @Test
    @DisplayName("a contract without a constructor entry has an empty Optional")
    void noConstructor() {
        ContractArtifact bare = new ContractArtifact("Bare", "", List.of(), "0x", "0x");
        assertThat(bare.constructor()).isEmpty();
    }

    @Test
    @DisplayName("the ABI list is defensively copied and immutable")
    void abiImmutable() {
        List<AbiEntry> mutable = new ArrayList<>();
        mutable.add(AbiEntry.function("f", List.of(), List.of(), "view"));
        ContractArtifact artifact = new ContractArtifact("X", "", mutable, "0x", "0x");
        mutable.clear();

        assertThat(artifact.abi()).hasSize(1);
        assertThatThrownBy(() -> artifact.abi().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Network: a plain network carries its URL, a secret one carries null")
    void networkInvariant() {
        Network plain = new Network("Local (anvil)", 31337, false, "http://127.0.0.1:8545");
        assertThat(plain.plainUrl()).isEqualTo("http://127.0.0.1:8545");
        assertThat(plain.secretUrl()).isFalse();

        // the documented invariant: secret networks keep plainUrl null and
        // resolve their URL through RpcSecrets (Web3WorkspaceIO pins the
        // file side of this)
        Network secret = new Network("Mainnet", 1, true, null);
        assertThat(secret.plainUrl()).isNull();
        assertThat(secret.secretUrl()).isTrue();
    }
}
