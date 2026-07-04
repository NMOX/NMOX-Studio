package org.nmox.studio.web3.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.nmox.studio.web3.engine.ContractSizeCheck;
import org.nmox.studio.web3.engine.DisplayValues;
import org.nmox.studio.web3.model.ContractArtifact;
import org.nmox.studio.web3.model.DeploymentRecord;

/**
 * The pure matcher behind Contract Studio's Quick Search (⌘I) reach:
 * contracts match on name and label with their EIP-170 headroom
 * ("Counter — contract, 4.9% of size limit"); deployments match on
 * contract name, address, or network and label as address-book lines
 * ("Counter @ 0x5FbDB231…0aa3 — Local (anvil)"). Case-insensitive
 * substring, contract hits before deployment hits, input order kept
 * within each group.
 *
 * <p>Deliberately UI-free — the QuickSearch SPI provider wraps this
 * class (the DbSearchIndex idiom); instances are immutable snapshots,
 * safe to share across threads.
 */
public final class Web3SearchIndex {

    /** What a hit points at. */
    public enum Kind { CONTRACT, DEPLOYMENT }

    /**
     * One search hit.
     *
     * @param kind         contract or deployment
     * @param label        ready-to-display text
     * @param contractName the artifact name behind the hit
     * @param deployment   the matched deployment; null for contract hits
     */
    public record Hit(Kind kind, String label, String contractName,
            DeploymentRecord deployment) {
    }

    private final List<ContractArtifact> artifacts;
    private final List<DeploymentRecord> deployments;

    /**
     * @param artifacts   the scanned artifacts, in display order
     * @param deployments the address book, newest first; may be null
     */
    public Web3SearchIndex(List<ContractArtifact> artifacts,
            List<DeploymentRecord> deployments) {
        this.artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        this.deployments = deployments == null ? List.of() : List.copyOf(deployments);
    }

    /**
     * Case-insensitive substring match; blank queries match nothing.
     * Contract hits first, then deployment hits.
     */
    public List<Hit> matches(String query) {
        List<Hit> hits = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return hits;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        for (ContractArtifact artifact : artifacts) {
            if (contains(artifact.name(), needle)) {
                hits.add(new Hit(Kind.CONTRACT, contractLabel(artifact),
                        artifact.name(), null));
            }
        }
        for (DeploymentRecord deployment : deployments) {
            if (contains(deployment.contractName(), needle)
                    || contains(deployment.address(), needle)
                    || contains(deployment.networkName(), needle)) {
                hits.add(new Hit(Kind.DEPLOYMENT, deploymentLabel(deployment),
                        deployment.contractName(), deployment));
            }
        }
        return hits;
    }

    /** "Counter — contract, 4.9% of size limit" (or "… OVER the size limit (101.2%)"). */
    static String contractLabel(ContractArtifact artifact) {
        ContractSizeCheck.Verdict verdict = ContractSizeCheck.check(artifact);
        String pct = String.format(Locale.ROOT, "%.1f%%", verdict.pct());
        if (verdict.over()) {
            return artifact.name() + " — contract, OVER the size limit (" + pct + ")";
        }
        return artifact.name() + " — contract, " + pct + " of size limit";
    }

    /** "Counter @ 0x5FbDB231…0aa3 — Local (anvil)". */
    static String deploymentLabel(DeploymentRecord deployment) {
        return deployment.contractName() + " @ "
                + DisplayValues.shortAddress(deployment.address())
                + " — " + deployment.networkName();
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null
                && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }
}
