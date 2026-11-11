package org.nmox.studio.ui.irc.engine;

import java.util.UUID;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The /alias store and the smart-filter switch, on the same throwaway
 * prefs node the rest of IrcConfig's tests use: names lowercase on
 * save, removal is total, the round trip survives a fresh IrcConfig
 * over the same node, and the smart filter defaults ON — the WeeChat
 * behavior is the shipped behavior, not an opt-in.
 */
class IrcAliasConfigTest {

    private Preferences root;
    private IrcConfig config;

    @BeforeEach
    void throwawayNode() {
        root = Preferences.userRoot().node("nmox-irc-alias-test-" + UUID.randomUUID());
        config = new IrcConfig(root);
    }

    @AfterEach
    void cleanRealUserRoot() {
        try {
            root.removeNode();
            Preferences.userRoot().flush();
        } catch (java.util.prefs.BackingStoreException ex) {
            // Best-effort janitor work only. A fresh CI runner can have no
            // ~/.java/.userPrefs to lock, and then nothing here was ever
            // persisted, so there is nothing durable to remove. Every
            // assertion ran before this; none is excused. It failed the
            // Linux lane of 3.12.0's pull request (the v1.286.0 class).
        }
    }

    @Test
    @DisplayName("An alias round-trips through a fresh IrcConfig over the same store")
    void aliasRoundTrip() {
        config.saveAlias("OPME", "msg chanserv op");
        IrcConfig reread = new IrcConfig(root);
        assertThat(reread.aliases()).containsEntry("opme", "msg chanserv op");
    }

    @Test
    @DisplayName("Removing an alias removes it whole; the rest survive")
    void removeIsTotal() {
        config.saveAlias("wc", "close");
        config.saveAlias("opme", "msg chanserv op");
        config.removeAlias("wc");
        assertThat(config.aliases()).containsOnlyKeys("opme");
    }

    @Test
    @DisplayName("The smart filter defaults ON and its toggle persists")
    void smartFilterDefaultsOnAndPersists() {
        assertThat(config.smartFilterEnabled()).isTrue();
        config.setSmartFilterEnabled(false);
        assertThat(new IrcConfig(root).smartFilterEnabled()).isFalse();
    }
}
