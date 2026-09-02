package org.nmox.studio.rack.engine;

/**
 * Complete with ORACLE, the pure half: the request (the code around the
 * caret, capped and refused-not-truncated), the prompt, and the reply
 * shape — exactly one fenced block holding ONLY the text to insert at the
 * caret.
 *
 * <p>This is R4 of the competitive lens on the house's terms: no network
 * without a gesture (the chord or the popup item), the CODE consent
 * (the same data classes as Ask/Edit — code around the caret, the file's
 * name, its language), every send bounded and named. There is no stream
 * and no keystroke ever leaves the machine on its own.
 */
public final class OracleComplete {

    /** Characters of code before the caret that travel; the rest is cut from the FRONT. */
    public static final int MAX_BEFORE_CHARS = 6_000;
    /** Characters of code after the caret that travel; the rest is cut from the END. */
    public static final int MAX_AFTER_CHARS = 1_500;
    /**
     * The marker the prompt places at the caret. Deliberately no angle
     * brackets: a bracketed marker made find-sec-bugs read the prompt as
     * XML under construction (POTENTIAL_XML_INJECTION on the first verify),
     * and the honest fix removes the trigger rather than excluding the rule.
     */
    public static final String CURSOR = "⟪CURSOR⟫";

    private OracleComplete() {
    }

    /**
     * What a completion sends: the file's name, its language, the code
     * before and after the caret. Unlike an edit, a completion is a
     * window, so the caps CLIP (before from the front, after from the
     * end) and the request remembers that it clipped; an empty
     * {@code before} is refused — nothing to continue.
     */
    public record CompletionRequest(String fileName, String language, String before,
            String after, boolean clipped) {

        public CompletionRequest {
            fileName = fileName == null ? "" : fileName;
            language = language == null ? "" : language;
            before = before == null ? "" : before;
            after = after == null ? "" : after;
            if (before.isBlank()) {
                throw new IllegalArgumentException(
                        "Nothing to continue — the caret has no code before it.");
            }
        }

        /** Builds a request, clipping the window to the caps. */
        public static CompletionRequest around(String fileName, String language,
                String before, String after) {
            String b = before == null ? "" : before;
            String a = after == null ? "" : after;
            boolean clipped = false;
            if (b.length() > MAX_BEFORE_CHARS) {
                b = b.substring(b.length() - MAX_BEFORE_CHARS);
                clipped = true;
            }
            if (a.length() > MAX_AFTER_CHARS) {
                a = a.substring(0, MAX_AFTER_CHARS);
                clipped = true;
            }
            return new CompletionRequest(fileName, language, b, a, clipped);
        }

        /** The characters this request sends (for the consent line). */
        public int codeChars() {
            return before.length() + after.length();
        }
    }

    /** The prompt: the window with the caret marked, and the reply contract. */
    public static String assembleCompletionPrompt(CompletionRequest r) {
        StringBuilder sb = new StringBuilder(r.codeChars() + 900);
        sb.append("You are completing code at the caret inside a developer's IDE. ")
                .append("You are given ONLY the code around the caret below — not the ")
                .append("rest of the file, not the project. The caret is marked ")
                .append(CURSOR).append(". Reply with ONLY the text to insert at the ")
                .append("caret, in exactly one fenced code block (```), and nothing ")
                .append("outside the fence. Continue naturally from the characters ")
                .append("just before the caret: do not repeat them, do not restate ")
                .append("the code after the caret, and stop where a careful ")
                .append("developer would pause (the end of the statement, block, or ")
                .append("declaration). If nothing sensible can be inserted, reply in ")
                .append("prose (no fence) saying why.\n\n");
        if (!r.fileName().isBlank()) {
            sb.append("File: ").append(r.fileName()).append('\n');
        }
        if (!r.language().isBlank()) {
            sb.append("Language: ").append(r.language()).append('\n');
        }
        if (r.clipped()) {
            sb.append("(The window was clipped to fit; the file continues beyond it.)\n");
        }
        sb.append("\nCode around the caret:\n```\n")
                .append(r.before()).append(CURSOR).append(r.after())
                .append("\n```\n");
        return sb.toString();
    }

    /**
     * The insertion text out of a reply: the single fenced block, or null
     * for prose / ambiguity (the caller refuses out loud). A block that
     * merely repeats the current line's head is trimmed of that repeat,
     * because models often echo the prefix they were told not to. The
     * honest ceiling (v2.61.1 review, recorded not fixed): a reply that
     * LEGITIMATELY begins with the same characters as the line head is
     * trimmed too — a text-only reply channel cannot say which it meant.
     */
    public static String extractInsertion(String reply, String lineBeforeCaret) {
        String code = OracleEdit.extractFencedCode(reply);
        if (code == null) {
            return null;
        }
        String head = lineBeforeCaret == null ? "" : lineBeforeCaret;
        String trimmedHead = head.stripLeading();
        if (!trimmedHead.isEmpty()) {
            if (code.startsWith(head)) {
                code = code.substring(head.length());
            } else if (code.startsWith(trimmedHead)) {
                code = code.substring(trimmedHead.length());
            }
        }
        if (code.endsWith("\n")) {
            code = code.substring(0, code.length() - 1);
        }
        return code.isEmpty() ? null : code;
    }

    /** The first line of an insertion, for the inline ghost; the rest is counted. */
    public static String firstLine(String insertion) {
        int nl = insertion.indexOf('\n');
        return nl < 0 ? insertion : insertion.substring(0, nl);
    }

    /** Lines beyond the first, for the "+N lines" hint; 0 for a one-liner. */
    public static int moreLines(String insertion) {
        int n = 0;
        for (int i = 0; i < insertion.length(); i++) {
            if (insertion.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }
}
