package org.nmox.studio.ui.irc.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;

/**
 * The IRC client's saved networks, {@code NbPreferences}-backed: each
 * network is a child node under {@code networks/} carrying host, port,
 * TLS flag, nick, and its autojoin channels — ONE preference entry per
 * channel ({@code channel.0}, {@code channel.1}, …), never a joined
 * value, because {@code java.util.prefs} caps a single value at 8 KB
 * and a long-lived install once broke Workspace Trust exactly that way
 * (the v1.27.0 finding; one-entry-per-item is law since).
 *
 * <p>The shipped default network is <b>freenode</b> —
 * {@code chat.freenode.net}, port 6697, TLS, nick {@code nmox-user} —
 * with Libera.Chat and OFTC as non-default presets, seeded once by
 * {@link #ensureDefaults()} on a fresh install and never re-imposed on
 * a user who edited or removed them.
 *
 * <p>NickServ passwords are NOT here — they live in the OS keychain via
 * {@link IrcSecrets} (the Keyring-only law). The {@link Preferences}
 * root is a constructor seam so tests run against a throwaway node and
 * the real userRoot is never polluted.
 */
public final class IrcConfig {

    private static final Logger LOG = Logger.getLogger(IrcConfig.class.getName());

    /** The name of the network a fresh install connects to by default. */
    public static final String DEFAULT_NETWORK = "freenode";

    private static final String NETWORKS = "networks";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_TLS = "tls";
    private static final String KEY_NICK = "nick";
    private static final String KEY_SASL = "saslAccount";
    private static final String KEY_LAST = "lastSelected";
    private static final String KEY_LOGGING = "logging";
    private static final String CHANNEL_PREFIX = "channel.";
    private static final String IGNORE_PREFIX = "ignore.";
    private static final String HIGHLIGHT_PREFIX = "highlight.";

    /**
     * One saved network.
     *
     * @param name        the display name and preferences key ("freenode")
     * @param host        server hostname
     * @param port        server port (6697 is the conventional TLS port)
     * @param tls         connect with TLS
     * @param nick        the nickname to register with
     * @param saslAccount the SASL PLAIN account name; {@code ""} means
     *                    "no SASL" (the NickServ-after-001 fallback runs
     *                    instead). The password itself is NEVER here —
     *                    it lives in the keychain via {@link IrcSecrets}
     * @param autojoin    channels to join after registration, may be empty
     */
    public record Network(String name, String host, int port, boolean tls,
            String nick, String saslAccount, List<String> autojoin) {
        public Network {
            saslAccount = saslAccount == null ? "" : saslAccount;
            autojoin = List.copyOf(autojoin);
        }

        /** The pre-SASL shape (v1.204.0 callers): no SASL account. */
        public Network(String name, String host, int port, boolean tls,
                String nick, List<String> autojoin) {
            this(name, host, port, tls, nick, "", autojoin);
        }
    }

    private final Preferences root;

    /** Test seam: any {@link Preferences} node works as the store. */
    public IrcConfig(Preferences root) {
        this.root = root;
    }

    /** The production store, under this module's NbPreferences node. */
    public static IrcConfig getDefault() {
        return new IrcConfig(NbPreferences.forModule(IrcConfig.class));
    }

    /**
     * Seeds the shipped networks on a store that has none: freenode
     * (the default), Libera.Chat, and OFTC. A store the user has touched
     * — even one they emptied of two presets — is left alone.
     */
    public void ensureDefaults() {
        try {
            if (root.node(NETWORKS).childrenNames().length > 0) {
                return;
            }
            save(new Network(DEFAULT_NETWORK, "chat.freenode.net", 6697, true,
                    "nmox-user", List.of()));
            save(new Network("Libera.Chat", "irc.libera.chat", 6697, true,
                    "nmox-user", List.of()));
            save(new Network("OFTC", "irc.oftc.net", 6697, true,
                    "nmox-user", List.of()));
            root.put(KEY_LAST, DEFAULT_NETWORK);
        } catch (BackingStoreException ex) {
            LOG.log(Level.WARNING, "could not seed default IRC networks", ex);
        }
    }

