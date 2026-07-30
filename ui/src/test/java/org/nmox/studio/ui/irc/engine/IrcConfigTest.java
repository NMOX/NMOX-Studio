package org.nmox.studio.ui.irc.engine;

import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The saved-network store: the shipped freenode default (exact host,
 * port, TLS, nick), the two non-default presets, the one-entry-per-item
 * channels law (the 8 KB prefs cap), and the save/load round trip.
 * Every test runs against a throwaway node under the real userRoot and
 * removes it in teardown — the prefs-pollution law.
 */
class IrcConfigTest {

    private Preferences root;
    private IrcConfig config;

    @BeforeEach
    void throwawayNode() {
        root = Preferences.userRoot().node("nmox-irc-config-test-" + UUID.randomUUID());
        config = new IrcConfig(root);
    }

    @AfterEach
    void cleanRealUserRoot() throws Exception {
        // restore the real userRoot's state: the throwaway node vanishes
        root.removeNode();
        Preferences.userRoot().flush();
    }

    @Test
    @DisplayName("A fresh store seeds freenode as THE default: chat.freenode.net 6697 TLS nmox-user")
    void freenodeIsTheShippedDefault() {
        config.ensureDefaults();
        assertThat(config.lastSelected()).isEqualTo("freenode");
        assertThat(IrcConfig.DEFAULT_NETWORK).isEqualTo("freenode");
        IrcConfig.Network freenode = config.network("freenode");
        assertThat(freenode).isNotNull();
        assertThat(freenode.host()).isEqualTo("chat.freenode.net");
        assertThat(freenode.port()).isEqualTo(6697);
        assertThat(freenode.tls()).isTrue();
        assertThat(freenode.nick()).isEqualTo("nmox-user");
        assertThat(freenode.autojoin()).isEmpty();
    }

    @Test
    @DisplayName("Libera.Chat and OFTC ship as presets, not defaults")
    void presetsShipBesideTheDefault() {
        config.ensureDefaults();
        IrcConfig.Network libera = config.network("Libera.Chat");
        assertThat(libera).isNotNull();
        assertThat(libera.host()).isEqualTo("irc.libera.chat");
        assertThat(libera.port()).isEqualTo(6697);
        assertThat(libera.tls()).isTrue();

        IrcConfig.Network oftc = config.network("OFTC");
        assertThat(oftc).isNotNull();
        assertThat(oftc.host()).isEqualTo("irc.oftc.net");
        assertThat(oftc.port()).isEqualTo(6697);
        assertThat(oftc.tls()).isTrue();

        assertThat(config.lastSelected())
                .as("neither preset is the default selection")
                .isEqualTo("freenode");
    }

    @Test
    @DisplayName("ensureDefaults never re-imposes presets on a store the user edited")
    void defaultsAreSeededOnlyOnce() {
        config.ensureDefaults();
        config.remove("Libera.Chat");
        config.remove("OFTC");
        config.ensureDefaults(); // a touched store is left alone
        assertThat(config.network("Libera.Chat")).isNull();
        assertThat(config.network("OFTC")).isNull();
        assertThat(config.network("freenode")).isNotNull();
    }

    @Test
    @DisplayName("Channels persist one preference entry per item, never a joined value")
    void channelsAreOneEntryPerItem() {
        config.save(new IrcConfig.Network("work", "irc.example.org", 6667, false,
                "dave", List.of("#dev", "#ops", "#chat")));
        Preferences node = root.node("networks").node("work");
        assertThat(node.get("channel.0", null)).isEqualTo("#dev");
        assertThat(node.get("channel.1", null)).isEqualTo("#ops");
        assertThat(node.get("channel.2", null)).isEqualTo("#chat");
        assertThat(node.get("channel.3", null)).isNull();
        assertThat(node.get("channels", null))
                .as("no joined single-value entry (the 8KB prefs law)")
                .isNull();
    }

    @Test
    @DisplayName("Save/load round-trips every field; re-saving fewer channels clears stale keys")
    void saveLoadRoundTrip() {
        IrcConfig.Network original = new IrcConfig.Network("work", "irc.example.org",
                6667, false, "dave", List.of("#dev", "#ops"));
        config.save(original);
        assertThat(config.network("work")).isEqualTo(original);

        IrcConfig.Network fewer = new IrcConfig.Network("work", "irc.example.org",
                6697, true, "dave2", List.of("#dev"));
        config.save(fewer);
        IrcConfig.Network reread = config.network("work");
        assertThat(reread).isEqualTo(fewer);
        assertThat(reread.autojoin())
                .as("the stale channel.1 entry is cleared, not resurrected")
                .containsExactly("#dev");
    }

