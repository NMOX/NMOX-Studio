package org.nmox.studio.rack.service;

import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v2.63.0 move of KVASIR's consent and model preferences from the
 * JVM-global java.util.prefs node to the userdir-scoped NbPreferences node:
 * legacy grants are carried over once and removed from the legacy node, and
 * a grant the userdir already holds is never overwritten.
 */
class KvasirPrefsMigrationTest {

    private final Preferences target = Preferences.userRoot().node("nmox-test/migration-target-" + System.nanoTime());
    private final Preferences legacy = Preferences.userRoot().node("nmox-test/migration-legacy-" + System.nanoTime());

    @AfterEach
    void cleanup() throws Exception {
        target.removeNode();
        legacy.removeNode();
    }

    @Test
    @DisplayName("Legacy keys move to the userdir node once and vanish from the legacy node")
    void legacyKeysMoveOnce() throws Exception {
        legacy.putBoolean("kvasir.code.consent", true);
        legacy.put("kvasir.ask.model", "claude-sonnet-5");
        Preferences out = KvasirConsent.migrated(target, legacy);
        assertThat(out).isSameAs(target);
        assertThat(target.getBoolean("kvasir.code.consent", false)).isTrue();
        assertThat(target.get("kvasir.ask.model", null)).isEqualTo("claude-sonnet-5");
        assertThat(legacy.keys()).isEmpty();
    }

    @Test
    @DisplayName("v2.95.0: oracle.* keys move to kvasir.* once and the old key goes")
    void oracleKeysRenamed() throws Exception {
        target.putBoolean("oracle.code.consent", true);
        target.put("oracle.ask.model", "claude-sonnet-5");
        target.putBoolean("oracle.kind.git.diff.consent", true);
        KvasirConsent.migrated(target, legacy);
        assertThat(target.getBoolean("kvasir.code.consent", false)).as("the grant survives the rename").isTrue();
        assertThat(target.get("kvasir.ask.model", null)).isEqualTo("claude-sonnet-5");
        assertThat(target.getBoolean("kvasir.kind.git.diff.consent", false)).isTrue();
        assertThat(java.util.Arrays.asList(target.keys())).noneMatch(k -> k.startsWith("oracle."));
    }

    @Test
    @DisplayName("v2.95.0: an existing kvasir.* value wins over its oracle.* twin")
    void kvasirKeyWins() throws Exception {
        target.put("oracle.ask.model", "claude-sonnet-5");
        target.put("kvasir.ask.model", "claude-haiku-4-5");
        KvasirConsent.migrated(target, legacy);
        assertThat(target.get("kvasir.ask.model", null)).isEqualTo("claude-haiku-4-5");
        assertThat(target.get("oracle.ask.model", null)).isNull();
    }

    @Test
    @DisplayName("A userdir value already present wins over the legacy one")
    void userdirWins() throws Exception {
        target.put("kvasir.ask.model", "claude-haiku-4-5");
        legacy.put("kvasir.ask.model", "claude-sonnet-5");
        KvasirConsent.migrated(target, legacy);
        assertThat(target.get("kvasir.ask.model", null)).isEqualTo("claude-haiku-4-5");
        assertThat(legacy.keys()).isEmpty();
    }

    @Test
    @DisplayName("An empty legacy node is a no-op")
    void emptyLegacy() throws Exception {
        KvasirConsent.migrated(target, legacy);
        assertThat(target.keys()).isEmpty();
    }
}
