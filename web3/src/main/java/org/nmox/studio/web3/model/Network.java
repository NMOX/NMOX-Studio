package org.nmox.studio.web3.model;

/**
 * One EVM network the Studio can talk to.
 *
 * <p><b>The security boundary, part one:</b> a network marked
 * {@code secretUrl} keeps its RPC URL (which may embed an Infura/Alchemy
 * style API key) in the OS keyring via
 * {@code org.nmox.studio.web3.io.RpcSecrets} — <b>{@code plainUrl} must be
 * {@code null} for such a network</b>, and
 * {@code org.nmox.studio.web3.io.Web3WorkspaceIO} additionally refuses to
 * write any {@code url} field for it (belt and braces, test-pinned). Only
 * networks whose URL carries no secret (a localhost anvil, a public
 * gateway) carry it here in the plain. The same law holds for
 * {@code wsUrl}: a secret network never carries one, because a gateway's
 * WebSocket URL embeds the same key.
 *
 * @param name      the user's label, e.g. {@code "Local (anvil)"} — also
 *                  the keyring lookup key for secret networks
 * @param chainId   the EIP-155 chain id (31337 for anvil/hardhat devnets)
 * @param secretUrl true when the RPC URL lives in the OS keyring only
 * @param plainUrl  the RPC URL when it is not a secret; {@code null} when
 *                  {@code secretUrl} is true (the invariant above)
 * @param wsUrl     the network's explicit {@code ws://}/{@code wss://}
 *                  subscription endpoint, or {@code null} to derive one
 *                  (see {@code org.nmox.studio.web3.engine.WatchEndpoint});
 *                  always {@code null} for a secret network
 */
public record Network(String name, int chainId, boolean secretUrl, String plainUrl,
        String wsUrl) {

    /** A network with no explicit subscription endpoint. */
    public Network(String name, int chainId, boolean secretUrl, String plainUrl) {
        this(name, chainId, secretUrl, plainUrl, null);
    }
}
