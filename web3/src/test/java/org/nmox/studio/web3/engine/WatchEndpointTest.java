package org.nmox.studio.web3.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Where the live subscription lives — derived honestly, never guessed. */
class WatchEndpointTest {

    @Test
    @DisplayName("a loopback HTTP endpoint subscribes on the same host and port (anvil, hardhat node)")
    void loopbackDerived() {
        assertThat(WatchEndpoint.wsUrl(null, "http://127.0.0.1:8545"))
                .isEqualTo("ws://127.0.0.1:8545");
        assertThat(WatchEndpoint.wsUrl("", "http://localhost:8545/rpc?x=1"))
                .isEqualTo("ws://localhost:8545/rpc?x=1");
        assertThat(WatchEndpoint.wsUrl(null, "HTTPS://[::1]:8545")).isEqualTo("wss://[::1]:8545");
    }

    @Test
    @DisplayName("a remote gateway polls unless it names its own WebSocket endpoint")
    void remoteIsNotGuessed() {
        assertThat(WatchEndpoint.wsUrl(null, "https://eth-mainnet.g.alchemy.com/v2/KEY")).isNull();
        assertThat(WatchEndpoint.wsUrl("  wss://eth-mainnet.g.alchemy.com/v2/KEY ",
                "https://eth-mainnet.g.alchemy.com/v2/KEY"))
                .isEqualTo("wss://eth-mainnet.g.alchemy.com/v2/KEY");
    }

    @Test
    @DisplayName("an explicit endpoint that is not a WebSocket URL yields nothing — a typo is not replaced by a guess")
    void explicitTypoIsNotGuessedAround() {
        assertThat(WatchEndpoint.wsUrl("https://127.0.0.1:8546", "http://127.0.0.1:8545")).isNull();
        assertThat(WatchEndpoint.wsUrl("ws://", "http://127.0.0.1:8545")).isNull();
        assertThat(WatchEndpoint.wsUrl("ws://bad host", "http://127.0.0.1:8545")).isNull();
    }

    @Test
    @DisplayName("no URL, or a loopback URL that is not HTTP, yields nothing")
    void nothingToDerive() {
        assertThat(WatchEndpoint.wsUrl(null, null)).isNull();
        assertThat(WatchEndpoint.wsUrl(null, "ftp://127.0.0.1:21")).isNull();
        assertThat(WatchEndpoint.isWebSocketUrl(null)).isFalse();
        assertThat(WatchEndpoint.isWebSocketUrl("WS://127.0.0.1:1")).isTrue();
    }
}
