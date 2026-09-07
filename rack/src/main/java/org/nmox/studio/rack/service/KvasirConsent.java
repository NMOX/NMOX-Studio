package org.nmox.studio.rack.service;

import org.nmox.studio.core.util.PlainText;
import java.awt.GraphicsEnvironment;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.nmox.studio.rack.engine.KvasirClient;
import org.nmox.studio.rack.engine.KvasirClient.FailureContext;
import org.nmox.studio.rack.engine.KvasirProvider;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbPreferences;

/**
 * The KVASIR outward-data-flow consent, its own one-time gate.
 *
 * <p><b>Why this is separate from WorkspaceTrust:</b> trust is an
 * <em>inward</em> execution guard — it asks before running a stranger's
 * tasks on your machine. Sending a failed run's output to an external API
 * is an <em>outward</em> data flow that trust neither describes nor
 * covers. So KVASIR asks its own question, once, spelling out exactly
 * what leaves the machine — and, just as importantly, what does not.
 *
 * <p><b>One grant per provider.</b> A yes given to send output to
 * Anthropic's API is not a yes to send it to Google's or OpenAI's: every
 * grant key below is scoped to the {@link KvasirProvider#configured()
 * configured provider} (the Anthropic keys keep their original bare
 * names, so every grant given before v2.96.0 still holds for Claude),
 * and the dialogs name the vendor that will receive the data.
 *
 * <p>The grant is a preference, not a secret, so it lives in ordinary
 * {@link Preferences} under the userdir via NbPreferences (unlike the API
 * key, which is Keyring-only) — the
 * same {@code java.util.prefs} mechanism {@link WorkspaceTrust} uses, so
 * the consent survives a userdir reset and reads cleanly in a headless
 * test JVM. Headless/CI runs auto-allow with no prompt and no persistence,
 * mirroring {@link WorkspaceTrust#requestTrust}: with no human to answer,
 * there is no interactive attack to defend, and nothing here reaches the
 * network on its own — a key must be set and EXPLAIN must be pressed.
 */
@org.openide.util.NbBundle.Messages({
    "KvasirConsent_recipient={0}''s API ({1})",
    "KvasirConsent_failureMessage=<html><b>Send this failure to {0} for an explanation?</b><br><br>KVASIR will send <b>only</b> the following, and nothing else:<ul><li>the failing command: <code>{1}</code></li><li>its exit code: <code>{2}</code></li><li>up to five sampled error lines</li><li>the device (task lane): <code>{3}</code></li><li>the project name: <code>{4}</code></li></ul>It does <b>not</b> send your source files, environment variables, or any secret.<br><br>Your API key is used to authenticate the request. This choice is remembered.</html>",
    "KvasirConsent_sendToKvasir=Send to KVASIR",
    "KvasirConsent_keepLocal=Keep Local",
    "KvasirConsent_failureTitle=KVASIR — send failure for explanation?",
    "KvasirConsent_kindMessage=<html><b>Send this to {0} for an explanation?</b><br><br>KVASIR will send <b>only</b> the following, and nothing else:<ul><li>{1}</li></ul>It sends <b>nothing</b> beyond the line above — no environment variables, no secrets, nothing it did not name.<br><br>Your API key is used to authenticate the request. This choice is remembered for this kind of request only.</html>",
    "KvasirConsent_kindTitle=KVASIR — send for explanation?",
    "KvasirConsent_codeMessage=<html><b>Send this code selection to {0}?</b><br><br>Ask KVASIR will send <b>only</b> the following, and nothing else:<ul><li>the code you selected ({1} characters)</li><li>the file''s name: <code>{2}</code></li><li>its language: <code>{3}</code></li><li>your question</li></ul>It does <b>not</b> send the rest of the file, other files, environment variables, or any secret.<br><br>Your API key authenticates the request. This choice is remembered.</html>",
    "KvasirConsent_codeTitle=Ask KVASIR — send selected code?",
    "KvasirConsent_unknown=(unknown)"
})
public final class KvasirConsent {

    /**
     * Userdir-scoped since v2.63.0: NbPreferences lives under the IDE's own
     * userdir like every platform setting, so a fresh userdir (a reinstall,
     * a throwaway walk) starts with NO consent — the v1.39.0 global-prefs
     * blessing covers WorkspaceTrust alone. Grants recorded by earlier
     * versions in the JVM-global node are carried over ONCE, then removed.
     */
    private static final Preferences PREFS = migrated(
            NbPreferences.forModule(KvasirConsent.class),
            Preferences.userNodeForPackage(KvasirConsent.class));
    private static final String GRANTED_KEY = "kvasir.external.consent";
    /** The CODE flow's own grant. The failure-flow dialog above promises
     *  "does not send your source files" — so a grant given there can
     *  never authorize sending source. Ask KVASIR asks its own question. */
    private static final String CODE_GRANTED_KEY = "kvasir.code.consent";

