package org.nmox.studio.rack.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.keyring.Keyring;

/**
 * Where the KVASIR API key actually lives: the OS keychain, via the
 * platform {@link Keyring}, under {@code "nmox.kvasir.apikey"}. No
 * preference and no workspace file ever carries it — this class and the
 * {@code ANTHROPIC_API_KEY} / {@code CLAUDE_API_KEY} environment variables
 * are the only doors (the {@code RpcSecrets}/{@code Passwords} idiom,
 * verbatim).
 *
 * <p><b>Honest fallback:</b> when no keyring backend is reachable
 * (headless test runs, a platform without a provider, a broken keychain),
 * every {@code Keyring} call is caught — any {@code Throwable}, because a
 * missing backend can surface as errors as well as exceptions — and the
 * key is held in an in-memory, process-lifetime slot instead. It is never
 * silently written to disk. Tests force the fallback directly so they
 * never need the OS keychain.
 *
 * <p>All methods are thread-safe and callable from any thread except the
 * EDT (the keyring may block on OS calls).
 */
public final class KvasirKeys {

    private static final Logger LOG = Logger.getLogger(KvasirKeys.class.getName());
    private static final String KEY = "nmox.kvasir.apikey";
    /**
     * The key's name before the v2.95.0 rename (ORACLE → KVASIR). Read as
     * a fallback and moved under {@link #KEY} on first use, so a key a
     * user stored under the old name is never silently lost.
     */
    static final String LEGACY_KEY = "nmox.oracle.apikey";
    /**
     * The environment fallbacks, tried in order — first non-blank wins.
     * {@code CLAUDE_API_KEY} is honored because it is what the Claude CLI
     * exports; {@code ANTHROPIC_API_KEY} is the canonical SDK name.
     */
    private static final String[] ENV_VARS = {"ANTHROPIC_API_KEY", "CLAUDE_API_KEY"};

    /**
     * The environment reader. Package-private and swappable so key-gating
     * tests exercise the env path hermetically, without touching (or
     * needing) the process environment — the {@code keyringUsable} seam
     * idiom, applied to {@code getenv}.
     */
    static java.util.function.Function<String, String> env = System::getenv;

    /** Process-lifetime fallback, used only when the keyring fails. */
    private static final ConcurrentMap<String, char[]> MEMORY = new ConcurrentHashMap<>();

    /**
     * Flipped to false the first time the keyring throws, so a broken
     * backend is probed once, not on every call. Package-private as a test
     * seam: KvasirKeysTest forces the fallback path with it.
     */
    static volatile boolean keyringUsable = true;

    private KvasirKeys() {
    }

    /** Stores the API key. A null or empty value is a {@link #delete()}. */
    public static void save(char[] apiKey) {
        if (apiKey == null || apiKey.length == 0) {
            delete();
            return;
        }
        if (keyringUsable) {
            try {
                Keyring.save(KEY, apiKey.clone(), "NMOX Studio KVASIR — Anthropic API key");
                MEMORY.remove(KEY);
                return;
            } catch (Throwable t) {
                degrade(t);
            }
        }
        MEMORY.put(KEY, apiKey.clone());
    }

    /**
     * The API key: the keychain (or in-memory fallback) if one was stored,
     * else {@code ANTHROPIC_API_KEY}, else {@code CLAUDE_API_KEY}, else
     * null. The returned array is the caller's copy — wipe it after use.
     */
    public static char[] read() {
        if (keyringUsable) {
            try {
                char[] fromKeyring = Keyring.read(KEY);
                if (fromKeyring != null && fromKeyring.length > 0) {
                    return fromKeyring;
                }
                char[] legacy = Keyring.read(LEGACY_KEY);
                if (legacy != null && legacy.length > 0) {
                    // migrate once: the new name owns it from now on
                    Keyring.save(KEY, legacy.clone(), "NMOX Studio KVASIR — Anthropic API key");
                    Keyring.delete(LEGACY_KEY);
                    return legacy;
                }
            } catch (Throwable t) {
                degrade(t);
            }
        }
        char[] fromMemory = MEMORY.get(KEY);
        if (fromMemory != null && fromMemory.length > 0) {
            return fromMemory.clone();
        }
        char[] legacyMemory = MEMORY.remove(LEGACY_KEY);
        if (legacyMemory != null && legacyMemory.length > 0) {
            MEMORY.put(KEY, legacyMemory);
            return legacyMemory.clone();
        }
        // Env fallbacks, first non-blank wins. The value is never logged or
        // echoed — the redaction rule covers env-sourced keys too.
        for (String name : ENV_VARS) {
            String value = env.apply(name);
            if (value != null && !value.isBlank()) {
                return value.toCharArray();
            }
        }
        return null;
    }

    /** True when a key is available from any source (keychain, memory, or env). */
    public static boolean hasKey() {
        char[] k = read();
        if (k == null) {
            return false;
        }
        java.util.Arrays.fill(k, '\0');
        return true;
    }

    /** Removes the stored key, if any. The env-var fallback is untouched. */
    public static void delete() {
        if (keyringUsable) {
            try {
                Keyring.delete(KEY);
                Keyring.delete(LEGACY_KEY);
            } catch (Throwable t) {
                degrade(t);
            }
        }
        for (String name : new String[] {KEY, LEGACY_KEY}) {
            char[] stale = MEMORY.remove(name);
            if (stale != null) {
                java.util.Arrays.fill(stale, '\0');
            }
        }
    }

    /** Test seam: a key stored under the pre-rename name, in the in-memory fallback. */
    static void seedLegacyForTest(char[] apiKey) {
        MEMORY.put(LEGACY_KEY, apiKey.clone());
    }

    /** Test seam: whether the in-memory fallback still holds the pre-rename entry. */
    static boolean legacyPresentForTest() {
        return MEMORY.containsKey(LEGACY_KEY);
    }

    private static void degrade(Throwable t) {
        if (keyringUsable) {
            keyringUsable = false;
            LOG.log(Level.WARNING,
                    "Keyring backend unavailable; KVASIR API key held in memory for this session only", t);
        }
        warnOnce();
    }

    /** One session-scoped balloon: silent degradation loses the key silently. */
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private static void warnOnce() {
        if (!WARNED.compareAndSet(false, true)) {
            return;
        }
        try {
            org.openide.awt.NotificationDisplayer.getDefault().notify(
                    "Keychain unavailable",
                    javax.swing.UIManager.getIcon("OptionPane.warningIcon"),
                    "The KVASIR API key will not be saved this session.", null);
        } catch (RuntimeException | LinkageError ignored) {
            // notifications unavailable (tests, stripped platform)
        }
    }
}
