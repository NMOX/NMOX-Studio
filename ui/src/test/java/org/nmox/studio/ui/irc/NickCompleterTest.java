package org.nmox.studio.ui.irc;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Tab completer's contract: prefix match (case-insensitive),
 * {@code "nick: "} at line start vs {@code "nick "} mid-line, cycling
 * on repeated Tab with wraparound, and a reset (any other key) that
 * breaks the cycle.
 */
class NickCompleterTest {

    private final List<String> nicks = List.of("alice", "Albert", "bob", "carol");

    @Test
    @DisplayName("At line start the completion is an address: 'nick: '")
    void lineStartAddsColonSpace() {
        NickCompleter c = new NickCompleter();
        NickCompleter.Result r = c.complete("al", 2, nicks);
        assertThat(r).isNotNull();
        assertThat(r.text()).isEqualTo("alice: ");
        assertThat(r.caret()).isEqualTo(7);
    }

    @Test
    @DisplayName("Mid-line the completion is just the nick plus a space")
    void midLineAddsPlainSpace() {
        NickCompleter c = new NickCompleter();
        NickCompleter.Result r = c.complete("thanks al", 9, nicks);
        assertThat(r).isNotNull();
        assertThat(r.text()).isEqualTo("thanks alice ");
        assertThat(r.caret()).isEqualTo(13);
    }

    @Test
    @DisplayName("Matching is case-insensitive but the completed nick keeps its case")
    void caseInsensitiveMatchKeepsNickCase() {
        NickCompleter c = new NickCompleter();
        NickCompleter.Result r = c.complete("ALB", 3, nicks);
        assertThat(r.text()).isEqualTo("Albert: ");
    }

    @Test
    @DisplayName("Repeated Tab cycles through every match and wraps around")
    void repeatedTabCyclesAndWraps() {
        NickCompleter c = new NickCompleter();
        NickCompleter.Result r1 = c.complete("a", 1, nicks);
        assertThat(r1.text()).isEqualTo("alice: ");
        NickCompleter.Result r2 = c.complete(r1.text(), r1.caret(), nicks);
        assertThat(r2.text()).isEqualTo("Albert: ");
        NickCompleter.Result r3 = c.complete(r2.text(), r2.caret(), nicks);
        assertThat(r3.text()).as("two matches wrap back to the first").isEqualTo("alice: ");
    }

    @Test
    @DisplayName("Cycling preserves text after the caret")
    void cyclingPreservesTail() {
        NickCompleter c = new NickCompleter();
        // caret after "al", with a tail the user already typed
        NickCompleter.Result r1 = c.complete("al, got a sec?", 2, nicks);
        assertThat(r1.text()).isEqualTo("alice: , got a sec?");
        NickCompleter.Result r2 = c.complete(r1.text(), r1.caret(), nicks);
        assertThat(r2.text()).isEqualTo("Albert: , got a sec?");
    }

    @Test
    @DisplayName("reset() breaks the cycle: the next Tab re-reads the prefix")
    void resetBreaksTheCycle() {
        NickCompleter c = new NickCompleter();
        NickCompleter.Result r1 = c.complete("a", 1, nicks);
        c.reset(); // the user typed something else
        NickCompleter.Result r2 = c.complete(r1.text(), r1.caret(), nicks);
        assertThat(r2).as("'alice: ' as a fresh prefix matches nothing").isNull();
    }

    @Test
    @DisplayName("No match, empty prefix, and bad caret all return null")
    void honestNulls() {
        NickCompleter c = new NickCompleter();
        assertThat(c.complete("zz", 2, nicks)).isNull();
        assertThat(c.complete("", 0, nicks)).isNull();
        assertThat(c.complete("hello ", 6, nicks)).as("caret after a space").isNull();
        assertThat(c.complete("al", 99, nicks)).as("caret out of range").isNull();
        assertThat(c.complete(null, 0, nicks)).isNull();
    }
}
