package org.nmox.studio.web3.io;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The in-memory fallback path of {@link RpcSecrets} — the mode this
 * headless test run would land in anyway when no keyring backend
 * exists. The seam ({@code keyringUsable=false}) makes that
 * deterministic instead of environment-dependent, so these tests never
 * touch (or need) the OS keychain. The Passwords idiom, verbatim.
 */
class RpcSecretsTest {

    @BeforeEach
    void forceInMemoryFallback() {
        RpcSecrets.keyringUsable = false;
    }

    @Test
    @DisplayName("save/read round-trips a secret URL through the in-memory fallback")
    void saveReadRoundTrip() {
        RpcSecrets.save("net-rt", "https://x.io/v3/KEY".toCharArray());

        assertThat(RpcSecrets.read("net-rt"))
                .isEqualTo("https://x.io/v3/KEY".toCharArray());
        RpcSecrets.delete("net-rt");
    }

    @Test
    @DisplayName("read returns null for a network that never saved a URL")
    void readUnknownIsNull() {
        assertThat(RpcSecrets.read("net-never-saved")).isNull();
    }

    @Test
    @DisplayName("delete removes the stored URL; a second delete is harmless")
    void deleteRemoves() {
        RpcSecrets.save("net-del", "gone".toCharArray());
        RpcSecrets.delete("net-del");

        assertThat(RpcSecrets.read("net-del")).isNull();
        RpcSecrets.delete("net-del"); // no-op, no throw
    }

    @Test
    @DisplayName("saving again overwrites the previous URL")
    void saveOverwrites() {
        RpcSecrets.save("net-ow", "old".toCharArray());
        RpcSecrets.save("net-ow", "new".toCharArray());

        assertThat(RpcSecrets.read("net-ow")).isEqualTo("new".toCharArray());
        RpcSecrets.delete("net-ow");
    }

    @Test
    @DisplayName("saving null is a delete")
    void saveNullDeletes() {
        RpcSecrets.save("net-null", "was-here".toCharArray());
        RpcSecrets.save("net-null", null);

        assertThat(RpcSecrets.read("net-null")).isNull();
    }

    @Test
    @DisplayName("the store keeps its own copy: mutating caller arrays changes nothing")
    void defensiveCopies() {
        char[] mine = "keepsafe".toCharArray();
        RpcSecrets.save("net-copy", mine);
        java.util.Arrays.fill(mine, 'x'); // caller wipes its array, as it should

        char[] out = RpcSecrets.read("net-copy");
        assertThat(out).isEqualTo("keepsafe".toCharArray());

        java.util.Arrays.fill(out, 'y'); // and mutating the returned copy...
        assertThat(RpcSecrets.read("net-copy"))
                .as("...does not corrupt the stored value")
                .isEqualTo("keepsafe".toCharArray());
        RpcSecrets.delete("net-copy");
    }

    @Test
    @DisplayName("null network ids are ignored, never a crash")
    void nullIdSafe() {
        RpcSecrets.save(null, "x".toCharArray());
        RpcSecrets.delete(null);
        assertThat(RpcSecrets.read(null)).isNull();
    }
}
