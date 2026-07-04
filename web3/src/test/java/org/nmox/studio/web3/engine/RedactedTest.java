package org.nmox.studio.web3.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The URL redactor — the test-pinned half of the "no RPC URL ever
 * reaches a log or an exception" rule. Scheme and host survive;
 * paths, queries and anything that could carry an API key do not.
 */
class RedactedTest {

    @Test
    @DisplayName("an Alchemy-style keyed URL keeps scheme and host only")
    void keyedUrl() {
        assertThat(Redacted.url("https://eth-mainnet.g.alchemy.com/v2/SECRETKEY123"))
                .isEqualTo("https://eth-mainnet.g.alchemy.com");
    }

    @Test
    @DisplayName("query strings are dropped too")
    void queryDropped() {
        assertThat(Redacted.url("https://rpc.example.com/path?apikey=SECRET"))
                .isEqualTo("https://rpc.example.com");
    }

    @Test
    @DisplayName("a local anvil URL redacts to scheme and host (port dropped)")
    void localUrl() {
        assertThat(Redacted.url("http://127.0.0.1:8545"))
                .isEqualTo("http://127.0.0.1");
    }

    @Test
    @DisplayName("null, blank and unparseable input all become the generic label")
    void degenerateInputs() {
        assertThat(Redacted.url(null)).isEqualTo("rpc endpoint");
        assertThat(Redacted.url("")).isEqualTo("rpc endpoint");
        assertThat(Redacted.url("   ")).isEqualTo("rpc endpoint");
        assertThat(Redacted.url("not a url at all")).isEqualTo("rpc endpoint");
        assertThat(Redacted.url("::::")).isEqualTo("rpc endpoint");
    }

    @Test
    @DisplayName("a scheme-less string never echoes back verbatim")
    void noEchoWithoutScheme() {
        assertThat(Redacted.url("example.com/v2/SECRET")).isEqualTo("rpc endpoint");
    }
}
