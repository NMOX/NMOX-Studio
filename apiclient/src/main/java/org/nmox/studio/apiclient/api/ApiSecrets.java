package org.nmox.studio.apiclient.api;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.keyring.Keyring;

/**
 * Where API Studio auth tokens actually live: the OS keychain, via the
 * platform {@link Keyring}, under {@code "nmox.api." + requestId}. The
 * {@code .nmoxapi.json} workspace file — which the module's own Auth
 * tab warns is committable — never carries a bearer token or a
 * user:password pair again (v1.97.0). This class is the only door,
 * exactly as {@code dbstudio.Passwords} and {@code web3.RpcSecrets}
 * are for their modules.
 *
 * <p><b>Honest fallback:</b> when no keyring backend is reachable
 * (headless test runs, a platform without a provider, a broken
 * keychain), every {@code Keyring} call is caught — any
 * {@code Throwable}, since a missing backend can surface as an error
 * as well as an exception — and the token is held in an in-memory,
 * process-lifetime map instead. Degraded tokens survive only until the
 * app exits; they are never silently written to disk. Tests force the
 * fallback with {@link #keyringUsable}.
 *
 * <p>Thread-safe and callable from any thread except the EDT (the
 * keyring may block on OS calls; UI code calls through the module's
 * RequestProcessor, same as every other IO here).
 */
public final class ApiSecrets {

    private static final Logger LOG = Logger.getLogger(ApiSecrets.class.getName());
    private static final String PREFIX = "nmox.api.";

    /** Process-lifetime fallback store, used only when the keyring fails. */
    private static final ConcurrentMap<String, char[]> MEMORY = new ConcurrentHashMap<>();

    /** Flipped false the first time the keyring throws, so a broken
     *  backend is probed once. Package-private test seam. */
    static volatile boolean keyringUsable = true;

    private ApiSecrets() {
    }

    /** Stores (or, for a blank/null token, deletes) the secret for a request. */
    public static void save(String requestId, String token) {
        if (requestId == null) {
            return;
        }
        if (token == null || token.isEmpty()) {
            delete(requestId);
            return;
        }
        String key = PREFIX + requestId;
        if (keyringUsable) {
            try {
                Keyring.save(key, token.toCharArray(), "NMOX Studio API Studio auth token");
                MEMORY.remove(key);
                return;
            } catch (Throwable t) {
                degrade(t);
            }
        }
        MEMORY.put(key, token.toCharArray());
    }

    /** Reads the secret for a request, or {@code ""} when none is stored. */
    public static String read(String requestId) {
        if (requestId == null) {
            return "";
        }
        String key = PREFIX + requestId;
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

    /** Removes the stored secret for a request, if any. */
    public static void delete(String requestId) {
        if (requestId == null) {
            return;
        }
        String key = PREFIX + requestId;
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
                    "Keyring backend unavailable; API auth tokens held in memory for this session only", t);
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
                    "API Studio auth tokens will not be saved this session.", null);
        } catch (RuntimeException | LinkageError ignored) {
            // notifications unavailable (tests, stripped platform)
        }
    }
}
