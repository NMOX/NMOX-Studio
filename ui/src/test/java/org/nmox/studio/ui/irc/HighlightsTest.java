package org.nmox.studio.ui.irc;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mention matcher, tested hard: word-boundary nick matches (a nick
 * inside a longer word must NOT fire), punctuation boundaries, case
 * insensitivity, IRC-legal nick characters, and the extra-keyword list.
 */
class HighlightsTest {

    @Test
    @DisplayName("The bare nick as a word matches, case-insensitively")
    void bareNickMatches() {
        assertThat(Highlights.matches("dave", List.of(), "dave: got a minute?")).isTrue();
        assertThat(Highlights.matches("dave", List.of(), "thanks Dave!")).isTrue();
        assertThat(Highlights.matches("dave", List.of(), "DAVE")).isTrue();
    }

    @Test
    @DisplayName("A nick inside a longer word does NOT match")
    void substringDoesNotMatch() {
        assertThat(Highlights.matches("dave", List.of(), "davenport is a city")).isFalse();
        assertThat(Highlights.matches("dave", List.of(), "superdave strikes again")).isFalse();
        assertThat(Highlights.matches("dave", List.of(), "dave2 was here")).isFalse();
        assertThat(Highlights.matches("dave", List.of(), "dave_ has spoken"))
                .as("underscore is a nick character — dave_ is a DIFFERENT nick")
                .isFalse();
    }

    @Test
    @DisplayName("Punctuation and whitespace are boundaries")
    void punctuationBounds() {
        assertThat(Highlights.matches("dave", List.of(), "hey,dave!")).isTrue();
        assertThat(Highlights.matches("dave", List.of(), "(dave)")).isTrue();
        assertThat(Highlights.matches("dave", List.of(), "ping dave?")).isTrue();
        assertThat(Highlights.matches("dave", List.of(), "@dave review please"))
                .as("@ is a status sigil in text, not a nick char")
                .isTrue();
    }

    @Test
    @DisplayName("Nicks with IRC-legal special characters match whole")
    void specialCharacterNicks() {
        assertThat(Highlights.matches("d[v]e", List.of(), "hi d[v]e how goes")).isTrue();
        assertThat(Highlights.matches("d[v]e", List.of(), "hi d[v]ee")).isFalse();
    }

    @Test
    @DisplayName("Extra keywords highlight like the nick does")
    void extraKeywords() {
        List<String> kw = List.of("nmox", "release day");
        assertThat(Highlights.matches("dave", kw, "is NMOX down?")).isTrue();
        assertThat(Highlights.matches("dave", kw, "nmoxstudio is fine"))
                .as("keyword obeys the same word boundary")
                .isFalse();
        assertThat(Highlights.matches("dave", kw, "happy Release Day everyone")).isTrue();
    }

    @Test
    @DisplayName("Empty text, empty nick, and null lists are quiet no-matches")
    void honestEmpties() {
        assertThat(Highlights.matches("dave", List.of(), "")).isFalse();
        assertThat(Highlights.matches("dave", null, "hello world")).isFalse();
        assertThat(Highlights.matches("", List.of(), "hello")).isFalse();
        assertThat(Highlights.matches(null, List.of("x"), "an x marker")).isTrue();
        assertThat(Highlights.matches("dave", List.of(""), "anything"))
                .as("an empty keyword never matches everything")
                .isFalse();
    }
}
