package org.nmox.studio.web3.search;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.web3.model.ContractArtifact;
import org.nmox.studio.web3.model.DeploymentRecord;
import org.nmox.studio.web3.search.Web3SearchIndex.Hit;
import org.nmox.studio.web3.search.Web3SearchIndex.Kind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Quick Search provider's index plumbing: Contract Studio
 * publishes its artifacts plus the address book, the provider searches
 * exactly that snapshot — a search keystroke never scans the disk or
 * talks to a node.
 */
class Web3SearchProviderTest {

    private static final ContractArtifact COUNTER = new ContractArtifact(
            "Counter", "src/Counter.sol", List.of(), "0xdeadbeef", "0xdead");
    private static final DeploymentRecord DEPLOYED = new DeploymentRecord(
            "Counter", "0x5FbDB2315678afecb367f032d93F642f64180aa3",
            "Local (anvil)", "0xtx1", 1, 1_000L);

    @AfterEach
    void forgetSnapshot() {
        Web3SearchProvider.reset();
    }

    @Test
    @DisplayName("a published snapshot answers contract queries")
    void publishedContracts() {
        Web3SearchProvider.publish(List.of(COUNTER), List.of());

        List<Hit> hits = Web3SearchProvider.currentIndex().matches("counter");

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).kind()).isEqualTo(Kind.CONTRACT);
        assertThat(hits.get(0).contractName()).isEqualTo("Counter");
    }

    @Test
    @DisplayName("deployments surface as address-book hits carrying their record")
    void publishedDeployments() {
        Web3SearchProvider.publish(List.of(), List.of(DEPLOYED));

        List<Hit> hits = Web3SearchProvider.currentIndex().matches("anvil");

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).kind()).isEqualTo(Kind.DEPLOYMENT);
        assertThat(hits.get(0).deployment()).isEqualTo(DEPLOYED);
    }

    @Test
    @DisplayName("re-publishing replaces the snapshot entirely")
    void republishReplaces() {
        Web3SearchProvider.publish(List.of(COUNTER), List.of());
        Web3SearchProvider.publish(List.of(), List.of(DEPLOYED));

        assertThat(Web3SearchProvider.currentIndex().matches("src/")).isEmpty();
        assertThat(Web3SearchProvider.currentIndex().matches("anvil")).isNotEmpty();
    }

    @Test
    @DisplayName("before any publish, the provider still yields a usable (disk-backed) index")
    void unpublishedFallsBack() {
        Web3SearchProvider.reset();

        // contents depend on the machine's workspace file; the contract under
        // test is "never null, never throws" — search stays usable pre-open
        assertThat(Web3SearchProvider.currentIndex()).isNotNull();
        assertThat(Web3SearchProvider.currentIndex().matches("")).isEmpty();
    }
}
