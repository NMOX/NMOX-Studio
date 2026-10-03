package org.nmox.studio.editor.snippets;

/**
 * Which part of the text before the caret is the start of a snippet's
 * prefix: the question the completion list asks of every snippet, and
 * the span an accepted snippet replaces.
 *
 * <p>The rule: the longest run of characters ending at the caret that
 * is the beginning of the prefix (letters matched without regard to
 * case), provided it begins a token. {@code lo|} is two characters of
 * {@code log}; {@code console.lo|} is too, since the dot ends the token
 * before; {@code xlo|} is none, because {@code lo} there is the tail of
 * another word. At a place where nothing has been typed yet (the line's
 * start, after a space or after punctuation) the answer is zero
 * characters and every snippet is a candidate, as VS Code's own list
 * has it.
 *
 * <p>A "word character" here is the identifier vocabulary of the
 * languages snippets are written for: ASCII letters, digits,
 * {@code _} and {@code $}, plus every character beyond ASCII, which is
 * counted as part of a word so that a prefix is never offered in the
 * middle of a name written in another script. Code syntax, not prose:
 * the reader's language decides nothing here.
 */
final class SnippetPrefix {

    /** A token tail longer than this is not folded into the replacement. */
    static final int MAX_TAIL = 64;

    private SnippetPrefix() {
    }

    /**
     * How many characters at the end of {@code before} start
     * {@code prefix}; 0 when the caret is at a fresh spot; -1 when the
     * caret is inside a word the prefix does not start.
     */
    static int typed(String before, String prefix) {
        int most = Math.min(before.length(), prefix.length());
        for (int k = most; k >= 1; k--) {
            int at = before.length() - k;
            if (!before.regionMatches(true, at, prefix, 0, k)) {
                continue;
            }
            boolean startsToken = at == 0 || !wordChar(before.charAt(at - 1)) || !wordChar(prefix.charAt(0));
            if (startsToken) {
                return k;
            }
        }
        return before.isEmpty() || !wordChar(before.charAt(before.length() - 1)) ? 0 : -1;
    }

    /**
     * How many characters at the head of {@code after} are the rest of
     * the token the caret is in: accepting {@code log} at {@code lo|g}
     * replaces the {@code g} too, instead of leaving it behind the
     * inserted body.
     */
    static int tail(String after) {
        int i = 0;
        while (i < after.length() && i < MAX_TAIL && wordChar(after.charAt(i))) {
            i++;
        }
        return i;
    }

    /** The word at {@code column} of {@code line} (touching it on either side), or null. */
    static String wordAt(String line, int column) {
        int col = Math.max(0, Math.min(column, line.length()));
        int from = col;
        while (from > 0 && wordChar(line.charAt(from - 1))) {
            from--;
        }
        int to = col;
        while (to < line.length() && wordChar(line.charAt(to))) {
            to++;
        }
        return from == to ? null : line.substring(from, to);
    }

    static boolean wordChar(char c) {
        return c == '_' || c == '$' || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z') || c >= 0x80;
    }
}
