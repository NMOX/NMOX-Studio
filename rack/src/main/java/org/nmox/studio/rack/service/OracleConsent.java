package org.nmox.studio.rack.service;

import java.awt.GraphicsEnvironment;
import java.util.prefs.Preferences;
import org.nmox.studio.rack.engine.OracleClient.FailureContext;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;

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
 * {@link Preferences} (unlike the API key, which is Keyring-only) — the
 * same {@code java.util.prefs} mechanism {@link WorkspaceTrust} uses, so
 * the consent survives a userdir reset and reads cleanly in a headless
 * test JVM. Headless/CI runs auto-allow with no prompt and no persistence,
 * mirroring {@link WorkspaceTrust#requestTrust}: with no human to answer,
 * there is no interactive attack to defend, and nothing here reaches the
 * network on its own — a key must be set and EXPLAIN must be pressed.
 */
public final class OracleConsent {

    private static final Preferences PREFS = Preferences.userNodeForPackage(OracleConsent.class);
    private static final String GRANTED_KEY = "oracle.external.consent";

    private OracleConsent() {
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

    private static String escape(String s) {
        if (s == null || s.isBlank()) {
            return "(unknown)";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
