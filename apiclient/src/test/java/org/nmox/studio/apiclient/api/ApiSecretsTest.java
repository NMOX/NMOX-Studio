package org.nmox.studio.apiclient.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ApiSecrets is the only door to an API Studio auth token (v1.97.0);
 * the {@code .nmoxapi.json} file never carries one. These tests drive
 * the in-memory fallback (no OS keychain in CI) via the
 * {@code keyringUsable} seam, exactly as PasswordsTest does for DB
 * Studio — proving save/read/delete round-trip and that a blank token
 * is a delete, without ever touching the real keychain.
 */
class ApiSecretsTest {

    private boolean original;

    @BeforeEach
    void forceFallback() {
        original = ApiSecrets.keyringUsable;
        ApiSecrets.keyringUsable = false; // never touch the OS keychain in tests
    }

    @AfterEach
    void restore() {
        ApiSecrets.delete("req-1");
        ApiSecrets.delete("req-2");
        ApiSecrets.keyringUsable = original;
    }

    @Test
    @DisplayName("A saved token reads back; a different request has none")
    void saveAndRead() {
        ApiSecrets.save("req-1", "Bearer abc123");
        assertThat(ApiSecrets.read("req-1")).isEqualTo("Bearer abc123");
        assertThat(ApiSecrets.read("req-2")).as("unset request has no secret").isEmpty();
        assertThat(ApiSecrets.read(null)).isEmpty();
    }

    @Test
    @DisplayName("A blank or null token is a delete; delete removes the secret")
    void blankIsDelete() {
        ApiSecrets.save("req-1", "tok");
        ApiSecrets.save("req-1", "");
        assertThat(ApiSecrets.read("req-1")).as("blank save clears").isEmpty();

        ApiSecrets.save("req-2", "tok2");
        ApiSecrets.save("req-2", null);
        assertThat(ApiSecrets.read("req-2")).as("null save clears").isEmpty();

        ApiSecrets.save("req-1", "again");
        ApiSecrets.delete("req-1");
        assertThat(ApiSecrets.read("req-1")).isEmpty();
    }

    @Test
    @DisplayName("null request ids are ignored everywhere, never a crash")
    void nullIdsSafe() {
        ApiSecrets.save(null, "tok");
        ApiSecrets.delete(null);
        assertThat(ApiSecrets.read(null)).isEmpty();
    }

    @Test
    @DisplayName("degrading flips the seam exactly once and warns without throwing (headless)")
    void degradeFlipsSeamOnceAndStaysQuiet() throws Exception {
        // degrade() is the seam's own flip switch; it never touches the
        // keyring itself, so invoking it directly (reflection — it is
        // deliberately private) exercises the degradation path without
        // going anywhere near a real keychain backend
        var degrade = ApiSecrets.class.getDeclaredMethod("degrade", Throwable.class);
        degrade.setAccessible(true);
        try {
            ApiSecrets.keyringUsable = true; // simulate the first-ever failure
            degrade.invoke(null, new IllegalStateException("no backend (test)"));
            assertThat(ApiSecrets.keyringUsable)
                    .as("first failure flips the probe off").isFalse();

            degrade.invoke(null, new IllegalStateException("again"));
            assertThat(ApiSecrets.keyringUsable)
                    .as("later failures stay degraded, quietly").isFalse();
        } finally {
            ApiSecrets.keyringUsable = false; // the posture every other test forces
        }
    }
}
