package org.nmox.studio.rack.mcp;

import java.io.IOException;
import java.net.BindException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import org.netbeans.api.keyring.Keyring;

/**
 * The Agent Port's opt-in memory (3.2): "Keep this address and token" and
 * "Start when NMOX Studio starts". Without them every start picks a new
 * port and a new token, so an agent configured on Monday is disconnected
 * on Tuesday; with them it connects once.
 *
 * <p>Where each fact lives, and why:
 * <ul>
 * <li>the TOKEN is a secret, so it lives in the OS keychain under
 *     {@link #TOKEN_KEY} (the {@code Passwords} idiom: any keyring failure
 *     degrades to an in-memory, process-lifetime slot, never a file);</li>
 * <li>the PORT and the two switches are not secrets, so they live in the
 *     module's preferences ({@link #KEEP}, {@link #PORT}, {@link #AUTOSTART}).</li>
 * </ul>
 * Forgetting deletes the keychain entry AND the preferences — clearing the
 * box is the user's way to make the token stop existing anywhere but a
 * running port.
 *
 * <p>Every method may touch the keychain, which can block on OS calls: call
 * from a worker, never the EDT ({@link AgentPortAction} rides its own lane).
 */
final class AgentPortKeep {

    static final String KEEP = "agentport.keep";
    static final String PORT = "agentport.keep.port";
    static final String AUTOSTART = "agentport.autostart";
    static final String TOKEN_KEY = "nmox.agentport.token";
    /** Read by the Welcome's Getting Started column (ui GettingStartedSignals). */
    static final String STARTED = "agentport.started";

    private static final Logger LOG = Logger.getLogger(AgentPortKeep.class.getName());

    /** The keychain, as a seam: tests hand in a map; production is {@link KeyringSecrets}. */
    interface Secrets {

        char[] read(String key);

        void save(String key, char[] value, String description);

        void delete(String key);

        /** False once the store has fallen back to memory: a kept token then dies with the JVM. */
        default boolean durable() {
            return true;
        }
    }

    /** How one start went: the port, and whether the agent must be told something new. */
    record Started(AgentPort port, boolean moved, boolean newToken) {
    }

    private final Preferences prefs;
    private final Secrets secrets;

    AgentPortKeep(Preferences prefs, Secrets secrets) {
        this.prefs = prefs;
        this.secrets = secrets;
    }

    /** The real one: the module's userdir preferences and the OS keychain. */
    static AgentPortKeep production() {
        return new AgentPortKeep(
                org.openide.util.NbPreferences.forModule(AgentPortAction.class),
                KeyringSecrets.INSTANCE);
    }

    /** True while the user has asked to keep the address and token. */
    boolean keeping() {
        return prefs.getBoolean(KEEP, false);
    }

    /**
     * True only when BOTH switches are on: a port that started itself on a
     * new address every boot would be a listener nobody can reach.
     */
    boolean autostart() {
        return keeping() && prefs.getBoolean(AUTOSTART, false);
    }

    /** Sets the start-with-the-IDE switch; refused (false) while nothing is kept. */
    boolean setAutostart(boolean on) {
        if (on && !keeping()) {
            return false;
        }
        prefs.putBoolean(AUTOSTART, on);
        return true;
    }

    /**
     * Keeps the running port's address and token: the token to the keychain,
     * the port to preferences. Returns false when the keychain has fallen
     * back to memory — the token is then kept for this session only, and
     * the caller says so rather than promise what a restart will break.
     */
    boolean keep(AgentPort running) {
        char[] token = running.token().toCharArray();
        try {
            secrets.save(TOKEN_KEY, token, "NMOX Studio Agent Port token");
        } finally {
            Arrays.fill(token, '\0');
        }
        prefs.putInt(PORT, running.port());
        prefs.putBoolean(KEEP, true);
        return secrets.durable();
    }

