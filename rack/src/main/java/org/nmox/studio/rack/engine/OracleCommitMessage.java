package org.nmox.studio.rack.engine;

/**
 * The pure core of Draft Commit Message with ORACLE — the git chip's
 * AI verb (competitive-lens second pass: every rival drafts commit
 * messages; ours does it under the house laws). The disclosure is the
 * STAGED diff only, capped; unlike an edit, truncation here is honest
 * — a message SUMMARIZES, so a clipped diff still yields a true
 * subject, and the prompt tells the model about the clip so it never
 * pretends completeness. The reply is the message itself; a model that
 * wraps it in a fence anyway is unwrapped leniently. Nothing in this
 * flow ever runs {@code git commit} — the draft lands in an editable
 * dialog and the user does the committing.
 */
public final class OracleCommitMessage {

    private OracleCommitMessage() {
    }

    /** Diff ceiling: enough for any reviewable staged change. */
    public static final int MAX_DIFF_CHARS = 12_000;

    /**
     * The prompt, a pure function so a test asserts it verbatim. States
     * the boundary to the model the same way the consent dialog states
     * it to the user, and demands the one shape the dialog shows:
     * subject line, blank line, optional wrapped body — nothing else.
     */
    public static String assemblePrompt(String projectName, String stat,
            String diff, boolean truncated) {
        StringBuilder sb = new StringBuilder();
        sb.append("Write a git commit message for the STAGED changes below. ")
                .append("Reply with ONLY the message: an imperative subject line ")
                .append("of at most 72 characters, then, only if the change ")
                .append("needs it, a blank line and a short body wrapped at 72. ")
                .append("No fences, no commentary, no trailing sign-off.\n\n");
        sb.append("Project: ").append(projectName == null || projectName.isBlank()
                ? "(unknown)" : projectName).append('\n');
        if (stat != null && !stat.isBlank()) {
            sb.append("Files changed:\n").append(stat.strip()).append('\n');
        }
        if (truncated) {
            sb.append("Note: the diff below is TRUNCATED at ")
                    .append(MAX_DIFF_CHARS)
                    .append(" characters — summarize what is visible and rely ")
                    .append("on the file list for breadth.\n");
        }
        sb.append("Staged diff:\n").append(diff).append('\n');
        return sb.toString();
    }

    /**
     * Caps the diff on a code-point boundary (the v1.149.0 cap law — a
     * cut landing mid-emoji must not emit a lone surrogate the API
     * rejects as bad JSON). Returns the capped text; the caller asks
     * {@link #isTruncated} to tell the model and the user.
     */
    public static String capDiff(String diff) {
        if (diff == null) {
            return "";
        }
        if (diff.length() <= MAX_DIFF_CHARS) {
            return diff;
        }
        int cut = Character.isHighSurrogate(diff.charAt(MAX_DIFF_CHARS - 1))
                ? MAX_DIFF_CHARS - 1 : MAX_DIFF_CHARS;
        return diff.substring(0, cut);
    }

    public static boolean isTruncated(String diff) {
        return diff != null && diff.length() > MAX_DIFF_CHARS;
    }

    /**
     * The reply lenient-unwrap: the prompt forbids fences, but a model
     * that wraps the whole message in exactly one fenced block anyway
     * is unwrapped rather than refused — unlike an edit, there is no
     * corruption risk (the user reads and edits the draft before any
     * commit), so leniency beats a refusal here. Anything else passes
     * through trimmed.
     */
    public static String unwrapReply(String reply) {
        if (reply == null) {
            return "";
        }
        String trimmed = reply.strip();
        String fenced = OracleEdit.extractFencedCode(trimmed);
        if (fenced != null && trimmed.startsWith("```")
                && trimmed.endsWith("```")) {
            return fenced.strip();
        }
        return trimmed;
    }
}
