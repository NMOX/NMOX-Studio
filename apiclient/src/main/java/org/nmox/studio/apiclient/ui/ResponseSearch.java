package org.nmox.studio.apiclient.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Find-in-response (v1.198.0), the pure half: case-insensitive match
 * positions for the search field over the body text. Bounded — a
 * pathological query over a megabyte body stops at {@link #MAX_MATCHES}
 * rather than building an unbounded list (the count label shows "+" so
 * the truncation is honest).
 */
final class ResponseSearch {

    static final int MAX_MATCHES = 10_000;

    private ResponseSearch() {
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
            from = at + 1; // overlapping matches count — "aa" in "aaa" is 2
        }
        return out;
    }

    /** The match to jump to after {@code current}, wrapping. -1 when none. */
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
