package org.nmox.studio.rack.service;

import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v2.63.0 move of ORACLE's consent and model preferences from the
 * JVM-global java.util.prefs node to the userdir-scoped NbPreferences node:
 * legacy grants are carried over once and removed from the legacy node, and
 * a grant the userdir already holds is never overwritten.
 */
class OraclePrefsMigrationTest {

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
        legacy.putBoolean("oracle.code.consent", true);
        legacy.put("oracle.ask.model", "claude-sonnet-5");
        Preferences out = OracleConsent.migrated(target, legacy);
        assertThat(out).isSameAs(target);
        assertThat(target.getBoolean("oracle.code.consent", false)).isTrue();
        assertThat(target.get("oracle.ask.model", null)).isEqualTo("claude-sonnet-5");
        assertThat(legacy.keys()).isEmpty();
    }

    @Test
    @DisplayName("A userdir value already present wins over the legacy one")
    void userdirWins() throws Exception {
        target.put("oracle.ask.model", "claude-haiku-4-5");
        legacy.put("oracle.ask.model", "claude-sonnet-5");
        OracleConsent.migrated(target, legacy);
        assertThat(target.get("oracle.ask.model", null)).isEqualTo("claude-haiku-4-5");
        assertThat(legacy.keys()).isEmpty();
    }

    @Test
    @DisplayName("An empty legacy node is a no-op")
    void emptyLegacy() throws Exception {
        OracleConsent.migrated(target, legacy);
        assertThat(target.keys()).isEmpty();
    }
}
