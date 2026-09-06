package org.nmox.studio.rack.service;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The in-memory + env fallback paths of {@link KvasirKeys} — the mode a
 * headless run lands in anyway when no keyring backend exists. The
 * {@code keyringUsable=false} seam makes that deterministic; the {@code env}
 * seam makes the environment path hermetic, so these tests never touch the
 * OS keychain or the real process environment.
 */
class KvasirKeysTest {

    @BeforeEach
    void forceFallbacks() {
        KvasirKeys.keyringUsable = false;
        KvasirKeys.delete();
        KvasirKeys.env = name -> null; // no env keys unless a test adds them
    }

    @AfterEach
    void restore() {
        KvasirKeys.delete();
        KvasirKeys.env = System::getenv;
    }

    @Test
    @DisplayName("v2.95.0: a key stored under the pre-rename name is read and moved under the new one")
    void legacyKeyMigrates() {
        KvasirKeys.seedLegacyForTest("sk-legacy-1234".toCharArray());
        assertThat(KvasirKeys.read()).as("the old entry answers").isEqualTo("sk-legacy-1234".toCharArray());
        assertThat(KvasirKeys.legacyPresentForTest()).as("moved, not copied").isFalse();
        assertThat(KvasirKeys.read()).as("the new entry answers from now on").isEqualTo("sk-legacy-1234".toCharArray());
        KvasirKeys.delete();
        assertThat(KvasirKeys.read()).isNull();
    }

    @Test
    @DisplayName("save/read round-trips through the in-memory fallback")
    void saveReadRoundTrip() {
        KvasirKeys.save("sk-abc".toCharArray());
        assertThat(KvasirKeys.read()).isEqualTo("sk-abc".toCharArray());
        assertThat(KvasirKeys.hasKey()).isTrue();
    }

    @Test
    @DisplayName("read is null with no stored key and no env key")
    void nullWhenNothing() {
        assertThat(KvasirKeys.read()).isNull();
        assertThat(KvasirKeys.hasKey()).isFalse();
    }

    @Test
    @DisplayName("saving null or empty is a delete")
    void saveEmptyDeletes() {
        KvasirKeys.save("was-here".toCharArray());
        KvasirKeys.save(new char[0]);
        assertThat(KvasirKeys.read()).isNull();
    }

    @Test
    @DisplayName("the store keeps its own copy: wiping the caller array changes nothing")
    void defensiveCopy() {
        char[] mine = "keepsafe".toCharArray();
        KvasirKeys.save(mine);
        java.util.Arrays.fill(mine, 'x');
        assertThat(KvasirKeys.read()).isEqualTo("keepsafe".toCharArray());
    }

    // ---- the env fallback chain (the live-E2E path) ------------------------

    @Test
    @DisplayName("ANTHROPIC_API_KEY is honored when no key is stored")
    void anthropicEnvFallback() {
        KvasirKeys.env = Map.of("ANTHROPIC_API_KEY", "sk-anthropic")::get;
        assertThat(KvasirKeys.read()).isEqualTo("sk-anthropic".toCharArray());
    }

    @Test
    @DisplayName("CLAUDE_API_KEY is honored when ANTHROPIC_API_KEY is absent")
    void claudeEnvFallback() {
        KvasirKeys.env = Map.of("CLAUDE_API_KEY", "sk-claude")::get;
        assertThat(KvasirKeys.read()).isEqualTo("sk-claude".toCharArray());
    }

    @Test
    @DisplayName("ANTHROPIC_API_KEY wins over CLAUDE_API_KEY (resolution order)")
    void envOrder() {
        KvasirKeys.env = Map.of(
                "ANTHROPIC_API_KEY", "sk-anthropic",
                "CLAUDE_API_KEY", "sk-claude")::get;
        assertThat(KvasirKeys.read()).isEqualTo("sk-anthropic".toCharArray());
    }

    @Test
    @DisplayName("a stored key wins over both env vars")
    void storedWinsOverEnv() {
        KvasirKeys.env = Map.of("ANTHROPIC_API_KEY", "sk-env")::get;
        KvasirKeys.save("sk-stored".toCharArray());
        assertThat(KvasirKeys.read()).isEqualTo("sk-stored".toCharArray());
    }

    @Test
    @DisplayName("a blank env value is ignored, not returned as an empty key")
    void blankEnvIgnored() {
        KvasirKeys.env = name -> "ANTHROPIC_API_KEY".equals(name) ? "   " : null;
        assertThat(KvasirKeys.read()).isNull();
    }
}
