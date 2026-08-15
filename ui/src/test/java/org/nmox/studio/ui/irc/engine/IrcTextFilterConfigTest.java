package org.nmox.studio.ui.irc.engine;

import java.util.UUID;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The custom-filter store (v2.10.0), same throwaway-node idiom as the
 * alias tests: names lowercase on save, the string form (which may
 * itself contain '|' inside the regex) survives a fresh IrcConfig over
 * the same node, and removal is total.
 */
class IrcTextFilterConfigTest {

    private Preferences root;
    private IrcConfig config;

    @BeforeEach
    void throwawayNode() {
        root = Preferences.userRoot().node("nmox-irc-tf-test-" + UUID.randomUUID());
        config = new IrcConfig(root);
    }

    @AfterEach
    void cleanRealUserRoot() throws Exception {
        root.removeNode();
        Preferences.userRoot().flush();
    }

    @Test
    @DisplayName("a filter's string form round-trips through a fresh IrcConfig, name lowercased")
    void filterRoundTrip() {
        config.saveTextFilter("Spam", "1|*|buy|sell now");
        IrcConfig reread = new IrcConfig(root);
        assertThat(reread.textFilters()).containsEntry("spam", "1|*|buy|sell now");
        reread.removeTextFilter("SPAM");
        assertThat(new IrcConfig(root).textFilters()).isEmpty();
    }
}
