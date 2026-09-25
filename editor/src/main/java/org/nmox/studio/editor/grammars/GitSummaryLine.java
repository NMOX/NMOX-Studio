package org.nmox.studio.editor.grammars;

/**
 * The one rule a commit message's first line answers to (3.2.0), kept
 * pure so every edge is a unit test: git's own tools — {@code git log
 * --oneline}, {@code format-patch} subjects, most hosting UIs — cut a
 * summary line at 72 characters, so a longer one is read truncated.
 *
 * <p>Deliberately narrow. Only the FIRST line is judged, because only
 * the summary has a length law (the body wraps however its author
 * likes); a first line that starts with {@code #} is git's template, not
 * a summary, and a message with no summary yet has nothing to judge.
 * The 50-character soft limit VS Code also marks is not raised here: a
 * warning past 50 fires on most real summaries and would teach the
 * reader to ignore the one past 72.
 */
public final class GitSummaryLine {

    /** Where git tools cut a summary line. */
    public static final int LIMIT = 72;

    private GitSummaryLine() {
    }

    /**
     * An over-long summary: its length in characters (code points, so an
     * emoji counts once), and the document offsets of the part past the
     * limit — the span a hint should mark.
     */
    public record Overflow(int length, int start, int end) {
    }

    /**
     * The overflow of {@code text}'s first line, or {@code null} when that
     * line is within the limit, is a {@code #} comment, or is empty.
     */
    public static Overflow overflow(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        int nl = text.indexOf('\n');
        int end = nl < 0 ? text.length() : nl;
        if (end > 0 && text.charAt(end - 1) == '\r') {
            end--;
        }
        String line = text.substring(0, end);
        if (line.startsWith("#")) {
            return null;
        }
        int length = line.codePointCount(0, line.length());
        if (length <= LIMIT) {
            return null;
        }
        return new Overflow(length, line.offsetByCodePoints(0, LIMIT), end);
    }
}
