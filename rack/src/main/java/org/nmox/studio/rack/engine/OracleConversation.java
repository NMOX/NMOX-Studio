package org.nmox.studio.rack.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.nmox.studio.rack.engine.OracleClient.CodeQuestion;
import org.nmox.studio.rack.engine.OracleClient.FailureContext;
import org.nmox.studio.rack.engine.OracleClient.Turn;

/**
 * One ORACLE conversation about one fixed subject — a code selection
 * (the editor's Ask) or a failed run (the device's EXPLAIN). The subject
 * is fixed at the first ask; follow-ups ride the same disclosed context,
 * so the consent given for that subject covers the whole conversation
 * and nothing new ever silently joins the payload.
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
    private final String title;
    /** Builds the FIRST user turn from the first question's text. */
    private final Function<String, String> openingTurn;
    private final List<Turn> turns = new ArrayList<>();

    /** A conversation about a code selection (the editor's Ask). */
    public OracleConversation(CodeQuestion subject) {
        this.subject = subject;
        this.title = subject.fileName();
        this.openingTurn = userText -> OracleClient.assembleCodePrompt(
                new CodeQuestion(subject.fileName(), subject.language(),
                        subject.code(), userText));
    }

    private OracleConversation(String title, Function<String, String> openingTurn) {
        this.subject = null;
        this.title = title;
        this.openingTurn = openingTurn;
    }

    /**
     * A conversation about a failed run (the device's EXPLAIN). The
     * opening turn is EXACTLY {@link OracleClient#assemblePrompt} — the
     * same bytes {@code explain()} sends — so a conversation seeded from
     * a completed EXPLAIN, via {@link #record}, replays to the model
     * precisely what it was actually told. Parity is test-pinned.
     */
    public static OracleConversation forFailure(FailureContext ctx) {
        return new OracleConversation(ctx == null ? "(no failure)" : ctx.device(),
                userText -> ctx == null ? "" : OracleClient.assemblePrompt(ctx));
    }

    /** True when there is anything to talk about — the engine's refusal gate. */
    public boolean hasSubject() {
        return subject != null ? !subject.code().isBlank() : !openingTurn.apply("").isBlank();
    }

    /** The window title's identity: file name or device name. */
    public String title() {
        return title;
    }

    public CodeQuestion subject() {
        return subject;
    }

    /** Completed question/answer pairs so far. */
    public int exchanges() {
        return turns.size() / 2;
    }

    /** The recorded turns, oldest first — the dialog renders these. */
    public List<Turn> history() {
        return List.copyOf(turns);
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
            out.add(new Turn("user", openingTurn.apply(userText)));
        } else {
            String text = userText == null ? "" : userText;
            if (text.length() > MAX_FOLLOW_UP_CHARS) {
                text = OracleClient.CodeQuestion.truncate(text, MAX_FOLLOW_UP_CHARS);
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
