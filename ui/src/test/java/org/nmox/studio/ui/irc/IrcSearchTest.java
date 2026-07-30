package org.nmox.studio.ui.irc;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The find-bar arithmetic: case-insensitive offsets, overlapping
 * matches, the bounded-list cap, and wrapping next-match cycling.
 */
class IrcSearchTest {

    @Test
    @DisplayName("Matches are case-insensitive start offsets")
    void caseInsensitiveOffsets() {
        assertThat(IrcSearch.matches("Hello hello HELLO", "hello"))
                .containsExactly(0, 6, 12);
    }

    @Test
    @DisplayName("Overlapping matches all count")
    void overlappingMatches() {
        assertThat(IrcSearch.matches("aaa", "aa")).containsExactly(0, 1);
    }

    @Test
    @DisplayName("A blank query or null text is an empty result")
    void honestEmpties() {
        assertThat(IrcSearch.matches("text", "")).isEmpty();
        assertThat(IrcSearch.matches("text", null)).isEmpty();
        assertThat(IrcSearch.matches(null, "q")).isEmpty();
    }

    @Test
    @DisplayName("The match list caps at MAX_MATCHES (bounded, honest)")
    void capHolds() {
        String text = "a".repeat(IrcSearch.MAX_MATCHES + 500);
        assertThat(IrcSearch.matches(text, "a")).hasSize(IrcSearch.MAX_MATCHES);
    }

    @Test
    @DisplayName("next() cycles forward and wraps to the first match")
    void nextWraps() {
        List<Integer> m = List.of(3, 9, 20);
        assertThat(IrcSearch.next(m, -1)).isEqualTo(3);
        assertThat(IrcSearch.next(m, 3)).isEqualTo(9);
        assertThat(IrcSearch.next(m, 20)).as("wraps").isEqualTo(3);
        assertThat(IrcSearch.next(List.of(), 0)).isEqualTo(-1);
    }
}
