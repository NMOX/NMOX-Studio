package org.nmox.studio.dbstudio.engine;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.keyring.Keyring;

/**
 * Where connection passwords actually live: the OS keychain, via the
 * platform {@link Keyring}, under {@code "nmox.db." + specId}. The
 * {@code .nmoxdb.json} workspace file never carries a secret — this
 * class is the only door.
 *
 * <p><b>Honest fallback:</b> when no keyring backend is reachable
 * (headless test runs, a platform without a provider, a broken
 * keychain), every {@code Keyring} call is caught — any
 * {@code Throwable}, because a missing backend can surface as errors
 * as well as exceptions — and the password is kept in an in-memory,
 * process-lifetime map instead. That means "saved" passwords survive
 * only until the app exits in that degraded mode; they are never
 * silently written to disk. Tests exercise the fallback directly so
 * they never need the OS keychain.
 *
 * <p>All methods are thread-safe and callable from any thread except
 * the EDT (the keyring may block on OS calls; UI code should call
 * through a worker, same as every other engine entry point).
 */
public final class Passwords {

    private static final Logger LOG = Logger.getLogger(Passwords.class.getName());
    private static final String PREFIX = "nmox.db.";

    /** Process-lifetime fallback store, used only when the keyring fails. */
    private static final ConcurrentMap<String, char[]> MEMORY = new ConcurrentHashMap<>();

    /**
     * Flipped to false the first time the keyring throws, so a broken
     * backend is probed once, not on every call. Package-private as a
     * test seam: PasswordsTest forces the fallback path with it.
     */
    static volatile boolean keyringUsable = true;

    private Passwords() {
    }

    /**
     * Stores the password for a connection spec. A null password is a
     * {@link #delete(String)}. The caller keeps ownership of the array
     * (a copy is stored) and may wipe it afterwards.
     */
    public static void save(String specId, char[] password) {
        if (specId == null) {
            return;
        }
        if (password == null) {
            delete(specId);
            return;
        }
        String key = PREFIX + specId;
        if (keyringUsable) {
            try {
                Keyring.save(key, password.clone(), "NMOX Studio database connection " + specId);
                MEMORY.remove(key);
                return;
            } catch (Throwable t) {
                degrade(t);
            }
        }
        MEMORY.put(key, password.clone());
    }

    /**
     * Reads the password for a connection spec, or null when none is
     * stored. The returned array is the caller's copy — wipe it after
     * building the connection.
     */
    public static char[] read(String specId) {
        if (specId == null) {
            return null;
        }
        String key = PREFIX + specId;
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

    /** Removes the stored password for a connection spec, if any. */
    public static void delete(String specId) {
        if (specId == null) {
            return;
        }
        String key = PREFIX + specId;
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
                    "Keyring backend unavailable; DB passwords held in memory for this session only", t);
        }
    }
}
