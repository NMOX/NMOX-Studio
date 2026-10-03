package org.nmox.studio.editor.snippets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Which characters before the caret are a prefix being typed, and which after it are the same token's rest. */
class SnippetPrefixTest {

    @Test
    @DisplayName("the longest run before the caret that starts the prefix, when it starts a token")
    void typed() {
        assertThat(SnippetPrefix.typed("lo", "log")).isEqualTo(2);
        assertThat(SnippetPrefix.typed("  log", "log")).isEqualTo(3);
        assertThat(SnippetPrefix.typed("console.lo", "log")).isEqualTo(2);
        assertThat(SnippetPrefix.typed("x = lo", "log")).isEqualTo(2);
        assertThat(SnippetPrefix.typed("LoG", "log")).isEqualTo(3);
        assertThat(SnippetPrefix.typed("logg", "log")).as("more typed than the prefix has").isEqualTo(-1);
    }

    @Test
    @DisplayName("inside another word a prefix is not being typed")
    void notInsideAWord() {
        assertThat(SnippetPrefix.typed("xlo", "log")).isEqualTo(-1);
        assertThat(SnippetPrefix.typed("catalog", "log")).isEqualTo(-1);
        assertThat(SnippetPrefix.typed("ключlo", "log")).as("a letter of any script is part of a word").isEqualTo(-1);
        assertThat(SnippetPrefix.typed("abc", "log")).isEqualTo(-1);
    }

    @Test
    @DisplayName("a prefix that begins with punctuation starts a token wherever it is")
    void punctuationPrefixes() {
        assertThat(SnippetPrefix.typed("items.lo", ".log")).isEqualTo(3);
        assertThat(SnippetPrefix.typed("x!", "!html")).isEqualTo(1);
        assertThat(SnippetPrefix.typed("@comp", "@component")).isEqualTo(5);
    }

    @Test
    @DisplayName("nothing typed yet: the line's start, a space, punctuation")
    void freshSpot() {
        assertThat(SnippetPrefix.typed("", "log")).isZero();
        assertThat(SnippetPrefix.typed("    ", "log")).isZero();
        assertThat(SnippetPrefix.typed("call(", "log")).isZero();
        assertThat(SnippetPrefix.typed("a.", "log")).isZero();
    }

    @Test
    @DisplayName("the tail is the token's remaining word characters, bounded")
    void tail() {
        assertThat(SnippetPrefix.tail("g.x")).isEqualTo(1);
        assertThat(SnippetPrefix.tail("ger_2$ rest")).isEqualTo(6);
        assertThat(SnippetPrefix.tail(" next")).isZero();
        assertThat(SnippetPrefix.tail("")).isZero();
        assertThat(SnippetPrefix.tail("a".repeat(500))).isEqualTo(SnippetPrefix.MAX_TAIL);
    }

    @Test
    @DisplayName("the word at the caret touches it on either side")
    void wordAt() {
        assertThat(SnippetPrefix.wordAt("const total = 1", 8)).isEqualTo("total");
        assertThat(SnippetPrefix.wordAt("const total = 1", 11)).isEqualTo("total");
        assertThat(SnippetPrefix.wordAt("const total = 1", 6)).isEqualTo("total");
        assertThat(SnippetPrefix.wordAt("a  b", 2)).isNull();
        assertThat(SnippetPrefix.wordAt("", 0)).isNull();
        assertThat(SnippetPrefix.wordAt("abc", 99)).isEqualTo("abc");
    }
}
