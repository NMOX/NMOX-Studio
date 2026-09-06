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

    /**
     * Where the LEXER's mime is coarser than the file's kind: the product
     * opens {@code .jsx} under {@code text/javascript} and {@code .tsx}
     * under {@code text/typescript} (one lexer pipeline serves both), so the
     * mime alone would tag a React component {@code javascript} — and a
     * renderer given {@code javascript} flags the markup. The walk found it:
     * the fence must read the extension first. Keys are lower-case extensions.
     */
    static final Map<String, String> EXTENSION_TAGS = Map.of(
            "jsx", "jsx",
            "tsx", "tsx",
            "vue", "vue",
            "svelte", "svelte",
            "astro", "astro");

    private CopyAsMarkdown() {
    }

    /** The fence info string for a file: its extension where the lexer mime is coarser, else the mime's tag. */
    public static String fence(String mime, String fileName) {
        if (fileName != null) {
            int dot = fileName.lastIndexOf('.');
            if (dot >= 0 && dot < fileName.length() - 1) {
                String ext = fileName.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
                String byExt = EXTENSION_TAGS.get(ext);
                if (byExt != null) {
                    return byExt;
                }
            }
        }
        return fence(mime);
    }

    /** The fence info string for a mime; {@code text} when nothing better is known. */
    public static String fence(String mime) {
        String id = LspLanguageIds.forMime(mime);
        if (id == null || id.isBlank()) {
            return "text";
        }
        return FENCE_EXCEPTIONS.getOrDefault(id, id);
    }

    /** The fenced block for a file: see {@link #fence(String, String)}. */
    public static String block(String code, String mime, String fileName) {
        return blockWithTag(code, fence(mime, fileName));
    }

    /** The fenced block: a fence longer than any backtick run inside, the tag, the code, one trailing newline. */
    public static String block(String code, String mime) {
        return blockWithTag(code, fence(mime));
    }

    private static String blockWithTag(String code, String tag) {
        String body = code == null ? "" : code;
        body = body.replace("\r\n", "\n");
        while (body.endsWith("\n")) {
            body = body.substring(0, body.length() - 1);
        }
        String fence = "`".repeat(Math.max(3, longestBacktickRun(body) + 1));
        return fence + tag + "\n" + body + "\n" + fence + "\n";
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