    @Test
    @DisplayName("networks() lists what is saved; remove() forgets; unknown names are null")
    void listRemoveAndUnknown() {
        config.ensureDefaults();
        assertThat(config.networks()).extracting(IrcConfig.Network::name)
                .containsExactlyInAnyOrder("freenode", "Libera.Chat", "OFTC");
        config.remove("OFTC");
        assertThat(config.networks()).extracting(IrcConfig.Network::name)
                .containsExactlyInAnyOrder("freenode", "Libera.Chat");
        assertThat(config.network("nope")).isNull();
        config.remove("nope"); // removing the unknown is a quiet no-op
    }

    @Test
    @DisplayName("The last-selected network round-trips and defaults to freenode")
    void lastSelectedRoundTrip() {
        assertThat(config.lastSelected()).isEqualTo("freenode");
        config.setLastSelected("OFTC");
        assertThat(config.lastSelected()).isEqualTo("OFTC");
    }

    @Test
    @DisplayName("The SASL account round-trips; a v1.204 network reads as no-SASL")
    void saslAccountRoundTrip() {
        config.save(new IrcConfig.Network("work", "irc.example.org", 6697, true,
                "dave", "daveacct", List.of()));
        assertThat(config.network("work").saslAccount()).isEqualTo("daveacct");

        // the pre-SASL constructor (and pre-SASL stores) default to ""
        config.save(new IrcConfig.Network("old", "irc.old.org", 6697, true,
                "dave", List.of()));
        assertThat(config.network("old").saslAccount()).isEmpty();

        // clearing the account removes the key
        config.save(new IrcConfig.Network("work", "irc.example.org", 6697, true,
                "dave", "", List.of()));
        assertThat(config.network("work").saslAccount()).isEmpty();
        assertThat(root.node("networks").node("work").get("saslAccount", null)).isNull();
    }

    @Test
    @DisplayName("Ignore list: one entry per nick, idempotent add, re-packing remove")
    void ignoreListRoundTrip() {
        config.save(new IrcConfig.Network("net", "h", 6697, true, "n", List.of()));
        config.addIgnored("net", "Troll");
        config.addIgnored("net", "TROLL"); // case-insensitive dedupe
        config.addIgnored("net", "spammer");
        config.addIgnored("net", "bore");
        assertThat(config.ignoredNicks("net")).containsExactly("troll", "spammer", "bore");
        assertThat(root.node("networks").node("net").get("ignore.0", null))
                .as("one preference entry per nick — the 8 KB law")
                .isEqualTo("troll");

        config.removeIgnored("net", "SPAMMER");
        assertThat(config.ignoredNicks("net")).containsExactly("troll", "bore");
        assertThat(root.node("networks").node("net").get("ignore.2", null))
                .as("the stale tail entry is cleared, not resurrected")
                .isNull();
        config.removeIgnored("net", "nobody"); // quiet no-op
        assertThat(config.ignoredNicks("net")).containsExactly("troll", "bore");
    }

    @Test
    @DisplayName("Highlight keywords replace as a list, one entry per word, shrink cleans up")
    void highlightKeywordsRoundTrip() {
        assertThat(config.highlightKeywords()).isEmpty();
        config.setHighlightKeywords(List.of("nmox", "release", "urgent"));
        assertThat(config.highlightKeywords()).containsExactly("nmox", "release", "urgent");
        config.setHighlightKeywords(List.of("nmox"));
        assertThat(config.highlightKeywords())
                .as("shrinking clears the stale entries")
                .containsExactly("nmox");
        assertThat(root.get("highlight.1", null)).isNull();
    }

    @Test
    @DisplayName("Logging defaults ON and the toggle persists")
    void loggingToggle() {
        assertThat(config.isLoggingEnabled()).as("fresh installs log by default").isTrue();
        config.setLoggingEnabled(false);
        assertThat(config.isLoggingEnabled()).isFalse();
        config.setLoggingEnabled(true);
        assertThat(config.isLoggingEnabled()).isTrue();
    }
}
