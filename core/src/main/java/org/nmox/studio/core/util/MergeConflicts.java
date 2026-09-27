package org.nmox.studio.core.util;

/**
 * Whether a file holds git's unresolved merge conflict (3.4, question 1).
 *
 * <p>The studios commit their workspaces beside the code, and teammates
 * merge them. Measured before 3.4: all seven studio files treated git's
 * conflict markers as ordinary corruption — the studio fell back to an
 * empty or starter workspace, and the next ordinary save (a Send, a query
 * Run, any edit) wrote that over the conflicted file, so {@code git commit}
 * recorded the loss as the merge; the rack moved the file aside, so
 * {@code git commit -am} deleted it. A conflicted file is neither corrupt
 * nor ours to repair: it holds BOTH people's work, and the one correct
 * answer is to leave it exactly as it is, write nothing over it, and say
 * that git is waiting for a resolution.
 *
 * <p>The markers are git's line-anchored triple: a line opening with seven
 * {@code <}, a line of exactly seven {@code =} (or opening
 * {@code |||||||} for diff3 style), and a line opening with seven
 * {@code >}. Inside a JSON workspace a marker can only appear at the start
 * of a line when git wrote it — string values escape their newlines — so an
 * opening and a closing marker together are the verdict. Built from
 * repeated characters here and in the tests, because the repository's own
 * gate refuses a literal marker at the start of any tracked line.
 */
public final class MergeConflicts {

    private static final String OURS = "<".repeat(7);
    private static final String THEIRS = ">".repeat(7);

    private MergeConflicts() {
    }

    /** True when {@code text} holds at least one unresolved conflict: an opening and a closing marker line. */
    public static boolean hasMarkers(CharSequence text) {
        if (text == null) {
            return false;
        }
        boolean opened = false;
        int len = text.length();
        int lineStart = 0;
        while (lineStart < len) {
            if (startsWith(text, lineStart, OURS)) {
                opened = true;
            } else if (opened && startsWith(text, lineStart, THEIRS)) {
                return true;
            }
            int next = indexOf(text, '\n', lineStart);
            if (next < 0) {
                break;
            }
            lineStart = next + 1;
        }
        return false;
    }

    private static boolean startsWith(CharSequence text, int at, String marker) {
        if (at + marker.length() > text.length()) {
            return false;
        }
        for (int i = 0; i < marker.length(); i++) {
            if (text.charAt(at + i) != marker.charAt(i)) {
                return false;
            }
        }
        // git writes "<<<<<<< HEAD" / ">>>>>>> branch": a space or the line's end follows
        int after = at + marker.length();
        return after == text.length() || text.charAt(after) == ' '
                || text.charAt(after) == '\n' || text.charAt(after) == '\r';
    }

    private static int indexOf(CharSequence text, char c, int from) {
        for (int i = from; i < text.length(); i++) {
            if (text.charAt(i) == c) {
                return i;
            }
        }
        return -1;
    }
}
