package org.nmox.studio.web3.model;

/**
 * One line of the deployment address book: which contract landed at
 * which address on which network. Appended on every successful deploy;
 * {@code Web3WorkspaceIO} persists the newest 200.
 *
 * @param contractName    the artifact name, e.g. {@code Counter}
 * @param address         the deployed contract address, 0x-prefixed
 * @param networkName     the {@link Network#name()} it was deployed on
 * @param txHash          the deployment transaction hash
 * @param blockNumber     the block the deployment landed in
 * @param timestampMillis wall-clock time of the deploy, epoch millis
 */
public record DeploymentRecord(
        String contractName,
        String address,
        String networkName,
        String txHash,
        long blockNumber,
        long timestampMillis) {
}
