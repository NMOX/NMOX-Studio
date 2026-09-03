package org.nmox.studio.rack.service;

import java.awt.GraphicsEnvironment;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.nmox.studio.rack.engine.OracleClient;
import org.nmox.studio.rack.engine.OracleClient.FailureContext;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbPreferences;

/**
 * The ORACLE outward-data-flow consent, its own one-time gate.
 *
 * <p><b>Why this is separate from WorkspaceTrust:</b> trust is an
 * <em>inward</em> execution guard — it asks before running a stranger's
 * tasks on your machine. Sending a failed run's output to an external API
 * is an <em>outward</em> data flow that trust neither describes nor
 * covers. So ORACLE asks its own question, once, spelling out exactly
 * what leaves the machine — and, just as importantly, what does not.
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
public final class OracleConsent {

    /**
     * Userdir-scoped since v2.63.0: NbPreferences lives under the IDE's own
     * userdir like every platform setting, so a fresh userdir (a reinstall,
     * a throwaway walk) starts with NO consent — the v1.39.0 global-prefs
     * blessing covers WorkspaceTrust alone. Grants recorded by earlier
     * versions in the JVM-global node are carried over ONCE, then removed.
     */
    private static final Preferences PREFS = migrated(
            NbPreferences.forModule(OracleConsent.class),
            Preferences.userNodeForPackage(OracleConsent.class));
    private static final String GRANTED_KEY = "oracle.external.consent";
    /** The CODE flow's own grant. The failure-flow dialog above promises
     *  "does not send your source files" — so a grant given there can
     *  never authorize sending source. Ask ORACLE asks its own question. */
    private static final String CODE_GRANTED_KEY = "oracle.code.consent";

    private OracleConsent() {
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
        return target;
    }

    /** True once the user has agreed to send failure context to the API. */
    public static boolean isGranted() {
        return PREFS.getBoolean(GRANTED_KEY, false);
    }

    /** Records consent (used after the dialog is accepted). */
    static void grant() {
        PREFS.putBoolean(GRANTED_KEY, true);
    }

    /** Test hook: forget the grant. */
    static void revokeForTest() {
        PREFS.remove(GRANTED_KEY);
    }

    /**
     * Ensures consent, prompting once if needed. Returns true when ORACLE
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
        String message = "<html><b>Send this failure to Anthropic's API for an explanation?</b>"
                + "<br><br>ORACLE will send <b>only</b> the following, and nothing else:"
                + "<ul>"
                + "<li>the failing command: <code>" + escape(ctx.command()) + "</code></li>"
                + "<li>its exit code: <code>" + ctx.exitCode() + "</code></li>"
                + "<li>up to five sampled error lines</li>"
                + "<li>the device (task lane): <code>" + escape(ctx.device()) + "</code></li>"
                + "<li>the project name: <code>" + escape(ctx.projectName()) + "</code></li>"
                + "</ul>"
                + "It does <b>not</b> send your source files, environment variables, or any secret."
                + "<br><br>Your API key is used to authenticate the request. This choice is remembered."
                + "</html>";
        Object sendOption = "Send to ORACLE";
        NotifyDescriptor nd = new NotifyDescriptor(
                new javax.swing.JLabel(message),
                "ORACLE — send failure for explanation?",
                NotifyDescriptor.DEFAULT_OPTION,
                NotifyDescriptor.QUESTION_MESSAGE,
                new Object[]{sendOption, "Keep Local"},
                "Keep Local");
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
        return PREFS.getBoolean(kindKey(kind), false);
    }

    static void grantKind(String kind) {
        PREFS.putBoolean(kindKey(kind), true);
    }

    /** Test hook: forget one kind's grant. */
    static void revokeKindForTest(String kind) {
        PREFS.remove(kindKey(kind));
    }

    private static String kindKey(String kind) {
        return "oracle.kind." + (kind == null || kind.isBlank() ? "unknown" : kind) + ".consent";
    }

    /**
     * Ensures a kind-scoped consent, prompting once if needed. The
     * consent-scoping law applied to studio flows reaching ORACLE through
     * {@code core.spi.OracleAsk}: each disclosure kind earns its own yes,
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
        String message = "<html><b>Send this to Anthropic's API for an explanation?</b>"
                + "<br><br>ORACLE will send <b>only</b> the following, and nothing else:"
                + "<ul><li>" + escape(what) + "</li></ul>"
                // the disclosure line above is the whole truth — some kinds
                // DO carry file content (space.check sends the checked file,
                // browser.error a source excerpt), so the old fixed "does
                // not send your source files" line could contradict the
                // bullet it sat under (caught live, v2.39.5). Say only what
                // is always true: nothing beyond what the bullet names.
                + "It sends <b>nothing</b> beyond the line above — no environment"
                + " variables, no secrets, nothing it did not name."
                + "<br><br>Your API key is used to authenticate the request. This choice is remembered"
                + " for this kind of request only."
                + "</html>";
        Object sendOption = "Send to ORACLE";
        NotifyDescriptor nd = new NotifyDescriptor(
                new javax.swing.JLabel(message),
                "ORACLE — send for explanation?",
                NotifyDescriptor.DEFAULT_OPTION,
                NotifyDescriptor.QUESTION_MESSAGE,
                new Object[]{sendOption, "Keep Local"},
                "Keep Local");
        if (DialogDisplayer.getDefault().notify(nd) == sendOption) {
            grantKind(kind);
            return true;
        }
        return false;
    }

    // ---- the code-question flow's own consent ----------------------------

    /** True once the user has agreed to send SELECTED CODE to the API. */
    public static boolean isCodeGranted() {
        return PREFS.getBoolean(CODE_GRANTED_KEY, false);
    }

    static void grantCode() {
        PREFS.putBoolean(CODE_GRANTED_KEY, true);
    }

    /** Test hook: forget the code grant. */
    static void revokeCodeForTest() {
        PREFS.remove(CODE_GRANTED_KEY);
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
    public static boolean requestCodeConsent(OracleClient.CodeQuestion q) {
        if (isCodeGranted()) {
            return true;
        }
        if (GraphicsEnvironment.isHeadless()) {
            return true;
        }
        String message = "<html><b>Send this code selection to Anthropic's API?</b>"
                + "<br><br>Ask ORACLE will send <b>only</b> the following, and nothing else:"
                + "<ul>"
                + "<li>the code you selected (" + q.code().length() + " characters)</li>"
                + "<li>the file's name: <code>" + escape(q.fileName()) + "</code></li>"
                + "<li>its language: <code>" + escape(q.language()) + "</code></li>"
                + "<li>your question</li>"
                + "</ul>"
                + "It does <b>not</b> send the rest of the file, other files, "
                + "environment variables, or any secret."
                + "<br><br>Your API key authenticates the request. This choice is remembered."
                + "</html>";
        Object sendOption = "Send to ORACLE";
        NotifyDescriptor nd = new NotifyDescriptor(
                new javax.swing.JLabel(message),
                "Ask ORACLE — send selected code?",
                NotifyDescriptor.DEFAULT_OPTION,
                NotifyDescriptor.QUESTION_MESSAGE,
                new Object[]{sendOption, "Keep Local"},
                "Keep Local");
        if (DialogDisplayer.getDefault().notify(nd) == sendOption) {
            grantCode();
            return true;
        }
        return false;
    }

    private static String escape(String s) {
        if (s == null || s.isBlank()) {
            return "(unknown)";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
