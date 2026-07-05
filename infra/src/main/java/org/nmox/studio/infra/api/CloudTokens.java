package org.nmox.studio.infra.api;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.keyring.Keyring;
import org.openide.util.NbPreferences;

/**
 * Where cloud API tokens actually live: the OS keychain, via the
 * platform {@link Keyring}, under {@code "nmox.cloud." + prefKey} — the
 * DB Studio {@code Passwords} / Contract Studio {@code RpcSecrets}
 * idiom, applied to the one suite that still kept secrets in plaintext
 * preferences.
 *
 * <p><b>Legacy migration:</b> installs up to v1.35 stored tokens in
 * {@code NbPreferences} node {@code nmox/cloud}. The first read that
 * finds nothing in the keychain but a value in the old preference moves
 * it into the keychain and removes the preference — and it removes the
 * preference ONLY after the keychain save succeeded, so a degraded
 * session can never destroy the only durable copy.
 *
 * <p><b>Honest fallback:</b> when no keyring backend is reachable
 * (headless test runs, a platform without a provider, a broken
 * keychain), every {@code Keyring} call is caught — any
 * {@code Throwable}, because a missing backend can surface as errors as
 * well as exceptions — and the token is kept in an in-memory,
 * process-lifetime map instead (legacy preferences are still readable,
 * and are left in place). A warn-once balloon tells the user that
 * tokens will not be saved this session.
 *
 * <p>All methods are thread-safe. Like the sibling stores, reads may
 * block on OS calls — {@link CloudProvider} caches the resolved value,
 * and the designer primes that cache off the EDT.
 */
final class CloudTokens {

    private static final Logger LOG = Logger.getLogger(CloudTokens.class.getName());
    private static final String KEY_PREFIX = "nmox.cloud.";

    /** Process-lifetime fallback store, used only when the keyring fails. */
    private static final ConcurrentMap<String, String> MEMORY = new ConcurrentHashMap<>();

    /**
     * Flipped to false the first time the keyring throws, so a broken
     * backend is probed once, not on every call. Package-private as a
     * test seam: CloudTokensTest forces the fallback path with it (which
     * also keeps tests off the real OS keychain and away from a real
     * install's migration — degraded reads never remove preferences).
     */
    static volatile boolean keyringUsable = true;

    /** One "keychain unavailable" balloon per session, however often it degrades. */
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    /**
     * Session cache of resolved values ("" = none), so EDT callers
     * ({@code hasToken()} gating buttons/plans) never block on an OS
     * keychain call after the designer's off-EDT priming pass.
     */
    private static final ConcurrentMap<String, String> CACHE = new ConcurrentHashMap<>();

    private CloudTokens() {
    }

    /**
     * {@link #read} through the session cache: the keychain is asked
     * once per key per session; stores refresh the cache in place.
     */
    static String readCached(String prefKey, String description) {
        return CACHE.computeIfAbsent(prefKey, k -> read(k, description));
    }

    /** The explicit prefs node v1.35-era installs kept tokens in. */
    private static java.util.prefs.Preferences legacyPrefs() {
        return NbPreferences.root().node("nmox/cloud");
    }

    /**
     * The stored token for a provider's pref key, or {@code ""} when
     * none: keychain first, then the in-memory degraded store, then the
     * legacy plaintext preference (migrating it into the keychain when
     * the keychain works).
     */
    static String read(String prefKey, String description) {
        String key = KEY_PREFIX + prefKey;
        if (keyringUsable) {
            try {
                char[] fromKeyring = Keyring.read(key);
                if (fromKeyring != null && fromKeyring.length > 0) {
                    String value = new String(fromKeyring);
                    java.util.Arrays.fill(fromKeyring, '\0');
                    return value;
                }
            } catch (Throwable t) {
                degrade(t);
            }
        }
        String fromMemory = MEMORY.get(key);
        if (fromMemory != null) {
            return fromMemory;
        }
        String legacy = legacyPrefs().get(prefKey, "");
        if (legacy.isBlank()) {
            return "";
        }
        if (keyringUsable) {
            try {
                Keyring.save(key, legacy.toCharArray(), description);
                // only a SUCCESSFUL keychain save earns the pref removal
                removeLegacyPref(prefKey);
            } catch (Throwable t) {
                degrade(t);
            }
        }
        return legacy;
    }

    /**
     * Stores a token for a provider's pref key; blank (or null) is a
     * delete. A successful keychain write also clears the legacy
     * preference, so the plaintext copy never lingers beside the
     * keychain one.
     */
    static void store(String prefKey, String token, String description) {
        String key = KEY_PREFIX + prefKey;
        String trimmed = token == null ? "" : token.trim();
        CACHE.put(prefKey, trimmed);
        if (trimmed.isEmpty()) {
            if (keyringUsable) {
                try {
                    Keyring.delete(key);
                } catch (Throwable t) {
                    degrade(t);
                }
            }
            MEMORY.remove(key);
            // a deleted token must not resurrect from the old plaintext pref
            removeLegacyPref(prefKey);
            return;
        }
        if (keyringUsable) {
            try {
                Keyring.save(key, trimmed.toCharArray(), description);
                MEMORY.remove(key);
                removeLegacyPref(prefKey);
                return;
            } catch (Throwable t) {
                degrade(t);
            }
        }
        MEMORY.put(key, trimmed);
    }

    private static void removeLegacyPref(String prefKey) {
        java.util.prefs.Preferences p = legacyPrefs();
        p.remove(prefKey);
        try {
            p.flush();
        } catch (java.util.prefs.BackingStoreException ignore) {
            // best effort; the removal still holds for this session
        }
    }

    private static void degrade(Throwable t) {
        if (keyringUsable) {
            keyringUsable = false;
            LOG.log(Level.WARNING,
                    "Keyring backend unavailable; cloud API tokens held in memory for this session only", t);
        }
        warnOnce();
    }

    /** The task-4 balloon, mirrored here: quiet, once per session, never a blocker. */
    private static void warnOnce() {
        if (!WARNED.compareAndSet(false, true)) {
            return;
        }
        try {
            org.openide.awt.NotificationDisplayer.getDefault().notify(
                    "Keychain unavailable",
                    javax.swing.UIManager.getIcon("OptionPane.warningIcon"),
                    "Cloud API tokens will not be saved this session.", null);
        } catch (RuntimeException | LinkageError ignored) {
            // notifications unavailable (tests, stripped platform)
        }
    }
}
