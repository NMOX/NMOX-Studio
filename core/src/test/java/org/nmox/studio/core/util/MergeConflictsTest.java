package org.nmox.studio.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Git's unresolved conflict, recognised by its line-anchored markers and nothing else. */
class MergeConflictsTest {

    private static final String OURS = "<".repeat(7);
    private static final String SEP = "=".repeat(7);
    private static final String THEIRS = ">".repeat(7);

    private static String conflicted(String nl) {
        return "{" + nl
                + "  \"requests\": [" + nl
                + OURS + " HEAD" + nl
                + "    {\"name\": \"alice\"}" + nl
                + SEP + nl
                + "    {\"name\": \"bob\"}" + nl
                + THEIRS + " feature" + nl
                + "  ]" + nl
                + "}" + nl;
    }

    @Test
    @DisplayName("git's conflict, with LF or CRLF line ends, is recognised")
    void recognised() {
        assertThat(MergeConflicts.hasMarkers(conflicted("\n"))).isTrue();
        assertThat(MergeConflicts.hasMarkers(conflicted("\r\n"))).isTrue();
    }

    @Test
    @DisplayName("diff3 style (a base section) is recognised too")
    void diff3() {
        String text = OURS + " HEAD\na\n" + "|".repeat(7) + " base\nb\n" + SEP + "\nc\n" + THEIRS + " x\n";
        assertThat(MergeConflicts.hasMarkers(text)).isTrue();
    }

    @Test
    @DisplayName("markers inside a value, or an unpaired one, are not a conflict")
    void notAConflict() {
        assertThat(MergeConflicts.hasMarkers("{\"note\": \"" + OURS + " HEAD then " + THEIRS + " x\"}")).isFalse();
        assertThat(MergeConflicts.hasMarkers(OURS + " HEAD\nonly an opening\n")).isFalse();
        assertThat(MergeConflicts.hasMarkers(THEIRS + " x\n" + OURS + " HEAD\n")).as("closing before opening").isFalse();
        assertThat(MergeConflicts.hasMarkers(OURS + "< eight\n" + THEIRS + "> eight\n")).as("eight is not seven").isFalse();
        assertThat(MergeConflicts.hasMarkers("")).isFalse();
        assertThat(MergeConflicts.hasMarkers(null)).isFalse();
    }

    @Test
    @DisplayName("a marker at the very end of the text, with no newline, still closes")
    void endOfText() {
        assertThat(MergeConflicts.hasMarkers(OURS + "\na\n" + SEP + "\nb\n" + THEIRS)).isTrue();
    }
}