    /** All saved networks, in the store's order. */
    public List<Network> networks() {
        List<Network> out = new ArrayList<>();
        try {
            String[] names = root.node(NETWORKS).childrenNames();
            Arrays.sort(names);
            for (String name : names) {
                Network n = network(name);
                if (n != null) {
                    out.add(n);
                }
            }
        } catch (BackingStoreException ex) {
            LOG.log(Level.WARNING, "could not list IRC networks", ex);
        }
        return out;
    }

    /** The named network, or {@code null} when none is saved under that name. */
    public Network network(String name) {
        try {
            if (!root.node(NETWORKS).nodeExists(name)) {
                return null;
            }
        } catch (BackingStoreException ex) {
            return null;
        }
        Preferences n = root.node(NETWORKS).node(name);
        List<String> channels = new ArrayList<>();
        for (int i = 0; ; i++) {
            String c = n.get(CHANNEL_PREFIX + i, null);
            if (c == null) {
                break;
            }
            channels.add(c);
        }
        return new Network(name,
                n.get(KEY_HOST, ""),
                n.getInt(KEY_PORT, 6697),
                n.getBoolean(KEY_TLS, true),
                n.get(KEY_NICK, "nmox-user"),
                n.get(KEY_SASL, ""),
                channels);
    }

    /** Saves (creates or overwrites) a network; channels are one entry per item. */
    public void save(Network network) {
        Preferences n = root.node(NETWORKS).node(network.name());
        n.put(KEY_HOST, network.host());
        n.putInt(KEY_PORT, network.port());
        n.putBoolean(KEY_TLS, network.tls());
        n.put(KEY_NICK, network.nick());
        if (network.saslAccount().isEmpty()) {
            n.remove(KEY_SASL);
        } else {
            n.put(KEY_SASL, network.saslAccount());
        }
        // clear stale channel.N entries past the new list's end
        for (int i = network.autojoin().size(); n.get(CHANNEL_PREFIX + i, null) != null; i++) {
            n.remove(CHANNEL_PREFIX + i);
        }
        for (int i = 0; i < network.autojoin().size(); i++) {
            n.put(CHANNEL_PREFIX + i, network.autojoin().get(i));
        }
    }

    /** Removes a saved network (its keychain password is {@link IrcSecrets}' job). */
    public void remove(String name) {
        try {
            if (root.node(NETWORKS).nodeExists(name)) {
                root.node(NETWORKS).node(name).removeNode();
            }
        } catch (BackingStoreException ex) {
            LOG.log(Level.WARNING, "could not remove IRC network " + name, ex);
        }
    }

    // ---- per-network ignore list (one entry per nick, the 8 KB law) ----

    /** The nicks ignored on {@code network}, lower-cased, in saved order. */
    public List<String> ignoredNicks(String network) {
        List<String> out = new ArrayList<>();
        Preferences n = root.node(NETWORKS).node(network);
        for (int i = 0; ; i++) {
            String nick = n.get(IGNORE_PREFIX + i, null);
            if (nick == null) {
                break;
            }
            out.add(nick);
        }
        return out;
    }

    /** Adds a nick to the network's ignore list (idempotent, case-insensitive). */
    public void addIgnored(String network, String nick) {
        String lower = nick.toLowerCase(java.util.Locale.ROOT);
        List<String> current = ignoredNicks(network);
        if (current.contains(lower)) {
            return;
        }
        root.node(NETWORKS).node(network).put(IGNORE_PREFIX + current.size(), lower);
    }

