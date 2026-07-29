package org.nmox.studio.ui.irc.engine;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.keyring.Keyring;

/**
 * Where the IRC client's NickServ passwords actually live: the OS
 * keychain, via the platform {@link Keyring}, under
 * {@code "nmox.irc." + networkName}. The Keyring-only law every studio
 * honors (dbstudio {@code Passwords}, web3 {@code RpcSecrets}, apiclient
 * {@code ApiSecrets}) applies here too: a NickServ password never lands
 * in {@code java.util.prefs}, a log line, or a transcript — the engine
 * reads it through this class right before the one
 * {@code PRIVMSG NickServ :IDENTIFY …} it sends after registration.
 *
 * <p><b>Honest fallback:</b> when no keyring backend is reachable
 * (headless test runs, a platform without a provider, a broken
 * keychain), every {@code Keyring} call is caught — any
 * {@code Throwable}, since a missing backend can surface as an error as
 * well as an exception — and the password is held in an in-memory,
 * process-lifetime map instead. Degraded passwords survive only until
 * the app exits; they are never silently written to disk. Tests force
 * the fallback with {@link #keyringUsable}.
 *
 * <p>Thread-safe and callable from any thread except the EDT (the
 * keyring may block on OS calls; the engine calls it on its own
 * RequestProcessor).
 */
public final class IrcSecrets {

    private static final Logger LOG = Logger.getLogger(IrcSecrets.class.getName());
    private static final String PREFIX = "nmox.irc.";

    /** Process-lifetime fallback store, used only when the keyring fails. */
    private static final ConcurrentMap<String, char[]> MEMORY = new ConcurrentHashMap<>();

    /** Flipped false the first time the keyring throws, so a broken
     *  backend is probed once. Package-private test seam. */
    static volatile boolean keyringUsable = true;

    private IrcSecrets() {
    }

    /** Stores (or, for a blank/null password, deletes) a network's NickServ password. */
    public static void save(String networkName, String password) {
        if (networkName == null) {
            return;
        }
        if (password == null || password.isEmpty()) {
            delete(networkName);
            return;
        }
        String key = PREFIX + networkName;
        if (keyringUsable) {
            try {
                Keyring.save(key, password.toCharArray(), "NMOX Studio IRC NickServ password");
                MEMORY.remove(key);
                return;
            } catch (Throwable t) {
                degrade(t);
            }
        }
        MEMORY.put(key, password.toCharArray());
    }

    /** Reads a network's NickServ password, or {@code ""} when none is stored. */
    public static String read(String networkName) {
        if (networkName == null) {
            return "";
        }
        String key = PREFIX + networkName;
        if (keyringUsable) {
            try {
                char[] fromKeyring = Keyring.read(key);
                if (fromKeyring != null) {
                    String s = new String(fromKeyring);
                    java.util.Arrays.fill(fromKeyring, '\0');
                    return s;
                }
            } catch (Throwable t) {
                degrade(t);
            }
        }
        char[] fromMemory = MEMORY.get(key);
        return fromMemory == null ? "" : new String(fromMemory);
    }

    /** Removes a network's stored password, if any. */
    public static void delete(String networkName) {
        if (networkName == null) {
            return;
        }
        String key = PREFIX + networkName;
        if (keyringUsable) {
            try {
                Keyring.delete(key);
            } catch (Throwable t) {
                degrade(t);
            }
        }
        char[] stale = MEMORY.remove(key);
        if (stale != null) {
            java.util.Arrays.fill(stale, '\0');
        }
    }

    private static void degrade(Throwable t) {
        if (keyringUsable) {
            keyringUsable = false;
            LOG.log(Level.WARNING,
                    "Keyring backend unavailable; IRC NickServ passwords held in memory for this session only", t);
        }
        warnOnce();
    }

    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private static void warnOnce() {
        if (!WARNED.compareAndSet(false, true)) {
            return;
        }
        try {
            org.openide.awt.NotificationDisplayer.getDefault().notify(
                    "Keychain unavailable",
                    javax.swing.UIManager.getIcon("OptionPane.warningIcon"),
                    "IRC NickServ passwords will not be saved this session.", null);
        } catch (RuntimeException | LinkageError ignored) {
            // notifications unavailable (tests, stripped platform)
        }
    }
}
