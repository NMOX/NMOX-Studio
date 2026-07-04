package org.nmox.studio.dbstudio.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The in-memory fallback path of {@link Passwords} — the mode this
 * headless test run would land in anyway when no keyring backend
 * exists. The seam ({@code keyringUsable=false}) makes that
 * deterministic instead of environment-dependent, so these tests never
 * touch (or need) the OS keychain.
 */
class PasswordsTest {

    @BeforeEach
    void forceInMemoryFallback() {
        Passwords.keyringUsable = false;
    }

    @Test
    @DisplayName("save/read round-trips a password through the in-memory fallback")
    void saveReadRoundTrip() {
        Passwords.save("spec-rt", "s3cret!".toCharArray());

        assertThat(Passwords.read("spec-rt")).isEqualTo("s3cret!".toCharArray());
        Passwords.delete("spec-rt");
    }

    @Test
    @DisplayName("read returns null for a spec that never saved a password")
    void readUnknownIsNull() {
        assertThat(Passwords.read("spec-never-saved")).isNull();
    }

    @Test
    @DisplayName("delete removes the stored password; a second delete is harmless")
    void deleteRemoves() {
        Passwords.save("spec-del", "gone".toCharArray());
        Passwords.delete("spec-del");

        assertThat(Passwords.read("spec-del")).isNull();
        Passwords.delete("spec-del"); // no-op, no throw
    }

    @Test
    @DisplayName("saving again overwrites the previous password")
    void saveOverwrites() {
        Passwords.save("spec-ow", "old".toCharArray());
        Passwords.save("spec-ow", "new".toCharArray());

        assertThat(Passwords.read("spec-ow")).isEqualTo("new".toCharArray());
        Passwords.delete("spec-ow");
    }

    @Test
    @DisplayName("saving null is a delete")
    void saveNullDeletes() {
        Passwords.save("spec-null", "was-here".toCharArray());
        Passwords.save("spec-null", null);

        assertThat(Passwords.read("spec-null")).isNull();
    }

    @Test
    @DisplayName("the store keeps its own copy: mutating caller arrays changes nothing")
    void defensiveCopies() {
        char[] mine = "keepsafe".toCharArray();
        Passwords.save("spec-copy", mine);
        java.util.Arrays.fill(mine, 'x'); // caller wipes its array, as it should

        char[] out = Passwords.read("spec-copy");
        assertThat(out).isEqualTo("keepsafe".toCharArray());

        java.util.Arrays.fill(out, 'y'); // and mutating the returned copy...
        assertThat(Passwords.read("spec-copy"))
                .as("...does not corrupt the stored value")
                .isEqualTo("keepsafe".toCharArray());
        Passwords.delete("spec-copy");
    }

    @Test
    @DisplayName("null spec ids are ignored, never a crash")
    void nullSpecIdSafe() {
        Passwords.save(null, "x".toCharArray());
        Passwords.delete(null);
        assertThat(Passwords.read(null)).isNull();
    }
}
