package org.nmox.studio.web3.engine;

import java.net.URI;
import java.util.Locale;

/**
 * Where a network's live subscription lives, derived honestly (ledger 12).
 *
 * <ol>
 * <li>A network that names a {@code ws://} or {@code wss://} endpoint
 * explicitly uses it. A named endpoint that is not one yields nothing —
 * a typo is never quietly replaced by a guess.</li>
 * <li>A loopback HTTP endpoint (anvil, a hardhat node) serves WebSocket on
 * the same host and port, so {@code http://127.0.0.1:8545} becomes
 * {@code ws://127.0.0.1:8545} ({@code https} becomes {@code wss}).</li>
 * <li>Anything else yields nothing and the Watch pane polls. A remote
 * gateway's WebSocket path is its own convention (Infura's {@code /ws/v3/},
 * Alchemy's same path on {@code wss}); guessing one would spend a
 * handshake timeout on every START for a URL nobody gave.</li>
 * </ol>
 */
public final class WatchEndpoint {

    private WatchEndpoint() {
    }

    /**
     * The WebSocket endpoint to subscribe at, or null to poll.
     *
     * @param explicitWsUrl the network's own {@code wsUrl}, may be null
     * @param rpcUrl        the network's resolved HTTP RPC URL, may be null
     */
    public static String wsUrl(String explicitWsUrl, String rpcUrl) {
        if (explicitWsUrl != null && !explicitWsUrl.isBlank()) {
            return isWebSocketUrl(explicitWsUrl) ? explicitWsUrl.trim() : null;
        }
        if (rpcUrl == null || !JsonRpcClient.loopback(rpcUrl)) {
            return null;
        }
        String trimmed = rpcUrl.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://")) {
            return "ws://" + trimmed.substring("http://".length());
        }
        if (lower.startsWith("https://")) {
            return "wss://" + trimmed.substring("https://".length());
        }
        return null;
    }

    /** True for a parseable {@code ws://} or {@code wss://} URL with a host. */
    public static boolean isWebSocketUrl(String url) {
        if (url == null) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            return scheme != null
                    && ("ws".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme))
                    && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (RuntimeException unparseable) {
            return false;
        }
    }
}
