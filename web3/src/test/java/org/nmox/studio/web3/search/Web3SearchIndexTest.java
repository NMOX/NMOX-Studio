package org.nmox.studio.web3.search;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.web3.model.ContractArtifact;
import org.nmox.studio.web3.model.DeploymentRecord;
import org.nmox.studio.web3.search.Web3SearchIndex.Hit;
import org.nmox.studio.web3.search.Web3SearchIndex.Kind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pure matcher behind Contract Studio's ⌘I reach: contract labels
 * carry their EIP-170 headroom, deployment labels read as address-book
 * lines, and matching never touches disk or network.
 */
class Web3SearchIndexTest {

    private static final ContractArtifact COUNTER = new ContractArtifact(
            "Counter", "src/Counter.sol", List.of(), "0xdeadbeef", "0xdead");
    private static final ContractArtifact TOKEN = new ContractArtifact(
            "Token", "src/Token.sol", List.of(), "0xcafe", "0xca");

    private static final DeploymentRecord LOCAL_COUNTER = new DeploymentRecord(
            "Counter", "0x5FbDB2315678afecb367f032d93F642f64180aa3",
            "Local (anvil)", "0xtx1", 1, 1_000L);
    private static final DeploymentRecord LOCAL_TOKEN = new DeploymentRecord(
            "Token", "0xe7f1725E7734CE288F8367e1Bb143E90bb3F0512",
            "Staging", "0xtx2", 2, 2_000L);

    @Test
    @DisplayName("a contract hit labels with its size-limit percentage")
    void contractLabelCarriesHeadroom() {
        Web3SearchIndex index = new Web3SearchIndex(List.of(COUNTER), List.of());

        List<Hit> hits = index.matches("coun");

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).kind()).isEqualTo(Kind.CONTRACT);
        assertThat(hits.get(0).label()).isEqualTo("Counter — contract, 0.0% of size limit");
        assertThat(hits.get(0).contractName()).isEqualTo("Counter");
        assertThat(hits.get(0).deployment()).isNull();
    }

    @Test
    @DisplayName("an over-limit contract says so in the label")
    void overLimitContractLabel() {
        String hugeBytecode = "0x" + "00".repeat(24_577); // one byte past EIP-170
        ContractArtifact whale = new ContractArtifact("Whale", "", List.of(),
                "0x00", hugeBytecode);

        List<Hit> hits = new Web3SearchIndex(List.of(whale), List.of()).matches("whale");

        assertThat(hits.get(0).label()).contains("OVER the size limit");
    }

    @Test
    @DisplayName("deployments match on name, address, and network — labelled as address-book lines")
    void deploymentMatching() {
        Web3SearchIndex index = new Web3SearchIndex(List.of(), List.of(LOCAL_COUNTER, LOCAL_TOKEN));

        assertThat(index.matches("counter")).hasSize(1);
        assertThat(index.matches("0x5fbdb2")).hasSize(1); // address, case-insensitive
        assertThat(index.matches("anvil")).hasSize(1);    // network name

        Hit hit = index.matches("anvil").get(0);
        assertThat(hit.kind()).isEqualTo(Kind.DEPLOYMENT);
        assertThat(hit.label()).isEqualTo("Counter @ 0x5FbDB2…0aa3 — Local (anvil)");
        assertThat(hit.deployment()).isEqualTo(LOCAL_COUNTER);
    }

    @Test
    @DisplayName("contract hits come before deployment hits, input order kept")
    void ordering() {
        Web3SearchIndex index = new Web3SearchIndex(
                List.of(COUNTER, TOKEN), List.of(LOCAL_COUNTER, LOCAL_TOKEN));

        List<Hit> hits = index.matches("t"); // matches everything here

        assertThat(hits).extracting(Hit::kind).containsExactly(
                Kind.CONTRACT, Kind.CONTRACT, Kind.DEPLOYMENT, Kind.DEPLOYMENT);
        assertThat(hits).extracting(Hit::contractName)
                .containsExactly("Counter", "Token", "Counter", "Token");
    }

    @Test
    @DisplayName("blank queries match nothing; null lists are empty, not errors")
    void blankAndNullTolerance() {
        Web3SearchIndex index = new Web3SearchIndex(null, null);

        assertThat(index.matches("anything")).isEmpty();
        assertThat(new Web3SearchIndex(List.of(COUNTER), List.of()).matches("  ")).isEmpty();
        assertThat(new Web3SearchIndex(List.of(COUNTER), List.of()).matches(null)).isEmpty();
    }
}
