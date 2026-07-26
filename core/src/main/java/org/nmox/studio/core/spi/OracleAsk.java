package org.nmox.studio.core.spi;

import org.openide.util.Lookup;

/**
 * The ORACLE "explain this" seam, published by the rack module and looked
 * up by studios that want the AI surface without depending on the rack.
 *
 * <p>Same soft-dependency shape as {@link ProjectAim} and
 * {@link LiveServings} (tech-debt ledger 30): API Studio compiles against
 * core only and branches on {@link #find()} returning null, so a stripped
 * platform or a plain unit test simply has no Explain button — an honest
 * lookup miss, never a caught classloader failure.
 *
 * <p><b>The disclosure is the caller's, not the seam's.</b> The consumer
 * assembles the text it is willing to send and passes it whole; this
 * interface carries no request, response or file objects, so the rack
 * cannot reach back into a studio's data and widen what leaves the
 * machine. What the consumer hands over IS what the consent dialog for
 * that flow promises — the {@code CodeQuestion}/{@code FailureContext}
 * discipline, generalized.
 */
public interface OracleAsk {

    /**
     * The provider registered by the rack module, or null when the rack
     * is absent. Callers branch on null; they must not cache it across
     * module lifecycle events.
     */
    static OracleAsk find() {
        return Lookup.getDefault().lookup(OracleAsk.class);
    }

    /**
     * One disclosure: everything the caller is willing to send, already
     * assembled and already redacted by the caller.
     *
     * @param kind      the flow's identity, used to scope its own consent
     *                  grant — a grant given for one disclosure can never
     *                  authorize another (the consent-scoping law). Must
     *                  be a stable, short slug such as {@code "api.response"}.
     * @param title     the window's identity (e.g. "GET /orders — 500").
     * @param what      a one-line, human-readable summary of what will be
     *                  sent, shown verbatim in the consent dialog. Write
     *                  it as the user would want to read it.
     * @param body      the text sent as the conversation's opening turn.
     *                  Cap and redact BEFORE calling: the seam sends what
     *                  it is given.
     * @param question  the user's question, or null for the flow's default.
     */
    record Disclosure(String kind, String title, String what, String body, String question) {

        public Disclosure {
            kind = kind == null || kind.isBlank() ? "unknown" : kind;
            title = title == null || title.isBlank() ? "ORACLE" : title;
            what = what == null ? "" : what;
            body = body == null ? "" : body;
            // a null question would fall through to the CODE flow's
            // default ("Explain what this code does.") — wrong words for
            // a response or a query, so the seam supplies a neutral one
            question = question == null || question.isBlank()
                    ? "Explain this and tell me what to check first." : question;
        }
    }

    /**
     * Opens an ORACLE conversation about this disclosure, prompting for
     * the flow's own one-time consent first and doing nothing if it is
     * declined or no API key is set (the LCD-honesty rule: the caller
     * gets false and says so in its own status line).
     *
     * <p>Call on the EDT. Returns false when the ask did not start —
     * declined consent, no key, or an empty disclosure.
     */
    boolean explain(Disclosure disclosure);
}
