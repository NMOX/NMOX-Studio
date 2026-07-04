package org.nmox.studio.web3.engine;

import java.net.URI;

/**
 * RPC URLs may embed API keys in their path or query
 * (Infura/Alchemy style), so nothing in this module ever logs, throws,
 * or toString()s a full endpoint URL — everything that must mention one
 * goes through {@link #url(String)}, which keeps scheme and host only.
 */
public final class Redacted {

    private Redacted() {
    }

    /**
     * {@code https://eth-mainnet.g.alchemy.com/v2/SECRET} →
     * {@code https://eth-mainnet.g.alchemy.com}. Anything unparseable →
     * {@code "rpc endpoint"} — never the original string.
     */
    public static String url(String url) {
        if (url == null || url.isBlank()) {
            return "rpc endpoint";
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || host.isBlank()) {
                return "rpc endpoint";
            }
            return scheme + "://" + host;
        } catch (RuntimeException unparseable) {
            return "rpc endpoint";
        }
    }
}
