package org.nmox.studio.ui.irc.engine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IrcSecrets is the only door to a NickServ password (the Keyring-only
 * law). These tests drive the in-memory fallback (no OS keychain in CI)
 * via the {@code keyringUsable} seam, exactly as ApiSecretsTest does for
 * API Studio — proving save/read/delete round-trip and that a blank
 * password is a delete, without ever touching the real keychain.
 */
class IrcSecretsTest {

    private boolean original;

    @BeforeEach
    void forceFallback() {
        original = IrcSecrets.keyringUsable;
        IrcSecrets.keyringUsable = false; // never touch the OS keychain in tests
    }

    @AfterEach
    void restore() {
        IrcSecrets.delete("net-1");
        IrcSecrets.delete("net-2");
        IrcSecrets.keyringUsable = original;
    }

    @Test
    @DisplayName("A saved password reads back; a different network has none")
    void saveAndRead() {
        IrcSecrets.save("net-1", "hunter2");
        assertThat(IrcSecrets.read("net-1")).isEqualTo("hunter2");
        assertThat(IrcSecrets.read("net-2")).as("unset network has no secret").isEmpty();
        assertThat(IrcSecrets.read(null)).isEmpty();
    }

    @Test
    @DisplayName("A blank or null password is a delete; delete removes the secret")
    void blankIsDelete() {
        IrcSecrets.save("net-1", "pw");
        IrcSecrets.save("net-1", "");
        assertThat(IrcSecrets.read("net-1")).as("blank save clears").isEmpty();

        IrcSecrets.save("net-2", "pw2");
        IrcSecrets.save("net-2", null);
        assertThat(IrcSecrets.read("net-2")).as("null save clears").isEmpty();

        IrcSecrets.save("net-1", "again");
        IrcSecrets.delete("net-1");
        assertThat(IrcSecrets.read("net-1")).isEmpty();
    }

    @Test
    @DisplayName("null network names are ignored everywhere, never a crash")
    void nullNamesSafe() {
        IrcSecrets.save(null, "pw");
        IrcSecrets.delete(null);
        assertThat(IrcSecrets.read(null)).isEmpty();
    }

    @Test
    @DisplayName("degrading flips the seam exactly once and warns without throwing (headless)")
    void degradeFlipsSeamOnceAndStaysQuiet() throws Exception {
        var degrade = IrcSecrets.class.getDeclaredMethod("degrade", Throwable.class);
        degrade.setAccessible(true);
        try {
            IrcSecrets.keyringUsable = true; // simulate the first-ever failure
            degrade.invoke(null, new IllegalStateException("no backend (test)"));
            assertThat(IrcSecrets.keyringUsable)
                    .as("first failure flips the probe off").isFalse();

            degrade.invoke(null, new IllegalStateException("again"));
            assertThat(IrcSecrets.keyringUsable)
                    .as("later failures stay degraded, quietly").isFalse();
        } finally {
            IrcSecrets.keyringUsable = false; // the posture every other test forces
        }
    }
}
