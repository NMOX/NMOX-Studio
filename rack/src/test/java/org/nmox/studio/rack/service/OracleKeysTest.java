package org.nmox.studio.rack.service;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The in-memory + env fallback paths of {@link OracleKeys} — the mode a
 * headless run lands in anyway when no keyring backend exists. The
 * {@code keyringUsable=false} seam makes that deterministic; the {@code env}
 * seam makes the environment path hermetic, so these tests never touch the
 * OS keychain or the real process environment.
 */
class OracleKeysTest {

    @BeforeEach
    void forceFallbacks() {
        OracleKeys.keyringUsable = false;
        OracleKeys.delete();
        OracleKeys.env = name -> null; // no env keys unless a test adds them
    }

    @AfterEach
    void restore() {
        OracleKeys.delete();
        OracleKeys.env = System::getenv;
    }

    @Test
    @DisplayName("save/read round-trips through the in-memory fallback")
    void saveReadRoundTrip() {
        OracleKeys.save("sk-abc".toCharArray());
        assertThat(OracleKeys.read()).isEqualTo("sk-abc".toCharArray());
        assertThat(OracleKeys.hasKey()).isTrue();
    }

    @Test
    @DisplayName("read is null with no stored key and no env key")
    void nullWhenNothing() {
        assertThat(OracleKeys.read()).isNull();
        assertThat(OracleKeys.hasKey()).isFalse();
    }

    @Test
    @DisplayName("saving null or empty is a delete")
    void saveEmptyDeletes() {
        OracleKeys.save("was-here".toCharArray());
        OracleKeys.save(new char[0]);
        assertThat(OracleKeys.read()).isNull();
    }

    @Test
    @DisplayName("the store keeps its own copy: wiping the caller array changes nothing")
    void defensiveCopy() {
        char[] mine = "keepsafe".toCharArray();
        OracleKeys.save(mine);
        java.util.Arrays.fill(mine, 'x');
        assertThat(OracleKeys.read()).isEqualTo("keepsafe".toCharArray());
    }

    // ---- the env fallback chain (the live-E2E path) ------------------------

    @Test
    @DisplayName("ANTHROPIC_API_KEY is honored when no key is stored")
    void anthropicEnvFallback() {
        OracleKeys.env = Map.of("ANTHROPIC_API_KEY", "sk-anthropic")::get;
        assertThat(OracleKeys.read()).isEqualTo("sk-anthropic".toCharArray());
    }

    @Test
    @DisplayName("CLAUDE_API_KEY is honored when ANTHROPIC_API_KEY is absent")
    void claudeEnvFallback() {
        OracleKeys.env = Map.of("CLAUDE_API_KEY", "sk-claude")::get;
        assertThat(OracleKeys.read()).isEqualTo("sk-claude".toCharArray());
    }

    @Test
    @DisplayName("ANTHROPIC_API_KEY wins over CLAUDE_API_KEY (resolution order)")
    void envOrder() {
        OracleKeys.env = Map.of(
                "ANTHROPIC_API_KEY", "sk-anthropic",
                "CLAUDE_API_KEY", "sk-claude")::get;
        assertThat(OracleKeys.read()).isEqualTo("sk-anthropic".toCharArray());
    }

    @Test
    @DisplayName("a stored key wins over both env vars")
    void storedWinsOverEnv() {
        OracleKeys.env = Map.of("ANTHROPIC_API_KEY", "sk-env")::get;
        OracleKeys.save("sk-stored".toCharArray());
        assertThat(OracleKeys.read()).isEqualTo("sk-stored".toCharArray());
    }

    @Test
    @DisplayName("a blank env value is ignored, not returned as an empty key")
    void blankEnvIgnored() {
        OracleKeys.env = name -> "ANTHROPIC_API_KEY".equals(name) ? "   " : null;
        assertThat(OracleKeys.read()).isNull();
    }
}
