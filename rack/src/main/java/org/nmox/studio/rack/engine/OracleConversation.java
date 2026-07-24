package org.nmox.studio.rack.engine;

import java.util.ArrayList;
import java.util.List;
import org.nmox.studio.rack.engine.OracleClient.CodeQuestion;
import org.nmox.studio.rack.engine.OracleClient.Turn;

/**
 * One Ask ORACLE conversation about one selection — the pure state the
 * dialog holds. The subject (the {@link CodeQuestion}) is fixed at the
 * first ask; follow-ups ride the same disclosed context, so consent
 * given for the selection covers the whole conversation and nothing new
 * ever silently joins the payload.
 *
 * <p>Bounded by law, like every send in the product: at most
 * {@link #MAX_EXCHANGES} question/answer pairs, each follow-up capped at
 * {@link #MAX_FOLLOW_UP_CHARS}. Past the cap {@link #canAsk} goes false
 * and the UI says so honestly — no silent trimming, because a trimmed
 * history would misrepresent what the model was actually told.
 */
public final class OracleConversation {

    public static final int MAX_EXCHANGES = 10;
    public static final int MAX_FOLLOW_UP_CHARS = 2_000;

    private final CodeQuestion subject;
    private final List<Turn> turns = new ArrayList<>();

    public OracleConversation(CodeQuestion subject) {
        this.subject = subject;
    }

    public CodeQuestion subject() {
        return subject;
    }

    /** Completed question/answer pairs so far. */
    public int exchanges() {
        return turns.size() / 2;
    }

    /** False once the exchange cap is reached — the UI disables input. */
    public boolean canAsk() {
        return exchanges() < MAX_EXCHANGES;
    }

    /**
     * The turn list to send for the next ask: the recorded history plus
     * the new user turn. The FIRST user turn is the full disclosed code
     * prompt; follow-ups are plain text (capped). Pure — nothing is
     * recorded until {@link #record} confirms an answer arrived.
     */
    public List<Turn> outgoing(String userText) {
        List<Turn> out = new ArrayList<>(turns);
        if (out.isEmpty()) {
            out.add(new Turn("user", OracleClient.assembleCodePrompt(
                    new CodeQuestion(subject.fileName(), subject.language(),
                            subject.code(), userText))));
        } else {
            String text = userText == null ? "" : userText;
            if (text.length() > MAX_FOLLOW_UP_CHARS) {
                text = text.substring(0, MAX_FOLLOW_UP_CHARS);
            }
            out.add(new Turn("user", text));
        }
        return out;
    }

    /** Commits an exchange after the API answered. */
    public void record(String userText, String answer) {
        List<Turn> sent = outgoing(userText);
        turns.clear();
        turns.addAll(sent);
        turns.add(new Turn("assistant", answer));
    }
}