    private KvasirConsent() {
    }

    /**
     * Copies every key the legacy JVM-global node still holds that the
     * userdir node lacks, then drops it from the legacy node — a one-time,
     * additive move: an existing userdir grant always wins.
     */
    static Preferences migrated(Preferences target, Preferences legacy) {
        try {
            for (String key : legacy.keys()) {
                if (target.get(key, null) == null) {
                    target.put(key, legacy.get(key, ""));
                }
                legacy.remove(key);
            }
        } catch (BackingStoreException | IllegalStateException e) {
            // the legacy store is unreadable or gone: nothing to carry over
        }
        return renamedKeys(target);
    }

    /** The key prefix every consent and model preference carried before v2.95.0. */
    static final String LEGACY_PREFIX = "oracle.";
    /** The prefix they carry now. */
    static final String PREFIX = "kvasir.";

    /**
     * The v2.95.0 rename (ORACLE → KVASIR): every {@code oracle.*} key still
     * in the node moves to its {@code kvasir.*} name, once, and the old key
     * goes — a grant a user already gave is never asked for twice because
     * the device changed its name. An existing {@code kvasir.*} value wins.
     * Idempotent, so every reader of the node may call it.
     */
    static Preferences renamedKeys(Preferences node) {
        try {
            for (String key : node.keys()) {
                if (!key.startsWith(LEGACY_PREFIX)) {
                    continue;
                }
                String renamed = PREFIX + key.substring(LEGACY_PREFIX.length());
                if (node.get(renamed, null) == null) {
                    node.put(renamed, node.get(key, ""));
                }
                node.remove(key);
            }
        } catch (BackingStoreException | IllegalStateException e) {
            // unreadable node: nothing to rename
        }
        return node;
    }

    /**
     * A grant key for the configured provider: the bare pre-v2.96.0 name
     * for Anthropic (so existing grants keep holding for Claude), the name
     * suffixed with the provider id for every other vendor — a consent
     * names its recipient.
     */
    static String scoped(String baseKey) {
        KvasirProvider p = KvasirProvider.configured();
        return p == KvasirProvider.ANTHROPIC ? baseKey : baseKey + "." + p.id();
    }

    /** The vendor the configured provider's request goes to, for the dialogs. */
    private static String recipient() {
        KvasirProvider p = KvasirProvider.configured();
        return Bundle.KvasirConsent_recipient(escape(p.vendor()), escape(p.product()));
    }

    /** True once the user has agreed to send failure context to the configured provider. */
    public static boolean isGranted() {
        return PREFS.getBoolean(scoped(GRANTED_KEY), false);
    }

    /** Records consent (used after the dialog is accepted). */
    static void grant() {
        PREFS.putBoolean(scoped(GRANTED_KEY), true);
    }

    /** Test hook: forget the configured provider's grant. */
    static void revokeForTest() {
        PREFS.remove(scoped(GRANTED_KEY));
    }

    /**
     * Ensures consent, prompting once if needed. Returns true when KVASIR
     * may send. The dialog names exactly what is sent — the failing
     * command, its exit code, up to five error lines, the device name and
     * the project name — and what is not: no source, no environment, no
     * secrets. A blocking platform dialog, safe to call from any thread.
     */
    public static boolean requestConsent(FailureContext ctx) {
        if (isGranted()) {
            return true;
        }
        // No human present (CI, tests, headless): no prompt to answer, and
        // nothing here sends on its own. Allow, but do not persist a grant a
        // user never made.
        if (GraphicsEnvironment.isHeadless()) {
            return true;
        }
        String message = Bundle.KvasirConsent_failureMessage(recipient(), escape(ctx.command()),
                String.valueOf(ctx.exitCode()), escape(ctx.device()), escape(ctx.projectName()));
        Object sendOption = Bundle.KvasirConsent_sendToKvasir();
        Object keepLocal = Bundle.KvasirConsent_keepLocal();
        NotifyDescriptor nd = new NotifyDescriptor(
                new javax.swing.JLabel(message),
                Bundle.KvasirConsent_failureTitle(),
                NotifyDescriptor.DEFAULT_OPTION,
                NotifyDescriptor.QUESTION_MESSAGE,
                new Object[]{sendOption, keepLocal},
                keepLocal);
        if (DialogDisplayer.getDefault().notify(nd) == sendOption) {
            grant();
            return true;
        }
        return false;
    }

