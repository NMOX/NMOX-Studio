package org.nmox.studio.web3.io;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.keyring.Keyring;

/**
 * Where secret RPC URLs actually live: the OS keychain, via the
 * platform {@link Keyring}, under {@code "nmox.web3." + networkId}. The
 * {@code .nmoxweb3.json} workspace file never carries one — this class
 * is the only door (the DB Studio Passwords idiom, verbatim).
 *
 * <p><b>Honest fallback:</b> when no keyring backend is reachable
 * (headless test runs, a platform without a provider, a broken
 * keychain), every {@code Keyring} call is caught — any
 * {@code Throwable}, because a missing backend can surface as errors as
 * well as exceptions — and the secret is kept in an in-memory,
 * process-lifetime map instead. "Saved" URLs then survive only until
 * the app exits in that degraded mode; they are never silently written
 * to disk. Tests force the fallback directly so they never need the OS
 * keychain.
 *
 * <p>All methods are thread-safe and callable from any thread except
 * the EDT (the keyring may block on OS calls).
 */
public final class RpcSecrets {

    private static final Logger LOG = Logger.getLogger(RpcSecrets.class.getName());
    private static final String PREFIX = "nmox.web3.";

    /** Process-lifetime fallback store, used only when the keyring fails. */
    private static final ConcurrentMap<String, char[]> MEMORY = new ConcurrentHashMap<>();

    /**
     * Flipped to false the first time the keyring throws, so a broken
     * backend is probed once, not on every call. Package-private as a
     * test seam: RpcSecretsTest forces the fallback path with it.
     */
    static volatile boolean keyringUsable = true;

    private RpcSecrets() {
    }

    /**
     * Stores the secret RPC URL for a network. A null value is a
     * {@link #delete(String)}. The caller keeps ownership of the array
     * (a copy is stored) and may wipe it afterwards.
     */
    public static void save(String networkId, char[] url) {
        if (networkId == null) {
            return;
        }
        if (url == null) {
            delete(networkId);
            return;
        }
        String key = PREFIX + networkId;
        if (keyringUsable) {
            try {
                Keyring.save(key, url.clone(), "NMOX Studio Web3 RPC endpoint " + networkId);
                MEMORY.remove(key);
                return;
            } catch (Throwable t) {
                degrade(t);
            }
        }
        MEMORY.put(key, url.clone());
    }

    /**
     * Reads the secret RPC URL for a network, or null when none is
     * stored. The returned array is the caller's copy — wipe it after
     * building the client.
     */
    public static char[] read(String networkId) {
        if (networkId == null) {
            return null;
        }
        String key = PREFIX + networkId;
        if (keyringUsable) {
            try {
                char[] fromKeyring = Keyring.read(key);
                if (fromKeyring != null) {
                    return fromKeyring;
                }
            } catch (Throwable t) {
                degrade(t);
            }
        }
        char[] fromMemory = MEMORY.get(key);
        return fromMemory == null ? null : fromMemory.clone();
    }

    /** Removes the stored URL for a network, if any. */
    public static void delete(String networkId) {
        if (networkId == null) {
            return;
        }
        String key = PREFIX + networkId;
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
                    "Keyring backend unavailable; Web3 RPC secrets held in memory for this session only", t);
        }
        warnOnce();
    }

    /** One session-scoped balloon: silent degradation loses secret URLs silently. */
    private static final java.util.concurrent.atomic.AtomicBoolean WARNED =
            new java.util.concurrent.atomic.AtomicBoolean();

    private static void warnOnce() {
        if (!WARNED.compareAndSet(false, true)) {
            return;
        }
        try {
            org.openide.awt.NotificationDisplayer.getDefault().notify(
                    "Keychain unavailable",
                    javax.swing.UIManager.getIcon("OptionPane.warningIcon"),
                    "Secret RPC URLs will not be saved this session.", null);
        } catch (RuntimeException | LinkageError ignored) {
            // notifications unavailable (tests, stripped platform)
        }
    }
}
