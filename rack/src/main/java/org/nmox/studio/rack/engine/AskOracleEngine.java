package org.nmox.studio.rack.engine;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.nmox.studio.rack.engine.OracleClient.CodeQuestion;

/**
 * The Ask ORACLE consult body — the editor twin of OracleDevice's
 * {@code consult}. One method is the single API path, and it never
 * touches the network unless there is a non-blank selection, a key, and
 * the CODE consent — the same two mutation-proven gates the rack's
 * EXPLAIN has, plus the selection refusal in front of them. Pure logic
 * with injected seams so plain tests reach everything; the Swing action
 * around it owns threading and dialogs.
 */
public final class AskOracleEngine {

    /** The verdict shapes the action turns into honest dialogs. */
    public enum Status { ANSWERED, NO_SELECTION, NO_KEY, NO_CONSENT, FAILED }

    public record Result(Status status, String text) {
    }

    private final OracleClient client;
    private final Supplier<char[]> keySource;
    private final Predicate<CodeQuestion> consentGate;

    /** Production wiring is supplied by the action; tests inject spies. */
    public AskOracleEngine(OracleClient client, Supplier<char[]> keySource,
            Predicate<CodeQuestion> consentGate) {
        this.client = client;
        this.keySource = keySource;
        this.consentGate = consentGate;
    }

    /**
     * Answers the question, or refuses honestly. Synchronous — the caller
     * runs it off the EDT. The key array is wiped before returning.
     */
    public Result answer(CodeQuestion q, String model) {
        if (q == null || q.code().isBlank()) {
            return new Result(Status.NO_SELECTION,
                    "Select some code first — Ask ORACLE sends only the selection.");
        }
        char[] key = keySource.get();
        try {
            if (key == null || key.length == 0) {
                return new Result(Status.NO_KEY,
                        "No API key. Set one on the ORACLE device (KEY…) or export "
                        + "ANTHROPIC_API_KEY / CLAUDE_API_KEY.");
            }
            if (!consentGate.test(q)) {
                return new Result(Status.NO_CONSENT, "Kept local — nothing was sent.");
            }
            return new Result(Status.ANSWERED, client.ask(q, model, key));
        } catch (IOException e) {
            return new Result(Status.FAILED, "ORACLE could not answer: " + e.getMessage());
        } finally {
            if (key != null) {
                Arrays.fill(key, '\0');
            }
        }
    }
}
