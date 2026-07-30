package org.nmox.studio.ui.irc;

import java.util.ArrayList;
import java.util.List;

/**
 * Find-in-transcript, the pure half (the API Studio v1.198.0
 * {@code ResponseSearch} idiom, re-stated here rather than reached
 * across modules): case-insensitive match offsets over the transcript
 * text, bounded at {@link #MAX_MATCHES} so a one-letter query over a
 * night's backlog can't build an unbounded list — the find bar's count
 * label shows a {@code +} when the cap bit, keeping the truncation
 * honest. The Swing find bar owns highlights and caret jumps; this
 * class owns the arithmetic.
 */
final class IrcSearch {

    static final int MAX_MATCHES = 10_000;

    private IrcSearch() {
    }

    /** Start offsets of every match, capped; empty for a blank query. */
    static List<Integer> matches(String text, String query) {
        List<Integer> out = new ArrayList<>();
        if (text == null || query == null || query.isEmpty()) {
            return out;
        }
        String haystack = text.toLowerCase(java.util.Locale.ROOT);
        String needle = query.toLowerCase(java.util.Locale.ROOT);
        int from = 0;
        while (out.size() < MAX_MATCHES) {
            int at = haystack.indexOf(needle, from);
            if (at < 0) {
                break;
            }
            out.add(at);
            from = at + 1; // overlapping matches count
        }
        return out;
    }

    /** The match to jump to after {@code caret}, wrapping. −1 when none. */
    static int next(List<Integer> matches, int caret) {
        if (matches.isEmpty()) {
            return -1;
        }
        for (int m : matches) {
            if (m > caret) {
                return m;
            }
        }
        return matches.get(0);
    }
}
