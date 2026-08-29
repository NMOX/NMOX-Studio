package org.nmox.studio.rack.engine;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.nmox.studio.rack.engine.OracleClient.Turn;
import org.nmox.studio.rack.engine.OracleEdit.EditRequest;

/**
 * The Edit with ORACLE consult body — {@link AskOracleEngine}'s editing
 * sibling. One method is the single API path, behind the same two
 * mutation-proven gates (key, consent); what comes back is parsed by
 * {@link OracleEdit#extractFencedCode} and anything that is not exactly
 * one code block is refused as {@code NOT_CODE} — a proposal is only
 * ever real code, and it still changes nothing until the user approves
 * the preview. Pure logic with injected seams; the Swing action owns
 * threading, dialogs, and the apply.
 */
public final class OracleEditEngine {

    /** The verdict shapes the action turns into honest dialogs. */
    public enum Status { PROPOSED, NO_KEY, NO_CONSENT, NOT_CODE, NO_CHANGE, FAILED }

    public record Proposal(Status status, String message, String replacement) {
    }

    private final OracleClient client;
    private final Supplier<char[]> keySource;
    private final Predicate<EditRequest> consentGate;

    /** Production wiring is supplied by the caller; tests inject spies. */
    public OracleEditEngine(OracleClient client, Supplier<char[]> keySource,
            Predicate<EditRequest> consentGate) {
        this.client = client;
        this.keySource = keySource;
        this.consentGate = consentGate;
    }

    /**
     * Asks for the revised selection. Never touches the network without a
     * key and the CODE consent — the same gate order as every other
     * ORACLE flow, so a decline provably sends nothing.
     */
    public Proposal propose(EditRequest r, String model) {
        char[] key = keySource.get();
        try {
            if (key == null || key.length == 0) {
                return new Proposal(Status.NO_KEY,
                        "No API key. Set one on the ORACLE device (KEY…) or export "
                        + "ANTHROPIC_API_KEY / CLAUDE_API_KEY.", null);
            }
            if (!consentGate.test(r)) {
                return new Proposal(Status.NO_CONSENT,
                        "Kept local — nothing was sent.", null);
            }
            String reply = client.converse(
                    List.of(new Turn("user", OracleEdit.assembleEditPrompt(r))),
                    model, key);
            String replacement = OracleEdit.extractFencedCode(reply);
            if (replacement == null) {
                // zero fences is the model's honest "can't" channel — show
                // its prose; 2+ blocks is ambiguity — never guess which
                return new Proposal(Status.NOT_CODE,
                        "ORACLE did not reply with a single code block — "
                        + "nothing was changed.\n\n" + reply, null);
            }
            if (replacement.equals(r.code())) {
                return new Proposal(Status.NO_CHANGE,
                        "ORACLE proposes no change to this selection.", null);
            }
            return new Proposal(Status.PROPOSED, "", replacement);
        } catch (IOException e) {
            return new Proposal(Status.FAILED,
                    "ORACLE could not answer: " + e.getMessage(), null);
        } finally {
            if (key != null) {
                Arrays.fill(key, '\0');
            }
        }
    }
}