    // ---- kind-scoped consent for SPI-published flows ---------------------

    /** True once this flow kind has been granted. Package-visible key shape
     *  mirrors the two named grants above: one preference per disclosure. */
    public static boolean isKindGranted(String kind) {
        return PREFS.getBoolean(scoped(kindKey(kind)), false);
    }

    static void grantKind(String kind) {
        PREFS.putBoolean(scoped(kindKey(kind)), true);
    }

    /** Test hook: forget one kind's grant for the configured provider. */
    static void revokeKindForTest(String kind) {
        PREFS.remove(scoped(kindKey(kind)));
    }

    private static String kindKey(String kind) {
        return "kvasir.kind." + (kind == null || kind.isBlank() ? "unknown" : kind) + ".consent";
    }

    /**
     * Ensures a kind-scoped consent, prompting once if needed. The
     * consent-scoping law applied to studio flows reaching KVASIR through
     * {@code core.spi.KvasirAsk}: each disclosure kind earns its own yes,
     * because a grant given for one kind of data can never authorize
     * another. The caller supplies the "what is sent" line verbatim — it
     * is the disclosure, so it must be the caller's own words about its
     * own data. Blocking and Swing-safe; headless auto-allows without
     * persisting, like the two flows above.
     */
    public static boolean requestKindConsent(String kind, String what) {
        if (isKindGranted(kind)) {
            return true;
        }
        if (GraphicsEnvironment.isHeadless()) {
            return true;
        }
        // the disclosure line is the whole truth — some kinds DO carry file
        // content (space.check sends the checked file, browser.error a
        // source excerpt), so the old fixed "does not send your source
        // files" line could contradict the bullet it sat under (caught
        // live, v2.39.5). Say only what is always true: nothing beyond
        // what the bullet names.
        String message = Bundle.KvasirConsent_kindMessage(recipient(), escape(what));
        Object sendOption = Bundle.KvasirConsent_sendToKvasir();
        Object keepLocal = Bundle.KvasirConsent_keepLocal();
        NotifyDescriptor nd = new NotifyDescriptor(
                new javax.swing.JLabel(message),
                Bundle.KvasirConsent_kindTitle(),
                NotifyDescriptor.DEFAULT_OPTION,
                NotifyDescriptor.QUESTION_MESSAGE,
                new Object[]{sendOption, keepLocal},
                keepLocal);
        if (DialogDisplayer.getDefault().notify(nd) == sendOption) {
            grantKind(kind);
            return true;
        }
        return false;
    }

    // ---- the code-question flow's own consent ----------------------------

    /** True once the user has agreed to send SELECTED CODE to the API. */
    public static boolean isCodeGranted() {
        return PREFS.getBoolean(scoped(CODE_GRANTED_KEY), false);
    }

    static void grantCode() {
        PREFS.putBoolean(scoped(CODE_GRANTED_KEY), true);
    }

    /** Test hook: forget the configured provider's code grant. */
    static void revokeCodeForTest() {
        PREFS.remove(scoped(CODE_GRANTED_KEY));
    }

    /**
     * Ensures the CODE consent, prompting once if needed. Separate from
     * {@link #requestConsent} by design: that dialog promises source never
     * leaves the machine, and this flow sends exactly the selection — so
     * it must earn its own yes. Names what is sent (the selected code,
     * the file's name and language, the question) and what is not (the
     * rest of the file, other files, environment, secrets). Blocking and
     * Swing-safe; headless auto-allows without persisting, like the
     * failure flow.
     */
    public static boolean requestCodeConsent(KvasirClient.CodeQuestion q) {
        if (isCodeGranted()) {
            return true;
        }
        if (GraphicsEnvironment.isHeadless()) {
            return true;
        }
        String message = Bundle.KvasirConsent_codeMessage(recipient(), String.valueOf(q.code().length()),
                escape(q.fileName()), escape(q.language()));
        Object sendOption = Bundle.KvasirConsent_sendToKvasir();
        Object keepLocal = Bundle.KvasirConsent_keepLocal();
        NotifyDescriptor nd = new NotifyDescriptor(
                new javax.swing.JLabel(message),
                Bundle.KvasirConsent_codeTitle(),
                NotifyDescriptor.DEFAULT_OPTION,
                NotifyDescriptor.QUESTION_MESSAGE,
                new Object[]{sendOption, keepLocal},
                keepLocal);
        if (DialogDisplayer.getDefault().notify(nd) == sendOption) {
            grantCode();
            return true;
        }
        return false;
    }

    private static String escape(String s) {
        if (s == null || s.isBlank()) {
            return Bundle.KvasirConsent_unknown();
        }
        return PlainText.escape(s);
    }
}
