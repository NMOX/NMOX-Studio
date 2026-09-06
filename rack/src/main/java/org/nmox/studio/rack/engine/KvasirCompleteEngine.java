package org.nmox.studio.rack.engine;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.nmox.studio.rack.engine.KvasirClient.Turn;
import org.nmox.studio.rack.engine.KvasirComplete.CompletionRequest;

/**
 * The gated send behind Complete with KVASIR — the {@link KvasirEditEngine}
 * shape with an insertion instead of a replacement. Both gates sit in
 * front of the one call: no key → nothing sent; consent declined →
 * nothing sent; the key is wiped after use. A reply that is not exactly
 * one fenced block (prose, or two blocks) is refused out loud, never
 * guessed at, and an empty insertion is its own honest verdict.
 */
public final class KvasirCompleteEngine {

    public enum Status { PROPOSED, NO_KEY, NO_CONSENT, NOT_CODE, EMPTY, FAILED }

    /** The verdict: a status, a message for anything but PROPOSED, the insertion. */
    public record Proposal(Status status, String message, String insertion) {
    }

    private final KvasirClient client;
    private final Supplier<char[]> keySource;
    private final Predicate<CompletionRequest> consentGate;

    public KvasirCompleteEngine(KvasirClient client, Supplier<char[]> keySource,
            Predicate<CompletionRequest> consentGate) {
        this.client = client;
        this.keySource = keySource;
        this.consentGate = consentGate;
    }

    /** Asks for the insertion at the caret; {@code lineBeforeCaret} lets an echoed prefix be trimmed. */
    public Proposal propose(CompletionRequest r, String lineBeforeCaret, String model) {
        char[] key = keySource.get();
        try {
            if (key == null || key.length == 0) {
                return new Proposal(Status.NO_KEY,
                        "No API key. Set one on the KVASIR device (KEY…) or export "
                        + "ANTHROPIC_API_KEY / CLAUDE_API_KEY.", null);
            }
            if (!consentGate.test(r)) {
                return new Proposal(Status.NO_CONSENT, "Kept local — nothing was sent.", null);
            }
            String reply = client.converse(
                    List.of(new Turn("user", KvasirComplete.assembleCompletionPrompt(r))),
                    model, key);
            if (KvasirEdit.extractFencedCode(reply) == null) {
                return new Proposal(Status.NOT_CODE,
                        "KVASIR did not reply with a single code block — nothing was "
                        + "inserted.\n\n" + reply, null);
            }
            String insertion = KvasirComplete.extractInsertion(reply, lineBeforeCaret);
            if (insertion == null) {
                return new Proposal(Status.EMPTY,
                        "KVASIR proposes nothing to add here.", null);
            }
            return new Proposal(Status.PROPOSED, "", insertion);
        } catch (IOException e) {
            return new Proposal(Status.FAILED, "KVASIR could not answer: " + e.getMessage(), null);
        } finally {
            if (key != null) {
                Arrays.fill(key, '\0');
            }
        }
    }
}
