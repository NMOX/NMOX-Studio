package org.nmox.studio.infra.api;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openide.util.NbPreferences;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The keychain token store's fallback and legacy-migration behavior —
 * the PasswordsTest idiom: the {@code keyringUsable=false} seam makes
 * the degraded path deterministic, keeps these tests off the real OS
 * keychain, and (critically) exercises the rule that a degraded session
 * NEVER removes the legacy plaintext preference — the only durable copy
 * a pre-v1.36 install has.
 *
 * <p>Every test uses its own unique pref key, so parallel/local runs
 * never collide with each other or with a developer's real provider
 * keys ({@code doToken}/{@code hetznerToken}/{@code cloudflareToken}).
 */
class CloudTokensTest {

    private String key;

    @BeforeEach
    void forceInMemoryFallback() {
        CloudTokens.keyringUsable = false;
        key = "unit-test-" + UUID.randomUUID();
    }

    @AfterEach
    void cleanLegacyPrefs() throws Exception {
        var prefs = NbPreferences.root().node("nmox/cloud");
        prefs.remove(key);
        prefs.flush();
    }

    @Test
    @DisplayName("store/read round-trips through the in-memory fallback")
    void storeReadRoundTrip() {
        CloudTokens.store(key, "tok-12345", "test");

        assertThat(CloudTokens.read(key, "test")).isEqualTo("tok-12345");
        assertThat(CloudTokens.readCached(key, "test")).isEqualTo("tok-12345");
    }

    @Test
    @DisplayName("a blank store is a delete")
    void blankStoreDeletes() {
        CloudTokens.store(key, "tok-toremove", "test");
        CloudTokens.store(key, "   ", "test");

        assertThat(CloudTokens.read(key, "test")).isEmpty();
        assertThat(CloudTokens.readCached(key, "test")).isEmpty();
    }

    @Test
    @DisplayName("an unknown key reads as empty, never null, never a crash")
    void unknownKeyIsEmpty() {
        assertThat(CloudTokens.read(key, "test")).isEmpty();
    }

    @Test
    @DisplayName("a legacy plaintext pref is readable — and a degraded session never removes it")
    void degradedReadKeepsLegacyPref() throws Exception {
        var prefs = NbPreferences.root().node("nmox/cloud");
        prefs.put(key, "legacy-token");
        prefs.flush();

        assertThat(CloudTokens.read(key, "test"))
                .as("the old pref still works").isEqualTo("legacy-token");
        assertThat(prefs.get(key, ""))
                .as("degraded mode must not destroy the only durable copy")
                .isEqualTo("legacy-token");
    }

    @Test
    @DisplayName("deleting a token also clears the legacy pref so it can't resurrect")
    void deleteClearsLegacyPref() throws Exception {
        var prefs = NbPreferences.root().node("nmox/cloud");
        prefs.put(key, "legacy-token");
        prefs.flush();

        CloudTokens.store(key, "", "test");

        assertThat(prefs.get(key, "")).isEmpty();
        assertThat(CloudTokens.read(key, "test")).isEmpty();
    }

    @Test
    @DisplayName("readCached serves stores made after the first read (the cache follows writes)")
    void cacheFollowsWrites() {
        assertThat(CloudTokens.readCached(key, "test")).isEmpty();
        CloudTokens.store(key, "tok-after", "test");
        assertThat(CloudTokens.readCached(key, "test")).isEqualTo("tok-after");
    }
}
