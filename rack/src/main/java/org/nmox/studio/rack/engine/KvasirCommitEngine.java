package org.nmox.studio.rack.engine;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.nmox.studio.rack.engine.KvasirClient.Turn;

/**
 * The Draft Commit Message consult body — the KVASIR family's third
 * engine, same shape as {@link AskKvasirEngine} and
 * {@link KvasirEditEngine}: one method is the single API path behind
 * the key and consent gates, both provably sending nothing when they
 * refuse. The consent kind is {@code git.diff} — a staged diff is its
 * own disclosure class, neither the failure context nor a selection.
 */
public final class KvasirCommitEngine {

    public enum Status { DRAFTED, NO_KEY, NO_CONSENT, FAILED }

    public record Draft(Status status, String message) {
    }

    private final KvasirClient client;
    private final Supplier<char[]> keySource;
    private final Predicate<String> consentGate; // receives the project name

    public KvasirCommitEngine(KvasirClient client, Supplier<char[]> keySource,
            Predicate<String> consentGate) {
        this.client = client;
        this.keySource = keySource;
        this.consentGate = consentGate;
    }

    /** Drafts the message for an already-read staged diff. */
    public Draft draft(String projectName, String stat, String rawDiff,
            String model) {
        char[] key = keySource.get();
        try {
            if (key == null || key.length == 0) {
                return new Draft(Status.NO_KEY,
                        "No API key. Set one on the KVASIR device (KEY…) or export "
                        + "ANTHROPIC_API_KEY / CLAUDE_API_KEY.");
            }
            if (!consentGate.test(projectName)) {
                return new Draft(Status.NO_CONSENT, "Kept local — nothing was sent.");
            }
            String prompt = KvasirCommitMessage.assemblePrompt(projectName, stat,
                    KvasirCommitMessage.capDiff(rawDiff),
                    KvasirCommitMessage.isTruncated(rawDiff));
            String reply = client.converse(List.of(new Turn("user", prompt)),
                    model, key);
            return new Draft(Status.DRAFTED, KvasirCommitMessage.unwrapReply(reply));
        } catch (IOException e) {
            return new Draft(Status.FAILED, "KVASIR could not answer: " + e.getMessage());
        } finally {
            if (key != null) {
                Arrays.fill(key, '\0');
            }
        }
    }
}
