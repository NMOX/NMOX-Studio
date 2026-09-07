package org.nmox.studio.rack.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.keyring.Keyring;
import org.nmox.studio.rack.engine.KvasirProvider;

/**
 * Where the KVASIR API keys actually live: the OS keychain, via the
 * platform {@link Keyring}, one entry per provider ({@code
 * "nmox.kvasir.apikey"} for Anthropic — the original name —
 * {@code "nmox.kvasir.openai.apikey"}, {@code "nmox.kvasir.google.apikey"}).
 * No preference and no workspace file ever carries a key — this class
 * and each provider's environment variables ({@code ANTHROPIC_API_KEY} /
 * {@code CLAUDE_API_KEY}, {@code OPENAI_API_KEY} / {@code CHATGPT_API_KEY}, {@code GEMINI_API_KEY} /
 * {@code GOOGLE_API_KEY}) are the only doors (the
 * {@code RpcSecrets}/{@code Passwords} idiom, verbatim).
 *
 * <p>The no-argument methods act on the {@link KvasirProvider#configured()
 * configured provider}, which is what every engine's {@code keySource}
 * seam wants: the key for whoever is answering right now. Keys never
 * cross providers — an OpenAI key is never offered to Google's API.
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
    /** The Anthropic entry — the name every key had before v2.96.0's providers. */
    private static final String KEY = "nmox.kvasir.apikey";
    /**
     * The key's name before the v2.95.0 rename (ORACLE → KVASIR). Read as
     * a fallback and moved under {@link #KEY} on first use, so a key a
     * user stored under the old name is never silently lost. Anthropic
     * only: the other providers never had an ORACLE-era entry.
     */
    static final String LEGACY_KEY = "nmox.oracle.apikey";

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

    // ---- the configured provider (what every engine's keySource wants) ----

    /** Stores the configured provider's key. A null or empty value is a {@link #delete()}. */
    public static void save(char[] apiKey) {
        save(KvasirProvider.configured(), apiKey);
    }

    /**
     * The configured provider's key: its keychain entry (or in-memory
     * fallback) if one was stored, else its environment variables in
     * order, else null. The returned array is the caller's copy — wipe it
     * after use.
     */
    public static char[] read() {
        return read(KvasirProvider.configured());
    }

    /** True when the configured provider has a key from any source. */
    public static boolean hasKey() {
        return hasKey(KvasirProvider.configured());
    }

    /** Removes the configured provider's stored key, if any. Env fallbacks are untouched. */
    public static void delete() {
        delete(KvasirProvider.configured());
    }

    // ---- per provider ------------------------------------------------------

    /** Stores one provider's key. A null or empty value deletes it. */
    public static void save(KvasirProvider provider, char[] apiKey) {
        if (apiKey == null || apiKey.length == 0) {
            delete(provider);
            return;
        }
        String name = provider.keyringName();
        if (keyringUsable) {
            try {
                Keyring.save(name, apiKey.clone(), description(provider));
                MEMORY.remove(name);
                return;
            } catch (Throwable t) {
                degrade(t);
            }
        }
        MEMORY.put(name, apiKey.clone());
    }

    /**
     * One provider's key: the keychain (or in-memory fallback) if one was
     * stored, else that provider's environment variables in order, else
     * null. Never another provider's key. The returned array is the
     * caller's copy — wipe it after use.
     */
    public static char[] read(KvasirProvider provider) {
        String name = provider.keyringName();
        boolean legacyEligible = provider == KvasirProvider.ANTHROPIC;
        if (keyringUsable) {
            try {
                char[] fromKeyring = Keyring.read(name);
                if (fromKeyring != null && fromKeyring.length > 0) {
                    return fromKeyring;
                }
                if (legacyEligible) {
                    char[] legacy = Keyring.read(LEGACY_KEY);
                    if (legacy != null && legacy.length > 0) {
                        // migrate once: the new name owns it from now on
                        Keyring.save(KEY, legacy.clone(), description(provider));
                        Keyring.delete(LEGACY_KEY);
                        return legacy;
                    }
                }
            } catch (Throwable t) {
                degrade(t);
            }
        }
        char[] fromMemory = MEMORY.get(name);
        if (fromMemory != null && fromMemory.length > 0) {
            return fromMemory.clone();
        }
        if (legacyEligible) {
            char[] legacyMemory = MEMORY.remove(LEGACY_KEY);
            if (legacyMemory != null && legacyMemory.length > 0) {
                MEMORY.put(KEY, legacyMemory);
                return legacyMemory.clone();
            }
        }
        // Env fallbacks, first non-blank wins. The value is never logged or
        // echoed — the redaction rule covers env-sourced keys too.
        for (String var : provider.envVars()) {
            String value = env.apply(var);
            if (value != null && !value.isBlank()) {
                return value.toCharArray();
            }
        }
        return null;
    }

    /** True when a key is available for the provider from any source. */
    public static boolean hasKey(KvasirProvider provider) {
        char[] k = read(provider);
        if (k == null) {
            return false;
        }
        java.util.Arrays.fill(k, '\0');
        return true;
    }

    /** Removes one provider's stored key, if any. Its env fallbacks are untouched. */
    public static void delete(KvasirProvider provider) {
        String name = provider.keyringName();
        boolean legacyEligible = provider == KvasirProvider.ANTHROPIC;
        if (keyringUsable) {
            try {
                Keyring.delete(name);
                if (legacyEligible) {
                    Keyring.delete(LEGACY_KEY);
                }
            } catch (Throwable t) {
                degrade(t);
            }
        }
        char[] stale = MEMORY.remove(name);
        if (stale != null) {
            java.util.Arrays.fill(stale, '\0');
        }
        if (legacyEligible) {
            char[] staleLegacy = MEMORY.remove(LEGACY_KEY);
            if (staleLegacy != null) {
                java.util.Arrays.fill(staleLegacy, '\0');
            }
        }
    }

    private static String description(KvasirProvider provider) {
        return "NMOX Studio KVASIR — " + provider.vendor() + " API key";
    }

    /** Test seam: a key stored under the pre-rename name, in the in-memory fallback. */
    static void seedLegacyForTest(char[] apiKey) {
        MEMORY.put(LEGACY_KEY, apiKey.clone());
    }

    /** Test seam: whether the in-memory fallback still holds the pre-rename entry. */
    static boolean legacyPresentForTest() {
        return MEMORY.containsKey(LEGACY_KEY);
    }

    /** Test seam: every provider's stored key forgotten. */
    static void deleteAllForTest() {
        for (KvasirProvider p : KvasirProvider.values()) {
            delete(p);
        }
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
