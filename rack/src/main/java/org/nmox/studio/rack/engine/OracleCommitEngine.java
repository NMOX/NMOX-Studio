package org.nmox.studio.rack.engine;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.nmox.studio.rack.engine.OracleClient.Turn;

/**
 * The Draft Commit Message consult body — the ORACLE family's third
 * engine, same shape as {@link AskOracleEngine} and
 * {@link OracleEditEngine}: one method is the single API path behind
 * the key and consent gates, both provably sending nothing when they
 * refuse. The consent kind is {@code git.diff} — a staged diff is its
 * own disclosure class, neither the failure context nor a selection.
 */
public final class OracleCommitEngine {

    public enum Status { DRAFTED, NO_KEY, NO_CONSENT, FAILED }

    public record Draft(Status status, String message) {
    }

    private final OracleClient client;
    private final Supplier<char[]> keySource;
    private final Predicate<String> consentGate; // receives the project name

    public OracleCommitEngine(OracleClient client, Supplier<char[]> keySource,
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
                        "No API key. Set one on the ORACLE device (KEY…) or export "
                        + "ANTHROPIC_API_KEY / CLAUDE_API_KEY.");
            }
            if (!consentGate.test(projectName)) {
                return new Draft(Status.NO_CONSENT, "Kept local — nothing was sent.");
            }
            String prompt = OracleCommitMessage.assemblePrompt(projectName, stat,
                    OracleCommitMessage.capDiff(rawDiff),
                    OracleCommitMessage.isTruncated(rawDiff));
            String reply = client.converse(List.of(new Turn("user", prompt)),
                    model, key);
            return new Draft(Status.DRAFTED, OracleCommitMessage.unwrapReply(reply));
        } catch (IOException e) {
            return new Draft(Status.FAILED, "ORACLE could not answer: " + e.getMessage());
        } finally {
            if (key != null) {
                Arrays.fill(key, '\0');
            }
        }
    }
}