    /** Forgets both: the keychain entry is deleted, the preferences removed, autostart off. */
    void forget() {
        secrets.delete(TOKEN_KEY);
        prefs.remove(PORT);
        prefs.remove(AUTOSTART);
        prefs.remove(KEEP);
    }

    /**
     * Starts the port. Without the keep switch this is exactly
     * {@link AgentPort#start(McpTools, String)}. With it, the kept token is
     * reused (a missing or malformed one is replaced and re-kept, and the
     * result says so) and the kept port is tried first; when something else
     * holds it, a new port is bound, kept, and {@link Started#moved} is true
     * so the caller can tell the user the agent needs the new address.
     */
    Started start(McpTools tools, String productVersion) throws IOException {
        Started s = startPort(tools, productVersion);
        // the FIRST STEPS record (v2.84.0): the port was started once — a
        // userdir preference, the Getting Started column's own idiom; it
        // never carries the token
        prefs.putBoolean(STARTED, true);
        return s;
    }

    private Started startPort(McpTools tools, String productVersion) throws IOException {
        if (!keeping()) {
            return new Started(AgentPort.start(tools, productVersion), false, false);
        }
        char[] kept = secrets.read(TOKEN_KEY);
        String token = kept == null ? null : new String(kept);
        if (kept != null) {
            Arrays.fill(kept, '\0');
        }
        boolean newToken = !AgentPort.wellFormedToken(token);
        if (newToken) {
            token = AgentPort.newToken();
        }
        int wanted = prefs.getInt(PORT, 0);
        AgentPort port;
        boolean moved = false;
        if (wanted > 0 && wanted <= 65535) {
            try {
                port = AgentPort.start(tools, productVersion, wanted, token);
            } catch (BindException taken) {
                // something else holds the kept port: a new one, said out loud
                // — AND a new token. Whatever holds the old address may be a
                // squatter the configured agent has just sent its bearer to,
                // and a scan of loopback would find the new port; a kept token
                // must never follow the port it was handed out on (3.2.0 review)
                token = AgentPort.newToken();
                newToken = true;
                port = AgentPort.start(tools, productVersion, 0, token);
                moved = true;
            }
        } else {
            port = AgentPort.start(tools, productVersion, 0, token);
            moved = true;
        }
        if (moved || newToken) {
            keep(port);
        }
        return new Started(port, moved, newToken);
    }

    /** The OS keychain, with the {@code Passwords} fallback: a failing backend degrades to memory, never to disk. */
    static final class KeyringSecrets implements Secrets {

        static final KeyringSecrets INSTANCE = new KeyringSecrets();

        private final ConcurrentMap<String, char[]> memory = new ConcurrentHashMap<>();
        private volatile boolean keyringUsable = true;

        @Override
        public char[] read(String key) {
            if (keyringUsable) {
                try {
                    char[] v = Keyring.read(key);
                    if (v != null) {
                        return v;
                    }
                } catch (Throwable t) {
                    degrade(t);
                }
            }
            char[] v = memory.get(key);
            return v == null ? null : v.clone();
        }

        @Override
        public void save(String key, char[] value, String description) {
            if (keyringUsable) {
                try {
                    Keyring.save(key, value.clone(), description);
                    wipe(memory.remove(key));
                    return;
                } catch (Throwable t) {
                    degrade(t);
                }
            }
            wipe(memory.put(key, value.clone()));
        }

        @Override
        public void delete(String key) {
            if (keyringUsable) {
                try {
                    Keyring.delete(key);
                } catch (Throwable t) {
                    degrade(t);
                }
            }
            wipe(memory.remove(key));
        }

        @Override
        public boolean durable() {
            return keyringUsable;
        }

        private void degrade(Throwable t) {
            if (keyringUsable) {
                keyringUsable = false;
                // the exception names the backend, never the token
                LOG.log(Level.WARNING,
                        "Keyring backend unavailable; the Agent Port token is kept in memory for this session only", t);
            }
        }

        private static void wipe(char[] stale) {
            if (stale != null) {
                Arrays.fill(stale, '\0');
            }
        }
    }
}
