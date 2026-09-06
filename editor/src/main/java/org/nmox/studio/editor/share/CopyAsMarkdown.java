package org.nmox.studio.editor.share;

import java.util.Map;
import org.nmox.studio.editor.lsp.LspLanguageIds;

/**
 * The pure half of Copy as Markdown (v2.87.0, the developer-evangelist
 * grant): a code snippet as a fenced Markdown block whose info string is
 * the language a renderer will highlight. The tag comes from the
 * product's ONE mime→language vocabulary ({@link LspLanguageIds}), with a
 * small exception table where an LSP id is not a fence name GitHub knows
 * ({@code shellscript} → {@code bash}). Two things a hand-typed fence
 * gets wrong are handled here: the code always ends in exactly one
 * newline before the closing fence (the v2.49.1 trailing-newline lesson),
 * and a snippet that itself contains a backtick run gets a LONGER fence —
 * CommonMark opens the block with any run of three or more and closes it
 * only with a run at least as long, so a ``` inside a ``` block would end
 * it early.
 */
public final class CopyAsMarkdown {

    static final Map<String, String> FENCE_EXCEPTIONS = Map.of(
            "shellscript", "bash",
            "plaintext", "text");

    private CopyAsMarkdown() {
    }

    /** The fence info string for a mime; {@code text} when nothing better is known. */
    public static String fence(String mime) {
        String id = LspLanguageIds.forMime(mime);
        if (id == null || id.isBlank()) {
            return "text";
        }
        return FENCE_EXCEPTIONS.getOrDefault(id, id);
    }

    /** The fenced block: a fence longer than any backtick run inside, the tag, the code, one trailing newline. */
    public static String block(String code, String mime) {
        String body = code == null ? "" : code;
        body = body.replace("\r\n", "\n");
        while (body.endsWith("\n")) {
            body = body.substring(0, body.length() - 1);
        }
        String fence = "`".repeat(Math.max(3, longestBacktickRun(body) + 1));
        return fence + fence(mime) + "\n" + body + "\n" + fence + "\n";
    }

    static int longestBacktickRun(String s) {
        int best = 0;
        int run = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '`') {
                run++;
                best = Math.max(best, run);
            } else {
                run = 0;
            }
        }
        return best;
    }

    /** Lines in a snippet, for the status line ("copied 12 lines"). */
    public static int lineCount(String code) {
        if (code == null || code.isEmpty()) {
            return 0;
        }
        int n = 1;
        for (int i = 0; i < code.length(); i++) {
            if (code.charAt(i) == '\n' && i < code.length() - 1) {
                n++;
            }
        }
        return n;
    }
}