    /** Removes a nick from the network's ignore list, re-packing indices. */
    public void removeIgnored(String network, String nick) {
        String lower = nick.toLowerCase(java.util.Locale.ROOT);
        List<String> current = new ArrayList<>(ignoredNicks(network));
        if (!current.remove(lower)) {
            return;
        }
        Preferences n = root.node(NETWORKS).node(network);
        for (int i = current.size(); n.get(IGNORE_PREFIX + i, null) != null; i++) {
            n.remove(IGNORE_PREFIX + i);
        }
        for (int i = 0; i < current.size(); i++) {
            n.put(IGNORE_PREFIX + i, current.get(i));
        }
    }

    // ---- the smart join/part/quit filter (WeeChat's signature) ----

    /** Whether presence churn from silent nicks is hidden. Default ON. */
    public boolean smartFilterEnabled() {
        return root.getBoolean("smartFilter", true);
    }

    public void setSmartFilterEnabled(boolean enabled) {
        root.putBoolean("smartFilter", enabled);
    }

    // ---- command aliases (/alias — one preferences entry per alias) ----

    /** Saved aliases, name → expansion, in name order. */
    public java.util.Map<String, String> aliases() {
        java.util.Map<String, String> out = new java.util.TreeMap<>();
        Preferences n = root.node("aliases");
        try {
            for (String key : n.keys()) {
                out.put(key, n.get(key, ""));
            }
        } catch (BackingStoreException ex) {
            // an unreadable store means no aliases, not a broken client
        }
        return out;
    }

    public void saveAlias(String name, String expansion) {
        root.node("aliases").put(name.toLowerCase(java.util.Locale.ROOT), expansion);
    }

    public void removeAlias(String name) {
        root.node("aliases").remove(name.toLowerCase(java.util.Locale.ROOT));
    }

    // ---- custom text filters (/filter add — one entry per filter, v2.10.0) ----

    /** Saved filters, name → the TextFilters string form, in name order. */
    public java.util.Map<String, String> textFilters() {
        java.util.Map<String, String> out = new java.util.TreeMap<>();
        Preferences n = root.node("textfilters");
        try {
            for (String key : n.keys()) {
                out.put(key, n.get(key, ""));
            }
        } catch (BackingStoreException ex) {
            // an unreadable store means no filters, not a broken client
        }
        return out;
    }

    public void saveTextFilter(String name, String stringForm) {
        root.node("textfilters").put(name.toLowerCase(java.util.Locale.ROOT), stringForm);
    }

    public void removeTextFilter(String name) {
        root.node("textfilters").remove(name.toLowerCase(java.util.Locale.ROOT));
    }

    // ---- global highlight keywords (one entry per keyword) ----

    /** Extra words that highlight like the nick does, in saved order. */
    public List<String> highlightKeywords() {
        List<String> out = new ArrayList<>();
        for (int i = 0; ; i++) {
            String kw = root.get(HIGHLIGHT_PREFIX + i, null);
            if (kw == null) {
                break;
            }
            out.add(kw);
        }
        return out;
    }

    /** Replaces the highlight keyword list; one preference entry per word. */
    public void setHighlightKeywords(List<String> keywords) {
        for (int i = keywords.size(); root.get(HIGHLIGHT_PREFIX + i, null) != null; i++) {
            root.remove(HIGHLIGHT_PREFIX + i);
        }
        for (int i = 0; i < keywords.size(); i++) {
            root.put(HIGHLIGHT_PREFIX + i, keywords.get(i));
        }
    }

    // ---- global logging toggle ----

    /** Whether per-channel logging is on (the persisted default; true on fresh installs). */
    public boolean isLoggingEnabled() {
        return root.getBoolean(KEY_LOGGING, true);
    }

    /** Persists the logging default ({@code /log on|off} writes through here). */
    public void setLoggingEnabled(boolean on) {
        root.putBoolean(KEY_LOGGING, on);
    }

    /** The network the UI selects on open; defaults to freenode. */
    public String lastSelected() {
        return root.get(KEY_LAST, DEFAULT_NETWORK);
    }

    /** Remembers the user's network selection across sessions. */
    public void setLastSelected(String name) {
        root.put(KEY_LAST, name);
    }
}
