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
    private static final String KEY_LAST = "lastSelected";
    private static final String CHANNEL_PREFIX = "channel.";

    /**
     * One saved network.
     *
     * @param name     the display name and preferences key ("freenode")
     * @param host     server hostname
     * @param port     server port (6697 is the conventional TLS port)
     * @param tls      connect with TLS
     * @param nick     the nickname to register with
     * @param autojoin channels to join after registration, may be empty
     */
    public record Network(String name, String host, int port, boolean tls,
            String nick, List<String> autojoin) {
        public Network {
            autojoin = List.copyOf(autojoin);
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
                channels);
    }

    /** Saves (or overwrites) a network; channels are one entry per item. */
    public void save(Network network) {
        Preferences n = root.node(NETWORKS).node(network.name());
        n.put(KEY_HOST, network.host());
        n.putInt(KEY_PORT, network.port());
        n.putBoolean(KEY_TLS, network.tls());
        n.put(KEY_NICK, network.nick());
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

    /** The network the UI selects on open; defaults to freenode. */
    public String lastSelected() {
        return root.get(KEY_LAST, DEFAULT_NETWORK);
    }

    /** Remembers the user's network selection across sessions. */
    public void setLastSelected(String name) {
        root.put(KEY_LAST, name);
    }
}
